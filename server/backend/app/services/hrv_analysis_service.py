"""
HRV 分析服务 — HeartPy + 自定义高级指标。

从 sandbox/analyze_hrv_advanced.py 移植，适配为服务端异步分析管线。
输入: PpiAnalysisWindow (DB row)
输出: HrvAnalysisResult (DB row) + Redis 基线更新
"""
import json
import logging
import math
from typing import Optional

import numpy as np
from scipy.spatial.distance import cdist
from sqlalchemy.orm import Session

# heartpy 1.2.x 调用 np.trapz；numpy 2.x 已改名为 trapezoid
if not hasattr(np, "trapz") and hasattr(np, "trapezoid"):
    np.trapz = np.trapezoid

# HeartPy 延迟导入 — 避免启动时未安装报错
try:
    import heartpy as hp
    HAS_HEARTPY = True
except ImportError:
    hp = None
    HAS_HEARTPY = False

from app.vitals_models import HrvAnalysisResult, PpiAnalysisWindow

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------
# HRV 指标安全提取
# ---------------------------------------------------------------
MIN_CLEAN_SAMPLES = 30       # 最小清洗后样本数
MIN_COVERAGE_PCT = 60.0      # 最小覆盖率
RR_MIN = 200                 # ms
RR_MAX = 2000                # ms
ACC_MOVEMENT_THRESHOLD = 1030.0  # mg — 与 state_classifier 一致
CONTINUITY_GAP_FACTOR = 2.0      # 相邻 RR 差 > median*factor 视为段间断


def _longest_continuous_segment(rr: np.ndarray, gap_factor: float = CONTINUITY_GAP_FACTOR) -> np.ndarray:
    """取最长连续 RR 段，避免跨 blocker/脱落间隙的伪相邻差污染 RMSSD。"""
    if len(rr) < 2:
        return rr
    median_rr = float(np.median(rr))
    if median_rr <= 0:
        return rr
    gaps = np.where(np.abs(np.diff(rr)) > median_rr * gap_factor)[0]
    if len(gaps) == 0:
        return rr
    breakpoints = np.concatenate([[0], gaps + 1, [len(rr)]])
    segments = [rr[breakpoints[i]:breakpoints[i + 1]] for i in range(len(breakpoints) - 1)]
    return max(segments, key=len)


# ---------------------------------------------------------------
# Poincaré 分析
# ---------------------------------------------------------------

def poincare_measures(rr: np.ndarray) -> dict:
    """Poincaré plot SD1/SD2 — 短/长程变异性。"""
    if len(rr) < 2:
        return {"sd1": None, "sd2": None, "sd1_sd2": None, "ell": None}
    xd = rr[:-1]
    yd = rr[1:]
    sd1 = float(np.std(xd - yd) / math.sqrt(2))
    sd2 = float(np.std(xd + yd) / math.sqrt(2))
    return {
        "sd1": round(sd1, 1),
        "sd2": round(sd2, 1),
        "sd1_sd2": round(sd1 / sd2, 4) if sd2 > 0 else None,
        "ell": round(sd1 * sd2 * math.pi, 1) if sd2 > 0 else None,
    }


# ---------------------------------------------------------------
# PNNx
# ---------------------------------------------------------------

def pnn_measures(rr: np.ndarray, thresholds=(20, 50)) -> dict:
    """PNNx: 相邻 RR 差值绝对值 > x ms 的比例。"""
    if len(rr) < 2:
        return {f"pnn{t}": None for t in thresholds}
    diff = np.abs(np.diff(rr))
    result = {}
    for t in thresholds:
        result[f"pnn{t}"] = round(float(np.mean(diff > t)), 4)
    return result


# ---------------------------------------------------------------
# 样本熵 (SampEn)
# ---------------------------------------------------------------

def _embed(signal: np.ndarray, m: int) -> np.ndarray:
    """将一维信号嵌入到 m 维空间。"""
    n = len(signal) - m + 1
    if n <= 0:
        return np.empty((0, m))
    emb = np.zeros((n, m))
    for i in range(n):
        emb[i, :] = signal[i:i + m]
    return emb


def sample_entropy(rr: np.ndarray, m: int = 2, r: float = 0.2) -> Optional[float]:
    """样本熵 — 越低越规律，越高越复杂（健康标志）。"""
    n = len(rr)
    if n < m + 2:
        return None

    r_tol = r * np.std(rr)
    if r_tol == 0:
        return None

    emb_m = _embed(rr, m)
    emb_m1 = _embed(rr, m + 1)

    if len(emb_m) < 2 or len(emb_m1) < 2:
        return None

    d_m = cdist(emb_m, emb_m, metric="chebyshev")
    np.fill_diagonal(d_m, np.inf)
    a = np.mean(d_m < r_tol, axis=1)

    d_m1 = cdist(emb_m1, emb_m1, metric="chebyshev")
    np.fill_diagonal(d_m1, np.inf)
    b = np.mean(d_m1 < r_tol, axis=1)

    b_mean = np.mean(b)
    a_mean = np.mean(a)
    if b_mean == 0 or a_mean == 0:
        return None

    return float(-math.log(b_mean / a_mean))


# ---------------------------------------------------------------
# DFA (Detrended Fluctuation Analysis)
# ---------------------------------------------------------------

def _dfa_alpha(rr: np.ndarray, scales: np.ndarray) -> Optional[float]:
    """对指定 scales 进行 DFA 分析，返回标度指数 α。"""
    if len(scales) == 0:
        return None
    if len(rr) < int(np.max(scales)) * 2:
        return None

    rr_mean = np.mean(rr)
    y = np.cumsum(rr - rr_mean)

    fluct = np.zeros(len(scales))
    for i, n in enumerate(scales):
        n_int = int(n)
        n_boxes = len(y) // n_int
        if n_boxes < 1:
            fluct[i] = float("nan")
            continue
        residuals = []
        for j in range(n_boxes):
            box = y[j * n_int:(j + 1) * n_int]
            x = np.arange(len(box))
            coeff = np.polyfit(x, box, 1)
            trend = np.polyval(coeff, x)
            residuals.append(np.sum((box - trend) ** 2) / n_int)
        if residuals:
            fluct[i] = math.sqrt(np.mean(residuals))

    valid = fluct > 0
    if np.sum(valid) < 2:
        return None

    alpha = float(np.polyfit(np.log(scales[valid]), np.log(fluct[valid]), 1)[0])
    return alpha


def dfa_measures(rr: np.ndarray) -> dict:
    """DFA α1 (短程, 4-16 beats) — 焦虑检测核心指标。"""
    alpha1 = _dfa_alpha(rr, np.arange(4, 17, 4))
    return {
        "dfa_alpha1": round(alpha1, 4) if alpha1 is not None and not math.isnan(alpha1) else None,
    }


# ---------------------------------------------------------------
# 呼吸频率提取（从 HeartPy 频谱 HF 峰值）
# ---------------------------------------------------------------

def extract_breathing_rate(wd: dict) -> Optional[float]:
    """
    从 HeartPy welch 频谱 HF 频段 (0.15–0.4 Hz) 峰值频率 → 呼吸频率 (次/分钟)。
    返回 None 表示数据不足或频谱质量差。
    """
    frq = wd.get("frq")
    psd = wd.get("psd")
    if frq is None or psd is None or len(frq) < 2 or len(psd) < 2:
        return None

    frq = np.array(frq)
    psd = np.array(psd)

    # HF 频段 0.15–0.4 Hz
    mask = (frq >= 0.15) & (frq <= 0.4)
    if not np.any(mask):
        return None

    hf_frq = frq[mask]
    hf_psd = psd[mask]
    peak_idx = np.argmax(hf_psd)
    peak_hz = float(hf_frq[peak_idx])

    # 转换为次/分钟
    return round(peak_hz * 60.0, 1)


# ---------------------------------------------------------------
# 静息心率突升检测
# ---------------------------------------------------------------

def detect_hr_surge(current_bpm: float, resting_bpm_ewma: Optional[float], acc_ok: bool) -> bool:
    """
    检测静息心率突升：当前 BPM 较静息基线上升 > 10 bpm，且无运动。
    """
    if resting_bpm_ewma is None:
        return False
    if not acc_ok:
        # 运动中心率上升属正常，不标记为突升
        return False
    return (current_bpm - resting_bpm_ewma) > 10.0


# ---------------------------------------------------------------
# 主分析服务
# ---------------------------------------------------------------

class HrvAnalysisService:
    """HRV 窗口分析器 — 从 PpiAnalysisWindow 计算全套指标。"""

    def __init__(self, baseline_manager=None):
        self.baseline = baseline_manager

    def analyze_window(self, window_id: int, db: Session) -> Optional[HrvAnalysisResult]:
        """对单个推流窗口执行全套 HRV 分析，写入 hrv_analysis_results。"""
        if not HAS_HEARTPY:
            logger.error("HeartPy not installed — cannot run HRV analysis")
            return None

        window = db.query(PpiAnalysisWindow).filter_by(id=window_id).first()
        if window is None:
            logger.warning("Window %d not found", window_id)
            return None

        # 1. 重建 RR 列表
        try:
            rr_raw = np.array(json.loads(window.rr_list_ms or "[]"), dtype=np.float64)
        except (json.JSONDecodeError, ValueError) as e:
            logger.warning("Window %d: invalid rr_list_ms JSON: %s", window_id, e)
            return None

        if len(rr_raw) < MIN_CLEAN_SAMPLES:
            logger.info("Window %d: insufficient samples %d < %d", window_id, len(rr_raw), MIN_CLEAN_SAMPLES)
            return None

        rr_segmented = _longest_continuous_segment(rr_raw)
        if len(rr_segmented) < len(rr_raw):
            logger.info(
                "Window %d: continuity segment %d -> %d beats",
                window_id, len(rr_raw), len(rr_segmented),
            )
        if len(rr_segmented) < MIN_CLEAN_SAMPLES:
            logger.info(
                "Window %d: longest segment too short %d < %d",
                window_id, len(rr_segmented), MIN_CLEAN_SAMPLES,
            )
            return None
        rr_raw = rr_segmented

        # 2. HeartPy 分析
        try:
            welch_wsize = max(30, len(rr_raw) / 200)
            wd, m = hp.process_rr(
                rr_raw,
                threshold_rr=True,
                clean_rr=True,
                clean_rr_method="quotient-filter",
                calc_freq=True,
                welch_wsize=welch_wsize,
            )
        except Exception as e:
            logger.error("Window %d: HeartPy processing failed: %s", window_id, e)
            return None

        rr_clean = np.array(wd.get("RR_list_cor", []))
        if len(rr_clean) < 10:
            rr_clean = rr_raw

        # 3. 质量门控
        n_clean = len(rr_clean)
        coverage_pct = (n_clean / len(rr_raw) * 100.0) if len(rr_raw) > 0 else 0.0
        if n_clean < MIN_CLEAN_SAMPLES or coverage_pct < MIN_COVERAGE_PCT:
            logger.info("Window %d: quality gate failed n_clean=%d coverage=%.1f%%",
                        window_id, n_clean, coverage_pct)
            return None

        # 4. 基础指标提取
        bpm = round(float(m.get("bpm", 0)), 1)
        sdnn = round(float(m.get("sdnn", 0)), 1)
        rmssd = round(float(m.get("rmssd", 0)), 1)
        pnn50_val = round(float(m.get("pnn50", 0)), 4)
        pnn20_val = round(float(m.get("pnn20", 0)), 4)
        lf = round(float(m.get("lf", 0)), 2)
        hf = round(float(m.get("hf", 0)), 2)
        lf_hf = round(float(m.get("lf/hf", 0)), 2)
        vlf = round(float(m.get("vlf", 0)), 2)

        # 5. 高级指标
        poincare = poincare_measures(rr_clean)
        pnnx = pnn_measures(rr_clean, thresholds=(20, 50))
        sampen = sample_entropy(rr_clean)
        dfa = dfa_measures(rr_clean)

        # 6. 呼吸频率
        breathing_rate = extract_breathing_rate(wd)

        # 7. 心率突升检测 + 运动标记
        acc_ok = True  # 默认值；分类阶段进一步判断
        is_movement = False
        if window.acc_magnitude_mean is not None:
            # 静止判定：加速度幅值 < 1030mg（约 1g + 30mg 噪声容限）
            acc_ok = window.acc_magnitude_mean < ACC_MOVEMENT_THRESHOLD
            is_movement = not acc_ok

        resting_bpm = None
        if self.baseline:
            bl = self.baseline.get_baseline(window.device_id)
            if bl:
                resting_bpm = bl.get("bpm")
        hr_surge = detect_hr_surge(bpm, resting_bpm, acc_ok)

        # 8. 写入数据库
        result = HrvAnalysisResult(
            window_id=window_id,
            device_id=window.device_id,
            ts=window.window_end_ts,
            bpm=bpm,
            rmssd=rmssd,
            sdnn=sdnn,
            pnn50=pnn50_val,
            pnn20=pnn20_val,
            lf=lf,
            hf=hf,
            lf_hf=lf_hf,
            vlf=vlf,
            sd1=poincare.get("sd1"),
            sd2=poincare.get("sd2"),
            sd1_sd2=poincare.get("sd1_sd2"),
            sampen=sampen,
            dfa_alpha1=dfa.get("dfa_alpha1"),
            breathing_rate=breathing_rate,
            hr_surge_flag=hr_surge,
            is_movement=is_movement,
            n_clean=n_clean,
            coverage_pct=round(coverage_pct, 1),
        )
        db.add(result)
        db.commit()
        db.refresh(result)

        logger.info(
            "HRV analysis OK: window=%d bpm=%.1f rmssd=%.1f sdnn=%.1f "
            "lf_hf=%.2f sampen=%s dfa_a1=%s hr_surge=%s breath=%.1f",
            window_id, bpm, rmssd, sdnn, lf_hf, sampen,
            dfa.get("dfa_alpha1"), hr_surge, breathing_rate or 0,
        )

        return result


# 单例
hrv_service = HrvAnalysisService()
