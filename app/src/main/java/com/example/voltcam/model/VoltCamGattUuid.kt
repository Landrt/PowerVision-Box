package com.example.voltcam.model

import java.util.UUID

/**
 * VoltCam Standard BLE GATT UUID Specification
 * Based on VoltCam Standard Contract
 */
object VoltCamGattUuid {
    // Service UUID: VoltCam Standard Primary Service
    val SERVICE_UUID: UUID = UUID.fromString("4F4C5443-1000-8000-8000-00805F9B34FB")

    // Characteristic: Device Info (Read)
    // Hardware ID, Model, Firmware, Battery Capacity
    val CHAR_DEVICE_INFO_UUID: UUID = UUID.fromString("4F4C5443-1001-8000-8000-00805F9B34FB")

    // Characteristic: Live Telemetry (Notify)
    // Sequence, timestamp, voltage, current, power, battery %, frequency, status
    val CHAR_LIVE_TELEMETRY_UUID: UUID = UUID.fromString("4F4C5443-1002-8000-8000-00805F9B34FB")

    // Characteristic: Event Stream (Indicate / Notify)
    // Event ID, eventType (OUTAGE, VOLTAGE_UNSTABLE, RESTORED, DEVICE_DISCONNECTED, TAMPER_SUSPECTED), occurredAt, lastGasp, summary
    val CHAR_EVENT_STREAM_UUID: UUID = UUID.fromString("4F4C5443-1003-8000-8000-00805F9B34FB")

    // Characteristic: Device Health (Read / Notify)
    // Heartbeat, BLE quality, battery state, measurement state
    val CHAR_DEVICE_HEALTH_UUID: UUID = UUID.fromString("4F4C5443-1004-8000-8000-00805F9B34FB")

    // Characteristic: Configuration (Read / Write)
    // Config version and active threshold settings
    val CHAR_CONFIGURATION_UUID: UUID = UUID.fromString("4F4C5443-1005-8000-8000-00805F9B34FB")

    // Standard Client Characteristic Configuration Descriptor (CCCD)
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
