"""
生理状态分析管道 — P2 HRV 分析 → 基线更新 → P3 分类 → P4 LLM → P5 推送。
"""
import logging
from typing import Optional

from sqlalchemy.orm import Session

from app.database import SessionLocal
from app.redis_client import get_redis
from app.services.baseline_manager import BaselineManager
from app.services.feedback_loop_service import FeedbackLoopService
from app.services.hrv_analysis_service import HrvAnalysisService
from app.services.llm_feedback_service import LlmFeedbackService
from app.services.push_service import PushService
from app.services.state_classifier import StateClassifier
from app.vitals_models import LlmFeedbackHistory, MoodEntry, PpiAnalysisWindow

logger = logging.getLogger(__name__)

_services: Optional["PhysioPipelineServices"] = None


class PhysioPipelineServices:
    """懒加载单例，共享 Redis 与各服务实例。"""

    def __init__(self) -> None:
        redis_client = get_redis()
        self.baseline_manager = BaselineManager(redis_client)
        self.hrv_service = HrvAnalysisService(baseline_manager=self.baseline_manager)
        self.state_classifier = StateClassifier(self.baseline_manager, redis_client)
        self.feedback_loop = FeedbackLoopService(SessionLocal)
        self.llm_service = LlmFeedbackService(feedback_loop=self.feedback_loop)
        self.push_service = PushService(redis_client)


def get_services() -> PhysioPipelineServices:
    global _services
    if _services is None:
        _services = PhysioPipelineServices()
    return _services


def _get_last_mood(db: Session, device_id: str) -> Optional[str]:
    entry = (
        db.query(MoodEntry)
        .filter(MoodEntry.device_id == device_id)
        .order_by(MoodEntry.occurred_at.desc())
        .first()
    )
    if entry is None:
        return None
    fact = (entry.fact or "").strip()
    if not fact:
        return None
    if len(fact) > 60:
        fact = fact[:57] + "..."
    return fact


def _get_last_ignored_feedback_ts(db: Session, device_id: str) -> Optional[int]:
    row = (
        db.query(LlmFeedbackHistory)
        .filter(
            LlmFeedbackHistory.device_id == device_id,
            LlmFeedbackHistory.user_response.is_(None),
        )
        .order_by(LlmFeedbackHistory.ts.desc())
        .first()
    )
    return row.ts if row else None


async def run_physio_pipeline(window_id: int) -> None:
    """BackgroundTasks 入口：对单个 PPI 窗口执行完整分析链。"""
    db = SessionLocal()
    try:
        services = get_services()
        window = db.query(PpiAnalysisWindow).filter_by(id=window_id).first()
        if window is None:
            logger.warning("Pipeline skip: window %d not found", window_id)
            return

        hrv_result = services.hrv_service.analyze_window(window_id, db)
        if hrv_result is None:
            logger.info("Pipeline stop: HRV analysis failed window=%d", window_id)
            return

        services.baseline_manager.update(window.device_id, hrv_result)

        classification = services.state_classifier.classify(hrv_result, window, db)
        if not classification.should_notify:
            logger.debug(
                "Pipeline stop: no notify device=%s label=%s score=%.1f",
                window.device_id,
                classification.state_label,
                classification.anxiety_score,
            )
            return

        baseline = services.baseline_manager.get_baseline(window.device_id)
        feedback = await services.llm_service.generate(
            classification=classification,
            hrv_result=hrv_result,
            baseline=baseline,
            last_mood=_get_last_mood(db, window.device_id),
            last_feedback_ts=_get_last_ignored_feedback_ts(db, window.device_id),
            db=db,
        )

        message = (feedback.get("message") or "").strip()
        if not message:
            logger.debug("Pipeline stop: empty LLM message window=%d", window_id)
            return

        sent = await services.push_service.send(
            device_id=window.device_id,
            message=message,
            state_label=classification.state_label,
            classification_id=classification.id,
            db=db,
        )
        logger.info(
            "Pipeline done: window=%d device=%s notify=%s push_sent=%s",
            window_id,
            window.device_id,
            classification.state_label,
            sent,
        )
    except Exception:
        logger.exception("Pipeline failed for window=%d", window_id)
    finally:
        db.close()


def snapshot_all_baselines() -> None:
    """APScheduler 任务：将所有 Redis 基线快照写入 DB。"""
    services = get_services()
    db = SessionLocal()
    try:
        count = 0
        for key in get_redis().scan_iter("baseline:*"):
            device_id = key.split(":", 1)[1]
            if services.baseline_manager.snapshot_daily(device_id, db):
                count += 1
        logger.info("Baseline snapshot job done: %d device(s)", count)
    except Exception:
        logger.exception("Baseline snapshot job failed")
    finally:
        db.close()
