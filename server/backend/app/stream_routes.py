"""
实时生理状态推流端点 — 接收 Android PpiStreamWorker 推送的 PPI 窗口，
落库后异步触发 HRV 分析链（Phase 2→3→4→5）。
"""
import json
import logging
from datetime import datetime, timezone
from typing import Optional

from fastapi import APIRouter, BackgroundTasks, Depends, Query
from pydantic import BaseModel, Field
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.auth import verify_api_key
from app.database import get_db
from app.redis_client import get_redis
from app.services.baseline_manager import BaselineManager
from app.services.physio_pipeline import get_services, run_physio_pipeline
from app.vitals_models import (
    FcmToken,
    HrvAnalysisResult,
    LlmFeedbackHistory,
    PhysioStateClassification,
    PpiAnalysisWindow,
)

logger = logging.getLogger(__name__)

router = APIRouter(
    prefix="/api/vitals/stream",
    tags=["physio-stream"],
    dependencies=[Depends(verify_api_key)],
)


# ---------------------------------------------------------------
# Request / Response schemas
# ---------------------------------------------------------------

class PpiWindowRequest(BaseModel):
    device_id: str
    window_start_ts: int
    window_end_ts: int
    rr_list_ms: list[int] = Field(default_factory=list)
    n_raw: int = 0
    n_clean: int = 0
    on_device_rmssd: Optional[float] = None
    on_device_sdnn: Optional[float] = None
    acc_magnitude_mean: Optional[float] = None


class PpiWindowResponse(BaseModel):
    window_id: Optional[int] = None
    accepted: bool = True


class FcmRegisterRequest(BaseModel):
    device_id: str
    fcm_token: str


class SnoozeRequest(BaseModel):
    device_id: str
    delay_min: int = 15
    notification_id: Optional[int] = None


class NotificationResponseRequest(BaseModel):
    device_id: str
    notification_id: Optional[int] = None
    response: str  # "logged" | "snoozed" | "dismissed"


# ---------------------------------------------------------------
# 端点
# ---------------------------------------------------------------

@router.post("/ppi-window", response_model=PpiWindowResponse)
async def ingest_ppi_window(
    payload: PpiWindowRequest,
    background_tasks: BackgroundTasks,
    db: Session = Depends(get_db),
):
    """接收 Android 推流的 60–120 秒 PPI 窗口，写入数据库后异步触发 HRV 分析。"""
    if payload.n_clean < 25:
        logger.info(
            "Reject window: n_clean=%d < 25 device=%s",
            payload.n_clean, payload.device_id,
        )
        return PpiWindowResponse(window_id=None, accepted=False)

    existing = (
        db.query(PpiAnalysisWindow)
        .filter_by(
            device_id=payload.device_id,
            window_start_ts=payload.window_start_ts,
        )
        .first()
    )
    if existing:
        logger.info(
            "Duplicate window ignored: id=%d device=%s start=%d",
            existing.id, payload.device_id, payload.window_start_ts,
        )
        return PpiWindowResponse(window_id=existing.id, accepted=True)

    window = PpiAnalysisWindow(
        device_id=payload.device_id,
        window_start_ts=payload.window_start_ts,
        window_end_ts=payload.window_end_ts,
        rr_list_ms=json.dumps(payload.rr_list_ms),
        n_raw=payload.n_raw,
        n_clean=payload.n_clean,
        on_device_rmssd=payload.on_device_rmssd,
        on_device_sdnn=payload.on_device_sdnn,
        acc_magnitude_mean=payload.acc_magnitude_mean,
    )
    try:
        db.add(window)
        db.commit()
        db.refresh(window)
    except IntegrityError:
        db.rollback()
        existing = (
            db.query(PpiAnalysisWindow)
            .filter_by(
                device_id=payload.device_id,
                window_start_ts=payload.window_start_ts,
            )
            .first()
        )
        if existing:
            return PpiWindowResponse(window_id=existing.id, accepted=True)
        raise

    window_id = window.id
    logger.info(
        "Window ingested: id=%d device=%s n_clean=%d rmssd=%.1f sdnn=%.1f",
        window_id, payload.device_id, payload.n_clean,
        payload.on_device_rmssd or 0, payload.on_device_sdnn or 0,
    )

    # Phase 2–5 异步分析链
    background_tasks.add_task(run_physio_pipeline, window_id)

    return PpiWindowResponse(window_id=window_id, accepted=True)


@router.post("/register-token")
def register_fcm_token(payload: FcmRegisterRequest, db: Session = Depends(get_db)):
    """注册/更新 FCM 推送 token。"""
    existing = db.query(FcmToken).filter_by(device_id=payload.device_id).first()
    if existing:
        existing.token = payload.fcm_token
    else:
        db.add(FcmToken(device_id=payload.device_id, token=payload.fcm_token))
    db.commit()
    logger.info("FCM token registered for device=%s", payload.device_id)
    return {"ok": True}


@router.post("/snooze")
def snooze_notification(payload: SnoozeRequest):
    """用户选择稍后提醒 — 延后 Redis 冷却并记录 snooze 响应。"""
    delay_min = max(payload.delay_min, 1)
    delay_sec = delay_min * 60
    now_ts = int(datetime.now(timezone.utc).timestamp())
    cooldown_until = now_ts + delay_sec

    cooldown_key = f"cooldown:{payload.device_id}"
    get_redis().setex(cooldown_key, delay_sec, str(cooldown_until))

    get_services().feedback_loop.on_notification_snoozed(
        payload.device_id,
        payload.notification_id,
    )

    logger.info(
        "Snooze applied: device=%s delay=%d min until=%d",
        payload.device_id,
        delay_min,
        cooldown_until,
    )
    return {"ok": True, "cooldown_until": cooldown_until}


@router.post("/notification-response")
def report_notification_response(payload: NotificationResponseRequest):
    """用户对通知的响应回报 — 写入反馈历史并更新模板权重。"""
    get_services().feedback_loop.on_notification_response(
        device_id=payload.device_id,
        notification_id=payload.notification_id,
        response=payload.response,
    )
    logger.info(
        "Notification response handled: device=%s notif_id=%s response=%s",
        payload.device_id,
        payload.notification_id,
        payload.response,
    )
    return {"ok": True}


def _baseline_window_count(device_id: str) -> int:
    baseline = BaselineManager(get_redis()).get_baseline(device_id)
    if baseline is None:
        return 0
    return int(baseline.get("window_count", 0))


def _hrv_to_dict(hrv: HrvAnalysisResult | None) -> dict | None:
    if hrv is None:
        return None
    return {
        "rmssd": hrv.rmssd,
        "sdnn": hrv.sdnn,
        "lf_hf": hrv.lf_hf,
        "breathing_rate": hrv.breathing_rate,
        "sampen": hrv.sampen,
        "dfa_alpha1": hrv.dfa_alpha1,
        "hr_surge_flag": bool(hrv.hr_surge_flag),
    }


def _classification_to_dict(
    classification: PhysioStateClassification | None,
    *,
    default_ts_ms: int = 0,
) -> dict:
    if classification is None:
        return {
            "state_label": "baseline_building",
            "anxiety_score": 0.0,
            "window_id": 0,
            "created_at_ms": default_ts_ms,
        }
    return {
        "state_label": classification.state_label,
        "anxiety_score": classification.anxiety_score,
        "window_id": classification.window_id,
        "created_at_ms": classification.ts,
    }


def _feedback_to_dict(feedback: LlmFeedbackHistory | None) -> dict | None:
    if feedback is None:
        return None
    return {"message": feedback.message}


def _feedback_history_item(feedback: LlmFeedbackHistory) -> dict:
    return {
        "id": feedback.id,
        "created_at_ms": feedback.ts,
        "state_label": feedback.state_label,
        "anxiety_score": feedback.anxiety_score,
        "message": feedback.message,
        "tone": feedback.tone,
        "user_response": feedback.user_response,
    }


@router.get("/status")
def get_device_status(
    device_id: str = Query(..., min_length=1),
    db: Session = Depends(get_db),
):
    """Android 状态 Tab 轮询 — 返回 flat JSON（无 ApiResponse 包装）。"""
    window_count = _baseline_window_count(device_id)

    latest_window = (
        db.query(PpiAnalysisWindow)
        .filter(PpiAnalysisWindow.device_id == device_id)
        .order_by(PpiAnalysisWindow.window_end_ts.desc())
        .first()
    )
    last_stream_ts = latest_window.window_end_ts if latest_window else 0

    classification = (
        db.query(PhysioStateClassification)
        .filter(PhysioStateClassification.device_id == device_id)
        .order_by(PhysioStateClassification.id.desc())
        .first()
    )

    hrv = (
        db.query(HrvAnalysisResult)
        .filter(HrvAnalysisResult.device_id == device_id)
        .order_by(HrvAnalysisResult.id.desc())
        .first()
    )

    latest_feedback = (
        db.query(LlmFeedbackHistory)
        .filter(LlmFeedbackHistory.device_id == device_id)
        .order_by(LlmFeedbackHistory.id.desc())
        .first()
    )

    feedback_history = (
        db.query(LlmFeedbackHistory)
        .filter(LlmFeedbackHistory.device_id == device_id)
        .order_by(LlmFeedbackHistory.id.desc())
        .limit(10)
        .all()
    )

    default_ts_ms = last_stream_ts or 0
    return {
        "window_count": window_count,
        "last_stream_ts": last_stream_ts,
        "latest_classification": _classification_to_dict(
            classification,
            default_ts_ms=default_ts_ms,
        ),
        "latest_hrv": _hrv_to_dict(hrv),
        "latest_feedback": _feedback_to_dict(latest_feedback),
        "feedback_history": [_feedback_history_item(item) for item in feedback_history],
    }
