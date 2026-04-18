# CPR Pilot — Data Pipeline Specification

## Overview

The CPR Pilot app collects accelerometer and biometric data from a Samsung Galaxy Watch worn by a rescuer performing CPR. Data flows through three layers, from raw hardware signals to actionable analytics.

```
Samsung Watch                                          Phone App
┌──────────────────────────────────────────┐           ┌──────────────────────┐
│                                          │           │                      │
│  Layer 1          Layer 2                │  sync     │  Layer 3             │
│  Raw Sensors ──►  Compression  ─────────────────►   Session Package  │
│  50 Hz            Events                 │           │  (JSON)              │
│  (clock-driven)   ~2 Hz                  │           │                      │
│                   (event-driven)         │           │  • Aggregates        │
│                        │                 │           │  • Time windows      │
│                        ▼                 │           │  • Pauses            │
│                   Haptic + Audio         │           │  • Fatigue analysis  │
│                   Feedback               │           │  • Scoring           │
│                                          │           │                      │
└──────────────────────────────────────────┘           └──────────────────────┘
```

## Layer 1 — Raw Sensor Stream

**What:** Direct hardware sensor output from the watch.

**Trigger:** Clock — one row every 20ms (50 Hz), regardless of what's happening.

**Where it lives:** On-watch memory during the session. Optionally synced to phone for reprocessing.

**Size:** ~153 KB per 60-second session.

### Schema


| Column          | Type    | Description                                                        |
| --------------- | ------- | ------------------------------------------------------------------ |
| `timestamp_ms`  | int64   | Milliseconds since session start (monotonic)                       |
| `accel_x_mps2`  | float32 | Lateral acceleration (m/s²)                                        |
| `accel_y_mps2`  | float32 | Forearm-axis acceleration (m/s²)                                   |
| `accel_z_mps2`  | float32 | Vertical acceleration — **primary compression axis** (m/s²)        |
| `gyro_x_dps`    | float32 | Angular velocity, roll (deg/s)                                     |
| `gyro_y_dps`    | float32 | Angular velocity, pitch (deg/s)                                    |
| `gyro_z_dps`    | float32 | Angular velocity, yaw (deg/s)                                      |
| `hr_bpm`        | uint8?  | Rescuer heart rate — null except ~once per second when PPG reports |
| `hr_confidence` | uint8?  | PPG signal confidence 0-100 — null when `hr_bpm` is null           |


### Example rows

```
timestamp_ms, accel_x, accel_y, accel_z,  gyro_x, gyro_y, gyro_z, hr_bpm, hr_confidence
0,            0.12,    0.34,    -2.71,    1.5,    -0.8,   0.3,    ,
20,           0.08,    0.29,    -5.63,    2.1,    -1.2,   0.1,    ,
40,           -0.15,   0.41,    -7.82,    3.4,    -2.1,   -0.5,   ,
...
1000,         0.22,    0.18,    -1.05,    0.9,    -0.3,   0.2,    95,     78
```

### Notes

- `accel_z` carries the compression signal. During active CPR at 110 BPM and 55mm depth, expect peaks of ~±7-10 m/s².
- `hr_bpm` is sparse — most rows are null. The PPG sensor reports ~1 reading per second, and noisy readings (confidence < 50) should be discarded.
- `gyro` data is used to correct for wrist orientation so the "vertical" axis stays aligned with gravity regardless of how the watch sits on the wrist.
- The x/y accelerometer axes carry low-amplitude noise (body sway, lateral movement) — useful for detecting motion artifacts but not primary signals.

---

## Layer 2 — Compression Events

**What:** Per-compression metrics derived from Layer 1 signal processing.

**Trigger:** Event — one row is emitted each time the signal processing pipeline detects a compression peak in the filtered `accel_z` signal.

**Where it lives:** On-watch memory. Drives the real-time instruction loop. Synced into Layer 3 after session ends.

**Size:** ~7 KB per 60-second session.

### This is NOT a time series

Layer 2 is an **event log**. Rows are spaced irregularly:

- During active CPR: rows arrive every ~450-600ms (100-130 BPM)
- During pauses: gaps of 2-50+ seconds (ventilation, fatigue, AED)
- Under fatigue: spacing shrinks as the rescuer unconsciously speeds up

### Schema


| Column                   | Type    | Description                                                                        |
| ------------------------ | ------- | ---------------------------------------------------------------------------------- |
| `compression_idx`        | uint16  | Sequential compression number (1-based)                                            |
| `timestamp_ms`           | int64   | Peak of this compression (ms since session start)                                  |
| `interval_ms`            | uint16  | Time since previous compression (ms)                                               |
| `instantaneous_rate_bpm` | float32 | 60000 / interval_ms                                                                |
| `rolling_rate_bpm`       | float32 | Rolling average of last 5 intervals                                                |
| `estimated_depth_mm`     | float32 | From double-integration of current cycle                                           |
| `recoil_pct`             | float32 | 0-100 — how fully chest returned to rest                                           |
| `duty_cycle`             | float32 | 0-1 — fraction of cycle spent in downstroke                                        |
| `peak_accel_mps2`        | float32 | Peak vertical acceleration this compression                                        |
| `wrist_angle_deg`        | float32 | Deviation from perpendicular to chest surface                                      |
| `rescuer_hr_bpm`         | uint8?  | Most recent valid HR reading from PPG                                              |
| `is_quality_good`        | bool    | True if rate AND depth AND recoil are all within AHA guidelines                    |
| `instruction`            | string  | Instruction issued after this compression (see below) — `"none"` if no instruction |
| `instruction_priority`   | uint8?  | P1-P6 — null if no instruction                                                     |


### Instruction values


| Value                 | Priority | Triggered when                                        |
| --------------------- | -------- | ----------------------------------------------------- |
| `none`                | —        | All metrics acceptable, or cooldown period active     |
| `resume_compressions` | P1       | Pause > 5 seconds detected                            |
| `faster`              | P2       | Rolling rate < 100 BPM for 3+ compressions            |
| `slower`              | P2       | Rolling rate > 120 BPM for 3+ compressions            |
| `push_harder`         | P3       | Depth < 50mm for 3+ compressions                      |
| `ease_up`             | P3       | Depth > 60mm for 3+ compressions                      |
| `let_chest_up`        | P4       | Recoil < 95% for 3+ compressions                      |
| `switch_rescuers`     | P5       | HR high + quality degrading (A+B fatigue condition)   |
| `consider_switching`  | P6       | 2-minute timer elapsed                                |
| `stay_strong`         | P5       | Solo rescuer — encouragement instead of switch prompt |


### Instruction rules

- Only **one instruction** at a time — highest priority wins
- Only fires after **3+ consecutive** compressions fail the same check
- Minimum **5 seconds** between voice prompts
- Haptic metronome at 110 BPM runs **continuously** (independent of instructions)

### Example rows

```
idx, timestamp_ms, interval_ms, inst_rate, rolling_rate, depth_mm, recoil_pct, instruction
1,   545,          545,         110.1,     110.1,        54.2,     97.8,       none
2,   1090,         545,         110.1,     110.1,        55.8,     98.1,       none
3,   1625,         535,         112.1,     110.8,        53.9,     97.2,       none
...
45,  24200,        420,         142.9,     138.5,        44.1,     86.3,       slower
...
78,  42100,        —,           —,         —,            —,        —,          resume_compressions
```

### How Layer 2 is produced from Layer 1

```
Layer 1 (50 Hz accel_z)
    │
    ├── 1. Orientation correction (gyro fusion → gravity-aligned vertical axis)
    ├── 2. Bandpass filter 1-8 Hz (4th-order Butterworth)
    ├── 3. Peak detection (adaptive threshold, min 300ms inter-peak)
    │
    └── For each detected peak:
        ├── Rate:   60000 / (this_peak_ms - prev_peak_ms)
        ├── Depth:  double-integrate accel between cycle boundaries, zero-velocity reset
        ├── Recoil: measure acceleration baseline between peaks
        ├── Priority gate → instruction decision
        └── Emit one Layer 2 row
```

---

## Layer 3 — Session Sync Package (TBD)

**Post analysis data based on Layer2.** 

