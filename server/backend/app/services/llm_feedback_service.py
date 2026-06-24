"""
LLM 反馈服务 — 硅基流动 DeepSeek 生成个性化中文身心健康提示。

Phase 4: 接收 PhysioStateClassification + 上下文 → 生成自然语言反馈
Phase 6: 上下文丰富（历史模式、模板权重）
"""
import asyncio
import json
import logging
from datetime import datetime, timezone
from typing import Optional

from sqlalchemy.orm import Session

from app.config import get_settings
from app.vitals_models import (
    LlmFeedbackHistory,
    PhysioStateClassification,
    HrvAnalysisResult,
    PpiAnalysisWindow,
)

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------
# 兜底模板（Phase 4 基础版）
# ---------------------------------------------------------------

FALLBACK_TEMPLATES = {
    "elevated":     "你的身体似乎在提醒你放慢一下节奏。现在感觉怎么样？",
    "anxious":      "心率和呼吸有些变化——是最近发生了什么吗？写下来也许会轻松些。",
    "high_anxiety": "此刻状态有些紧绷。深呼吸，或者把此刻的感受记录下来。",
}

# ---------------------------------------------------------------
# System Prompt
# ---------------------------------------------------------------

SYSTEM_PROMPT = """你是一个温暖、富有同理心的身心健康伴侣。
用非医学、非诊断性的语言解读用户的可穿戴设备生理数据。
回复需在150字以内，避免模板化，每次回复风格略有变化。
语气温和而有力量，像一位懂你的朋友在关心你。
不要提到任何具体的医学数值，只说趋势和感受。
输出仅包含给用户的回复文本，不要加任何前缀、引号或标记。"""

# ---------------------------------------------------------------
# 上下文构建
# ---------------------------------------------------------------

def _build_context(
    classification: PhysioStateClassification,
    hrv_result: Optional[HrvAnalysisResult],
    baseline: Optional[dict],
    last_mood: Optional[str],
    last_feedback_ts: Optional[int],
    history_note: Optional[str] = None,
) -> str:
    """构建发送给 LLM 的上下文 prompt。"""
    parts = [f"## 当前生理状态"]
    parts.append(f"- 状态: {classification.state_label} ({classification.anxiety_score:.0f}/100)")

    if hrv_result:
        bpm = hrv_result.bpm
        parts.append(f"- 心率: {bpm:.0f} bpm" if bpm else "- 心率: —")
        if baseline and baseline.get("bpm"):
            parts.append(f"  (习惯: {baseline['bpm']:.0f} bpm)")

        rmssd = hrv_result.rmssd
        if rmssd and baseline and baseline.get("rmssd"):
            delta_pct = (rmssd - baseline["rmssd"]) / baseline["rmssd"] * 100
            parts.append(f"- 心率变异性(RMSSD): {rmssd:.0f}ms (较习惯{'下降' if delta_pct < 0 else '上升'}{abs(delta_pct):.0f}%)")

        lf_hf = hrv_result.lf_hf
        if lf_hf and baseline and baseline.get("lf_hf"):
            parts.append(f"- 交感-副交感平衡: LF/HF={lf_hf:.1f} (习惯: {baseline['lf_hf']:.1f})")

        br_val = hrv_result.breathing_rate
        if br_val:
            parts.append(f"- 呼吸频率: {br_val:.0f}次/分")
            if baseline and baseline.get("breathing_rate"):
                parts.append(f"  (习惯: {baseline['breathing_rate']:.0f}次/分)")

        if hrv_result.hr_surge_flag:
            parts.append("- ⚠️ 检测到静息心率突然升高")

        sampen = hrv_result.sampen
        if sampen and baseline and baseline.get("sampen"):
            if sampen < baseline["sampen"]:
                parts.append("- 心律模式比平时更规律/机械")

    # 皮温
    if classification.skin_temp_delta is not None:
        d = classification.skin_temp_delta
        if d < -0.3:
            parts.append(f"- 皮肤温度较基线下降{d:.1f}°C（外周血管收缩）")
        elif d > 0.5:
            parts.append(f"- 皮肤温度较基线上升{d:.1f}°C")

    # 活动状态
    if classification.acc_suppressed:
        parts.append("- 活动状态: 检测到身体活动（可能正在运动）")
    else:
        parts.append("- 活动状态: 静止")

    # 情绪上下文
    if last_mood:
        parts.append(f"\n## 最近心情\n- {last_mood}")

    if history_note:
        parts.append(f"\n## 历史模式\n{history_note}")

    # 上次反馈
    if last_feedback_ts:
        ts = datetime.fromtimestamp(last_feedback_ts / 1000, tz=timezone.utc)
        parts.append(f"\n上次反馈: {ts.strftime('%H:%M')}（用户已忽略）")

    parts.append(f"\n请生成一条简短、温暖的消息，邀请自我觉察或记录心情。")
    return "\n".join(parts)


# ---------------------------------------------------------------
# 反馈服务
# ---------------------------------------------------------------

class LlmFeedbackService:
    """硅基流动 DeepSeek 身心健康反馈生成器。"""

    def __init__(self, feedback_loop=None):
        self.feedback_loop = feedback_loop  # Phase 6 注入

    async def generate(
        self,
        classification: PhysioStateClassification,
        hrv_result: Optional[HrvAnalysisResult] = None,
        baseline: Optional[dict] = None,
        last_mood: Optional[str] = None,
        last_feedback_ts: Optional[int] = None,
        db: Optional[Session] = None,
    ) -> dict:
        """
        生成反馈消息。先尝试 LLM，超时/失败则用模板。

        返回: {"message": str, "tone": str, "llm_used": bool}
        """
        label = classification.state_label
        if label in ("calm", "normal"):
            return {"message": "", "tone": "none", "llm_used": False}

        # 构建上下文
        context = _build_context(classification, hrv_result, baseline, last_mood,
                                 last_feedback_ts, self._history_note(classification.device_id))

        # 尝试 LLM
        tone = self._best_tone(label)
        message = None
        llm_used = False

        settings = get_settings()
        if settings.siliconflow_api_key:
            try:
                message = await asyncio.wait_for(
                    self._call_siliconflow(context, settings),
                    timeout=settings.llm_feedback_timeout_seconds,
                )
                llm_used = True
                logger.info(
                    "LLM feedback generated for device=%s model=%s",
                    classification.device_id,
                    settings.siliconflow_model,
                )
            except asyncio.TimeoutError:
                logger.warning("LLM timeout for device=%s, using fallback", classification.device_id)
            except Exception as e:
                logger.warning("LLM error for device=%s: %s", classification.device_id, e)

        # 降级到模板
        if message is None:
            message = FALLBACK_TEMPLATES.get(label, FALLBACK_TEMPLATES["elevated"])
            tone = "gentle_inquiry"

        # 写入历史
        if db is not None:
            history = LlmFeedbackHistory(
                device_id=classification.device_id,
                ts=classification.ts,
                classification_id=classification.id,
                state_label=label,
                anxiety_score=classification.anxiety_score,
                message=message,
                tone=tone,
                llm_used=llm_used,
                triggered_notification=False,
            )
            db.add(history)
            db.commit()

        return {
            "message": message,
            "tone": tone,
            "llm_used": llm_used,
        }

    # -----------------------------------------------------------
    # 内部
    # -----------------------------------------------------------

    async def _call_siliconflow(self, context: str, settings) -> str:
        """调用硅基流动 OpenAI 兼容 Chat Completions API。"""
        from openai import AsyncOpenAI

        client = AsyncOpenAI(
            api_key=settings.siliconflow_api_key,
            base_url=settings.siliconflow_base_url.rstrip("/"),
        )
        response = await client.chat.completions.create(
            model=settings.siliconflow_model,
            messages=[
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": context},
            ],
            max_tokens=200,
            temperature=0.7,
        )
        text = (response.choices[0].message.content or "").strip()
        if text.startswith('"') and text.endswith('"'):
            text = text[1:-1]
        if len(text) > 200:
            text = text[:197] + "..."
        if not text:
            raise ValueError("SiliconFlow returned empty content")
        return text

    def _best_tone(self, state_label: str) -> str:
        """获取最佳 tone（Phase 6 从 template_weights 查询；当前硬编码）。"""
        tones = {
            "elevated":     "gentle_reminder",
            "anxious":      "gentle_inquiry",
            "high_anxiety": "urgent_care",
        }
        return tones.get(state_label, "gentle_inquiry")

    def _history_note(self, device_id: str) -> Optional[str]:
        """Phase 6: 从反馈闭环获取历史模式描述。"""
        if self.feedback_loop:
            return self.feedback_loop.get_history_note(device_id)
        return None
