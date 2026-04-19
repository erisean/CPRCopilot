package com.hackathon.cprwatch.haptic

import android.content.Context
import android.speech.tts.TextToSpeech
import com.hackathon.cprwatch.sensor.CompressionFeedback
import java.util.Locale

class VoiceCoach(context: Context) : TextToSpeech.OnInitListener {

    private val tts = TextToSpeech(context, this)
    private var ready = false
    private var lastSpokenFeedback: CompressionFeedback? = null
    private var lastSpokenTimeMs = 0L
    private val cooldownMs = 5000L

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            tts.setSpeechRate(1.2f)
            ready = true
        }
    }

    fun speak(feedback: CompressionFeedback) {
        if (!ready) return

        val message = when (feedback) {
            CompressionFeedback.TOO_SLOW -> "Speed up"
            CompressionFeedback.TOO_FAST -> "Slow down"
//            CompressionFeedback.TOO_SHALLOW -> "Push harder"
//            CompressionFeedback.TOO_DEEP -> "Ease up"
            else -> return
        }

        val now = System.currentTimeMillis()
        if (feedback == lastSpokenFeedback && now - lastSpokenTimeMs < cooldownMs) return

        lastSpokenFeedback = feedback
        lastSpokenTimeMs = now
        tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "cpr_feedback")
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
