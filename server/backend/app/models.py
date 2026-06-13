from datetime import datetime, timezone

from sqlalchemy import Boolean, DateTime, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


class AudioFile(Base):
  __tablename__ = "audio_files"

  file_id: Mapped[str] = mapped_column(String(64), primary_key=True)
  device_id: Mapped[str] = mapped_column(String(64), index=True)
  local_id: Mapped[str] = mapped_column(String(64))
  file_name: Mapped[str] = mapped_column(String(255))
  format: Mapped[str] = mapped_column(String(16))
  start_time: Mapped[str] = mapped_column(String(64))
  end_time: Mapped[str] = mapped_column(String(64))
  duration: Mapped[int] = mapped_column(Integer)
  oss_key: Mapped[str] = mapped_column(String(512))
  status: Mapped[str] = mapped_column(String(32), default="pending", index=True)
  transcript: Mapped[str | None] = mapped_column(Text, nullable=True)
  title: Mapped[str | None] = mapped_column(String(64), nullable=True)
  summary: Mapped[str | None] = mapped_column(Text, nullable=True)
  keywords_json: Mapped[str | None] = mapped_column(Text, nullable=True)
  alert_flag: Mapped[bool] = mapped_column(Boolean, default=False)
  risk_level: Mapped[str | None] = mapped_column(String(16), nullable=True)
  message: Mapped[str | None] = mapped_column(Text, nullable=True)
  error_message: Mapped[str | None] = mapped_column(Text, nullable=True)
  processed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
  created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
