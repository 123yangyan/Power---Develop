import asyncio
import json
import logging
import uuid
from datetime import datetime, timedelta, timezone
from functools import lru_cache

import redis
from apscheduler.schedulers.background import BackgroundScheduler
from apscheduler.triggers.cron import CronTrigger
from fastapi import Depends, FastAPI, File, Form, HTTPException, UploadFile
from fastapi.responses import FileResponse
from sqlalchemy.orm import Session

from app.auth import verify_api_key
from app.config import get_settings
from app.database import SessionLocal, get_db, init_db
from app.models import AudioFile
from app.vitals_api import router as vitals_router
# oss_client 延迟导入，避免 OSS 配置错误导致 API 无法启动
from app.schemas import (
    ApiResponse,
    AudioResultDto,
    BatchResultRequest,
    BatchResultResponseData,
    SubmitResultData,
    SubmitResultRequest,
    UploadResponseData,
    WorkerNextJobData,
    error,
    success,
    to_audio_result,
)

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="TimedRecorder API", version="1.0.0")

# 身心数据 API
app.include_router(vitals_router)


@lru_cache
def get_redis():
    settings = get_settings()
    return redis.from_url(settings.redis_url, decode_responses=True)


@app.on_event("startup")
def on_startup() -> None:
    init_db()
    _start_aggregation_scheduler()
    logger.info("API started")


@app.get("/dashboard")
@app.get("/dashboard/")
def dashboard_page():
    """返回身心交织看板单页（由 FastAPI 提供，避免 nginx alias 500）。"""
    from pathlib import Path

    html_path = Path(__file__).parent / "static" / "dashboard.html"
    if not html_path.exists():
        raise HTTPException(status_code=404, detail="dashboard.html not found")
    return FileResponse(str(html_path), media_type="text/html")


@app.get("/api/health")
def health() -> dict:
    return {"status": "ok"}


@app.post("/api/audio/upload", response_model=ApiResponse[UploadResponseData], dependencies=[Depends(verify_api_key)])
async def upload_audio(
    file: UploadFile = File(...),
    fileName: str = Form(...),
    format: str = Form(...),
    startTime: str = Form(...),
    endTime: str = Form(...),
    duration: str = Form(...),
    deviceId: str = Form(...),
    localId: str = Form(...),
    db: Session = Depends(get_db),
):
    from app.oss_client import build_object_key, upload_bytes

    settings = get_settings()
    file_id = uuid.uuid4().hex
    content = await file.read()
    if not content:
        raise HTTPException(status_code=400, detail="Empty file")

    object_key = build_object_key(deviceId, file_id, fileName)
    content_type = "audio/mp4" if format.lower() in {"m4a", "aac"} else "application/octet-stream"
    try:
        await asyncio.to_thread(upload_bytes, object_key, content, content_type)
    except Exception as exc:
        logger.exception("OSS upload failed")
        return error(500, f"OSS upload failed: {exc}")

    record = AudioFile(
        file_id=file_id,
        device_id=deviceId,
        local_id=localId,
        file_name=fileName,
        format=format,
        start_time=startTime,
        end_time=endTime,
        duration=int(duration),
        oss_key=object_key,
        status="pending",
    )
    db.add(record)
    db.commit()

    get_redis().lpush(settings.job_queue_key, file_id)
    logger.info(
        "Upload OK file_id=%s device=%s file=%s oss_key=%s queued",
        file_id, deviceId, fileName, object_key,
    )
    return success(UploadResponseData(fileId=file_id, status="uploaded"))


@app.get("/api/audio/result", response_model=ApiResponse[AudioResultDto], dependencies=[Depends(verify_api_key)])
def get_result(fileId: str, db: Session = Depends(get_db)):
    record = db.get(AudioFile, fileId)
    if record is None:
        return error(404, "file not found")
    return success(to_audio_result(record))


@app.post("/api/audio/result/batch", response_model=ApiResponse[BatchResultResponseData], dependencies=[Depends(verify_api_key)])
def batch_result(request: BatchResultRequest, db: Session = Depends(get_db)):
    results: list[AudioResultDto] = []
    for file_id in request.fileIds:
        record = db.get(AudioFile, file_id)
        if record is not None:
            results.append(to_audio_result(record))
    return success(BatchResultResponseData(results=results))


# ---------- 本地 Worker 接口 ----------

@app.get(
    "/api/worker/next-job",
    response_model=ApiResponse[WorkerNextJobData | None],
    dependencies=[Depends(verify_api_key)],
)
def get_next_job(db: Session = Depends(get_db)):
    """
    返回最早的一条待处理任务（status=pending），附带 OSS 签名下载 URL。
    若没有待处理任务则返回 data=null。
    Worker 轮询间隔建议 5 秒。
    """
    from app.oss_client import generate_signed_url

    settings = get_settings()

    # --- 陈腐任务回收：超过 2 分钟还卡在 processing 的自动退回 pending ---
    STALE_MINUTES = 2
    stale_cutoff = datetime.now(timezone.utc) - timedelta(minutes=STALE_MINUTES)
    recovered = (
        db.query(AudioFile)
        .filter(
            AudioFile.status == "processing",
            AudioFile.processing_at.isnot(None),
            AudioFile.processing_at < stale_cutoff,
        )
        .update(
            {"status": "pending", "processing_at": None},
            synchronize_session=False,
        )
    )
    if recovered:
        db.commit()
        logger.info("Recovered %d stale processing job(s)", recovered)

    # --- 查找待处理任务 ---
    record = (
        db.query(AudioFile)
        .filter(AudioFile.status == "pending")
        .order_by(AudioFile.created_at.asc())
        .first()
    )

    if record is None:
        return success(None)

    # 标记为 processing，防止其他 Worker 重复领取
    record.status = "processing"
    record.processing_at = datetime.now(timezone.utc)
    db.commit()

    signed_url = generate_signed_url(
        record.oss_key, settings.signed_url_expires_seconds
    )

    return success(
        WorkerNextJobData(
            fileId=record.file_id,
            fileName=record.file_name,
            signedUrl=signed_url,
        )
    )


@app.post(
    "/api/worker/submit-result",
    response_model=ApiResponse[SubmitResultData],
    dependencies=[Depends(verify_api_key)],
)
def submit_result(request: SubmitResultRequest, db: Session = Depends(get_db)):
    """
    接收本地 Worker 的 ASR 转写 + LLM 分析结果，回写到数据库。
    """
    record = db.get(AudioFile, request.fileId)
    if record is None:
        return error(404, "file not found")

    if request.transcript:
        record.transcript = request.transcript
        record.title = request.title or "录音笔记"
        record.summary = request.summary
        record.keywords_json = json.dumps(request.keywords, ensure_ascii=False)
        record.alert_flag = request.alertFlag
        record.risk_level = request.riskLevel
        record.message = request.message or None
        record.status = "completed"
        record.processed_at = datetime.now(timezone.utc)
        record.processing_at = None
        record.error_message = None
    else:
        # 空转写内容 → 标记失败
        record.status = "failed"
        record.processing_at = None
        record.error_message = request.message or "转写结果为空"

    db.commit()

    return success(
        SubmitResultData(fileId=request.fileId, status=record.status)
    )


# ---------- APScheduler：降采样聚合定时任务 ----------

_scheduler: BackgroundScheduler | None = None


def _start_aggregation_scheduler() -> None:
    """启动 APScheduler，每天凌晨 02:00 UTC 执行 A 组降采样聚合。"""
    global _scheduler
    from app.agg_job import run_hourly_aggregation

    def _job() -> None:
        db = SessionLocal()
        try:
            run_hourly_aggregation(db)
        except Exception:
            logger.exception("Aggregation job failed")
        finally:
            db.close()

    _scheduler = BackgroundScheduler(timezone="UTC")
    _scheduler.add_job(
        _job,
        trigger=CronTrigger(hour=2, minute=0),
        id="hourly_aggregation",
        replace_existing=True,
    )
    _scheduler.start()
    logger.info("APScheduler started: hourly_aggregation at 02:00 UTC daily")
