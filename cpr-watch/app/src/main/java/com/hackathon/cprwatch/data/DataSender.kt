package com.hackathon.cprwatch.data

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.hackathon.cprwatch.shared.CprDataPoint
import com.hackathon.cprwatch.shared.CompressionEvent
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DataSender(context: Context) {

    private val messageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)

    suspend fun sendCompressionEvent(event: CompressionEvent) {
        val json = Json.encodeToString(event)
        val nodes = nodeClient.connectedNodes.await()
        for (node in nodes) {
            messageClient.sendMessage(
                node.id,
                CompressionEvent.MESSAGE_PATH,
                json.toByteArray()
            ).await()
        }
    }

    suspend fun sendDataPoint(dataPoint: CprDataPoint) {
        val json = Json.encodeToString(dataPoint)
        val nodes = nodeClient.connectedNodes.await()
        for (node in nodes) {
            messageClient.sendMessage(
                node.id,
                CprDataPoint.MESSAGE_PATH,
                json.toByteArray()
            ).await()
        }
    }

    suspend fun sendSessionStart() {
        val nodes = nodeClient.connectedNodes.await()
        for (node in nodes) {
            messageClient.sendMessage(
                node.id,
                CprDataPoint.SESSION_START_PATH,
                byteArrayOf()
            ).await()
        }
    }

    suspend fun sendSessionStop() {
        val nodes = nodeClient.connectedNodes.await()
        for (node in nodes) {
            messageClient.sendMessage(
                node.id,
                CprDataPoint.SESSION_STOP_PATH,
                byteArrayOf()
            ).await()
        }
    }
}
