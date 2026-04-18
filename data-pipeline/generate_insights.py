"""
CPR Pilot — AI-powered post-session insights using Claude API.

Takes a Layer 2 compression event CSV (or session aggregates dict),
sends it to Claude with a structured prompt, and returns typed
insights the phone app can render on the scorecard.

Usage:
    # From CLI with a synthetic data file
    python3 generate_insights.py synthetic-data/07_gradual_fatigue.csv

    # From code
    from generate_insights import generate_insights
    insights = generate_insights("synthetic-data/07_gradual_fatigue.csv")
"""

import anthropic
import json
import sys
import numpy as np
import pandas as pd


# ═══════════════════════════════════════════════════════════
# RESPONSE SCHEMA
#
# This is what the AI returns. The phone app renders each
# insight card from this structure.
# ═══════════════════════════════════════════════════════════

INSIGHT_SCHEMA = {
    "name": "session_insights",
    "description": "Post-session CPR performance insights for the rescuer scorecard",
    "input_schema": {
        "type": "object",
        "properties": {
            "overall_summary": {
                "type": "string",
                "description": "2-3 sentence overall session summary. Lead with the most important finding. Be specific with numbers."
            },
            "overall_grade": {
                "type": "string",
                "enum": ["A", "B", "C", "D", "F"],
                "description": "A=excellent, B=good, C=adequate, D=needs work, F=poor"
            },
            "insights": {
                "type": "array",
                "description": "One insight per metric category. Only include categories with meaningful findings — skip if nothing notable.",
                "items": {
                    "type": "object",
                    "properties": {
                        "category": {
                            "type": "string",
                            "enum": [
                                "compression_rate",
                                "compression_depth",
                                "recoil",
                                "cpr_fraction",
                                "fatigue",
                                "consistency",
                                "overall_technique"
                            ],
                            "description": "Which metric this insight is about"
                        },
                        "title": {
                            "type": "string",
                            "description": "Short title for the insight card, 5-8 words. e.g. 'Rate dropped after two minutes'"
                        },
                        "status": {
                            "type": "string",
                            "enum": ["good", "warning", "critical"],
                            "description": "good=in guideline, warning=marginal or trending bad, critical=out of guideline"
                        },
                        "metric_value": {
                            "type": "string",
                            "description": "The key number(s) for this category. e.g. 'Avg 118 BPM (range 105-132)'"
                        },
                        "explanation": {
                            "type": "string",
                            "description": "2-4 sentences. Explain WHY the metric looks this way — connect to root cause (fatigue, technique, anxiety). Reference specific data points (times, percentages, trends). Do NOT just restate the number."
                        },
                        "recommendation": {
                            "type": "string",
                            "description": "1-2 sentences. One specific, actionable thing to try next session. Be concrete — not 'try to improve' but 'use the haptic metronome for the full session' or 'practice 3-minute sets to build endurance'."
                        }
                    },
                    "required": ["category", "title", "status", "metric_value", "explanation", "recommendation"]
                }
            },
            "top_priority": {
                "type": "string",
                "description": "Single most impactful thing to focus on next session. One sentence."
            }
        },
        "required": ["overall_summary", "overall_grade", "insights", "top_priority"]
    }
}


# ═══════════════════════════════════════════════════════════
# SYSTEM PROMPT
# ═══════════════════════════════════════════════════════════

SYSTEM_PROMPT = """\
You are a CPR performance coach analyzing post-session data from a smartwatch-based CPR training app.

Your audience is the rescuer who just finished a CPR session (on a mannequin or in a drill). They want to know how they did and what to improve. They are NOT medical professionals by default — use plain language.

Guidelines you evaluate against (AHA 2020):
- Compression rate: 100-120 BPM
- Compression depth: 50-60mm (5-6 cm)
- Chest recoil: full release between compressions (>= 95% in our system)
- CPR fraction: >= 80% (minimize hands-off time)
- Rescuer swap: every 2 minutes to prevent fatigue

Tone rules:
- Be specific with numbers. "Your depth dropped 40%" not "your depth decreased."
- Connect metrics to root causes. Don't just report — explain WHY.
- When multiple metrics degrade together, identify the common cause (usually fatigue).
- Give credit where due. If rate was perfect, say so — even if depth was bad.
- Recommendations must be concrete and actionable. "Practice 3-minute continuous sets" not "try to push harder."
- Never be discouraging. Frame weakness as opportunity.
- Do NOT pad with generic CPR education. Every sentence should reference THIS session's data.
"""


# ═══════════════════════════════════════════════════════════
# SESSION DATA → PROMPT
# ═══════════════════════════════════════════════════════════

def compute_session_summary(df: pd.DataFrame) -> dict:
    """Compute aggregate metrics from Layer 2 compression events."""
    n = len(df)
    duration_sec = (df["timestamp_ms"].iloc[-1] - df["timestamp_ms"].iloc[0]) / 1000

    rates = df["instantaneous_rate_bpm"].values
    depths = df["estimated_depth_mm"].values
    recoils = df["recoil_pct"].values
    hr = df["rescuer_hr_bpm"].values

    # Time windows (30-second buckets)
    windows = []
    for w_start in range(0, int(duration_sec) + 1, 30):
        w_end = w_start + 30
        mask = (df["timestamp_ms"] >= w_start * 1000) & (df["timestamp_ms"] < w_end * 1000)
        w = df[mask]
        if len(w) == 0:
            continue
        windows.append({
            "window": f"{w_start}-{w_end}s",
            "compressions": int(len(w)),
            "mean_rate_bpm": round(float(w["instantaneous_rate_bpm"].mean()), 1),
            "mean_depth_mm": round(float(w["estimated_depth_mm"].mean()), 1),
            "mean_recoil_pct": round(float(w["recoil_pct"].mean()), 1),
            "pct_quality_good": round(float(w["is_quality_good"].mean() * 100), 1),
            "mean_rescuer_hr": int(w["rescuer_hr_bpm"].mean()),
        })

    # Detect pauses
    intervals = df["timestamp_ms"].diff().dropna().values
    pauses = []
    for i, gap in enumerate(intervals):
        if gap > 2000:
            pauses.append({
                "after_compression": int(i + 1),
                "duration_sec": round(gap / 1000, 1),
                "at_time_sec": round(df["timestamp_ms"].iloc[i + 1] / 1000, 1),
            })

    total_pause_time = sum(p["duration_sec"] for p in pauses)
    cpr_fraction = (duration_sec - total_pause_time) / duration_sec if duration_sec > 0 else 0

    # Instructions issued
    instructions = df[df["instruction"] != "none"]["instruction"].value_counts().to_dict()

    return {
        "total_compressions": int(n),
        "duration_sec": round(duration_sec, 1),
        "rate": {
            "mean": round(float(rates.mean()), 1),
            "median": round(float(np.median(rates)), 1),
            "std": round(float(rates.std()), 1),
            "min": round(float(rates.min()), 1),
            "max": round(float(rates.max()), 1),
            "pct_in_guideline": round(float(np.mean((rates >= 100) & (rates <= 120)) * 100), 1),
        },
        "depth": {
            "mean_mm": round(float(depths.mean()), 1),
            "std_mm": round(float(depths.std()), 1),
            "min_mm": round(float(depths.min()), 1),
            "max_mm": round(float(depths.max()), 1),
            "pct_in_guideline": round(float(np.mean((depths >= 50) & (depths <= 60)) * 100), 1),
        },
        "recoil": {
            "mean_pct": round(float(recoils.mean()), 1),
            "pct_full_recoil": round(float(np.mean(recoils >= 95) * 100), 1),
        },
        "cpr_fraction": round(cpr_fraction * 100, 1),
        "pauses": pauses,
        "rescuer_hr": {
            "start": int(hr[:5].mean()),
            "end": int(hr[-5:].mean()),
            "peak": int(hr.max()),
        },
        "time_windows": windows,
        "instructions_issued": instructions,
        "pct_all_guidelines_met": round(float(df["is_quality_good"].mean() * 100), 1),
    }


def build_user_prompt(summary: dict) -> str:
    """Format session data into the user prompt for Claude."""
    return f"""\
Analyze this CPR session and provide performance insights.

## Session Data

**Duration:** {summary['duration_sec']}s | **Total compressions:** {summary['total_compressions']} | **Overall quality:** {summary['pct_all_guidelines_met']}% met all guidelines

### Compression Rate
- Mean: {summary['rate']['mean']} BPM (std: {summary['rate']['std']})
- Range: {summary['rate']['min']} – {summary['rate']['max']} BPM
- In guideline (100-120): {summary['rate']['pct_in_guideline']}%

### Compression Depth
- Mean: {summary['depth']['mean_mm']} mm (std: {summary['depth']['std_mm']})
- Range: {summary['depth']['min_mm']} – {summary['depth']['max_mm']} mm
- In guideline (50-60mm): {summary['depth']['pct_in_guideline']}%

### Recoil
- Mean: {summary['recoil']['mean_pct']}%
- Full recoil (>=95%): {summary['recoil']['pct_full_recoil']}%

### CPR Fraction
- {summary['cpr_fraction']}%
- Pauses: {len(summary['pauses'])} detected
{chr(10).join(f"  - At {p['at_time_sec']}s: {p['duration_sec']}s pause" for p in summary['pauses']) if summary['pauses'] else '  - No pauses detected'}

### Rescuer Heart Rate
- Start: {summary['rescuer_hr']['start']} BPM
- End: {summary['rescuer_hr']['end']} BPM
- Peak: {summary['rescuer_hr']['peak']} BPM

### Performance Over Time (30-second windows)
{json.dumps(summary['time_windows'], indent=2)}

### Real-time Instructions Issued During Session
{json.dumps(summary['instructions_issued'], indent=2) if summary['instructions_issued'] else 'None — all metrics stayed in range'}
"""


# ═══════════════════════════════════════════════════════════
# API CALL
# ═══════════════════════════════════════════════════════════

def generate_insights(csv_path: str) -> dict:
    """
    Generate AI insights for a compression event CSV.

    Returns the structured insight object matching INSIGHT_SCHEMA.
    """
    df = pd.read_csv(csv_path)
    summary = compute_session_summary(df)
    user_prompt = build_user_prompt(summary)

    client = anthropic.Anthropic()

    response = client.messages.create(
        model="claude-sonnet-4-6",
        max_tokens=2048,
        system=SYSTEM_PROMPT,
        tools=[INSIGHT_SCHEMA],
        tool_choice={"type": "tool", "name": "session_insights"},
        messages=[{"role": "user", "content": user_prompt}],
    )

    for block in response.content:
        if block.type == "tool_use":
            return block.input

    raise RuntimeError("No tool_use block in response")


# ═══════════════════════════════════════════════════════════
# CLI
# ═══════════════════════════════════════════════════════════

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python3 generate_insights.py <compression_events.csv>")
        print("Example: python3 generate_insights.py synthetic-data/07_gradual_fatigue.csv")
        sys.exit(1)

    csv_path = sys.argv[1]
    print(f"Analyzing: {csv_path}")
    print()

    insights = generate_insights(csv_path)
    print(json.dumps(insights, indent=2))
