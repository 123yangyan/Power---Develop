import json
import logging
import re
import urllib.request
from datetime import datetime, timezone

import dashscope
from dashscope import Generation
from dashscope.audio.asr import Transcription
from sqlalchemy.orm import Session

from app.config import get_settings
from app.models import AudioFile
from app.oss_client import generate_signed_url

logger = logging.getLogger(__name__)


def _parse_transcription_json(data: dict) -> str:
    """从 Fun-ASR 结果 JSON 中提取纯文本。"""
    texts: list[str] = []
    for item in data.get("transcripts") or []:
        if isinstance(item, dict):
            text = item.get("text") or ""
            if text.strip():
                texts.append(text.strip())
    if texts:
        return "\n".join(texts)
    # 兼容旧版/其他字段
    for key in ("text", "transcription"):
        if data.get(key):
            return str(data[key]).strip()
    return ""


def _fetch_transcription_url(url: str) -> str:
    with urllib.request.urlopen(url, timeout=30) as resp:
        payload = json.loads(resp.read().decode("utf-8"))
    return _parse_transcription_json(payload)


def _extract_transcript(asr_result) -> str:
    """Fun-ASR 完成后需从 results[].transcription_url 下载 JSON 再取文本。"""
    if asr_result is None:
        return ""
    output = getattr(asr_result, "output", None)
    if output is None:
        return ""

    results = getattr(output, "results", None)
    if results is None and isinstance(output, dict):
        results = output.get("results")
    if not results:
        return ""

    texts: list[str] = []
    for item in results:
        subtask_status = item.get("subtask_status") if isinstance(item, dict) else getattr(item, "subtask_status", "")
        if subtask_status and subtask_status != "SUCCEEDED":
            logger.warning("ASR subtask not succeeded: %s", item)
            continue

        transcription_url = item.get("transcription_url") if isinstance(item, dict) else getattr(item, "transcription_url", None)
        if transcription_url:
            try:
                text = _fetch_transcription_url(transcription_url)
                if text:
                    texts.append(text)
            except Exception as exc:
                logger.warning("Failed to fetch transcription_url: %s", exc)
            continue

        direct = ""
        if isinstance(item, dict):
            direct = item.get("text") or item.get("transcription") or ""
        else:
            direct = getattr(item, "text", None) or getattr(item, "transcription", None) or ""
        if direct:
            texts.append(str(direct))

    return "\n".join(texts).strip()


def _run_asr(signed_url: str) -> str:
    settings = get_settings()
    dashscope.api_key = settings.dashscope_api_key
    task_response = Transcription.async_call(
        model=settings.asr_model,
        file_urls=[signed_url],
        language_hints=["zh", "en"],
    )
    if task_response.status_code != 200:
        raise RuntimeError(f"ASR submit failed: {task_response.message}")
    task_id = task_response.output.task_id
    final = Transcription.wait(task=task_id)
    if final.output.task_status != "SUCCEEDED":
        raise RuntimeError(f"ASR failed: {final.output.task_status}")
    return _extract_transcript(final).strip()


def _normalize_title(raw: str) -> str:
    """将 LLM 返回的标题截断到 10 个字符以内。"""
    title = str(raw or "").strip()
    if not title:
        return "录音笔记"
    return title[:10]


def _run_llm_analysis(transcript: str) -> dict:
    settings = get_settings()
    dashscope.api_key = settings.dashscope_api_key
    if not transcript:
        return {
            "title": "无有效内容",
            "summary": "未识别到有效语音内容。",
            "keywords": [],
            "alertFlag": False,
            "riskLevel": "low",
            "message": "",
        }

    prompt = (
        "你是录音分析助手。根据以下转写文本，输出 JSON，字段必须是："
        "title(10字以内中文短标题，概括录音主题), "
        "summary(中文摘要), keywords(字符串数组), alertFlag(布尔), "
        "riskLevel(low/medium/high 小写), message(给用户的提醒，可为空)。"
        "若出现投诉、退款、纠纷、威胁等风险词，适当提高 riskLevel 并设置 alertFlag。\n\n"
        f"转写文本：\n{transcript}"
    )
    response = Generation.call(
        model=settings.llm_model,
        messages=[{"role": "user", "content": prompt}],
        result_format="message",
    )
    if response.status_code != 200:
        raise RuntimeError(f"LLM failed: {response.message}")

    content = response.output.choices[0].message.content
    match = re.search(r"\{.*\}", content, re.DOTALL)
    if not match:
        return {
            "title": _normalize_title(content[:10]),
            "summary": content[:500],
            "keywords": [],
            "alertFlag": False,
            "riskLevel": "low",
            "message": "",
        }
    data = json.loads(match.group())
    keywords = data.get("keywords") or []
    if not isinstance(keywords, list):
        keywords = []
    risk = str(data.get("riskLevel", "low")).lower()
    if risk not in {"low", "medium", "high"}:
        risk = "low"
    alert_flag = bool(data.get("alertFlag", False))
    if keywords and not alert_flag and risk == "low":
        alert_flag = True
    return {
        "title": _normalize_title(str(data.get("title", ""))),
        "summary": str(data.get("summary", "")).strip() or "已完成分析。",
        "keywords": [str(k) for k in keywords],
        "alertFlag": alert_flag,
        "riskLevel": risk,
        "message": str(data.get("message", "")).strip(),
    }


def process_audio_record(db: Session, record: AudioFile) -> None:
    settings = get_settings()
    record.status = "processing"
    db.commit()
    logger.info(
        "[1/4] 开始处理 file_id=%s file_name=%s oss_key=%s",
        record.file_id, record.file_name, record.oss_key,
    )

    try:
        signed_url = generate_signed_url(record.oss_key, settings.signed_url_expires_seconds)
        logger.info("[2/4] 已生成 OSS 签名 URL，提交 Fun-ASR 识别...")
        transcript = _run_asr(signed_url)
        logger.info("[3/4] ASR 完成，转写长度=%s 字符，提交通义千问分析...", len(transcript))
        analysis = _run_llm_analysis(transcript)

        record.transcript = transcript
        record.title = analysis["title"]
        record.summary = analysis["summary"]
        record.keywords_json = json.dumps(analysis["keywords"], ensure_ascii=False)
        record.alert_flag = analysis["alertFlag"]
        record.risk_level = analysis["riskLevel"]
        record.message = analysis["message"] or None
        if record.alert_flag and not record.message and analysis["keywords"]:
            joined = "、".join(analysis["keywords"][:5])
            record.message = f"检测到关键词：{joined}"
        record.status = "completed"
        record.processed_at = datetime.now(timezone.utc)
        record.error_message = None
        db.commit()
        logger.info(
            "[4/4] 处理完成 file_id=%s keywords=%s risk=%s alert=%s",
            record.file_id, analysis.get("keywords"), analysis.get("riskLevel"), analysis.get("alertFlag"),
        )
    except Exception as exc:
        logger.exception("Processing failed file_id=%s", record.file_id)
        record.status = "failed"
        record.error_message = str(exc)
        record.message = "处理失败，请稍后重试"
        db.commit()
        raise
