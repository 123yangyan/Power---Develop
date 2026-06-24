"""
身心数据表（13 张时序表 + 3 张小时聚合表）。

A 组：实时 BLE 流 — hr / skin_temp / acc_minute / ppi（高频，30d 降采样）
B 组：设备离线同步 — activity_day / hr_247 / ppi_247 / skin_temp_247 /
      nightly_recharge / activity_minute / sleep / training
C 组：用户输入 — mood

统一列约定：
  - device_id   : 设备标识（indexed）
  - source      : "LIVE_STREAM" | "OFFLINE_SYNC" | "USER_INPUT"
  - client_row_id : Android 端主键（与 device_id + source 构成唯一约束）
  - ts          : 时间戳 ms
  - created_at  : 服务端接收时间

幂等上报：(device_id, source, client_row_id, ts) UNIQUE + ON CONFLICT DO NOTHING
"""

from datetime import datetime, timezone

from sqlalchemy import (
    BigInteger,
    Boolean,
    DateTime,
    Float,
    Integer,
    String,
    Text,
    UniqueConstraint,
)
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


class VitalMixin:
    device_id: Mapped[str] = mapped_column(String(64), index=True, nullable=False)
    source: Mapped[str] = mapped_column(String(32), nullable=False, default="LIVE_STREAM")
    client_row_id: Mapped[str] = mapped_column(String(64), nullable=False)
    ts: Mapped[int] = mapped_column(BigInteger, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)

    @classmethod
    def __declare_last__(cls) -> None:
        tbl = cls.__table__
        tbl.append_constraint(
            UniqueConstraint("device_id", "source", "client_row_id", "ts", name=f"uq_{cls.__tablename__}_dedup")
        )


class HrSample(Base, VitalMixin):
    __tablename__ = "hr_samples"
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    bpm: Mapped[int] = mapped_column(Integer, nullable=False)
    rr_ms: Mapped[int | None] = mapped_column(Integer, nullable=True)


class SkinTempSample(Base, VitalMixin):
    __tablename__ = "skin_temp_samples"
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    temperature_c: Mapped[float] = mapped_column(Float, nullable=False)


class AccMinuteSummary(Base, VitalMixin):
    __tablename__ = "acc_minute_summary"
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    avg_magnitude_mg: Mapped[float] = mapped_column(Float, nullable=False)
    max_magnitude_mg: Mapped[float] = mapped_column(Float, nullable=False)
    sample_count: Mapped[int] = mapped_column(Integer, nullable=False)


class PpiSample(Base, VitalMixin):
    __tablename__ = "ppi_samples"
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    ppi_ms: Mapped[int] = mapped_column(Integer, nullable=False)
    error_estimate_ms: Mapped[int | None] = mapped_column(Integer, nullable=True)
    hr_bpm: Mapped[int | None] = mapped_column(Integer, nullable=True)
    blocker_bit: Mapped[bool] = mapped_column(Boolean, default=False)
    skin_contact_supported: Mapped[bool] = mapped_column(Boolean, default=True)
    skin_contact_status: Mapped[bool] = mapped_column(Boolean, default=True)


class ActivityDaySummary(Base, VitalMixin):
    __tablename__ = "activity_day_summary"
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    steps: Mapped[int | None] = mapped_column(Integer, nullable=True)
    active_time_minutes: Mapped[int | None] = mapped_column(Integer, nullable=True)
    calories_total: Mapped[int | None] = mapped_column(Integer, nullable=True)
    calories_activity: Mapped[int | None] = mapped_column(Integer, nullable=True)
    calories_training: Mapped[int | None] = mapped_column(Integer, nullable=True)
    calories_bmr: Mapped[int | None] = mapped_column(Integer, nullable=True)


class Hr247Sample(Base, VitalMixin):
    __tablename__ = "hr_247_samples"
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    bpm: Mapped[int] = mapped_column(Integer, nullable=False)
    trigger_type: Mapped[str | None] = mapped_column(String(32), nullable=True)


class Ppi247Sample(Base, VitalMixin):
    __tablename__ = "ppi_247_samples"
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    ppi_ms: Mapped[int] = mapped_column(Integer, nullable=False)
    error_estimate_ms: Mapped[int | None] = mapped_column(Integer, nullable=True)
    trigger_type: Mapped[str | None] = mapped_column(String(32), nullable=True)
    skin_contact: Mapped[str | None] = mapped_column(String(16), nullable=True)
    movement: Mapped[str | None] = mapped_column(String(16), nullable=True)


class SkinTemp247Sample(Base, VitalMixin):
    __tablename__ = "skin_temp_247_samples"
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    temperature_c: Mapped[float] = mapped_column(Float, nullable=False)


class NightlyRecharge(Base, VitalMixin):
    __tablename__ = "nightly_recharge"
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    ans_charge_percent: Mapped[int | None] = mapped_column(Integer, nullable=True)
    recovery_indicator: Mapped[int | None] = mapped_column(Integer, nullable=True)
    ans_rate: Mapped[int | None] = mapped_column(Integer, nullable=True)
    hr_mean_bpm: Mapped[int | None] = mapped_column(Integer, nullable=True)
    hr_min_bpm: Mapped[int | None] = mapped_column(Integer, nullable=True)
    rr_mean_ms: Mapped[int | None] = mapped_column(Integer, nullable=True)
    breathing_rate_hz: Mapped[float | None] = mapped_column(Float, nullable=True)
    sleep_tip: Mapped[str | None] = mapped_column(Text, nullable=True)
    vitality_tip: Mapped[str | None] = mapped_column(Text, nullable=True)
    exercise_tip: Mapped[str | None] = mapped_column(Text, nullable=True)


class ActivityMinuteSample(Base, VitalMixin):
    __tablename__ = "activity_minute_samples"
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    steps: Mapped[int | None] = mapped_column(Integer, nullable=True)
    met_x100: Mapped[int | None] = mapped_column(Integer, nullable=True)
    activity_level: Mapped[int | None] = mapped_column(Integer, nullable=True)


class SleepSession(Base, VitalMixin):
    __tablename__ = "sleep_sessions"
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    sleep_start_time_ms: Mapped[int | None] = mapped_column(BigInteger, nullable=True)
    sleep_end_time_ms: Mapped[int | None] = mapped_column(BigInteger, nullable=True)
    sleep_goal_minutes: Mapped[int | None] = mapped_column(Integer, nullable=True)
    user_sleep_rating: Mapped[int | None] = mapped_column(Integer, nullable=True)
    battery_ran_out: Mapped[bool] = mapped_column(Boolean, default=False)
    sleep_skin_temp_celsius: Mapped[float | None] = mapped_column(Float, nullable=True)
    sleep_skin_temp_deviation: Mapped[float | None] = mapped_column(Float, nullable=True)
    sleep_wake_phases_json: Mapped[str | None] = mapped_column(Text, nullable=True)
    sleep_cycles_json: Mapped[str | None] = mapped_column(Text, nullable=True)


class TrainingSession(Base, VitalMixin):
    __tablename__ = "training_sessions"
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    session_date: Mapped[str | None] = mapped_column(String(32), nullable=True)
    file_size_bytes: Mapped[int] = mapped_column(BigInteger, default=0)
    exercise_count: Mapped[int] = mapped_column(Integer, default=0)
    start_time_ms: Mapped[int | None] = mapped_column(BigInteger, nullable=True)
    end_time_ms: Mapped[int | None] = mapped_column(BigInteger, nullable=True)
    duration_seconds: Mapped[int | None] = mapped_column(Integer, nullable=True)


class MoodEntry(Base, VitalMixin):
    __tablename__ = "mood_entries"
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    fact: Mapped[str] = mapped_column(Text, nullable=False)
    coord_x: Mapped[int] = mapped_column(Integer, nullable=False)
    coord_y: Mapped[int] = mapped_column(Integer, nullable=False)
    occurred_at: Mapped[int] = mapped_column(BigInteger, nullable=False)
    hr_at_entry: Mapped[int | None] = mapped_column(Integer, nullable=True)
    role_id: Mapped[str | None] = mapped_column(String(64), nullable=True)


# ========== 小时级聚合表（降采样） ==========

class HrHourlyAgg(Base):
    __tablename__ = "hr_hourly_agg"
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    device_id: Mapped[str] = mapped_column(String(64), index=True, nullable=False)
    hour_ts: Mapped[int] = mapped_column(BigInteger, nullable=False)
    avg_bpm: Mapped[float] = mapped_column(Float, nullable=False)
    min_bpm: Mapped[int] = mapped_column(Integer, nullable=False)
    max_bpm: Mapped[int] = mapped_column(Integer, nullable=False)
    sample_count: Mapped[int] = mapped_column(Integer, nullable=False)
    __table_args__ = (UniqueConstraint("device_id", "hour_ts", name="uq_hr_hourly"),)


class SkinTempHourlyAgg(Base):
    __tablename__ = "skin_temp_hourly_agg"
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    device_id: Mapped[str] = mapped_column(String(64), index=True, nullable=False)
    hour_ts: Mapped[int] = mapped_column(BigInteger, nullable=False)
    avg_temperature_c: Mapped[float] = mapped_column(Float, nullable=False)
    min_temperature_c: Mapped[float] = mapped_column(Float, nullable=False)
    max_temperature_c: Mapped[float] = mapped_column(Float, nullable=False)
    sample_count: Mapped[int] = mapped_column(Integer, nullable=False)
    __table_args__ = (UniqueConstraint("device_id", "hour_ts", name="uq_skin_temp_hourly"),)


class PpiHourlyAgg(Base):
    __tablename__ = "ppi_hourly_agg"
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    device_id: Mapped[str] = mapped_column(String(64), index=True, nullable=False)
    hour_ts: Mapped[int] = mapped_column(BigInteger, nullable=False)
    avg_ppi_ms: Mapped[float] = mapped_column(Float, nullable=False)
    sample_count: Mapped[int] = mapped_column(Integer, nullable=False)
    __table_args__ = (UniqueConstraint("device_id", "hour_ts", name="uq_ppi_hourly"),)


# ========== Phase 1–6: 实时生理状态检测分析表 ==========

class PpiAnalysisWindow(Base):
    """Android 推流上来的 PPI 原始窗口 — 服务器异步分析的入口。"""
    __tablename__ = "ppi_analysis_windows"
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    device_id: Mapped[str] = mapped_column(String(64), index=True, nullable=False)
    window_start_ts: Mapped[int] = mapped_column(BigInteger, nullable=False, index=True)
    window_end_ts: Mapped[int] = mapped_column(BigInteger, nullable=False)
    rr_list_ms: Mapped[str | None] = mapped_column(Text, nullable=True)  # JSON array
    n_raw: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    n_clean: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    on_device_rmssd: Mapped[float | None] = mapped_column(Float, nullable=True)
    on_device_sdnn: Mapped[float | None] = mapped_column(Float, nullable=True)
    acc_magnitude_mean: Mapped[float | None] = mapped_column(Float, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)
    __table_args__ = (UniqueConstraint("device_id", "window_start_ts", name="uq_ppi_window"),)


class HrvAnalysisResult(Base):
    """HeartPy + 自定义指标分析结果。"""
    __tablename__ = "hrv_analysis_results"
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    window_id: Mapped[int] = mapped_column(Integer, index=True, nullable=False)
    device_id: Mapped[str] = mapped_column(String(64), index=True, nullable=False)
    ts: Mapped[int] = mapped_column(BigInteger, nullable=False)
    bpm: Mapped[float | None] = mapped_column(Float, nullable=True)
    rmssd: Mapped[float | None] = mapped_column(Float, nullable=True)
    sdnn: Mapped[float | None] = mapped_column(Float, nullable=True)
    pnn50: Mapped[float | None] = mapped_column(Float, nullable=True)
    pnn20: Mapped[float | None] = mapped_column(Float, nullable=True)
    lf: Mapped[float | None] = mapped_column(Float, nullable=True)
    hf: Mapped[float | None] = mapped_column(Float, nullable=True)
    lf_hf: Mapped[float | None] = mapped_column(Float, nullable=True)
    vlf: Mapped[float | None] = mapped_column(Float, nullable=True)
    sd1: Mapped[float | None] = mapped_column(Float, nullable=True)
    sd2: Mapped[float | None] = mapped_column(Float, nullable=True)
    sd1_sd2: Mapped[float | None] = mapped_column(Float, nullable=True)
    sampen: Mapped[float | None] = mapped_column(Float, nullable=True)
    dfa_alpha1: Mapped[float | None] = mapped_column(Float, nullable=True)
    breathing_rate: Mapped[float | None] = mapped_column(Float, nullable=True)
    hr_surge_flag: Mapped[bool] = mapped_column(Boolean, default=False)
    is_movement: Mapped[bool] = mapped_column(Boolean, default=False)
    n_clean: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    coverage_pct: Mapped[float | None] = mapped_column(Float, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)


class PhysioStateClassification(Base):
    """生理状态分类结果 — 焦虑分数 + 状态标签。"""
    __tablename__ = "physio_state_classifications"
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    window_id: Mapped[int] = mapped_column(Integer, index=True, nullable=False)
    device_id: Mapped[str] = mapped_column(String(64), index=True, nullable=False)
    ts: Mapped[int] = mapped_column(BigInteger, nullable=False)
    anxiety_score: Mapped[float] = mapped_column(Float, nullable=False)
    state_label: Mapped[str] = mapped_column(String(32), nullable=False, index=True)
    z_scores_json: Mapped[str | None] = mapped_column(Text, nullable=True)  # JSON
    skin_temp_delta: Mapped[float | None] = mapped_column(Float, nullable=True)
    acc_suppressed: Mapped[bool] = mapped_column(Boolean, default=False)
    should_notify: Mapped[bool] = mapped_column(Boolean, default=False)
    cooldown_until: Mapped[int | None] = mapped_column(BigInteger, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)
    __table_args__ = (UniqueConstraint("device_id", "window_id", name="uq_state_classification"),)


class LlmFeedbackHistory(Base):
    """LLM 生成的反馈记录。"""
    __tablename__ = "llm_feedback_history"
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    device_id: Mapped[str] = mapped_column(String(64), index=True, nullable=False)
    ts: Mapped[int] = mapped_column(BigInteger, nullable=False)
    classification_id: Mapped[int | None] = mapped_column(Integer, nullable=True)
    state_label: Mapped[str] = mapped_column(String(32), nullable=False)
    anxiety_score: Mapped[float] = mapped_column(Float, nullable=False)
    message: Mapped[str] = mapped_column(Text, nullable=False)
    tone: Mapped[str] = mapped_column(String(32), nullable=False)
    llm_used: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    triggered_notification: Mapped[bool] = mapped_column(Boolean, default=False)
    user_response: Mapped[str | None] = mapped_column(String(32), nullable=True)  # logged/snoozed/dismissed/none
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)


class PhysioStateLabel(Base):
    """用户情绪标注 — 为未来 ML 模型积累训练数据。"""
    __tablename__ = "physio_state_labels"
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    window_id: Mapped[int | None] = mapped_column(Integer, index=True, nullable=True)
    device_id: Mapped[str] = mapped_column(String(64), index=True, nullable=False)
    label: Mapped[str] = mapped_column(String(32), nullable=False)  # anxious/calm/stressed/normal
    labeled_by: Mapped[str] = mapped_column(String(32), nullable=False, default="user")
    labeled_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)


class DeviceBaselineSnapshot(Base):
    """设备个人基线每日快照。"""
    __tablename__ = "device_baseline_snapshots"
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    device_id: Mapped[str] = mapped_column(String(64), index=True, nullable=False)
    date: Mapped[str] = mapped_column(String(10), nullable=False)  # "2026-06-20"
    window_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    rmssd_ewma: Mapped[float | None] = mapped_column(Float, nullable=True)
    sdnn_ewma: Mapped[float | None] = mapped_column(Float, nullable=True)
    pnn50_ewma: Mapped[float | None] = mapped_column(Float, nullable=True)
    lf_hf_ewma: Mapped[float | None] = mapped_column(Float, nullable=True)
    sd1_sd2_ewma: Mapped[float | None] = mapped_column(Float, nullable=True)
    sampen_ewma: Mapped[float | None] = mapped_column(Float, nullable=True)
    dfa_alpha1_ewma: Mapped[float | None] = mapped_column(Float, nullable=True)
    breathing_rate_ewma: Mapped[float | None] = mapped_column(Float, nullable=True)
    bpm_ewma: Mapped[float | None] = mapped_column(Float, nullable=True)
    skin_temp_c_ewma: Mapped[float | None] = mapped_column(Float, nullable=True)
    snapshot_json: Mapped[str | None] = mapped_column(Text, nullable=True)  # 完整基线 JSON
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, nullable=False)
    __table_args__ = (UniqueConstraint("device_id", "date", name="uq_baseline_snapshot"),)


class FcmToken(Base):
    """FCM 设备推送 token。"""
    __tablename__ = "fcm_tokens"
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    device_id: Mapped[str] = mapped_column(String(64), index=True, nullable=False, unique=True)
    token: Mapped[str] = mapped_column(String(512), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now, nullable=False)


class TemplateWeight(Base):
    """通知模板权重 — Phase 6 反馈闭环使用。"""
    __tablename__ = "template_weights"
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    state_label: Mapped[str] = mapped_column(String(32), nullable=False)
    tone: Mapped[str] = mapped_column(String(32), nullable=False)
    response_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    total_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    __table_args__ = (UniqueConstraint("state_label", "tone", name="uq_template_weight"),)
