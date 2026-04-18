package com.hackathon.cprwatch.mobile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hackathon.cprwatch.shared.CprDataPoint
import com.hackathon.cprwatch.shared.CprSession
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class MobileUiState(
    val currentSession: CprSession? = null,
    val pastSessions: List<CprSession> = emptyList(),
    val latestDataPoint: CprDataPoint? = null
)

class CprViewModel : ViewModel() {

    val uiState: StateFlow<MobileUiState> = combine(
        CprRepository.currentSession,
        CprRepository.pastSessions
    ) { current, past ->
        MobileUiState(
            currentSession = current,
            pastSessions = past,
            latestDataPoint = current?.dataPoints?.lastOrNull()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MobileUiState())
}
