"""
FCM 推送服务 — 将 LLM 反馈推送到 Android 设备。

Phase 5: FCM 推送 + 免打扰 + 日推上限 + 冷却管理。
"""
import logging
from datetime import datetime, timezone
from typing import Optional

import redis
from sqlalchemy.orm import Session

from app.vitals_models import FcmToken, LlmFeedbackHistory

logger = logging.getLogger(__name__)

# 免打扰时段：UTC+8 00:00–07:00 → UTC 16:00–22:59（左闭右开）
SLEEP_START_HOUR_UTC = 16
SLEEP_END_HOUR_UTC = 23

# 日推上限
MAX_NOTIFICATIONS_PER_DAY = 3


class PushService:
    """FCM 推送管理器。"""

    def __init__(self, redis_client: redis.Redis):
        self.redis = redis_client
        self._firebase_app = None

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
        发送 FCM 推送。返回 True 表示推送已发出。

        免打扰 / 日推上限 / 无 token 时静默跳过。
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

        # 3. 获取 FCM token
        fcm_token = None
        if db is not None:
            token_row = db.query(FcmToken).filter_by(device_id=device_id).first()
            if token_row:
                fcm_token = token_row.token

        if not fcm_token:
            if reserved_slot:
                self.redis.decr(daily_key)
            logger.debug("Push skipped: no FCM token for device=%s", device_id)
            return False

        # 4. 发送
        try:
            sent = self._send_fcm(fcm_token, message, state_label)
            if not sent:
                if reserved_slot:
                    self.redis.decr(daily_key)
                return False
        except Exception as e:
            if reserved_slot:
                self.redis.decr(daily_key)
            logger.error("FCM send failed for device=%s: %s", device_id, e)
            return False

        # 5. 更新 LLM 历史（仅在 FCM 实际发出后标记）
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

        logger.info("FCM push sent: device=%s label=%s", device_id, state_label)
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

    def _send_fcm(self, token: str, message: str, state_label: str) -> bool:
        """调用 Firebase Admin SDK 发送推送。返回 True 表示已实际发出。"""
        try:
            import firebase_admin
            from firebase_admin import messaging

            if not firebase_admin._apps:
                # Firebase 尚未初始化则跳过（需在启动时配置）
                logger.warning("Firebase not initialized, cannot send FCM")
                return False

            android_config = messaging.AndroidConfig(
                priority="high",
                notification=messaging.AndroidNotification(
                    channel_id="physio_feedback",
                    click_action="OPEN_MOOD_LOG",
                ),
            )

            msg = messaging.Message(
                token=token,
                notification=messaging.Notification(
                    title="身心状态",
                    body=message,
                ),
                data={
                    "type": "physio_feedback",
                    "state_label": state_label,
                    "click_action": "OPEN_MOOD_LOG",
                },
                android=android_config,
            )

            messaging.send(msg)
            return True
        except ImportError:
            logger.warning("firebase-admin not installed — FCM push unavailable")
            return False
        except Exception as e:
            logger.error("FCM send error: %s", e)
            raise
