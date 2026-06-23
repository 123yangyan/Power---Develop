"""
反馈闭环服务 — 用户响应追踪 + 模板权重进化 + LLM 上下文丰富。

Phase 6: 四条反馈通路：
  1. 基线个性化 — 用户标注后标记对应的 HRV 窗口
  2. 模板进化   — 追踪每种 tone 的响应率
  3. LLM 上下文 — 记忆过往情绪模式
  4. 标注积累   — 500+ 标注后可用于训练 ML 模型
"""
import json
import logging
from datetime import datetime, timedelta, timezone
from typing import Optional

from sqlalchemy.orm import Session

from app.vitals_models import (
    LlmFeedbackHistory,
    PhysioStateLabel,
    TemplateWeight,
)

logger = logging.getLogger(__name__)

# 用户通知行为类型
RESPONSE_LOGGED = "logged"      # 点按"记录心情"并在 5 分钟内记录
RESPONSE_SNOOZED = "snoozed"    # 点按"稍后提醒"
RESPONSE_DISMISSED = "dismissed"  # 点按"今日不再提醒"
RESPONSE_IGNORED = "ignored"     # 无任何操作

# 标注类型
VALID_LABELS = {"anxious", "calm", "stressed", "normal", "happy", "sad"}


class FeedbackLoopService:
    """反馈闭环 — 响应率追踪 + 用户标注 + LLM 记忆增强。"""

    def __init__(self, db_session_factory):
        self.db_factory = db_session_factory

    # -----------------------------------------------------------
    # 1. 用户响应处理
    # -----------------------------------------------------------

    def on_mood_logged(
        self,
        device_id: str,
        mood_label: str,
        timestamp_ms: int,
        window_id: Optional[int] = None,
    ) -> None:
        """
        用户记录心情后调用：
        - 若在最近一次通知 5 分钟内 → 标记响应 + 更新模板权重
        - 写入 physio_state_labels
        """
        db = self.db_factory()
        try:
            self._record_label(db, device_id, mood_label, window_id, "user")

            five_min_ms = 5 * 60 * 1000
            # 查找最近 5 分钟内已推送的通知
            five_min_ago = datetime.now(timezone.utc) - timedelta(minutes=5)
            recent_notif = (
                db.query(LlmFeedbackHistory)
                .filter(
                    LlmFeedbackHistory.device_id == device_id,
                    LlmFeedbackHistory.triggered_notification == True,
                    LlmFeedbackHistory.created_at >= five_min_ago,
                )
                .order_by(LlmFeedbackHistory.created_at.desc())
                .first()
            )

            if recent_notif and abs(timestamp_ms - recent_notif.ts) <= five_min_ms:
                recent_notif.user_response = RESPONSE_LOGGED
                self._update_template_weight(db, recent_notif.state_label, recent_notif.tone, responded=True)
                logger.info("Feedback loop: device=%s logged mood within 5min of notification", device_id)
            else:
                logger.info("Feedback loop: device=%s logged mood (no recent notification)", device_id)

            db.commit()
        except Exception as e:
            db.rollback()
            logger.error("Feedback loop on_mood_logged error: %s", e)
        finally:
            db.close()

    def on_notification_dismissed(self, device_id: str, notification_id: Optional[int] = None) -> None:
        """用户点按"今日不再提醒"。"""
        self.on_notification_response(device_id, notification_id, "dismissed")

    def on_notification_snoozed(self, device_id: str, notification_id: Optional[int] = None) -> None:
        """用户点按"稍后提醒"。"""
        self.on_notification_response(device_id, notification_id, "snoozed")

    def on_notification_response(
        self,
        device_id: str,
        notification_id: Optional[int],
        response: str,
    ) -> None:
        """统一处理通知按钮响应（logged / snoozed / dismissed）。"""
        normalized = response.lower().strip()
        response_map = {
            "logged": RESPONSE_LOGGED,
            "snoozed": RESPONSE_SNOOZED,
            "dismissed": RESPONSE_DISMISSED,
        }
        mapped = response_map.get(normalized)
        if mapped is None:
            logger.warning("Unknown notification response: %s device=%s", response, device_id)
            return

        db = self.db_factory()
        try:
            notif = None
            if notification_id is not None:
                notif = db.query(LlmFeedbackHistory).filter_by(id=notification_id).first()
            if notif is None:
                notif = (
                    db.query(LlmFeedbackHistory)
                    .filter(
                        LlmFeedbackHistory.device_id == device_id,
                        LlmFeedbackHistory.triggered_notification == True,
                    )
                    .order_by(LlmFeedbackHistory.created_at.desc())
                    .first()
                )

            if notif is None:
                logger.info(
                    "Notification response ignored: no matching record device=%s response=%s",
                    device_id,
                    normalized,
                )
                return

            notif.user_response = mapped
            self._update_template_weight(
                db,
                notif.state_label,
                notif.tone,
                responded=(mapped == RESPONSE_LOGGED),
            )
            db.commit()
            logger.info(
                "Notification response recorded: device=%s notif_id=%s response=%s",
                device_id,
                notif.id,
                mapped,
            )
        except Exception as e:
            db.rollback()
            logger.error("Feedback loop notification response error: %s", e)
        finally:
            db.close()

    # -----------------------------------------------------------
    # 2. 模板权重查询（供 LLM 选择最佳 tone）
    # -----------------------------------------------------------

    def get_best_tone(self, state_label: str) -> str:
        """返回该状态下响应率最高的 tone。"""
        db = self.db_factory()
        try:
            weights = (
                db.query(TemplateWeight)
                .filter(TemplateWeight.state_label == state_label)
                .all()
            )
            if not weights:
                return self._default_tone(state_label)

            best = max(
                weights,
                key=lambda w: (w.response_count / max(w.total_count, 1)) if w.total_count > 3 else 0,
            )
            # 数据量少时用默认
            if best.total_count < 3:
                return self._default_tone(state_label)
            rate = best.response_count / max(best.total_count, 1)
            logger.debug("Best tone for %s: %s (%.0f%% response rate)", state_label, best.tone, rate * 100)
            return best.tone
        except Exception as e:
            logger.error("get_best_tone error: %s", e)
            return self._default_tone(state_label)
        finally:
            db.close()

    # -----------------------------------------------------------
    # 3. LLM 上下文历史模式
    # -----------------------------------------------------------

    def get_history_note(self, device_id: str) -> Optional[str]:
        """从过去 7 天的标注中提取模式描述，供 LLM context 使用。"""
        db = self.db_factory()
        try:
            from datetime import timedelta

            seven_days_ago = datetime.now(timezone.utc) - timedelta(days=7)
            labels = (
                db.query(PhysioStateLabel)
                .filter(
                    PhysioStateLabel.device_id == device_id,
                    PhysioStateLabel.labeled_at >= seven_days_ago,
                )
                .order_by(PhysioStateLabel.labeled_at.desc())
                .limit(10)
                .all()
            )

            if not labels:
                return None

            label_counts: dict[str, int] = {}
            for lbl in labels:
                label_counts[lbl.label] = label_counts.get(lbl.label, 0) + 1

            most_common = max(label_counts, key=label_counts.get)
            total = len(labels)

            response_count = 0
            db2 = self.db_factory()
            try:
                response_count = (
                    db2.query(LlmFeedbackHistory)
                    .filter(
                        LlmFeedbackHistory.device_id == device_id,
                        LlmFeedbackHistory.user_response == RESPONSE_LOGGED,
                        LlmFeedbackHistory.created_at >= seven_days_ago,
                    )
                    .count()
                )
            finally:
                db2.close()

            return (
                f"过去7天中有{total}次情绪标注，最常见的是'{most_common}'"
                f"（{label_counts[most_common]}次）。"
                f"收到生理反馈后{response_count}次记录了心情。"
            )
        except Exception as e:
            logger.error("get_history_note error: %s", e)
            return None
        finally:
            db.close()

    # -----------------------------------------------------------
    # 4. ML 标注数据积累查询
    # -----------------------------------------------------------

    def labeled_window_count(self, device_id: str) -> int:
        """检查该设备已积累的标注窗口数（用于判断是否可训练 ML）。"""
        db = self.db_factory()
        try:
            return db.query(PhysioStateLabel).filter_by(device_id=device_id).count()
        finally:
            db.close()

    # -----------------------------------------------------------
    # 内部
    # -----------------------------------------------------------

    @staticmethod
    def _default_tone(state_label: str) -> str:
        return {
            "elevated": "gentle_reminder",
            "anxious": "gentle_inquiry",
            "high_anxiety": "urgent_care",
        }.get(state_label, "gentle_inquiry")

    def _record_label(
        self, db: Session, device_id: str,
        label: str, window_id: Optional[int], labeled_by: str,
    ) -> None:
        """写入用户情绪标注。"""
        normalized = label.lower().strip()
        if normalized not in VALID_LABELS:
            normalized = "normal"
        entry = PhysioStateLabel(
            window_id=window_id,
            device_id=device_id,
            label=normalized,
            labeled_by=labeled_by,
        )
        db.add(entry)

    @staticmethod
    def _update_template_weight(
        db: Session, state_label: str, tone: str, responded: bool,
    ) -> None:
        """更新模板权重表。"""
        weight = (
            db.query(TemplateWeight)
            .filter_by(state_label=state_label, tone=tone)
            .first()
        )
        if weight is None:
            weight = TemplateWeight(
                state_label=state_label,
                tone=tone,
                response_count=1 if responded else 0,
                total_count=1,
            )
            db.add(weight)
        else:
            weight.total_count += 1
            if responded:
                weight.response_count += 1
