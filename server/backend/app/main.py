import asyncio
import logging
import uuid
from functools import lru_cache

import redis
from fastapi import Depends, FastAPI, File, Form, HTTPException, UploadFile
from sqlalchemy.orm import Session

from app.auth import verify_api_key
from app.config import get_settings
from app.database import get_db, init_db
from app.models import AudioFile
# oss_client 延迟导入，避免 OSS 配置错误导致 API 无法启动
from app.schemas import (
    ApiResponse,
    AudioResultDto,
    BatchResultRequest,
    BatchResultResponseData,
    UploadResponseData,
    error,
    success,
    to_audio_result,
)

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="TimedRecorder API", version="1.0.0")


@lru_cache
def get_redis():
    settings = get_settings()
    return redis.from_url(settings.redis_url, decode_responses=True)


@app.on_event("startup")
def on_startup() -> None:
    init_db()
    logger.info("API started")


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
