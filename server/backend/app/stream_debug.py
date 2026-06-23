"""
PPI 推流调试观察端点 — Phase 1 数据接入验证用。
无需鉴权，浏览器直接访问 /api/vitals/stream/debug 查看推送状态。
"""
import json
import logging
import math
from datetime import datetime, timedelta, timezone
from pathlib import Path

from fastapi import APIRouter, Depends, HTTPException, Query
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field
from sqlalchemy import func
from sqlalchemy.orm import Session

from app.database import get_db
from app.schemas import success
from app.vitals_models import HrvAnalysisResult, PpiAnalysisWindow

logger = logging.getLogger(__name__)

router = APIRouter(
    prefix="/api/vitals/stream/debug",
    tags=["ppi-stream-debug"],
)

GATE_MIN_N_CLEAN = 25
HEALTH_FLOWING_H = 1
HEALTH_STALE_H = 6
ACC_STATIC_THRESHOLD = 50.0  # 经验阈值：低于此视为相对静止


def _dt_to_iso(val: datetime | None) -> str | None:
    if val is None:
        return None
    if val.tzinfo is None:
        val = val.replace(tzinfo=timezone.utc)
    return val.isoformat().replace("+00:00", "Z")


def _ensure_aware(dt: datetime) -> datetime:
    if dt.tzinfo is None:
        return dt.replace(tzinfo=timezone.utc)
    return dt


def _classify_health(last_seen: datetime | None) -> str:
    if last_seen is None:
        return "no_data"
    now = datetime.now(timezone.utc)
    last_seen = _ensure_aware(last_seen)
    age = now - last_seen
    if age < timedelta(hours=HEALTH_FLOWING_H):
        return "flowing"
    if age < timedelta(hours=HEALTH_STALE_H):
        return "stale"
    return "no_data"


def _health_label(health: str) -> str:
    return {
        "flowing": "正常推流",
        "stale": "超过1小时未收到",
        "no_data": "暂无数据",
    }.get(health, health)


def _motion_label(acc: float | None) -> str:
    if acc is None:
        return "未知"
    return "静止" if acc < ACC_STATIC_THRESHOLD else "运动中"


def _window_duration_sec(start_ts: int, end_ts: int) -> int:
    if end_ts <= start_ts:
        return 0
    return max(0, (end_ts - start_ts) // 1000)


def _parse_rr_list(raw: str | None) -> list[int]:
    if not raw:
        return []
    try:
        data = json.loads(raw)
        return [int(x) for x in data] if isinstance(data, list) else []
    except (json.JSONDecodeError, TypeError, ValueError):
        return []


def _safe_float(val: float | None, digits: int | None = None) -> float | None:
    """过滤 NaN/Inf，避免 Pydantic 序列化 500。"""
    if val is None:
        return None
    try:
        f = float(val)
    except (TypeError, ValueError):
        return None
    if not math.isfinite(f):
        return None
    return round(f, digits) if digits is not None else f


def _hrv_result_to_summary(result: HrvAnalysisResult) -> "HrvSummary":
    return HrvSummary(
        n_clean=result.n_clean,
        coverage_pct=_safe_float(result.coverage_pct, 1),
        bpm=_safe_float(result.bpm, 1),
        rmssd=_safe_float(result.rmssd, 1),
        sdnn=_safe_float(result.sdnn, 1),
        pnn20=_safe_float(result.pnn20, 4),
        pnn50=_safe_float(result.pnn50, 4),
        lf=_safe_float(result.lf, 2),
        hf=_safe_float(result.hf, 2),
        lf_hf=_safe_float(result.lf_hf, 2),
        vlf=_safe_float(result.vlf, 2),
        sd1=_safe_float(result.sd1, 1),
        sd2=_safe_float(result.sd2, 1),
        sd1_sd2=_safe_float(result.sd1_sd2, 4),
        sampen=_safe_float(result.sampen, 3),
        dfa_alpha1=_safe_float(result.dfa_alpha1, 4),
        breathing_rate=_safe_float(result.breathing_rate, 1),
        hr_surge_flag=bool(result.hr_surge_flag),
    )


class DeviceStreamStatus(BaseModel):
    device_id: str
    health: str
    health_label: str
    last_received_at: str | None = None
    last_window_duration_sec: int | None = None
    last_n_raw: int | None = None
    last_n_clean: int | None = None
    gate_passed: bool = False
    motion_label: str = "未知"
    windows_last_hour: int = 0
    total_windows: int = 0


class StreamOverviewResponse(BaseModel):
    gate_min_n_clean: int = GATE_MIN_N_CLEAN
    total_devices: int = 0
    active_devices_1h: int = 0
    total_windows_1h: int = 0
    last_received_at: str | None = None
    devices: list[DeviceStreamStatus] = Field(default_factory=list)


class HrvSummary(BaseModel):
    n_clean: int
    coverage_pct: float | None
    bpm: float | None
    rmssd: float | None
    sdnn: float | None
    pnn20: float | None
    pnn50: float | None
    lf: float | None
    hf: float | None
    lf_hf: float | None
    vlf: float | None
    sd1: float | None
    sd2: float | None
    sd1_sd2: float | None
    sampen: float | None
    dfa_alpha1: float | None
    breathing_rate: float | None
    hr_surge_flag: bool


class WindowSummary(BaseModel):
    received_at: str
    window_start: str
    window_end: str
    duration_sec: int
    n_raw: int
    n_clean: int
    gate_passed: bool
    rr_count: int
    rr_preview_ms: list[int] = Field(default_factory=list)
    on_device_rmssd: float | None = None
    on_device_sdnn: float | None = None
    motion_label: str = "未知"
    hrv: HrvSummary | None = None


class DeviceStreamDetailResponse(BaseModel):
    device_id: str
    total_windows: int = 0
    windows: list[WindowSummary] = Field(default_factory=list)


@router.get("")
@router.get("/")
def debug_page():
    html_path = Path(__file__).parent / "static" / "stream_debug.html"
    if not html_path.exists():
        raise HTTPException(status_code=404, detail="stream_debug.html not found")
    return FileResponse(str(html_path), media_type="text/html")


@router.get("/overview")
def stream_overview(
    db: Session = Depends(get_db),
    hours: int = Query(24, ge=1, le=168),
):
    now = datetime.now(timezone.utc)
    cutoff = now - timedelta(hours=hours)
    cutoff_1h = now - timedelta(hours=1)

    windows_in_range = (
        db.query(PpiAnalysisWindow)
        .filter(PpiAnalysisWindow.created_at >= cutoff)
        .order_by(PpiAnalysisWindow.created_at.desc())
        .all()
    )

    device_map: dict[str, dict] = {}
    for w in windows_in_range:
        if w.device_id not in device_map:
            device_map[w.device_id] = {
                "total_windows": 0,
                "windows_last_hour": 0,
                "last_window": None,
            }
        dd = device_map[w.device_id]
        dd["total_windows"] += 1
        if _ensure_aware(w.created_at) >= cutoff_1h:
            dd["windows_last_hour"] += 1
        if dd["last_window"] is None:
            dd["last_window"] = w

    device_statuses: list[DeviceStreamStatus] = []
    for did, dd in device_map.items():
        last_w = dd["last_window"]
        health = _classify_health(last_w.created_at)
        device_statuses.append(DeviceStreamStatus(
            device_id=did,
            health=health,
            health_label=_health_label(health),
            last_received_at=_dt_to_iso(last_w.created_at),
            last_window_duration_sec=_window_duration_sec(
                last_w.window_start_ts, last_w.window_end_ts
            ),
            last_n_raw=last_w.n_raw,
            last_n_clean=last_w.n_clean,
            gate_passed=last_w.n_clean >= GATE_MIN_N_CLEAN,
            motion_label=_motion_label(last_w.acc_magnitude_mean),
            windows_last_hour=dd["windows_last_hour"],
            total_windows=dd["total_windows"],
        ))

    device_statuses.sort(key=lambda d: d.last_received_at or "", reverse=True)

    last_received_at = (
        _dt_to_iso(windows_in_range[0].created_at) if windows_in_range else None
    )

    return success(StreamOverviewResponse(
        gate_min_n_clean=GATE_MIN_N_CLEAN,
        total_devices=len(device_statuses),
        active_devices_1h=sum(1 for d in device_statuses if d.windows_last_hour > 0),
        total_windows_1h=sum(d.windows_last_hour for d in device_statuses),
        last_received_at=last_received_at,
        devices=device_statuses,
    ))


@router.get("/device/{device_id}")
def device_stream_detail(
    device_id: str,
    db: Session = Depends(get_db),
    limit: int = Query(20, ge=1, le=100),
):
    total_windows = (
        db.query(func.count(PpiAnalysisWindow.id))
        .filter(PpiAnalysisWindow.device_id == device_id)
        .scalar()
    ) or 0

    windows = (
        db.query(PpiAnalysisWindow)
        .filter(PpiAnalysisWindow.device_id == device_id)
        .order_by(PpiAnalysisWindow.window_start_ts.desc())
        .limit(limit)
        .all()
    )

    window_ids = [w.id for w in windows]
    hrv_map: dict[int, HrvAnalysisResult] = {}
    if window_ids:
        hrv_rows = (
            db.query(HrvAnalysisResult)
            .filter(HrvAnalysisResult.window_id.in_(window_ids))
            .all()
        )
        hrv_map = {r.window_id: r for r in hrv_rows}

    window_summaries: list[WindowSummary] = []
    for w in windows:
        rr_list = _parse_rr_list(w.rr_list_ms)
        hrv_row = hrv_map.get(w.id)
        window_summaries.append(WindowSummary(
            received_at=_dt_to_iso(w.created_at) or "",
            window_start=_dt_to_iso(
                datetime.fromtimestamp(w.window_start_ts / 1000, tz=timezone.utc)
            ) or "",
            window_end=_dt_to_iso(
                datetime.fromtimestamp(w.window_end_ts / 1000, tz=timezone.utc)
            ) or "",
            duration_sec=_window_duration_sec(w.window_start_ts, w.window_end_ts),
            n_raw=w.n_raw,
            n_clean=w.n_clean,
            gate_passed=w.n_clean >= GATE_MIN_N_CLEAN,
            rr_count=len(rr_list),
            rr_preview_ms=rr_list[:20],
            on_device_rmssd=_safe_float(w.on_device_rmssd, 1),
            on_device_sdnn=_safe_float(w.on_device_sdnn, 1),
            motion_label=_motion_label(w.acc_magnitude_mean),
            hrv=_hrv_result_to_summary(hrv_row) if hrv_row else None,
        ))

    return success(DeviceStreamDetailResponse(
        device_id=device_id,
        total_windows=total_windows,
        windows=window_summaries,
    ))
