package com.hackathon.cprwatch.mobile

import com.hackathon.cprwatch.shared.CompressionEvent
import com.hackathon.cprwatch.shared.CprSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

object CprRepository {

    private val _currentSession = MutableStateFlow<CprSession?>(null)
    val currentSession: StateFlow<CprSession?> = _currentSession

    private val _pastSessions = MutableStateFlow<List<CprSession>>(emptyList())
    val pastSessions: StateFlow<List<CprSession>> = _pastSessions

    private val _simulating = MutableStateFlow(false)
    val simulating: StateFlow<Boolean> = _simulating

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening

    fun startListening() {
        _listening.value = true
    }

    fun stopListening() {
        _listening.value = false
    }

    fun startSession() {
        _currentSession.value = CprSession(startTimeMs = System.currentTimeMillis())
    }

    fun addCompressionEvent(event: CompressionEvent) {
        _currentSession.update { session ->
            session?.copy(compressionEvents = session.compressionEvents + event)
                ?: CprSession(
                    startTimeMs = System.currentTimeMillis(),
                    compressionEvents = listOf(event)
                )
        }
    }

    fun endSession() {
        val session = _currentSession.value ?: return
        if (session.compressionEvents.isNotEmpty()) {
            _pastSessions.update { it + session }
        }
        _currentSession.value = null
    }

    fun setSimulating(value: Boolean) {
        _simulating.value = value
    }
}
