"""Shared MQTT client for physio push notifications."""
from __future__ import annotations

import logging
from typing import Optional
from urllib.parse import urlparse

import paho.mqtt.client as mqtt

from app.config import get_settings

logger = logging.getLogger(__name__)

_client: Optional[mqtt.Client] = None


def _parse_broker(url: str) -> tuple[str, int]:
    normalized = url.strip()
    if normalized.startswith("mqtt://"):
        normalized = "tcp://" + normalized[len("mqtt://") :]
    elif not normalized.startswith(("tcp://", "ssl://", "ws://", "wss://")):
        normalized = "tcp://" + normalized
    parsed = urlparse(normalized)
    host = parsed.hostname or "localhost"
    port = parsed.port or (8883 if parsed.scheme == "ssl" else 1883)
    return host, port


def init_mqtt_client() -> Optional[mqtt.Client]:
    """Connect to Mosquitto on startup. Returns None when push is disabled."""
    global _client

    settings = get_settings()
    if not settings.mqtt_broker_url:
        logger.info("MQTT broker URL not configured — push disabled")
        return None

    password = settings.mqtt_password or settings.api_key
    if settings.mqtt_username and not password:
        logger.warning("MQTT username set but password/API_KEY empty — push disabled")
        return None

    host, port = _parse_broker(settings.mqtt_broker_url)
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="timedrecorder-api")
    if settings.mqtt_username:
        client.username_pw_set(settings.mqtt_username, password)

    try:
        client.connect(host, port, keepalive=60)
        client.loop_start()
        _client = client
        logger.info("MQTT client connected to %s:%d", host, port)
    except Exception as exc:
        logger.error("MQTT client connect failed: %s", exc)
        _client = None
    return _client


def get_mqtt_client() -> Optional[mqtt.Client]:
    return _client


def shutdown_mqtt_client() -> None:
    global _client
    if _client is None:
        return
    try:
        _client.loop_stop()
        _client.disconnect()
    except Exception as exc:
        logger.warning("MQTT client shutdown error: %s", exc)
    finally:
        _client = None
