package com.hackathon.cprwatch.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.MaterialTheme

class MainActivity : ComponentActivity() {

    private val viewModel: CprViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val state by viewModel.uiState.collectAsState()
                when (state.screen) {
                    ScreenState.IDLE -> IdleScreen(
                        pastSessions = state.pastSessions,
                        onStartDebug = { viewModel.startSimulation() }
                    )
                    ScreenState.LIVE -> LiveSessionScreen(
                        session = state.currentSession,
                        latestDataPoint = state.latestDataPoint,
                        isSimulating = state.isSimulating,
                        onStopDebug = { viewModel.stopSimulation() }
                    )
                    ScreenState.SCORECARD -> ScorecardScreen(
                        session = state.completedSession,
                        onDismiss = { viewModel.dismissScorecard() }
                    )
                }
            }
        }
    }
}
