"""共享 Redis 客户端（避免 main ↔ stream_routes 循环导入）。"""
from functools import lru_cache

import redis

from app.config import get_settings


@lru_cache
def get_redis() -> redis.Redis:
    return redis.from_url(get_settings().redis_url, decode_responses=True)
