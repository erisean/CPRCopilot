package com.hackathon.cprwatch.mobile

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.hackathon.cprwatch.shared.CompressionEvent
import com.hackathon.cprwatch.shared.MessagePaths
import kotlinx.serialization.json.Json

class CprListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            MessagePaths.COMPRESSION_EVENT -> {
                val json = String(event.data)
                val compression = Json.decodeFromString<CompressionEvent>(json)
                CprRepository.addCompressionEvent(compression)
            }
            MessagePaths.SESSION_START -> {
                CprRepository.startSession()
            }
            MessagePaths.SESSION_STOP -> {
                CprRepository.endSession()
            }
        }
    }
}
