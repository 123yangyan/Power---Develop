import json
import logging
import urllib.error
import urllib.request
from datetime import datetime, timezone

import oss2
from oss2.credentials import Credentials, CredentialsProvider

from app.config import Settings, get_settings

logger = logging.getLogger(__name__)


class _EcsRamRoleProvider(CredentialsProvider):
    """ECS 实例 RAM 角色凭证（支持 OSS V4 签名）。"""

    def __init__(self, role_name: str) -> None:
        self.role_name = role_name

    def get_credentials(self) -> Credentials:
        ak, sk, token = _fetch_ecs_ram_credentials(self.role_name)
        return Credentials(ak, sk, token)


def _endpoint(settings: Settings) -> str:
    suffix = "-internal" if settings.oss_internal_endpoint else ""
    return f"https://oss-{settings.oss_region}{suffix}.aliyuncs.com"


def _fetch_ecs_ram_credentials(role_name: str) -> tuple[str, str, str]:
    """从 ECS 元数据服务获取 RAM 角色临时凭证。"""
    url = f"http://100.100.100.200/latest/meta-data/ram/security-credentials/{role_name}"
    try:
        with urllib.request.urlopen(url, timeout=10) as resp:
            data = json.loads(resp.read().decode())
    except urllib.error.URLError as exc:
        raise RuntimeError(f"无法获取 ECS RAM 凭证: {exc}") from exc

    if data.get("Code") != "Success":
        raise RuntimeError(f"ECS RAM 凭证返回失败: {data}")

    return data["AccessKeyId"], data["AccessKeySecret"], data["SecurityToken"]


def _create_bucket(settings: Settings) -> oss2.Bucket:
    endpoint = _endpoint(settings)
    region = settings.oss_region

    if settings.oss_access_key_id and settings.oss_access_key_secret:
        auth = oss2.Auth(settings.oss_access_key_id, settings.oss_access_key_secret)
        return oss2.Bucket(auth, endpoint, settings.oss_bucket, region=region)

    provider = _EcsRamRoleProvider(settings.oss_ram_role_name)
    auth = oss2.ProviderAuthV4(provider)
    return oss2.Bucket(auth, endpoint, settings.oss_bucket, region=region)


def build_object_key(device_id: str, file_id: str, file_name: str) -> str:
    date_prefix = datetime.now(timezone.utc).strftime("%Y%m%d")
    safe_name = file_name.replace("/", "_")
    return f"audio/{device_id}/{date_prefix}/{file_id}_{safe_name}"


def upload_bytes(object_key: str, data: bytes, content_type: str = "audio/mp4") -> None:
    settings = get_settings()
    bucket = _create_bucket(settings)
    logger.info("Uploading to OSS bucket=%s key=%s bytes=%s", settings.oss_bucket, object_key, len(data))
    result = bucket.put_object(object_key, data, headers={"Content-Type": content_type})
    if result.status // 100 != 2:
        raise RuntimeError(f"OSS put_object failed, status={result.status}")


def generate_signed_url(object_key: str, expires_seconds: int) -> str:
    bucket = _create_bucket(get_settings())
    return bucket.sign_url("GET", object_key, expires_seconds, slash_safe=True)
