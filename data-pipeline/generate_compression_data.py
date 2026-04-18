"""
Generate synthetic compression event data (Layer 2) for CPR Pilot.

Each CSV matches the Layer 2 schema from README.md exactly.
Produces 15 distinct patterns covering normal CPR, common failure modes,
fatigue variants, edge cases, and multi-phase sessions.

Usage:
    python3 generate_compression_data.py
"""

import numpy as np
import pandas as pd
import os

OUTPUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "synthetic-data")
os.makedirs(OUTPUT_DIR, exist_ok=True)

np.random.seed(42)

# AHA guidelines
RATE_MIN, RATE_MAX = 100, 120
DEPTH_MIN, DEPTH_MAX = 50, 60
RECOIL_GOOD = 95


def build_session(
    duration_sec: int,
    rate_fn,
    depth_fn,
    recoil_fn,
    hr_fn,
    pauses: list[tuple[float, float]] | None = None,
):
    """
    Generate a compression event log.

    rate_fn(i, t):   returns BPM for compression i at time t
    depth_fn(i, t):  returns depth_mm for compression i at time t
    recoil_fn(i, t): returns recoil_pct (0-100) for compression i at time t
    hr_fn(i, t):     returns rescuer HR for compression i at time t
    pauses:          list of (start_sec, end_sec) — no compressions during these
    """
    pauses = pauses or []
    rows = []
    t = 0.0
    idx = 0
    last_instruction_t = -10.0
    consecutive_bad = 0
    recent_intervals = []

    while t < duration_sec:
        # Skip if in a pause
        in_pause = False
        for p_start, p_end in pauses:
            if p_start <= t < p_end:
                t = p_end
                in_pause = True
                break
        if in_pause and t >= duration_sec:
            break

        idx += 1
        rate_bpm = rate_fn(idx, t)
        interval_ms = int(60000 / rate_bpm)
        interval_ms += int(np.random.normal(0, interval_ms * 0.03))
        interval_ms = max(250, interval_ms)

        if idx > 1:
            t += interval_ms / 1000.0
        else:
            t += interval_ms / 1000.0

        if t >= duration_sec:
            break

        # Check if we jumped into a pause
        in_pause = False
        for p_start, p_end in pauses:
            if p_start <= t < p_end:
                t = p_end
                in_pause = True
                break
        if in_pause:
            continue

        timestamp_ms = int(t * 1000)
        depth_mm = depth_fn(idx, t)
        recoil_pct = recoil_fn(idx, t)
        hr_bpm = hr_fn(idx, t)

        # Compute rates
        inst_rate = 60000 / interval_ms
        recent_intervals.append(interval_ms)
        if len(recent_intervals) > 5:
            recent_intervals.pop(0)
        rolling_rate = 60000 / np.mean(recent_intervals)

        # Quality check
        rate_ok = RATE_MIN <= rolling_rate <= RATE_MAX
        depth_ok = DEPTH_MIN <= depth_mm <= DEPTH_MAX
        recoil_ok = recoil_pct >= RECOIL_GOOD
        is_good = rate_ok and depth_ok and recoil_ok

        if not is_good:
            consecutive_bad += 1
        else:
            consecutive_bad = 0

        # Instruction logic
        instruction = "none"
        priority = None
        time_since_last = t - last_instruction_t

        # Check if resuming after a pause (P1)
        if idx > 1 and len(rows) > 0:
            gap = timestamp_ms - rows[-1]["timestamp_ms"]
            if gap > 5000:
                instruction = "resume_compressions"
                priority = 1
                last_instruction_t = t

        # P2-P4 only after 3 consecutive bad + 5s cooldown
        if instruction == "none" and consecutive_bad >= 3 and time_since_last >= 5.0:
            if not rate_ok:
                instruction = "faster" if rolling_rate < RATE_MIN else "slower"
                priority = 2
                last_instruction_t = t
            elif not depth_ok:
                instruction = "push_harder" if depth_mm < DEPTH_MIN else "ease_up"
                priority = 3
                last_instruction_t = t
            elif not recoil_ok:
                instruction = "let_chest_up"
                priority = 4
                last_instruction_t = t

        # P5: fatigue — HR high + quality dropping
        if instruction == "none" and hr_bpm > 155 and not is_good and time_since_last >= 30.0:
            instruction = "switch_rescuers"
            priority = 5
            last_instruction_t = t

        # P6: 2-min timer
        if instruction == "none" and t >= 120 and time_since_last >= 30.0:
            for checkpoint in [120, 240, 360]:
                if abs(t - checkpoint) < 1.0:
                    instruction = "consider_switching"
                    priority = 6
                    last_instruction_t = t
                    break

        # Derived fields
        peak_accel = depth_mm / 1000 * (2 * np.pi * inst_rate / 60) ** 2
        peak_accel += np.random.normal(0, 0.3)
        wrist_angle = abs(np.random.normal(0, 3.0))
        duty_cycle = np.clip(0.50 + np.random.normal(0, 0.03), 0.35, 0.65)

        rows.append({
            "compression_idx": idx,
            "timestamp_ms": timestamp_ms,
            "interval_ms": interval_ms,
            "instantaneous_rate_bpm": round(inst_rate, 1),
            "rolling_rate_bpm": round(rolling_rate, 1),
            "estimated_depth_mm": round(depth_mm, 1),
            "recoil_pct": round(recoil_pct, 1),
            "duty_cycle": round(duty_cycle, 3),
            "peak_accel_mps2": round(peak_accel, 2),
            "wrist_angle_deg": round(wrist_angle, 1),
            "rescuer_hr_bpm": int(hr_bpm),
            "is_quality_good": is_good,
            "instruction": instruction,
            "instruction_priority": priority,
        })

    return pd.DataFrame(rows)


def save(df, name):
    path = os.path.join(OUTPUT_DIR, f"{name}.csv")
    df.to_csv(path, index=False)
    n = len(df)
    dur = df["timestamp_ms"].iloc[-1] / 1000 if n > 0 else 0
    good_pct = df["is_quality_good"].mean() * 100 if n > 0 else 0
    instructions = df[df["instruction"] != "none"]["instruction"].value_counts().to_dict()
    print(f"  {name}.csv: {n} compressions, {dur:.0f}s, {good_pct:.0f}% good quality")
    if instructions:
        print(f"    instructions: {instructions}")


# ═══════════════════════════════════════════════════════════
# SCENARIOS
# ═══════════════════════════════════════════════════════════

def gen_01_textbook():
    """Perfect CPR. 110 BPM, 55mm, full recoil. 120 seconds."""
    return build_session(
        duration_sec=120,
        rate_fn=lambda i, t: 110 + np.random.normal(0, 1.5),
        depth_fn=lambda i, t: 55 + np.random.normal(0, 1.0),
        recoil_fn=lambda i, t: np.clip(98 + np.random.normal(0, 1.0), 90, 100),
        hr_fn=lambda i, t: 95 + t / 20 + np.random.normal(0, 2),
    )


def gen_02_too_fast():
    """Panicked rescuer. 135-140 BPM, shallow. 60 seconds."""
    return build_session(
        duration_sec=60,
        rate_fn=lambda i, t: 138 + np.random.normal(0, 3),
        depth_fn=lambda i, t: 46 + np.random.normal(0, 2.5),
        recoil_fn=lambda i, t: np.clip(87 + np.random.normal(0, 3), 70, 100),
        hr_fn=lambda i, t: 120 + t / 6 + np.random.normal(0, 3),
    )


def gen_03_too_slow():
    """Hesitant rescuer. 75-80 BPM, good depth. 60 seconds."""
    return build_session(
        duration_sec=60,
        rate_fn=lambda i, t: 77 + np.random.normal(0, 4),
        depth_fn=lambda i, t: 58 + np.random.normal(0, 2),
        recoil_fn=lambda i, t: np.clip(96 + np.random.normal(0, 1.5), 88, 100),
        hr_fn=lambda i, t: 85 + t / 30 + np.random.normal(0, 2),
    )


def gen_04_too_shallow():
    """Weak compressions. Good rate, 30-35mm depth. 60 seconds."""
    return build_session(
        duration_sec=60,
        rate_fn=lambda i, t: 110 + np.random.normal(0, 2),
        depth_fn=lambda i, t: 33 + np.random.normal(0, 2),
        recoil_fn=lambda i, t: np.clip(97 + np.random.normal(0, 1), 90, 100),
        hr_fn=lambda i, t: 90 + t / 15 + np.random.normal(0, 2),
    )


def gen_05_too_deep():
    """Over-aggressive. Good rate, 65-70mm depth. 60 seconds."""
    return build_session(
        duration_sec=60,
        rate_fn=lambda i, t: 108 + np.random.normal(0, 2),
        depth_fn=lambda i, t: 67 + np.random.normal(0, 2.5),
        recoil_fn=lambda i, t: np.clip(94 + np.random.normal(0, 2), 85, 100),
        hr_fn=lambda i, t: 105 + t / 10 + np.random.normal(0, 3),
    )


def gen_06_incomplete_recoil():
    """Leaning on chest. Good rate and depth, 65-75% recoil. 60 seconds."""
    return build_session(
        duration_sec=60,
        rate_fn=lambda i, t: 108 + np.random.normal(0, 2),
        depth_fn=lambda i, t: 53 + np.random.normal(0, 1.5),
        recoil_fn=lambda i, t: np.clip(70 + np.random.normal(0, 3), 55, 85),
        hr_fn=lambda i, t: 100 + t / 12 + np.random.normal(0, 3),
    )


def gen_07_gradual_fatigue():
    """Starts perfect, degrades over 180 seconds. Classic fatigue signature."""
    def rate(i, t):
        progress = t / 180
        return 110 + 25 * progress ** 1.5 + np.random.normal(0, 2)
    def depth(i, t):
        progress = t / 180
        return 56 - 24 * progress ** 1.3 + np.random.normal(0, 1.5)
    def recoil(i, t):
        progress = t / 180
        return np.clip(98 - 30 * progress ** 1.2 + np.random.normal(0, 2), 55, 100)
    def hr(i, t):
        progress = t / 180
        return 88 + 75 * progress ** 1.1 + np.random.normal(0, 4)
    return build_session(180, rate, depth, recoil, hr)


def gen_08_sudden_fatigue():
    """Good for 90 seconds, then sharp cliff. Like hitting a wall."""
    def rate(i, t):
        if t < 90:
            return 110 + np.random.normal(0, 1.5)
        drop = (t - 90) / 30
        return 110 + 30 * min(drop, 1.0) + np.random.normal(0, 3)
    def depth(i, t):
        if t < 90:
            return 55 + np.random.normal(0, 1)
        drop = (t - 90) / 30
        return 55 - 25 * min(drop, 1.0) + np.random.normal(0, 2)
    def recoil(i, t):
        if t < 90:
            return np.clip(98 + np.random.normal(0, 1), 92, 100)
        drop = (t - 90) / 30
        return np.clip(98 - 35 * min(drop, 1.0) + np.random.normal(0, 3), 55, 100)
    def hr(i, t):
        if t < 90:
            return 95 + t / 10 + np.random.normal(0, 2)
        return 140 + (t - 90) / 5 + np.random.normal(0, 4)
    return build_session(150, rate, depth, recoil, hr)


def gen_09_ventilation_pauses():
    """Good CPR with proper 30:2 pauses (~3-4s each). 120 seconds."""
    pauses = []
    t = 0
    for cycle in range(7):
        start = 16 + cycle * 17
        end = start + np.random.uniform(3, 4.5)
        if end < 120:
            pauses.append((start, end))
    return build_session(
        duration_sec=120,
        rate_fn=lambda i, t: 110 + np.random.normal(0, 1.5),
        depth_fn=lambda i, t: 55 + np.random.normal(0, 1),
        recoil_fn=lambda i, t: np.clip(97 + np.random.normal(0, 1.5), 90, 100),
        hr_fn=lambda i, t: 95 + t / 15 + np.random.normal(0, 2),
        pauses=pauses,
    )


def gen_10_long_pauses():
    """CPR with problematic long pauses (>10s). Compression fraction drops."""
    pauses = [
        (15, 18.5),     # 3.5s ok
        (35, 47),       # 12s — TOO LONG
        (62, 65),       # 3s ok
        (80, 93),       # 13s — TOO LONG
        (105, 108),     # 3s ok
    ]
    return build_session(
        duration_sec=120,
        rate_fn=lambda i, t: 112 + np.random.normal(0, 2),
        depth_fn=lambda i, t: 54 + np.random.normal(0, 1.5),
        recoil_fn=lambda i, t: np.clip(96 + np.random.normal(0, 1.5), 88, 100),
        hr_fn=lambda i, t: 100 + t / 12 + np.random.normal(0, 3),
        pauses=pauses,
    )


def gen_11_irregular_rhythm():
    """Untrained rescuer. Rate oscillates 85-140, depth all over the place."""
    def rate(i, t):
        return 110 + 25 * np.sin(2 * np.pi * 0.08 * t) + np.random.normal(0, 10)
    def depth(i, t):
        return 50 + 12 * np.sin(2 * np.pi * 0.06 * t) + np.random.normal(0, 5)
    def recoil(i, t):
        return np.clip(88 + 10 * np.sin(2 * np.pi * 0.1 * t) + np.random.normal(0, 5), 55, 100)
    def hr(i, t):
        return 110 + t / 8 + np.random.normal(0, 5)
    return build_session(90, rate, depth, recoil, hr)


def gen_12_improving():
    """Rescuer starts bad (first time), gets coaching, improves over 120s."""
    def rate(i, t):
        progress = min(t / 60, 1.0)
        base = 135 - 25 * progress  # 135 → 110
        return base + np.random.normal(0, 3 - 1.5 * progress)
    def depth(i, t):
        progress = min(t / 60, 1.0)
        base = 38 + 18 * progress  # 38 → 56
        return base + np.random.normal(0, 3 - 1.5 * progress)
    def recoil(i, t):
        progress = min(t / 60, 1.0)
        base = 78 + 20 * progress  # 78 → 98
        return np.clip(base + np.random.normal(0, 3 - 1.5 * progress), 60, 100)
    def hr(i, t):
        return 115 + t / 20 + np.random.normal(0, 3)
    return build_session(120, rate, depth, recoil, hr)


def gen_13_fast_and_shallow():
    """Common combo failure: racing + not pushing hard enough. 60 seconds."""
    return build_session(
        duration_sec=60,
        rate_fn=lambda i, t: 142 + np.random.normal(0, 4),
        depth_fn=lambda i, t: 35 + np.random.normal(0, 3),
        recoil_fn=lambda i, t: np.clip(82 + np.random.normal(0, 4), 65, 95),
        hr_fn=lambda i, t: 130 + t / 5 + np.random.normal(0, 4),
    )


def gen_14_rescuer_swap():
    """Two rescuers swapping at 120s. First fatigues, second starts fresh."""
    def rate(i, t):
        if t < 120:
            progress = t / 120
            return 110 + 18 * progress ** 1.5 + np.random.normal(0, 2)
        return 108 + np.random.normal(0, 1.5)
    def depth(i, t):
        if t < 120:
            progress = t / 120
            return 56 - 18 * progress ** 1.3 + np.random.normal(0, 1.5)
        return 57 + np.random.normal(0, 1)
    def recoil(i, t):
        if t < 120:
            progress = t / 120
            return np.clip(98 - 22 * progress ** 1.2 + np.random.normal(0, 2), 60, 100)
        return np.clip(98 + np.random.normal(0, 1), 92, 100)
    def hr(i, t):
        if t < 120:
            return 90 + t / 2.5 + np.random.normal(0, 3)
        return 95 + (t - 120) / 15 + np.random.normal(0, 2)
    # 5-second swap pause at 120s
    return build_session(240, rate, depth, recoil, hr, pauses=[(120, 125)])


def gen_15_depth_only_fade():
    """Rate stays perfect, but depth slowly fades. Subtle fatigue pattern."""
    def rate(i, t):
        return 110 + np.random.normal(0, 1.5)
    def depth(i, t):
        progress = t / 120
        return 57 - 15 * progress ** 1.5 + np.random.normal(0, 1.5)
    def recoil(i, t):
        progress = t / 120
        return np.clip(98 - 8 * progress + np.random.normal(0, 1.5), 80, 100)
    def hr(i, t):
        return 92 + t / 8 + np.random.normal(0, 2)
    return build_session(120, rate, depth, recoil, hr)


# ═══════════════════════════════════════════════════════════
# MAIN
# ═══════════════════════════════════════════════════════════

SCENARIOS = [
    ("01_textbook",          gen_01_textbook,          "Perfect CPR — baseline positive class"),
    ("02_too_fast",          gen_02_too_fast,           "Panicked rescuer, rate ~138 BPM"),
    ("03_too_slow",          gen_03_too_slow,           "Hesitant rescuer, rate ~77 BPM"),
    ("04_too_shallow",       gen_04_too_shallow,        "Weak compressions, depth ~33mm"),
    ("05_too_deep",          gen_05_too_deep,           "Over-aggressive, depth ~67mm"),
    ("06_incomplete_recoil", gen_06_incomplete_recoil,  "Leaning on chest, recoil ~70%"),
    ("07_gradual_fatigue",   gen_07_gradual_fatigue,    "Classic fatigue over 180s"),
    ("08_sudden_fatigue",    gen_08_sudden_fatigue,     "Good for 90s then cliff"),
    ("09_ventilation_pauses",gen_09_ventilation_pauses, "Proper 30:2 pauses"),
    ("10_long_pauses",       gen_10_long_pauses,        "Problematic long pauses (>10s)"),
    ("11_irregular_rhythm",  gen_11_irregular_rhythm,   "Untrained rescuer, chaotic rhythm"),
    ("12_improving",         gen_12_improving,          "Bad start, responds to coaching"),
    ("13_fast_and_shallow",  gen_13_fast_and_shallow,   "Common combo: racing + weak"),
    ("14_rescuer_swap",      gen_14_rescuer_swap,       "Two rescuers, swap at 120s"),
    ("15_depth_only_fade",   gen_15_depth_only_fade,    "Subtle: rate ok but depth fades"),
]


if __name__ == "__main__":
    print("=" * 65)
    print("CPR Pilot — Compression Event Generator (Layer 2)")
    print("=" * 65)

    for name, gen_fn, desc in SCENARIOS:
        print(f"\n{name}: {desc}")
        df = gen_fn()
        save(df, name)

    print(f"\nDone. {len(SCENARIOS)} scenarios → {OUTPUT_DIR}/")
