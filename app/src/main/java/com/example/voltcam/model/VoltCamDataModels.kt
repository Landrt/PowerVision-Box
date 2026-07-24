package com.example.voltcam.model

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Hardware Box Configuration Identity
 */
data class HardwareConfig(
    val deviceId: String = "VTC-2026-DEMO-001",
    val model: String = "VoltCam-Standard-v1",
    val firmwareVersion: String = "1.0.0",
    val zoneId: String = "yaounde-vi-biyem-assi",
    val serialNumber: String = "SN-2026-8842",
    val nominalVoltage: Double = 220.0,
    val lowVoltageThreshold: Double = 180.0,
    val highVoltageThreshold: Double = 250.0,
    val heartbeatIntervalSec: Int = 5,
    val bleAdvName: String = "VoltCam-001"
) {
    fun toJsonString(): String {
        return JSONObject().apply {
            put("deviceId", deviceId)
            put("model", model)
            put("firmwareVersion", firmwareVersion)
            put("zoneId", zoneId)
            put("serialNumber", serialNumber)
            put("nominalVoltage", nominalVoltage)
            put("lowVoltageThreshold", lowVoltageThreshold)
            put("highVoltageThreshold", highVoltageThreshold)
            put("heartbeatIntervalSec", heartbeatIntervalSec)
            put("bleAdvName", bleAdvName)
        }.toString()
    }
}

/**
 * Live Telemetry Measurement Frame
 */
data class TelemetryData(
    val sequence: Long,
    val sampledAt: String = getCurrentIsoTimestamp(),
    val voltage: Double,
    val current: Double,
    val power: Double,
    val batteryPercent: Int,
    val frequency: Double = 50.0,
    val powerFactor: Double = 0.95,
    val isAcPowerPresent: Boolean = true,
    val qualityState: VoltageQualityState = VoltageQualityState.STABLE
) {
    fun toJsonString(): String {
        return JSONObject().apply {
            put("protocolVersion", 1)
            put("sequence", sequence)
            put("sampledAt", sampledAt)
            put("voltage", String.format(Locale.US, "%.1f", voltage).toDouble())
            put("current", String.format(Locale.US, "%.2f", current).toDouble())
            put("power", String.format(Locale.US, "%.1f", power).toDouble())
            put("batteryPercent", batteryPercent)
            put("frequency", String.format(Locale.US, "%.1f", frequency).toDouble())
            put("powerFactor", String.format(Locale.US, "%.2f", powerFactor).toDouble())
            put("isAcPowerPresent", isAcPowerPresent)
            put("qualityState", qualityState.name)
        }.toString()
    }
}

enum class VoltageQualityState {
    STABLE,
    LOW_VOLTAGE,
    HIGH_VOLTAGE,
    UNSTABLE,
    OUTAGE
}

/**
 * Event Stream Payload (OUTAGE, VOLTAGE_UNSTABLE, RESTORED, DEVICE_DISCONNECTED, TAMPER_SUSPECTED)
 */
data class EventData(
    val eventId: String,
    val type: EventType,
    val occurredAt: String = getCurrentIsoTimestamp(),
    val lastGasp: Boolean = false,
    val voltageBeforeLoss: Double = 220.0,
    val batteryPercent: Int = 90,
    val summaryText: String = ""
) {
    fun toJsonString(): String {
        return JSONObject().apply {
            put("eventId", eventId)
            put("type", type.name)
            put("occurredAt", occurredAt)
            put("lastGasp", lastGasp)
            put("summary", JSONObject().apply {
                put("voltageBeforeLoss", String.format(Locale.US, "%.1f", voltageBeforeLoss).toDouble())
                put("batteryPercent", batteryPercent)
                if (summaryText.isNotEmpty()) {
                    put("description", summaryText)
                }
            })
        }.toString()
    }
}

enum class EventType {
    OUTAGE,
    VOLTAGE_UNSTABLE,
    RESTORED,
    DEVICE_DISCONNECTED,
    TAMPER_SUSPECTED
}

/**
 * Device Health Payload (Heartbeat)
 */
data class DeviceHealthData(
    val deviceId: String,
    val status: String = "ONLINE",
    val heartbeatSeq: Long,
    val timestamp: String = getCurrentIsoTimestamp(),
    val batteryPercent: Int,
    val isCharging: Boolean,
    val bleConnectedClients: Int,
    val uptimeSeconds: Long
) {
    fun toJsonString(): String {
        return JSONObject().apply {
            put("deviceId", deviceId)
            put("status", status)
            put("heartbeatSeq", heartbeatSeq)
            put("timestamp", timestamp)
            put("batteryPercent", batteryPercent)
            put("isCharging", isCharging)
            put("bleConnectedClients", bleConnectedClients)
            put("uptimeSeconds", uptimeSeconds)
        }.toString()
    }
}

/**
 * Simulator Activity Log Item
 */
data class LogEntry(
    val id: Long = System.currentTimeMillis(),
    val timestamp: String = getCurrentShortTime(),
    val category: LogCategory,
    val message: String,
    val payloadJson: String? = null
)

enum class LogCategory {
    BLE_GATT,
    TELEMETRY,
    EVENT,
    SERVER,
    SCENARIO,
    SYSTEM
}

fun getCurrentIsoTimestamp(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date())
}

fun getCurrentShortTime(): String {
    val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    return sdf.format(Date())
}
