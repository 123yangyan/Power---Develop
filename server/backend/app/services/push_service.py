"""
ntfy 推送服务 — 将 LLM 反馈推送到 Android 设备（经 ntfy App 订阅 topic）。

Phase 5: ntfy HTTP POST + 免打扰 + 日推上限 + 冷却管理。
"""
import logging
from datetime import datetime, timezone
from typing import Optional

import httpx
import redis
from sqlalchemy.orm import Session

from app.config import get_settings
from app.vitals_models import LlmFeedbackHistory

logger = logging.getLogger(__name__)

# 免打扰时段：UTC+8 00:00–07:00 → UTC 16:00–22:59（左闭右开）
SLEEP_START_HOUR_UTC = 16
SLEEP_END_HOUR_UTC = 23

# 日推上限
MAX_NOTIFICATIONS_PER_DAY = 3


class PushService:
    """ntfy 推送管理器。"""

    def __init__(self, redis_client: redis.Redis):
        self.redis = redis_client

    # -----------------------------------------------------------
    # 公共 API
    # -----------------------------------------------------------

    async def send(
        self,
        device_id: str,
        message: str,
        state_label: str,
        classification_id: Optional[int] = None,
        db: Optional[Session] = None,
        bypass_limits: bool = False,
    ) -> bool:
        """
        发送 ntfy 推送。返回 True 表示推送已发出。

        免打扰 / 日推上限 / ntfy 未配置时静默跳过。
        """
        # 1. 免打扰时段检查
        if not bypass_limits and self._is_sleep_time():
            logger.debug("Push skipped: sleep time for device=%s", device_id)
            return False

        daily_key = f"daily_notif:{device_id}:{datetime.now(timezone.utc).strftime('%Y%m%d')}"
        reserved_slot = False

        # 2. 日推上限（原子 INCR 占位，失败时回滚）
        if not bypass_limits:
            new_count = int(self.redis.incr(daily_key))
            if new_count == 1:
                self.redis.expire(daily_key, 86400)
            if new_count > MAX_NOTIFICATIONS_PER_DAY:
                self.redis.decr(daily_key)
                logger.debug("Push skipped: daily limit reached for device=%s", device_id)
                return False
            reserved_slot = True

        # 3. ntfy 配置检查
        settings = get_settings()
        if not settings.ntfy_topic_prefix.strip():
            if reserved_slot:
                self.redis.decr(daily_key)
            logger.debug("Push skipped: ntfy_topic_prefix not configured for device=%s", device_id)
            return False

        # 4. 发送
        try:
            sent = await self._send_ntfy(device_id, message, state_label)
            if not sent:
                if reserved_slot:
                    self.redis.decr(daily_key)
                return False
        except Exception as e:
            if reserved_slot:
                self.redis.decr(daily_key)
            logger.error("ntfy send failed for device=%s: %s", device_id, e)
            return False

        # 5. 更新 LLM 历史（仅在 ntfy 实际发出后标记）
        if db is not None and classification_id is not None:
            feedback = (
                db.query(LlmFeedbackHistory)
                .filter_by(classification_id=classification_id)
                .order_by(LlmFeedbackHistory.created_at.desc())
                .first()
            )
            if feedback:
                feedback.triggered_notification = True
                db.commit()

        logger.info("ntfy push sent: device=%s label=%s", device_id, state_label)
        return True

    async def send_test(self, device_id: str, message: str, db: Optional[Session] = None) -> bool:
        """发送测试推送（绕开免打扰和日推上限）。"""
        return await self.send(
            device_id,
            message,
            "test",
            db=db,
            bypass_limits=True,
        )

    # -----------------------------------------------------------
    # 内部
    # -----------------------------------------------------------

    def _is_sleep_time(self) -> bool:
        hour = datetime.now(timezone.utc).hour
        return SLEEP_START_HOUR_UTC <= hour < SLEEP_END_HOUR_UTC

    async def _send_ntfy(self, device_id: str, message: str, state_label: str) -> bool:
        """POST to ntfy topic. Returns True when server accepted the message."""
        settings = get_settings()
        prefix = settings.ntfy_topic_prefix.strip()
        topic = f"{prefix}-{device_id}"
        base = settings.ntfy_server.rstrip("/")
        url = f"{base}/{topic}"

        async with httpx.AsyncClient(timeout=10.0) as client:
            response = await client.post(
                url,
                content=message.encode("utf-8"),
                headers={
                    "Title": "生理状态提醒",
                    "Priority": "high",
                    "Tags": "heart",
                    "Content-Type": "text/plain; charset=utf-8",
                },
            )

        if response.status_code != 200:
            logger.warning(
                "ntfy publish failed topic=%s status=%s body=%s",
                topic,
                response.status_code,
                response.text[:200],
            )
            return False
        return True
