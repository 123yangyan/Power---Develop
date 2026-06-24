"""身心数据 API：批量上报 + 看板查询。"""

import logging
from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import text
from sqlalchemy.orm import Session

from app.auth import verify_api_key
from app.database import get_db, SessionLocal
from app.schemas import (
    VALID_TABLES,
    DailyWrite,
    DaySummary,
    DeviceInfo,
    MoodCalendarEntry,
    SeriesData,
    SeriesPoint,
    SeriesQueryResponse,
    StatsResponse,
    TableStat,
    VitalsBatchRequest,
    VitalsBatchResponseData,
    error,
    success,
)

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/vitals", tags=["vitals"], dependencies=[Depends(verify_api_key)])

TABLE_COLUMNS: dict[str, list[str]] = {
    "hr_samples": ["bpm", "rr_ms"],
    "skin_temp_samples": ["temperature_c"],
    "acc_minute_summary": ["avg_magnitude_mg", "max_magnitude_mg", "sample_count"],
    "ppi_samples": ["ppi_ms", "error_estimate_ms", "hr_bpm", "blocker_bit", "skin_contact_supported", "skin_contact_status"],
    "activity_day_summary": ["steps", "active_time_minutes", "calories_total", "calories_activity", "calories_training", "calories_bmr"],
    "hr_247_samples": ["bpm", "trigger_type"],
    "ppi_247_samples": ["ppi_ms", "error_estimate_ms", "trigger_type", "skin_contact", "movement"],
    "skin_temp_247_samples": ["temperature_c"],
    "nightly_recharge": ["ans_charge_percent", "recovery_indicator", "ans_rate", "hr_mean_bpm", "hr_min_bpm", "rr_mean_ms", "breathing_rate_hz", "sleep_tip", "vitality_tip", "exercise_tip"],
    "activity_minute_samples": ["steps", "met_x100", "activity_level"],
    "sleep_sessions": ["sleep_start_time_ms", "sleep_end_time_ms", "sleep_goal_minutes", "user_sleep_rating", "battery_ran_out", "sleep_skin_temp_celsius", "sleep_skin_temp_deviation", "sleep_wake_phases_json", "sleep_cycles_json"],
    "training_sessions": ["session_date", "file_size_bytes", "exercise_count", "start_time_ms", "end_time_ms", "duration_seconds"],
    "mood_entries": ["fact", "coord_x", "coord_y", "occurred_at", "hr_at_entry", "role_id"],
}

TABLE_GROUPS: dict[str, str] = {
    "hr_samples": "A 组·实时BLE",
    "skin_temp_samples": "A 组·实时BLE",
    "acc_minute_summary": "A 组·实时BLE",
    "ppi_samples": "A 组·实时BLE",
    "activity_day_summary": "B 组·离线同步",
    "hr_247_samples": "B 组·离线同步",
    "ppi_247_samples": "B 组·离线同步",
    "skin_temp_247_samples": "B 组·离线同步",
    "nightly_recharge": "B 组·离线同步",
    "activity_minute_samples": "B 组·离线同步",
    "sleep_sessions": "B 组·离线同步",
    "training_sessions": "B 组·离线同步",
    "mood_entries": "C 组·用户输入",
}


@router.post("/batch")
def vitals_batch(request: VitalsBatchRequest, db: Session = Depends(get_db)):
    """幂等批量上报身心数据。每批最多 500 行。"""
    table = request.table
    if table not in VALID_TABLES:
        raise HTTPException(status_code=400, detail=f"Invalid table: {table}")

    rows = request.rows
    if len(rows) > 500:
        raise HTTPException(status_code=400, detail="Max 500 rows per batch")

    if not rows:
        return success(VitalsBatchResponseData(inserted=0, skipped=0))

    columns = TABLE_COLUMNS.get(table, [])
    now = datetime.now(timezone.utc)
    col_list = ", ".join(columns)
    val_placeholders = ", ".join(f":{c}" for c in columns)

    inserted = 0
    skipped = 0

    # PostgreSQL 幂等插入（含 ts 的唯一索引兼容 TimescaleDB hypertable）
    insert_sql = text(f"""
        INSERT INTO {table}
            (device_id, source, client_row_id, ts, created_at, {col_list})
        VALUES
            (:device_id, :source, :client_row_id, :ts, :created_at, {val_placeholders})
        ON CONFLICT (device_id, source, client_row_id, ts) DO NOTHING
    """)

    for row in rows:
        client_row_id = str(row.get("clientRowId", ""))
        ts = row.get("ts", 0)

        values: dict = {}
        for col in columns:
            values[col] = row.get(col)

        params = {
            "device_id": request.deviceId,
            "source": request.source,
            "client_row_id": client_row_id,
            "ts": ts,
            "created_at": now,
            **values,
        }

        # 每行独立事务，避免单行失败毒化整批 session
        try:
            with SessionLocal.begin() as row_db:
                row_db.execute(insert_sql, params)
            inserted += 1
        except Exception as exc:
            skipped += 1
            logger.warning(
                "Vitals batch row skipped table=%s device=%s client_row_id=%s: %s",
                table, request.deviceId, client_row_id, exc,
            )

    logger.info("Vitals batch table=%s device=%s inserted=%d skipped=%d", table, request.deviceId, inserted, skipped)
    return success(VitalsBatchResponseData(inserted=inserted, skipped=skipped))


@router.get("/devices")
def list_devices(db: Session = Depends(get_db)):
    """返回有数据的 deviceId 列表（最多 50 个）。"""
    devices: list[DeviceInfo] = []
    seen: set[str] = set()

    for table in sorted(VALID_TABLES):
        try:
            rows = db.execute(
                text(f"SELECT DISTINCT device_id, MAX(created_at) FROM {table} GROUP BY device_id LIMIT 50")
            ).fetchall()
            for r in rows:
                if r[0] not in seen:
                    seen.add(r[0])
                    devices.append(DeviceInfo(
                        deviceId=r[0],
                        lastActiveAt=_dt_to_iso(r[1]),
                    ))
        except Exception:
            continue

    devices.sort(key=lambda d: d.lastActiveAt or "", reverse=True)
    return success(devices[:50])


@router.get("/series")
def query_series(
    device: str,
    from_: int,
    to_: int,
    tables: str = "",
    maxPoints: int = 1000,
    db: Session = Depends(get_db),
):
    """按时间窗查询多表数据，支持降采样。"""
    requested = [t.strip() for t in tables.split(",") if t.strip() in VALID_TABLES]
    if not requested:
        requested = ["hr_samples", "skin_temp_samples", "mood_entries"]

    time_range = to_ - from_
    if time_range <= 0:
        raise HTTPException(status_code=400, detail="Invalid time range")

    bucket_ms = max(60_000, time_range // maxPoints)

    series_list: list[SeriesData] = []

    for table in requested:
        try:
            if table == "mood_entries":
                rows = db.execute(
                    text(f"SELECT occurred_at, coord_x, coord_y, fact, role_id, hr_at_entry FROM {table} WHERE device_id=:did AND occurred_at BETWEEN :f AND :t ORDER BY occurred_at LIMIT :lim"),
                    {"did": device, "f": from_, "t": to_, "lim": maxPoints},
                ).fetchall()
                pts = []
                for r in rows:
                    pts.append(SeriesPoint(ts=r[0], value=float(r[1])))
                series_list.append(SeriesData(table=table + "_coord_x", points=pts))
                continue

            # 数值表：取第一个数值列（sleep_sessions 用持续时长而非起始时间戳）
            cols = TABLE_COLUMNS.get(table, [])
            if not cols:
                continue
            if table == "sleep_sessions":
                val_col = "(sleep_end_time_ms - sleep_start_time_ms)"
            else:
                val_col = cols[0]

            sql = f"""
                SELECT (ts / :bucket) * :bucket AS bucket_ts, AVG({val_col}) AS avg_val
                FROM {table}
                WHERE device_id = :did AND ts BETWEEN :f AND :t
                GROUP BY bucket_ts
                ORDER BY bucket_ts
                LIMIT :lim
            """
            rows = db.execute(text(sql), {
                "did": device,
                "f": from_,
                "t": to_,
                "bucket": bucket_ms,
                "lim": maxPoints,
            }).fetchall()

            pts = [SeriesPoint(ts=int(r[0]), value=round(float(r[1]), 2)) for r in rows]
            series_list.append(SeriesData(table=table, points=pts))
        except Exception as e:
            logger.warning("Series query failed for table=%s: %s", table, e)
            continue

    return success(SeriesQueryResponse(
        deviceId=device,
        fromTs=from_,
        toTs=to_,
        series=series_list,
    ).model_dump())


@router.get("/summary")
def daily_summary(device: str, date: str, db: Session = Depends(get_db)):
    """某天的聚合摘要：步数、卡路里、睡眠、情绪数、平均心率。"""
    day_start = date + "T00:00:00+00:00"
    day_end = date + "T23:59:59+00:00"

    summary = DaySummary(date=date)

    try:
        r = db.execute(text(
            "SELECT steps, calories_total, active_time_minutes FROM activity_day_summary WHERE device_id=:did AND client_row_id LIKE :d"
        ), {"did": device, "d": date + "%"}).first()
        if r:
            summary.steps = r[0]
            summary.calories_total = r[1]
            summary.active_minutes = r[2]
    except Exception:
        pass

    try:
        r = db.execute(text(
            "SELECT sleep_end_time_ms - sleep_start_time_ms, user_sleep_rating FROM sleep_sessions WHERE device_id=:did AND client_row_id LIKE :d LIMIT 1"
        ), {"did": device, "d": date + "%"}).first()
        if r and r[0]:
            summary.sleep_duration_minutes = r[0] // 60000
            summary.sleep_rating = r[1]
    except Exception:
        pass

    try:
        r = db.execute(text(
            "SELECT ans_charge_percent FROM nightly_recharge WHERE device_id=:did AND client_row_id LIKE :d LIMIT 1"
        ), {"did": device, "d": date + "%"}).first()
        if r:
            summary.ans_charge = r[0]
    except Exception:
        pass

    try:
        r = db.execute(text(
            "SELECT COUNT(*) FROM mood_entries WHERE device_id=:did AND occurred_at BETWEEN :s AND :e"
        ), {"did": device, "s": from_iso(day_start), "e": from_iso(day_end)}).scalar()
        summary.mood_count = r or 0
    except Exception:
        pass

    return success(summary.model_dump())


@router.get("/mood-calendar")
def mood_calendar(device: str, year: int, month: int, db: Session = Depends(get_db)):
    """月度情绪日历热力图数据。"""
    import calendar

    days_in_month = calendar.monthrange(year, month)[1]
    start_ts = int(datetime(year, month, 1, tzinfo=timezone.utc).timestamp() * 1000)
    end_ts = int(datetime(year, month, days_in_month, 23, 59, 59, tzinfo=timezone.utc).timestamp() * 1000)

    entries: list[MoodCalendarEntry] = []
    try:
        rows = db.execute(text(
            "SELECT occurred_at, coord_x, coord_y, fact, role_id, hr_at_entry FROM mood_entries WHERE device_id=:did AND occurred_at BETWEEN :s AND :e ORDER BY occurred_at"
        ), {"did": device, "s": start_ts, "e": end_ts}).fetchall()
        for r in rows:
            ts_ms = r[0]
            day_str = datetime.fromtimestamp(ts_ms / 1000, tz=timezone.utc).strftime("%Y-%m-%d")
            entries.append(MoodCalendarEntry(
                date=day_str,
                coord_x=r[1],
                coord_y=r[2],
                fact=r[3],
                role_id=r[4],
                hr_at_entry=r[5],
            ))
    except Exception as e:
        logger.warning("Mood calendar query failed: %s", e)

    return success({"deviceId": device, "year": year, "month": month, "entries": [e.model_dump() for e in entries]})


def _dt_to_iso(val) -> str | None:
    """SQLite raw SQL 可能返回 str 或 datetime，统一转为 ISO 字符串。"""
    if val is None:
        return None
    if isinstance(val, datetime):
        return val.isoformat().replace("+00:00", "Z")
    # SQLite 原始查询返回字符串如 "2026-06-19 10:26:57.000000+00:00"
    return str(val).replace(" ", "T").replace("+00:00", "Z")


@router.get("/stats")
def table_stats(device: str = "", db: Session = Depends(get_db)):
    """返回 13 张表的落库状态：行数、最新/最早时间、设备数、近 7 天每日写入量。"""
    stats: list[TableStat] = []
    since = datetime.now(timezone.utc).replace(hour=0, minute=0, second=0, microsecond=0) - timedelta(days=6)
    since_iso = since.isoformat()

    for table in sorted(VALID_TABLES):
        try:
            # 主统计
            where = "WHERE device_id = :did" if device else ""
            params = {"did": device} if device else {}
            row = db.execute(text(
                f"SELECT COUNT(*), MAX(created_at), MIN(created_at), COUNT(DISTINCT device_id) FROM {table} {where}"
            ), params).first()

            total_rows = int(row[0]) if row and row[0] else 0
            latest_at = _dt_to_iso(row[1]) if row else None
            earliest_at = _dt_to_iso(row[2]) if row else None
            device_count = int(row[3]) if row and row[3] else 0

            # 近 7 天每日写入量
            daily_writes: list[DailyWrite] = []
            dw_where = "WHERE created_at >= :since" + (" AND device_id = :did" if device else "")
            dw_params = {"since": since_iso}
            if device:
                dw_params["did"] = device
            dw_rows = db.execute(text(
                f"SELECT DATE(created_at) as day, COUNT(*) as cnt FROM {table} {dw_where} GROUP BY day ORDER BY day"
            ), dw_params).fetchall()
            for dr in dw_rows:
                daily_writes.append(DailyWrite(date=str(dr[0]), count=int(dr[1])))

            stats.append(TableStat(
                table=table,
                group=TABLE_GROUPS.get(table, "未分组"),
                totalRows=total_rows,
                latestAt=latest_at,
                earliestAt=earliest_at,
                deviceCount=device_count,
                dailyWrites=daily_writes,
            ))
        except Exception as e:
            logger.warning("Stats query failed for table=%s: %s", table, e)
            stats.append(TableStat(
                table=table,
                group=TABLE_GROUPS.get(table, "未分组"),
                totalRows=0,
                deviceCount=0,
            ))

    return success(StatsResponse(stats=stats).model_dump())


@router.get("/table-rows")
def table_rows(
    device: str,
    table: str,
    from_: int,
    to_: int,
    limit: int = 500,
    db: Session = Depends(get_db),
):
    """返回指定表的原始行数据（所有业务列），供看板图表使用。"""
    if table not in VALID_TABLES:
        raise HTTPException(status_code=400, detail=f"Invalid table: {table}")

    columns = TABLE_COLUMNS.get(table, [])
    if not columns:
        return success({"table": table, "columns": ["ts"], "rows": []})

    col_list = ", ".join(columns)
    try:
        rows = db.execute(text(
            f"SELECT ts, {col_list} FROM {table} WHERE device_id=:did AND ts BETWEEN :f AND :t ORDER BY ts LIMIT :lim"
        ), {"did": device, "f": from_, "t": to_, "lim": limit}).fetchall()

        result_rows = []
        for r in rows:
            row_vals = []
            for i, val in enumerate(r):
                if i == 0:  # ts column
                    row_vals.append(int(val) if val else 0)
                elif isinstance(val, datetime):
                    row_vals.append(val.isoformat().replace("+00:00", "Z"))
                elif val is None:
                    row_vals.append(None)
                else:
                    row_vals.append(val)
            result_rows.append(row_vals)

        return success({
            "table": table,
            "columns": ["ts"] + columns,
            "rows": result_rows,
        })
    except Exception as e:
        logger.warning("Table rows query failed for table=%s: %s", table, e)
        raise HTTPException(status_code=500, detail=str(e))


def from_iso(s: str) -> int:
    """ISO 字符串转 Unix ms。"""
    try:
        dt = datetime.fromisoformat(s)
        return int(dt.timestamp() * 1000)
    except Exception:
        return 0
