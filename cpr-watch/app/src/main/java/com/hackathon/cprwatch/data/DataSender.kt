package com.hackathon.cprwatch.data

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import com.hackathon.cprwatch.shared.CompressionEvent
import com.hackathon.cprwatch.shared.CprDataPoint
import com.hackathon.cprwatch.shared.MessagePaths
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DataSender(context: Context) {

    private val messageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)

    suspend fun sendCompressionEvent(event: CompressionEvent) {
        val json = Json.encodeToString(event)
        sendToAll(MessagePaths.COMPRESSION_EVENT, json.toByteArray())
    }

    suspend fun sendDataPoint(dataPoint: CprDataPoint) {
        val json = Json.encodeToString(dataPoint)
        sendToAll(MessagePaths.DATA_POINT, json.toByteArray())
    }

    suspend fun sendSessionStart() {
        sendToAll(MessagePaths.SESSION_START, byteArrayOf())
    }

    suspend fun sendSessionStop() {
        sendToAll(MessagePaths.SESSION_STOP, byteArrayOf())
    }

    private suspend fun sendToAll(path: String, data: ByteArray) {
        val nodes = nodeClient.connectedNodes.await()
        Log.d("CPRWatch", "sendToAll path=$path nodes=${nodes.size} dataSize=${data.size}")
        if (nodes.isEmpty()) {
            Log.w("CPRWatch", "No connected nodes found — message not sent")
        }
        for (node in nodes) {
            Log.d("CPRWatch", "Sending to node=${node.displayName} id=${node.id}")
            messageClient.sendMessage(node.id, path, data).await()
        }
    }
}
