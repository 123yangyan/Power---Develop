import json
from datetime import datetime, timezone
from typing import Any, Generic, TypeVar

from pydantic import BaseModel, Field

from app.models import AudioFile

T = TypeVar("T")


class ApiResponse(BaseModel, Generic[T]):
    code: int
    message: str
    data: T | None = None


class UploadResponseData(BaseModel):
    fileId: str
    status: str


class AudioResultDto(BaseModel):
    fileId: str
    fileName: str
    status: str
    transcript: str | None = None
    title: str | None = None
    summary: str | None = None
    keywords: list[str] = Field(default_factory=list)
    alertFlag: bool = False
    riskLevel: str | None = None
    message: str | None = None
    processedAt: str | None = None


class BatchResultRequest(BaseModel):
    fileIds: list[str]


class BatchResultResponseData(BaseModel):
    results: list[AudioResultDto] = Field(default_factory=list)


# ---------- 本地 Worker 接口 schemas ----------

class WorkerNextJobData(BaseModel):
    """GET /api/worker/next-job 返回的任务数据。"""
    fileId: str
    fileName: str
    signedUrl: str


class SubmitResultRequest(BaseModel):
    """POST /api/worker/submit-result 的请求体。"""
    fileId: str
    transcript: str = ""
    title: str = ""
    summary: str = ""
    keywords: list[str] = Field(default_factory=list)
    alertFlag: bool = False
    riskLevel: str = "low"
    message: str = ""


class SubmitResultData(BaseModel):
    """提交结果成功后的返回数据。"""
    fileId: str
    status: str


def format_processed_at(value: datetime | None) -> str | None:
    if value is None:
        return None
    if value.tzinfo is None:
        value = value.replace(tzinfo=timezone.utc)
    return value.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


def parse_keywords(raw: str | None) -> list[str]:
    if not raw:
        return []
    try:
        data = json.loads(raw)
        if isinstance(data, list):
            return [str(item) for item in data]
    except json.JSONDecodeError:
        pass
    return []


def to_audio_result(record: AudioFile) -> AudioResultDto:
    return AudioResultDto(
        fileId=record.file_id,
        fileName=record.file_name,
        status=record.status,
        transcript=record.transcript,
        title=record.title,
        summary=record.summary,
        keywords=parse_keywords(record.keywords_json),
        alertFlag=record.alert_flag,
        riskLevel=record.risk_level,
        message=record.message,
        processedAt=format_processed_at(record.processed_at),
    )


# ---------- 身心数据同步 / 看板 schemas ----------

VALID_TABLES = frozenset({
    "hr_samples", "skin_temp_samples", "acc_minute_summary", "ppi_samples",
    "activity_day_summary", "hr_247_samples", "ppi_247_samples", "skin_temp_247_samples",
    "nightly_recharge", "activity_minute_samples", "sleep_sessions", "training_sessions",
    "mood_entries",
})


class VitalsBatchRequest(BaseModel):
    deviceId: str
    source: str = "LIVE_STREAM"          # LIVE_STREAM | OFFLINE_SYNC | USER_INPUT
    table: str                           # 表名，必须在 VALID_TABLES 中
    rows: list[dict[str, Any]]           # 每行的字段与目标表列对齐


class VitalsBatchResponseData(BaseModel):
    inserted: int = 0
    skipped: int = 0


class DeviceInfo(BaseModel):
    deviceId: str
    lastActiveAt: str | None = None      # ISO format


class SeriesPoint(BaseModel):
    ts: int
    value: float


class SeriesData(BaseModel):
    table: str
    points: list[SeriesPoint] = Field(default_factory=list)


class SeriesQueryResponse(BaseModel):
    deviceId: str
    fromTs: int
    toTs: int
    series: list[SeriesData] = Field(default_factory=list)


class DaySummary(BaseModel):
    date: str
    steps: int | None = None
    calories_total: int | None = None
    active_minutes: int | None = None
    sleep_duration_minutes: int | None = None
    sleep_rating: int | None = None
    ans_charge: int | None = None
    mood_count: int = 0
    avg_hr: float | None = None


class MoodCalendarEntry(BaseModel):
    date: str
    coord_x: int | None = None
    coord_y: int | None = None
    fact: str | None = None
    role_id: str | None = None
    hr_at_entry: int | None = None


# ---------- 数据存储总览 schemas ----------

class DailyWrite(BaseModel):
    date: str
    count: int


class TableStat(BaseModel):
    table: str
    group: str
    totalRows: int = 0
    latestAt: str | None = None          # ISO format
    earliestAt: str | None = None        # ISO format
    deviceCount: int = 0
    dailyWrites: list[DailyWrite] = Field(default_factory=list)


class StatsResponse(BaseModel):
    stats: list[TableStat] = Field(default_factory=list)


# ---------- 通用响应 ----------

def success(data: Any) -> ApiResponse[Any]:
    return ApiResponse(code=0, message="success", data=data)


def error(code: int, message: str) -> ApiResponse[None]:
    return ApiResponse(code=code, message=message, data=None)
