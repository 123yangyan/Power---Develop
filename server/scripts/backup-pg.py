#!/usr/bin/env python3
"""
PostgreSQL 每日备份 → 阿里云 OSS。

流程：
1. pg_dump | gzip → 本地临时文件
2. oss2 上传到 oss://{BUCKET}/backups/pg/recorder_YYYYMMDD_HHMMSS.sql.gz
3. 清理 OSS 上超过 RETAIN_DAYS 天的旧备份
4. 删除本地临时文件

用法：
  python backup-pg.py                  # 使用 .env 中的配置
  DATABASE_URL=... python backup-pg.py # 覆盖数据库 URL

定时执行（docker-compose cron 容器或 crontab）：
  0 3 * * * cd /opt/timedrecorder && python scripts/backup-pg.py >> /var/log/pg-backup.log 2>&1
"""

import gzip
import logging
import os
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

# 将 backend 目录加入 sys.path，以便导入 app 模块
sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "backend"))

from app.config import get_settings
from app.oss_client import _create_bucket

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger(__name__)

# 备份保留天数
RETAIN_DAYS = 30


def main() -> None:
    settings = get_settings()
    database_url = os.environ.get("DATABASE_URL", settings.database_url)

    timestamp = datetime.now(timezone.utc).strftime("%Y%m%d_%H%M%S")
    dump_filename = f"recorder_{timestamp}.sql.gz"
    dump_path = Path(f"/tmp/{dump_filename}")

    # ── 1. pg_dump | gzip ──
    logger.info("Starting pg_dump → %s", dump_path)
    try:
        with open(dump_path, "wb") as f:
            proc = subprocess.Popen(
                ["pg_dump", database_url],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
            )
            for chunk in proc.stdout:
                f.write(gzip.compress(chunk))
            _, stderr = proc.communicate()
            if proc.returncode != 0:
                logger.error("pg_dump failed: %s", stderr.decode())
                sys.exit(1)
    except FileNotFoundError:
        logger.error("pg_dump not found — is postgresql-client installed?")
        sys.exit(1)

    dump_size = dump_path.stat().st_size
    logger.info("pg_dump completed: %s (%d bytes)", dump_filename, dump_size)

    # ── 2. 上传到 OSS ──
    oss_key = f"backups/pg/{dump_filename}"
    try:
        bucket = _create_bucket(settings)
        bucket.put_object_from_file(oss_key, str(dump_path))
        logger.info("Uploaded to oss://%s/%s", settings.oss_bucket, oss_key)
    except Exception as exc:
        logger.error("OSS upload failed: %s", exc)
        # 保留本地文件以便手动上传
        logger.info("Local dump preserved at %s", dump_path)
        sys.exit(1)

    # ── 3. 清理旧备份（保留最近 RETAIN_DAYS 天） ──
    _cleanup_old_backups(bucket, settings.oss_bucket)

    # ── 4. 删除本地临时文件 ──
    dump_path.unlink()
    logger.info("Local temp file removed, backup completed: %s", dump_filename)


def _cleanup_old_backups(bucket: oss2.Bucket, bucket_name: str) -> None:
    """删除 OSS 上超过 RETAIN_DAYS 天的旧备份。"""
    from datetime import timedelta

    cutoff = datetime.now(timezone.utc) - timedelta(days=RETAIN_DAYS)
    prefix = "backups/pg/"

    deleted = 0
    for obj in oss2.ObjectIterator(bucket, prefix=prefix):
        # 对象名格式: backups/pg/recorder_20240615_030000.sql.gz
        name = obj.key.split("/")[-1]
        try:
            date_str = name.split("_")[1]  # 20240615
            obj_date = datetime.strptime(date_str, "%Y%m%d").replace(tzinfo=timezone.utc)
            if obj_date < cutoff:
                bucket.delete_object(obj.key)
                deleted += 1
        except (IndexError, ValueError):
            continue

    if deleted:
        logger.info("Cleaned up %d old backup(s) (older than %d days)", deleted, RETAIN_DAYS)
    else:
        logger.info("No old backups to clean up")


if __name__ == "__main__":
    main()
