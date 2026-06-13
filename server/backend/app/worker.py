import logging
import time

import redis
from sqlalchemy.orm import Session

from app.config import get_settings
from app.database import SessionLocal, init_db
from app.models import AudioFile
from app.pipeline import process_audio_record

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger(__name__)


def handle_job(file_id: str) -> None:
    db: Session = SessionLocal()
    try:
        record = db.get(AudioFile, file_id)
        if record is None:
            logger.warning("Job skipped, record not found: %s", file_id)
            return
        if record.status == "completed":
            logger.info("Job skipped, already completed file_id=%s", file_id)
            return
        process_audio_record(db, record)
    finally:
        db.close()


def main() -> None:
    settings = get_settings()
    init_db()
    client = redis.from_url(settings.redis_url, decode_responses=True)
    logger.info("Worker started, queue=%s", settings.job_queue_key)

    while True:
        item = client.brpop(settings.job_queue_key, timeout=5)
        if not item:
            continue
        _, file_id = item
        try:
            handle_job(file_id)
        except Exception:
            logger.exception("Unhandled worker error for file_id=%s", file_id)
            time.sleep(1)


if __name__ == "__main__":
    main()
