package com.hackathon.cprwatch.mobile

import com.hackathon.cprwatch.shared.CprDataPoint
import com.hackathon.cprwatch.shared.CprSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

object CprRepository {

    private val _currentSession = MutableStateFlow<CprSession?>(null)
    val currentSession: StateFlow<CprSession?> = _currentSession

    private val _pastSessions = MutableStateFlow<List<CprSession>>(emptyList())
    val pastSessions: StateFlow<List<CprSession>> = _pastSessions

    fun startSession() {
        _currentSession.value = CprSession(startTimeMs = System.currentTimeMillis())
    }

    fun addDataPoint(dataPoint: CprDataPoint) {
        _currentSession.update { session ->
            session?.copy(dataPoints = session.dataPoints + dataPoint)
                ?: CprSession(
                    startTimeMs = System.currentTimeMillis(),
                    dataPoints = listOf(dataPoint)
                )
        }
    }

    fun endSession() {
        val session = _currentSession.value ?: return
        if (session.dataPoints.isNotEmpty()) {
            _pastSessions.update { it + session }
        }
        _currentSession.value = null
    }
}
