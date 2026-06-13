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


def success(data: Any) -> ApiResponse[Any]:
    return ApiResponse(code=0, message="success", data=data)


def error(code: int, message: str) -> ApiResponse[None]:
    return ApiResponse(code=code, message=message, data=None)
