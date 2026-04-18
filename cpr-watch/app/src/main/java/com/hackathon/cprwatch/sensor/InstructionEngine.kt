package com.hackathon.cprwatch.sensor

data class Instruction(
    val value: String,
    val priority: Int?
) {
    companion object {
        val NONE = Instruction("none", null)
    }
}

class InstructionEngine {

    private var lastInstructionTimeMs = 0L
    private val cooldownMs = 5000L
    private val requiredConsecutive = 3

    // Consecutive failure counters
    private var consecutiveTooSlow = 0
    private var consecutiveTooFast = 0
    private var consecutiveTooShallow = 0
    private var consecutiveTooDeep = 0
    private var consecutivePoorRecoil = 0

    // Fatigue tracking
    private var sessionStartMs = 0L
    private var lastTwoMinutePromptMs = 0L

    fun reset() {
        lastInstructionTimeMs = 0L
        consecutiveTooSlow = 0
        consecutiveTooFast = 0
        consecutiveTooShallow = 0
        consecutiveTooDeep = 0
        consecutivePoorRecoil = 0
        sessionStartMs = 0L
        lastTwoMinutePromptMs = 0L
    }

    fun checkPause(timestampMs: Long): Instruction {
        if (sessionStartMs == 0L) sessionStartMs = timestampMs
        return emit(timestampMs, "resume_compressions", 1)
    }

    fun evaluate(
        timestampMs: Long,
        rollingRate: Float,
        depthMm: Float,
        recoilPct: Float,
        rescuerHr: Int?,
        isQualityGood: Boolean
    ): Instruction {
        if (sessionStartMs == 0L) sessionStartMs = timestampMs

        // Update consecutive counters
        if (rollingRate < 100) consecutiveTooSlow++ else consecutiveTooSlow = 0
        if (rollingRate > 120) consecutiveTooFast++ else consecutiveTooFast = 0
        if (depthMm < 50) consecutiveTooShallow++ else consecutiveTooShallow = 0
        if (depthMm > 60) consecutiveTooDeep++ else consecutiveTooDeep = 0
        if (recoilPct < 95) consecutivePoorRecoil++ else consecutivePoorRecoil = 0

        // Cooldown check
        if (timestampMs - lastInstructionTimeMs < cooldownMs) {
            return Instruction.NONE
        }

        // Priority evaluation: highest priority wins
        // P2: Rate issues
        if (consecutiveTooSlow >= requiredConsecutive) {
            return emit(timestampMs, "faster", 2)
        }
        if (consecutiveTooFast >= requiredConsecutive) {
            return emit(timestampMs, "slower", 2)
        }

        // P3: Depth issues
        if (consecutiveTooShallow >= requiredConsecutive) {
            return emit(timestampMs, "push_harder", 3)
        }
        if (consecutiveTooDeep >= requiredConsecutive) {
            return emit(timestampMs, "ease_up", 3)
        }

        // P4: Recoil
        if (consecutivePoorRecoil >= requiredConsecutive) {
            return emit(timestampMs, "let_chest_up", 4)
        }

        // P5: Fatigue detection (high HR + degrading quality)
        if (rescuerHr != null && rescuerHr > 140 && !isQualityGood) {
            return emit(timestampMs, "switch_rescuers", 5)
        }

        // P6: 2-minute reminder
        val elapsed = timestampMs - sessionStartMs
        if (elapsed > 120_000 && timestampMs - lastTwoMinutePromptMs > 120_000) {
            lastTwoMinutePromptMs = timestampMs
            return emit(timestampMs, "consider_switching", 6)
        }

        // P5: Encouragement when quality is good and it's been a while
        if (elapsed > 60_000 && isQualityGood) {
            return emit(timestampMs, "stay_strong", 5)
        }

        return Instruction.NONE
    }

    private fun emit(timestampMs: Long, value: String, priority: Int): Instruction {
        lastInstructionTimeMs = timestampMs
        // Reset the counter that triggered to prevent re-firing immediately after cooldown
        when (value) {
            "faster" -> consecutiveTooSlow = 0
            "slower" -> consecutiveTooFast = 0
            "push_harder" -> consecutiveTooShallow = 0
            "ease_up" -> consecutiveTooDeep = 0
            "let_chest_up" -> consecutivePoorRecoil = 0
        }
        return Instruction(value, priority)
    }
}
