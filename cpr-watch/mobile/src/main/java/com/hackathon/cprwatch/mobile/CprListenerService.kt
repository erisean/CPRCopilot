package com.hackathon.cprwatch.mobile

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.hackathon.cprwatch.shared.CprDataPoint
import kotlinx.serialization.json.Json

class CprListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            CprDataPoint.MESSAGE_PATH -> {
                val json = String(event.data)
                val dataPoint = Json.decodeFromString<CprDataPoint>(json)
                CprRepository.addDataPoint(dataPoint)
            }
            CprDataPoint.SESSION_START_PATH -> {
                CprRepository.startSession()
            }
            CprDataPoint.SESSION_STOP_PATH -> {
                CprRepository.endSession()
            }
        }
    }
}
