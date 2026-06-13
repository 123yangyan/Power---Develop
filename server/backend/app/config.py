from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    oss_region: str = "cn-hangzhou"
    oss_bucket: str
    oss_ram_role_name: str = "TimedRecorderECSRole"
    oss_internal_endpoint: bool = True
    # 仅本地调试可选；生产 ECS 请使用 RAM 角色，不要配置以下两项
    oss_access_key_id: str = ""
    oss_access_key_secret: str = ""

    dashscope_api_key: str
    api_key: str = ""

    database_url: str = "sqlite:////data/recorder.db"
    redis_url: str = "redis://redis:6379/0"

    signed_url_expires_seconds: int = 3600
    asr_model: str = "fun-asr"
    llm_model: str = "qwen-plus"

    job_queue_key: str = "timedrecorder:audio_jobs"


@lru_cache
def get_settings() -> Settings:
    return Settings()
