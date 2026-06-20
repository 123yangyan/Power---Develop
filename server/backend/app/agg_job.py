"""
A 组高频数据降采样调度任务。

每天凌晨 02:00 UTC 执行：
1. hr / skin_temp / ppi 超过 30 天的原始数据 → 按 (device_id, hour) 聚合写入 *_hourly_agg，然后删除原始行
2. acc_minute_summary 超过 30 天的数据 → 直接删除（无聚合需要）

B 组 + C 组（activity/sleep/training/mood）永久保留原始数据，不触碰。

安全设计：
- 聚合使用 ON CONFLICT DO NOTHING（幂等）
- 聚合成功后才删除对应原始行（同一事务内）
- 每批处理 1 天的数据，避免长事务锁表
"""

import logging
from datetime import datetime, timedelta, timezone

from sqlalchemy import text
from sqlalchemy.orm import Session

logger = logging.getLogger(__name__)

# 保留策略
HR_CUTOFF_DAYS = 30
ACC_CUTOFF_DAYS = 30
MS_PER_DAY = 86_400_000
MS_PER_HOUR = 3_600_000


def run_hourly_aggregation(db: Session) -> dict:
    """执行一次降采样聚合 + 清理，返回统计信息。"""
    stats = {"hr_deleted": 0, "skin_temp_deleted": 0, "ppi_deleted": 0, "acc_deleted": 0}

    cutoff_ms = int((datetime.now(timezone.utc) - timedelta(days=HR_CUTOFF_DAYS)).timestamp() * 1000)
    acc_cutoff_ms = int((datetime.now(timezone.utc) - timedelta(days=ACC_CUTOFF_DAYS)).timestamp() * 1000)

    # ── 1. hr_samples → hr_hourly_agg ──
    stats["hr_deleted"] = _aggregate_and_delete(
        db=db,
        source_table="hr_samples",
        agg_table="hr_hourly_agg",
        cutoff_ms=cutoff_ms,
        agg_select="""
            SELECT
                device_id,
                (ts / {ms_per_hour}) * {ms_per_hour} AS hour_ts,
                AVG(bpm)::FLOAT   AS avg_bpm,
                MIN(bpm)::INT     AS min_bpm,
                MAX(bpm)::INT     AS max_bpm,
                COUNT(*)::INT     AS sample_count
            FROM hr_samples
            WHERE ts < :cutoff_ms
            GROUP BY device_id, (ts / {ms_per_hour}) * {ms_per_hour}
        """.format(ms_per_hour=MS_PER_HOUR),
        insert_cols="device_id, hour_ts, avg_bpm, min_bpm, max_bpm, sample_count",
        unique_constraint="uq_hr_hourly",
    )

    # ── 2. skin_temp_samples → skin_temp_hourly_agg ──
    stats["skin_temp_deleted"] = _aggregate_and_delete(
        db=db,
        source_table="skin_temp_samples",
        agg_table="skin_temp_hourly_agg",
        cutoff_ms=cutoff_ms,
        agg_select="""
            SELECT
                device_id,
                (ts / {ms_per_hour}) * {ms_per_hour} AS hour_ts,
                AVG(temperature_c)::FLOAT   AS avg_temperature_c,
                MIN(temperature_c)::FLOAT   AS min_temperature_c,
                MAX(temperature_c)::FLOAT   AS max_temperature_c,
                COUNT(*)::INT               AS sample_count
            FROM skin_temp_samples
            WHERE ts < :cutoff_ms
            GROUP BY device_id, (ts / {ms_per_hour}) * {ms_per_hour}
        """.format(ms_per_hour=MS_PER_HOUR),
        insert_cols="device_id, hour_ts, avg_temperature_c, min_temperature_c, max_temperature_c, sample_count",
        unique_constraint="uq_skin_temp_hourly",
    )

    # ── 3. ppi_samples → ppi_hourly_agg ──
    stats["ppi_deleted"] = _aggregate_and_delete(
        db=db,
        source_table="ppi_samples",
        agg_table="ppi_hourly_agg",
        cutoff_ms=cutoff_ms,
        agg_select="""
            SELECT
                device_id,
                (ts / {ms_per_hour}) * {ms_per_hour} AS hour_ts,
                AVG(ppi_ms)::FLOAT  AS avg_ppi_ms,
                COUNT(*)::INT       AS sample_count
            FROM ppi_samples
            WHERE ts < :cutoff_ms
            GROUP BY device_id, (ts / {ms_per_hour}) * {ms_per_hour}
        """.format(ms_per_hour=MS_PER_HOUR),
        insert_cols="device_id, hour_ts, avg_ppi_ms, sample_count",
        unique_constraint="uq_ppi_hourly",
    )

    # ── 4. acc_minute_summary → 30 天直删 ──
    result = db.execute(
        text("DELETE FROM acc_minute_summary WHERE ts < :cutoff_ms"),
        {"cutoff_ms": acc_cutoff_ms},
    )
    stats["acc_deleted"] = result.rowcount
    db.commit()
    logger.info("acc_minute_summary: deleted %d rows older than %d days", stats["acc_deleted"], ACC_CUTOFF_DAYS)

    logger.info("Aggregation job completed: %s", stats)
    return stats


def _aggregate_and_delete(
    db: Session,
    source_table: str,
    agg_table: str,
    cutoff_ms: int,
    agg_select: str,
    insert_cols: str,
    unique_constraint: str,
) -> int:
    """聚合写入 hourly_agg 表，然后删除已聚合的原始行。

    使用 INSERT ... ON CONFLICT DO NOTHING 保证幂等。
    分批按天处理，避免长事务。
    """
    total_deleted = 0

    # 计算需要处理的天数范围，分天聚合
    # 找到最老的需要处理的记录
    oldest = db.execute(
        text(f"SELECT MIN(ts) FROM {source_table} WHERE ts < :cutoff_ms"),
        {"cutoff_ms": cutoff_ms},
    ).scalar()

    if oldest is None:
        logger.info("%s: no rows older than cutoff, skip", source_table)
        return 0

    # 从最老记录到 cutoff，逐天处理
    day_start = (oldest // MS_PER_DAY) * MS_PER_DAY
    while day_start < cutoff_ms:
        day_end = day_start + MS_PER_DAY

        # 聚合写入
        db.execute(text(f"""
            INSERT INTO {agg_table} ({insert_cols})
            {agg_select}
            AND ts >= :day_start AND ts < :day_end
            ON CONFLICT ON CONSTRAINT {unique_constraint} DO NOTHING
        """), {"cutoff_ms": cutoff_ms, "day_start": day_start, "day_end": day_end})

        # 删除原始行
        result = db.execute(text(f"""
            DELETE FROM {source_table}
            WHERE ts >= :day_start AND ts < :day_end AND ts < :cutoff_ms
        """), {"day_start": day_start, "day_end": day_end, "cutoff_ms": cutoff_ms})
        total_deleted += result.rowcount

        db.commit()
        day_start = day_end

    logger.info("%s → %s: aggregated and deleted %d rows", source_table, agg_table, total_deleted)
    return total_deleted
