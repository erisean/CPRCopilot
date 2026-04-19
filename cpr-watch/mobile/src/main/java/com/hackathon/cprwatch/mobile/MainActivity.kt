package com.hackathon.cprwatch.mobile

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.MaterialTheme
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.hackathon.cprwatch.shared.CompressionEvent
import com.hackathon.cprwatch.shared.MessagePaths
import kotlinx.serialization.json.Json

class MainActivity : ComponentActivity(), MessageClient.OnMessageReceivedListener {

    private val viewModel: CprViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                val state by viewModel.uiState.collectAsState()

                AnimatedContent(
                    targetState = state.screen,
                    transitionSpec = {
                        val forward = targetState.ordinal > initialState.ordinal
                        if (forward) {
                            (slideInHorizontally(tween(300)) { it } + fadeIn(tween(300)))
                                .togetherWith(slideOutHorizontally(tween(300)) { -it / 3 } + fadeOut(tween(150)))
                        } else {
                            (slideInHorizontally(tween(300)) { -it } + fadeIn(tween(300)))
                                .togetherWith(slideOutHorizontally(tween(300)) { it / 3 } + fadeOut(tween(150)))
                        }
                    },
                    label = "screen"
                ) { screen ->
                    when (screen) {
                        ScreenState.IDLE -> IdleScreen(
                            pastSessions = state.pastSessions,
                            watchConnected = state.watchConnected,
                            watchName = state.watchName,
                            onStartSession = { viewModel.startSession() },
                            onStartDebug = { viewModel.startSimulation() },
                            onShowHistory = { viewModel.showHistory() }
                        )
                        ScreenState.LIVE -> LiveSessionScreen(
                            session = state.currentSession,
                            latestEvent = state.latestEvent,
                            surfaceCalibrated = state.surfaceCalibrated,
                            surfaceCalibrationProgress = state.surfaceCalibrationProgress,
                            surfaceProfile = state.surfaceProfile,
                            onStopSession = { viewModel.stopSession() }
                        )
                        ScreenState.SCORECARD -> ScorecardScreen(
                            session = state.completedSession,
                            surfaceProfile = state.surfaceProfile,
                            onDismiss = { viewModel.dismissScorecard() }
                        )
                        ScreenState.HISTORY -> SessionHistoryScreen(
                            pastSessions = state.pastSessions,
                            onSelectSession = { viewModel.showHistoryDetail(it) },
                            onBack = { viewModel.backFromHistory() }
                        )
                        ScreenState.HISTORY_DETAIL -> ScorecardScreen(
                            session = state.selectedHistorySession,
                            onDismiss = { viewModel.backFromHistoryDetail() }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getMessageClient(this).addListener(this)
        Log.d("CPRWatch", "MessageClient listener registered")
    }

    override fun onPause() {
        super.onPause()
        Wearable.getMessageClient(this).removeListener(this)
    }

    override fun onMessageReceived(event: MessageEvent) {
        Log.d("CPRWatch", "Message received: path=${event.path}, size=${event.data.size}")
        when (event.path) {
            MessagePaths.COMPRESSION_EVENT -> {
                try {
                    val json = String(event.data)
                    val compression = Json.decodeFromString<CompressionEvent>(json)
                    CprRepository.addCompressionEvent(compression)
                    Log.d("CPRWatch", "Compression #${compression.compressionIdx} rate=${compression.rollingRateBpm}")
                } catch (e: Exception) {
                    Log.e("CPRWatch", "Failed to parse compression event", e)
                }
            }
            MessagePaths.SESSION_START -> {
                Log.d("CPRWatch", "Session start received from watch")
                CprRepository.startSession()
            }
            MessagePaths.SESSION_STOP -> {
                Log.d("CPRWatch", "Session stop received from watch")
                CprRepository.endSession()
            }
        }
    }
}
