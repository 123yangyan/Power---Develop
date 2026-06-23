"""
生理状态分类器 — 加权 z-score 焦虑评分。

9 个核心焦虑敏感指标加权累加 → 0-100 焦虑分，
皮温辅助调节置信度，ACC 运动抑制，冷却管理。
"""
import json
import logging
import math
from datetime import datetime, timezone
from typing import Optional

import redis
from sqlalchemy.orm import Session

from app.vitals_models import (
    HrvAnalysisResult,
    PhysioStateClassification,
    PpiAnalysisWindow,
)

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------
# 指标权重（方案 Phase 3）
# ---------------------------------------------------------------

# 指标 → (权重, 焦虑方向: -1 = 低值焦虑, +1 = 高值焦虑)
WEIGHTS = {
    "rmssd":           (0.12, -1),  # 副交感撤退
    "pnn50":           (0.08, -1),  # 逐跳变异减少
    "sdnn":            (0.05, -1),  # 整体变异性受呼吸变浅影响
    "sd1_sd2":         (0.08, -1),  # 短/长程变异性调节失衡
    "lf_hf":           (0.12, +1),  # 交感激活
    "sampen":          (0.08, -1),  # 心律更规则
    "dfa_alpha1_dev":  (0.08, +1),  # |α1 - 1.0| 分形退化
    "hr_surge":        (0.15, +1),  # 静息心率突升（最急性应激信号）
    "breathing_rate":  (0.08, +1),  # 深腹式→浅胸式
}

# 皮肤温度辅助因子
SKIN_TEMP_CONFIRM_THRESHOLD = -0.3   # 下降 >0.3°C → 确认
SKIN_TEMP_CONFIRM_FACTOR = 1.2       # ×1.2
SKIN_TEMP_SUPPRESS_THRESHOLD = 0.5   # 上升 >0.5°C → 抑制
SKIN_TEMP_SUPPRESS_FACTOR = 0.7      # ×0.7

# ACC 运动抑制权重
ACC_SUPPRESS_FACTOR = 0.5

# 状态阈值
THRESHOLDS = [
    (0,  20,  "calm",         0),       # 无通知
    (20, 40,  "normal",       0),       # 仅记录
    (40, 60,  "elevated",     120),     # 2h 冷却
    (60, 80,  "anxious",      60),      # 1h 冷却
    (80, 100, "high_anxiety", 30),      # 30min 冷却
]

# 指标 → DB 字段名（与 HrvAnalysisResult 列名对齐）
METRIC_DB_FIELDS = {
    "hr_surge": "hr_surge_flag",
    "dfa_alpha1_dev": "dfa_alpha1",
}

# z-score 安全边界
MAX_Z_SCORE = 3.0


# ---------------------------------------------------------------
# 分类器
# ---------------------------------------------------------------

class StateClassifier:
    """多指标加权 z-score 焦虑分类器。"""

    def __init__(self, baseline_manager, redis_client: redis.Redis):
        self.baseline = baseline_manager
        self.redis = redis_client

    # -----------------------------------------------------------
    # 公共 API
    # -----------------------------------------------------------

    def classify(
        self,
        hrv_result: HrvAnalysisResult,
        window: Optional[PpiAnalysisWindow] = None,
        db: Optional[Session] = None,
    ) -> PhysioStateClassification:
        """
        对单窗口 HRV 结果进行状态分类。

        返回 PhysioStateClassification（已写入 DB）。
        """
        device_id = hrv_result.device_id

        # 1. 获取基线
        baseline = self.baseline.get_baseline(device_id)
        if baseline is None:
            return self._make_no_baseline_result(hrv_result, db)

        if not self.baseline.is_mature(device_id):
            return self._make_immature_result(hrv_result, db)

        z_scores: dict[str, float] = {}

        # 2. 各指标 z-score 加权
        raw_score = 0.0
        total_weight = 0.0

        for metric, (weight, direction) in WEIGHTS.items():
            db_field = METRIC_DB_FIELDS.get(metric, metric)
            current_raw = getattr(hrv_result, db_field, None)
            if current_raw is None:
                continue

            # dfa_alpha1_dev = |α1 - 1.0|
            if metric == "dfa_alpha1_dev":
                current_val = abs(float(current_raw) - 1.0)
                baseline_mean = 0.0  # 理想值是 1.0，偏离即异常
                baseline_std = baseline.get("dfa_alpha1_std", 0.1) or 0.1
            elif metric == "hr_surge":
                current_val = 1.0 if current_raw else 0.0
                baseline_mean = 0.0  # 正常为 0（无突升）
                baseline_std = 0.3    # 二值化，需要较小 std 才有 z-score
            else:
                current_val = float(current_raw)
                baseline_mean = baseline.get(metric)
                baseline_std = baseline.get(f"{metric}_std", 0.01) or 0.01

            if baseline_mean is None:
                continue

            # z-score（方向校正）
            raw_z = (current_val - baseline_mean) / max(baseline_std, 0.01)
            z = max(-MAX_Z_SCORE, min(MAX_Z_SCORE, raw_z))  # clamp

            # 按焦虑方向调整符号：低值焦虑则取反
            adjusted_z = z * direction
            z_scores[metric] = round(adjusted_z, 3)

            raw_score += weight * adjusted_z
            total_weight += weight

        if total_weight == 0:
            return self._make_no_baseline_result(hrv_result, db)

        # 归一化到 0-100（z-score 和 → 百分位映射）
        # 默认基线 z=0 → 分数=30（正常中点），z=±3 → 0/100
        normalized = 30.0 + (raw_score / total_weight) * (70.0 / MAX_Z_SCORE)
        anxiety_score = max(0.0, min(100.0, normalized))

        # 3. 皮肤温度调节（从 skin_temp_samples 查 5 分钟滑动均值）
        skin_temp_delta = None
        skin_temp_c = self._query_skin_temp_c(db, device_id, hrv_result.ts)
        if skin_temp_c is not None:
            bl_temp = baseline.get("skin_temp_c")
            if bl_temp is not None:
                skin_temp_delta = round(float(skin_temp_c) - bl_temp, 3)

        if skin_temp_delta is not None:
            if skin_temp_delta < SKIN_TEMP_CONFIRM_THRESHOLD:
                anxiety_score *= SKIN_TEMP_CONFIRM_FACTOR
                logger.debug("Skin temp confirm: delta=%.2f score=%.1f", skin_temp_delta, anxiety_score)
            elif skin_temp_delta > SKIN_TEMP_SUPPRESS_THRESHOLD:
                anxiety_score *= SKIN_TEMP_SUPPRESS_FACTOR
                logger.debug("Skin temp suppress: delta=%.2f score=%.1f", skin_temp_delta, anxiety_score)
            anxiety_score = max(0.0, min(100.0, anxiety_score))

        # 4. ACC 运动抑制
        acc_suppressed = False
        if window is not None and window.acc_magnitude_mean is not None:
            if window.acc_magnitude_mean > 1030.0:  # >1g+30mg 即有明显运动
                anxiety_score *= ACC_SUPPRESS_FACTOR
                acc_suppressed = True
                logger.debug("ACC suppress: mag=%.0f score=%.1f", window.acc_magnitude_mean, anxiety_score)

        # 5. 状态标签
        state_label = "calm"
        cooldown_minutes = 0
        for lo, hi, label, cooldown in THRESHOLDS:
            if lo <= anxiety_score < hi:
                state_label = label
                cooldown_minutes = cooldown
                break
        if anxiety_score >= 100:
            state_label = "high_anxiety"
            cooldown_minutes = 30

        # 6. 冷却检查
        should_notify = False
        cooldown_until = None

        if state_label in ("elevated", "anxious", "high_anxiety"):
            cooldown_key = f"cooldown:{device_id}"
            current_cooldown = self.redis.get(cooldown_key)
            now_ts = int(datetime.now(timezone.utc).timestamp())
            if current_cooldown is None:
                should_notify = True
                if cooldown_minutes > 0:
                    cooldown_until = now_ts + cooldown_minutes * 60
                    self.redis.setex(cooldown_key, cooldown_minutes * 60, str(cooldown_until))
            else:
                logger.debug("Cooldown active for device=%s until=%s", device_id, current_cooldown)

        # 7. 落库
        classification = PhysioStateClassification(
            window_id=hrv_result.window_id,
            device_id=device_id,
            ts=hrv_result.ts,
            anxiety_score=round(anxiety_score, 1),
            state_label=state_label,
            z_scores_json=json.dumps(z_scores),
            skin_temp_delta=skin_temp_delta,
            acc_suppressed=acc_suppressed,
            should_notify=should_notify,
            cooldown_until=cooldown_until,
        )
        if db is not None:
            db.add(classification)
            db.commit()
            db.refresh(classification)

        logger.info(
            "Classification: device=%s score=%.1f label=%s notify=%s z=%s",
            device_id, anxiety_score, state_label, should_notify, z_scores,
        )

        return classification

    # -----------------------------------------------------------
    # 内部
    # -----------------------------------------------------------

    def _make_no_baseline_result(
        self, hrv_result: HrvAnalysisResult, db: Optional[Session] = None,
    ) -> PhysioStateClassification:
        """基线缺失时返回默认（仅记录，不通知）。"""
        classification = PhysioStateClassification(
            window_id=hrv_result.window_id,
            device_id=hrv_result.device_id,
            ts=hrv_result.ts,
            anxiety_score=0.0,
            state_label="normal",
            z_scores_json="{}",
            should_notify=False,
        )
        if db is not None:
            db.add(classification)
            db.commit()
            db.refresh(classification)
        return classification

    def _make_immature_result(
        self, hrv_result: HrvAnalysisResult, db: Optional[Session] = None,
    ) -> PhysioStateClassification:
        """基线未成熟时仅记录，不计算焦虑分、不触发通知。"""
        classification = PhysioStateClassification(
            window_id=hrv_result.window_id,
            device_id=hrv_result.device_id,
            ts=hrv_result.ts,
            anxiety_score=0.0,
            state_label="baseline_building",
            z_scores_json="{}",
            should_notify=False,
        )
        if db is not None:
            db.add(classification)
            db.commit()
            db.refresh(classification)
        return classification

    @staticmethod
    def _query_skin_temp_c(
        db: Optional[Session], device_id: str, window_end_ts: int,
    ) -> Optional[float]:
        """查询窗口结束前 5 分钟内的皮肤温度均值。"""
        if db is None:
            return None
        from sqlalchemy import func

        from app.vitals_models import SkinTempSample

        five_min_ms = 5 * 60 * 1000
        avg_temp = (
            db.query(func.avg(SkinTempSample.temperature_c))
            .filter(
                SkinTempSample.device_id == device_id,
                SkinTempSample.ts >= window_end_ts - five_min_ms,
                SkinTempSample.ts <= window_end_ts,
            )
            .scalar()
        )
        return float(avg_temp) if avg_temp is not None else None
