package com.hackathon.cprwatch.mobile

import android.content.Context
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.coroutineContext

data class WatchConnectionState(
    val isConnected: Boolean = false,
    val watchName: String? = null
)

class WatchConnectionMonitor(private val context: Context) {

    private val nodeClient = Wearable.getNodeClient(context)

    private val _state = MutableStateFlow(WatchConnectionState())
    val state: StateFlow<WatchConnectionState> = _state

    suspend fun startMonitoring() {
        while (coroutineContext.isActive) {
            try {
                val nodes: List<Node> = nodeClient.connectedNodes.await()
                val node = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
                _state.value = WatchConnectionState(
                    isConnected = node != null,
                    watchName = node?.displayName
                )
            } catch (_: Exception) {
                _state.value = WatchConnectionState(isConnected = false)
            }
            delay(3000)
        }
    }
}
