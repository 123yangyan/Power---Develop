"""
个人基线管理器 — Redis EWMA 滑动基线 + 每日快照。

每个指标维护 24h 指数加权移动平均（α=0.05，约 4.5h 半衰期），
存储于 Redis（TTL 48h）。每日凌晨 03:00 UTC 快照写入 device_baseline_snapshots。
"""
import json
import logging
from datetime import datetime, timezone
from typing import Optional

import redis
from sqlalchemy.orm import Session

from app.vitals_models import DeviceBaselineSnapshot, HrvAnalysisResult

logger = logging.getLogger(__name__)

# EWMA 衰减率 — 约 4.5 小时半衰期
ALPHA = 0.05
# Redis TTL（秒）— 48 小时
TTL_SECONDS = 48 * 3600
# 基线成熟所需最少窗口数
MIN_WINDOWS = 50

# 纳入 EWMA 的指标
BASELINE_KEYS = [
    "rmssd", "sdnn", "pnn50", "lf_hf", "sd1_sd2",
    "sampen", "dfa_alpha1", "breathing_rate", "bpm",
]

# 基线中应存储标准差的指标（用于 z-score 计算）
STD_KEYS = [
    "rmssd_std", "sdnn_std", "pnn50_std", "lf_hf_std",
    "sd1_sd2_std", "sampen_std", "dfa_alpha1_std",
    "breathing_rate_std", "bpm_std",
]


class BaselineManager:
    """Redis 存储的个人 EWMA 基线。"""

    def __init__(self, redis_client: redis.Redis):
        self.redis = redis_client

    # -----------------------------------------------------------
    # 公共 API
    # -----------------------------------------------------------

    def get_baseline(self, device_id: str) -> Optional[dict]:
        """读取当前设备基线。无基线或已过期返回 None。"""
        key = f"baseline:{device_id}"
        raw = self.redis.get(key)
        if raw is None:
            return None
        try:
            return json.loads(raw)
        except json.JSONDecodeError:
            return None

    def update(
        self,
        device_id: str,
        hrv_result: HrvAnalysisResult,
        skip_if_movement: bool = True,
    ) -> dict:
        """EWMA 更新基线，返回更新后的基线 dict。"""
        key = f"baseline:{device_id}"
        current = self.get_baseline(device_id) or self._init_baseline(device_id)

        count = current.get("window_count", 0) + 1
        is_movement = bool(getattr(hrv_result, "is_movement", False))

        if skip_if_movement and is_movement:
            updated = dict(current)
            updated["window_count"] = count
            self.redis.setex(key, TTL_SECONDS, json.dumps(updated))
            logger.debug(
                "Baseline EWMA skipped (movement): device=%s windows=%d",
                device_id, count,
            )
            return updated

        updated: dict = {"window_count": count}

        for metric in BASELINE_KEYS:
            new_val = getattr(hrv_result, metric, None)
            old_val = current.get(metric)

            if new_val is not None and not (isinstance(new_val, float) and new_val != new_val):  # NaN check
                if old_val is not None:
                    updated[metric] = round(ALPHA * new_val + (1 - ALPHA) * old_val, 4)
                else:
                    # 首次写入直接采用当前值
                    updated[metric] = round(float(new_val), 4)
            else:
                updated[metric] = old_val

            # 跟踪标准差（用于 z-score）：使用 Welford 风格的指数加权
            std_key = f"{metric}_std"
            old_std = current.get(std_key)
            if new_val is not None and updated.get(metric) is not None:
                delta = float(new_val) - float(updated[metric])
                if old_std is not None:
                    updated[std_key] = round(
                        (1 - ALPHA) * old_std + ALPHA * abs(delta), 4
                    )
                else:
                    updated[std_key] = round(abs(delta), 4)
            else:
                updated[std_key] = old_std

        # 皮肤温度单独跟踪（慢变量）
        skin_temp = getattr(hrv_result, "skin_temp_c", None)
        if skin_temp is not None:
            old_st = current.get("skin_temp_c")
            updated["skin_temp_c"] = round(
                ALPHA * float(skin_temp) + (1 - ALPHA) * old_st, 4
            ) if old_st is not None else round(float(skin_temp), 4)
        else:
            updated["skin_temp_c"] = current.get("skin_temp_c")

        # 写入 Redis
        self.redis.setex(key, TTL_SECONDS, json.dumps(updated))
        logger.debug("Baseline updated: device=%s windows=%d", device_id, count)

        return updated

    def is_mature(self, device_id: str) -> bool:
        """基线是否已成熟（≥ MIN_WINDOWS 个窗口）。"""
        baseline = self.get_baseline(device_id)
        if baseline is None:
            return False
        return baseline.get("window_count", 0) >= MIN_WINDOWS

    def snapshot_daily(self, device_id: str, db: Session) -> Optional[DeviceBaselineSnapshot]:
        """将当前基线快照写入 DB（由 APScheduler 每日调用）。"""
        baseline = self.get_baseline(device_id)
        if baseline is None:
            return None

        today = datetime.now(timezone.utc).strftime("%Y-%m-%d")

        snap = DeviceBaselineSnapshot(
            device_id=device_id,
            date=today,
            window_count=baseline.get("window_count", 0),
            rmssd_ewma=baseline.get("rmssd"),
            sdnn_ewma=baseline.get("sdnn"),
            pnn50_ewma=baseline.get("pnn50"),
            lf_hf_ewma=baseline.get("lf_hf"),
            sd1_sd2_ewma=baseline.get("sd1_sd2"),
            sampen_ewma=baseline.get("sampen"),
            dfa_alpha1_ewma=baseline.get("dfa_alpha1"),
            breathing_rate_ewma=baseline.get("breathing_rate"),
            bpm_ewma=baseline.get("bpm"),
            skin_temp_c_ewma=baseline.get("skin_temp_c"),
            snapshot_json=json.dumps(baseline),
        )
        db.add(snap)
        db.commit()
        logger.info("Baseline snapshot saved: device=%s date=%s windows=%d",
                     device_id, today, baseline.get("window_count", 0))
        return snap

    # -----------------------------------------------------------
    # 内部
    # -----------------------------------------------------------

    def _init_baseline(self, device_id: str) -> dict:
        """初始化空基线。"""
        baseline: dict = {"device_id": device_id, "window_count": 0}
        for k in BASELINE_KEYS:
            baseline[k] = None
        for k in STD_KEYS:
            baseline[k] = None
        baseline["skin_temp_c"] = None
        return baseline
