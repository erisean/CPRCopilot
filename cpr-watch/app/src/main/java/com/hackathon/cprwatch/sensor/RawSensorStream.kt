package com.hackathon.cprwatch.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

data class RawSensorSample(
    val timestampMs: Long,
    val accelX: Float,
    val accelY: Float,
    val accelZ: Float,
    val gyroX: Float,
    val gyroY: Float,
    val gyroZ: Float,
    val hrBpm: Int? = null,
    val hrConfidence: Int? = null
)

class RawSensorStream(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val heartRate = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

    private val _samples = MutableSharedFlow<RawSensorSample>(extraBufferCapacity = 256)
    val samples: SharedFlow<RawSensorSample> = _samples

    private var sessionStartNanos = 0L
    private var latestAccel = FloatArray(3)
    private var latestGyro = FloatArray(3)
    private var latestHr: Int? = null
    private var latestHrConfidence: Int? = null
    private var lastAccelTimestamp = 0L

    fun start() {
        sessionStartNanos = System.nanoTime()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
        heartRate?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        sessionStartNanos = 0L
        latestHr = null
        latestHrConfidence = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                latestAccel[0] = event.values[0]
                latestAccel[1] = event.values[1]
                latestAccel[2] = event.values[2]
                lastAccelTimestamp = event.timestamp
                emitSample()
            }
            Sensor.TYPE_GYROSCOPE -> {
                latestGyro[0] = event.values[0]
                latestGyro[1] = event.values[1]
                latestGyro[2] = event.values[2]
            }
            Sensor.TYPE_HEART_RATE -> {
                val bpm = event.values[0].toInt()
                val confidence = if (event.values.size > 1) event.values[1].toInt() else null
                if (confidence == null || confidence >= 50) {
                    latestHr = bpm
                    latestHrConfidence = confidence
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun emitSample() {
        val elapsedMs = (lastAccelTimestamp - sessionStartNanos) / 1_000_000
        _samples.tryEmit(
            RawSensorSample(
                timestampMs = elapsedMs,
                accelX = latestAccel[0],
                accelY = latestAccel[1],
                accelZ = latestAccel[2],
                gyroX = latestGyro[0],
                gyroY = latestGyro[1],
                gyroZ = latestGyro[2],
                hrBpm = latestHr,
                hrConfidence = latestHrConfidence
            )
        )
        latestHr = null
        latestHrConfidence = null
    }
}
