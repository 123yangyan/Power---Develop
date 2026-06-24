from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    oss_region: str = "cn-hangzhou"
    # 留空时录音上传功能不可用，但身心看板 /api/vitals/* 可正常运行
    oss_bucket: str = ""
    oss_ram_role_name: str = "TimedRecorderECSRole"
    oss_internal_endpoint: bool = True
    # 仅本地调试可选；生产 ECS 请使用 RAM 角色，不要配置以下两项
    oss_access_key_id: str = ""
    oss_access_key_secret: str = ""

    # 留空时录音 ASR/LLM 分析不可用，但身心看板可正常运行
    dashscope_api_key: str = ""
    api_key: str = ""

    # 实时生理状态检测 — LLM 反馈 (Phase 4，硅基流动 OpenAI 兼容 API)
    siliconflow_api_key: str = ""
    siliconflow_base_url: str = "https://api.siliconflow.cn/v1"
    siliconflow_model: str = "deepseek-ai/DeepSeek-V3.1-Terminus"
    llm_feedback_timeout_seconds: float = 10.0

    # Phase 5: ntfy 推送（留空 ntfy_topic_prefix 时推送不可用，其他功能不受影响）
    ntfy_server: str = "https://ntfy.sh"
    ntfy_topic_prefix: str = "mindbody"

    database_url: str = "postgresql://recorder:changeme@postgres:5432/recorder"
    redis_url: str = "redis://redis:6379/0"

    # PostgreSQL 连接池配置
    db_pool_size: int = 10
    db_max_overflow: int = 20

    signed_url_expires_seconds: int = 3600
    asr_model: str = "fun-asr"
    llm_model: str = "qwen-plus"

    job_queue_key: str = "timedrecorder:audio_jobs"


@lru_cache
def get_settings() -> Settings:
    return Settings()
