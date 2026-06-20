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
