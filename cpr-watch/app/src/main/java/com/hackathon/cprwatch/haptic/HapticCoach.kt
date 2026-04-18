package com.hackathon.cprwatch.haptic

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HapticCoach(context: Context) {

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    private var metronomeJob: Job? = null

    // 110 bpm = 545ms interval (middle of AHA's 100-120 range)
    private val targetIntervalMs = 545L

    private val tickEffect = VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)
    private val warningEffect = VibrationEffect.createWaveform(
        longArrayOf(0, 100, 50, 100),
        intArrayOf(0, 255, 0, 255),
        -1
    )

    fun startMetronome(scope: CoroutineScope) {
        stopMetronome()
        metronomeJob = scope.launch {
            while (isActive) {
                vibrator.vibrate(tickEffect)
                delay(targetIntervalMs)
            }
        }
    }

    fun stopMetronome() {
        metronomeJob?.cancel()
        metronomeJob = null
        vibrator.cancel()
    }

    fun pulseWarning() {
        vibrator.vibrate(warningEffect)
    }

    val isRunning: Boolean get() = metronomeJob?.isActive == true
}
