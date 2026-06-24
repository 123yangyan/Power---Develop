import logging

from sqlalchemy import create_engine, text
from sqlalchemy.orm import DeclarativeBase, sessionmaker

from app.config import get_settings

logger = logging.getLogger(__name__)


class Base(DeclarativeBase):
    pass


settings = get_settings()

# PostgreSQL 连接池（SQLite 不再使用 connect_args）
engine = create_engine(
    settings.database_url,
    pool_size=settings.db_pool_size,
    max_overflow=settings.db_max_overflow,
    pool_pre_ping=True,
)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


def init_db() -> None:
    from app import models  # noqa: F401
    from app import vitals_models  # noqa: F401

    Base.metadata.create_all(bind=engine)

    # 兼容已有数据库：增量列（失败时打日志，避免静默 500）
    with engine.begin() as conn:
        for stmt in (
            "ALTER TABLE audio_files ADD COLUMN IF NOT EXISTS processing_at TIMESTAMPTZ",
            "ALTER TABLE hrv_analysis_results ADD COLUMN IF NOT EXISTS is_movement BOOLEAN NOT NULL DEFAULT FALSE",
            "ALTER TABLE llm_feedback_history ADD COLUMN IF NOT EXISTS llm_used BOOLEAN NOT NULL DEFAULT FALSE",
        ):
            try:
                conn.execute(text(stmt))
            except Exception as exc:
                logger.warning("Schema patch skipped (%s): %s", stmt[:60], exc)

    # TimescaleDB：为 A 组高频表创建 hypertable（幂等）
    _create_hypertables()
    _ensure_vitals_dedup_indexes()


VITALS_DEDUP_TABLES = [
    "hr_samples", "skin_temp_samples", "acc_minute_summary", "ppi_samples",
    "activity_day_summary", "hr_247_samples", "ppi_247_samples", "skin_temp_247_samples",
    "nightly_recharge", "activity_minute_samples", "sleep_sessions", "training_sessions",
    "mood_entries",
]


def _ensure_vitals_dedup_indexes() -> None:
    """为 ON CONFLICT 创建含分区键 ts 的唯一索引（hypertable 必需）。"""
    created = 0
    for table_name in VITALS_DEDUP_TABLES:
        try:
            with engine.begin() as conn:
                conn.execute(text(f"""
                    CREATE UNIQUE INDEX IF NOT EXISTS uq_{table_name}_dedup_idx
                    ON {table_name} (device_id, source, client_row_id, ts)
                """))
            created += 1
        except Exception as exc:
            logger.warning("Vitals dedup index %s skipped (%s)", table_name, exc)
    if created:
        logger.info("Vitals dedup unique indexes verified (%d tables)", created)


def _create_hypertables() -> None:
    """将 A 组 4 张高频表转为 TimescaleDB hypertable（自动按天分区）。

    仅在连接到 PostgreSQL + TimescaleDB 时生效；
    如果用 SQLite 或 TimescaleDB 扩展未安装则静默跳过。
    """
    hypertables = [
        ("hr_samples", "ts"),
        ("skin_temp_samples", "ts"),
        ("ppi_samples", "ts"),
        ("acc_minute_summary", "ts"),
        ("ppi_analysis_windows", "window_start_ts"),
    ]
    try:
        with engine.begin() as conn:
            for table_name, partition_col in hypertables:
                conn.execute(text(f"""
                    SELECT create_hypertable(
                        '{table_name}', '{partition_col}',
                        chunk_time_interval => 86400000,
                        if_not_exists => TRUE,
                        migrate_data => TRUE
                    );
                """))
        logger.info("TimescaleDB hypertables created/verified")
    except Exception as exc:
        # SQLite 或未安装 timescaledb 扩展时静默跳过
        logger.info("TimescaleDB hypertable creation skipped (%s)", exc)


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
