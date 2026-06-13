from fastapi import Header, HTTPException

from app.config import get_settings


def verify_api_key(authorization: str | None = Header(default=None)) -> None:
    """可选 Bearer 鉴权：配置了 API_KEY 时才校验。"""
    settings = get_settings()
    if not settings.api_key:
        return
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Missing or invalid Authorization header")
    token = authorization.removeprefix("Bearer ").strip()
    if token != settings.api_key:
        raise HTTPException(status_code=401, detail="Invalid API key")
