"""列出最近上传的录音及处理状态。用法: python -m app.show_records [条数]"""
from __future__ import annotations

import json
import sys

from sqlalchemy import select

from app.database import SessionLocal, init_db
from app.models import AudioFile


def main() -> None:
    limit = int(sys.argv[1]) if len(sys.argv) > 1 else 10
    init_db()
    db = SessionLocal()
    try:
        rows = db.execute(
            select(AudioFile).order_by(AudioFile.created_at.desc()).limit(limit)
        ).scalars().all()
        if not rows:
            print("（暂无记录）")
            return
        print(f"最近 {len(rows)} 条录音记录：\n")
        for r in rows:
            keywords = []
            if r.keywords_json:
                try:
                    keywords = json.loads(r.keywords_json)
                except json.JSONDecodeError:
                    keywords = [r.keywords_json]
            print("-" * 60)
            print(f"fileId    : {r.file_id}")
            print(f"fileName  : {r.file_name}")
            print(f"deviceId  : {r.device_id}")
            print(f"status    : {r.status}")
            print(f"ossKey    : {r.oss_key}")
            if r.summary:
                print(f"summary   : {r.summary[:120]}{'...' if len(r.summary or '') > 120 else ''}")
            if keywords:
                print(f"keywords  : {', '.join(keywords)}")
            if r.risk_level:
                print(f"riskLevel : {r.risk_level}")
            if r.alert_flag:
                print(f"alertFlag : {r.alert_flag}")
            if r.error_message:
                print(f"error     : {r.error_message[:200]}")
            print(f"created   : {r.created_at}")
            if r.processed_at:
                print(f"processed : {r.processed_at}")
    finally:
        db.close()


if __name__ == "__main__":
    main()
