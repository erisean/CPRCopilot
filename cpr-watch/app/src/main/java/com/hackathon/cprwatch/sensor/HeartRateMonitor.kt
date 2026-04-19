package com.hackathon.cprwatch.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class HeartRateMonitor(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val hrSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

    private val _heartRate = MutableStateFlow(0)
    val heartRate: StateFlow<Int> = _heartRate

    fun start() {
        hrSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d("HeartRateMonitor", "Heart rate sensor registered")
        } ?: Log.w("HeartRateMonitor", "No heart rate sensor available")
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        _heartRate.value = 0
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_HEART_RATE) return
        val bpm = event.values[0].toInt()
        if (bpm > 0) {
            _heartRate.value = bpm
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
