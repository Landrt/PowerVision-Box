package com.example.voltcam.simulator

import android.content.Context
import com.example.voltcam.ble.VoltCamBleManager
import com.example.voltcam.model.DeviceHealthData
import com.example.voltcam.model.EventData
import com.example.voltcam.model.EventType
import com.example.voltcam.model.HardwareConfig
import com.example.voltcam.model.LogCategory
import com.example.voltcam.model.LogEntry
import com.example.voltcam.model.TelemetryData
import com.example.voltcam.model.VoltageQualityState
import com.example.voltcam.model.getCurrentIsoTimestamp
import com.example.voltcam.server.VoltCamLocalServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.sin

data class BoxSimulatorState(
    val config: HardwareConfig = HardwareConfig(),
    val isAcPowerPresent: Boolean = true,
    val voltage: Double = 220.0,
    val current: Double = 2.4,
    val powerFactor: Double = 0.95,
    val frequency: Double = 50.0,
    val batteryPercent: Int = 92,
    val isBatteryCharging: Boolean = true,
    val telemetrySequence: Long = 100L,
    val heartbeatSequence: Long = 10L,
    val eventSequence: Long = 1L,
    val isTelemetryActive: Boolean = true,
    val isNoiseEnabled: Boolean = true,
    val isInstabilityScenarioRunning: Boolean = false,
    val activeScenarioName: String? = null,
    val uptimeSeconds: Long = 0L
) {
    val power: Double
        get() = if (isAcPowerPresent) voltage * current * powerFactor else 0.0

    val qualityState: VoltageQualityState
        get() = when {
            !isAcPowerPresent || voltage < 50.0 -> VoltageQualityState.OUTAGE
            voltage < config.lowVoltageThreshold -> VoltageQualityState.LOW_VOLTAGE
            voltage > config.highVoltageThreshold -> VoltageQualityState.HIGH_VOLTAGE
            isInstabilityScenarioRunning -> VoltageQualityState.UNSTABLE
            else -> VoltageQualityState.STABLE
        }
}

class BoxSimulatorEngine(
    private val context: Context,
    val bleManager: VoltCamBleManager = VoltCamBleManager(context),
    val localServer: VoltCamLocalServer = VoltCamLocalServer(context)
) {
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _simState = MutableStateFlow(BoxSimulatorState())
    val simState: StateFlow<BoxSimulatorState> = _simState.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private var telemetryJob: Job? = null
    private var heartbeatJob: Job? = null
    private var batteryLoopJob: Job? = null
    private var scenarioJob: Job? = null
    private var uptimeJob: Job? = null

    init {
        // Wire BLE and Server logs to engine
        bleManager.onLogListener = { cat, msg, payload ->
            val logCat = when (cat) {
                "BLE_GATT" -> LogCategory.BLE_GATT
                "SERVER" -> LogCategory.SERVER
                else -> LogCategory.SYSTEM
            }
            addLog(logCat, msg, payload)
        }

        localServer.onLogListener = { cat, msg, payload ->
            addLog(LogCategory.SERVER, msg, payload)
        }

        bleManager.onConfigWrittenListener = { json ->
            addLog(LogCategory.BLE_GATT, "Nouvelle configuration écrite par le client Flutter", json)
        }

        startEngine()
    }

    fun startEngine() {
        addLog(LogCategory.SYSTEM, "Démarrage du moteur de simulation du boîtier VoltCam", null)

        // 1. Telemetry Stream Loop (every 1.5s)
        telemetryJob?.cancel()
        telemetryJob = scope.launch {
            while (isActive) {
                delay(1500)
                if (_simState.value.isTelemetryActive) {
                    emitTelemetryStep()
                }
            }
        }

        // 2. Heartbeat Loop (every 5s)
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(5000)
                emitHeartbeatStep()
            }
        }

        // 3. Battery & Uptime Loop (every 1s)
        uptimeJob?.cancel()
        uptimeJob = scope.launch {
            while (isActive) {
                delay(1000)
                val curr = _simState.value
                val newUptime = curr.uptimeSeconds + 1

                // Battery logic
                val isAc = curr.isAcPowerPresent
                var batt = curr.batteryPercent
                var charging = curr.isBatteryCharging

                if (isAc && batt < 100 && newUptime % 10L == 0L) {
                    batt = (batt + 1).coerceAtMost(100)
                    charging = true
                } else if (!isAc && batt > 0 && newUptime % 15L == 0L) {
                    batt = (batt - 1).coerceAtLeast(0)
                    charging = false
                }

                _simState.value = curr.copy(
                    uptimeSeconds = newUptime,
                    batteryPercent = batt,
                    isBatteryCharging = charging
                )
            }
        }

        // Auto-start BLE Server & Local HTTP Server
        bleManager.startBleServer(_simState.value.config)
        localServer.latestHardwareConfig = _simState.value.config
        localServer.startServer(8080)
    }

    private fun emitTelemetryStep() {
        val curr = _simState.value

        // Slight noise variation if noise enabled
        val noiseV = if (curr.isNoiseEnabled && curr.isAcPowerPresent && !curr.isInstabilityScenarioRunning) {
            (Math.random() - 0.5) * 1.8
        } else 0.0

        val noiseI = if (curr.isNoiseEnabled && curr.isAcPowerPresent) {
            (Math.random() - 0.5) * 0.15
        } else 0.0

        val actualV = if (curr.isAcPowerPresent) (curr.voltage + noiseV).coerceAtLeast(0.0) else 0.0
        val actualI = if (curr.isAcPowerPresent) (curr.current + noiseI).coerceAtLeast(0.0) else 0.0

        val nextSeq = curr.telemetrySequence + 1
        _simState.value = curr.copy(
            telemetrySequence = nextSeq
        )

        val telemetry = TelemetryData(
            sequence = nextSeq,
            voltage = actualV,
            current = actualI,
            power = actualV * actualI * curr.powerFactor,
            batteryPercent = curr.batteryPercent,
            frequency = curr.frequency,
            powerFactor = curr.powerFactor,
            isAcPowerPresent = curr.isAcPowerPresent,
            qualityState = curr.qualityState
        )

        val json = telemetry.toJsonString()

        // Send over BLE GATT Notification
        bleManager.sendTelemetryNotification(json)

        // Broadcast over WebSocket
        localServer.broadcastJson(json)

        // Log telemetry sample periodically (every 5 samples)
        if (nextSeq % 5L == 0L) {
            addLog(
                LogCategory.TELEMETRY,
                "Télémétrie TR [${String.format(Locale.US, "%.1f", actualV)}V, ${String.format(Locale.US, "%.2f", actualI)}A, ${telemetry.qualityState}]",
                json
            )
        }
    }

    private fun emitHeartbeatStep() {
        val curr = _simState.value
        val nextSeq = curr.heartbeatSequence + 1
        _simState.value = curr.copy(heartbeatSequence = nextSeq)

        val health = DeviceHealthData(
            deviceId = curr.config.deviceId,
            status = if (curr.isAcPowerPresent) "ONLINE" else "DEGRADED_BATTERY",
            heartbeatSeq = nextSeq,
            batteryPercent = curr.batteryPercent,
            isCharging = curr.isBatteryCharging,
            bleConnectedClients = bleManager.serverState.value.connectedDevicesCount,
            uptimeSeconds = curr.uptimeSeconds
        )

        val json = health.toJsonString()
        bleManager.sendHealthNotification(json)
    }

    fun addLog(category: LogCategory, message: String, payloadJson: String? = null) {
        val newEntry = LogEntry(
            category = category,
            message = message,
            payloadJson = payloadJson
        )
        val currentList = _logs.value.toMutableList()
        currentList.add(0, newEntry) // insert at top
        if (currentList.size > 200) {
            currentList.removeAt(currentList.lastIndex)
        }
        _logs.value = currentList
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    // --- MANUAL CONTROLS ---

    fun setAcPower(present: Boolean) {
        _simState.value = _simState.value.copy(
            isAcPowerPresent = present,
            isBatteryCharging = present
        )
        addLog(
            LogCategory.SYSTEM,
            "Alimentation secteur modifiée : ${if (present) "Secteur PRÉSENT (220V)" else "Coupure secteur (0V)"}",
            null
        )
    }

    fun setVoltage(v: Double) {
        _simState.value = _simState.value.copy(voltage = v)
    }

    fun setCurrent(i: Double) {
        _simState.value = _simState.value.copy(current = i)
    }

    fun setBatteryPercent(b: Int) {
        _simState.value = _simState.value.copy(batteryPercent = b.coerceIn(0, 100))
    }

    fun setFrequency(f: Double) {
        _simState.value = _simState.value.copy(frequency = f)
    }

    fun toggleTelemetry(active: Boolean) {
        _simState.value = _simState.value.copy(isTelemetryActive = active)
        addLog(LogCategory.SYSTEM, "Flux de télémétrie ${if (active) "ACTIVÉ" else "PAUSÉ"}", null)
    }

    fun toggleNoise(enabled: Boolean) {
        _simState.value = _simState.value.copy(isNoiseEnabled = enabled)
    }

    fun updateHardwareConfig(newConfig: HardwareConfig) {
        _simState.value = _simState.value.copy(config = newConfig)
        bleManager.updateHardwareConfig(newConfig)
        localServer.latestHardwareConfig = newConfig
        addLog(LogCategory.SYSTEM, "Configuration du boîtier mise à jour", newConfig.toJsonString())
    }

    // --- PRESET SCENARIO RUNNERS ---

    /**
     * Scenario 1: Coupure brute avec Dernier Souffle (Outage + Last Gasp)
     */
    fun runScenarioOutageWithLastGasp() {
        scenarioJob?.cancel()
        scenarioJob = scope.launch {
            val curr = _simState.value
            val prevV = curr.voltage
            val batt = curr.batteryPercent

            _simState.value = curr.copy(
                activeScenarioName = "🔴 Coupure avec Dernier Souffle",
                isAcPowerPresent = false,
                isBatteryCharging = false
            )

            val eventSeq = curr.eventSequence + 1
            _simState.value = _simState.value.copy(eventSequence = eventSeq)

            val eventId = "${curr.config.deviceId}-${System.currentTimeMillis() % 10000}"
            val event = EventData(
                eventId = eventId,
                type = EventType.OUTAGE,
                lastGasp = true,
                voltageBeforeLoss = prevV,
                batteryPercent = batt,
                summaryText = "Coupure brute détectée. Signal de dernier souffle émis par la batterie de secours."
            )

            val json = event.toJsonString()

            addLog(
                LogCategory.SCENARIO,
                "SCÉNARIO 1 : Émission de l'événement OUTAGE avec Dernier Souffle (Last Gasp)",
                json
            )

            // Send via BLE Event Stream (Indication) and Local Server WebSocket
            bleManager.sendEventIndication(json)
            localServer.broadcastJson(json)

            // Immediate telemetry emission reflecting outage
            emitTelemetryStep()
        }
    }

    /**
     * Scenario 2: Instabilité de Tension (Voltage Instability Oscillations)
     */
    fun runScenarioVoltageInstability() {
        scenarioJob?.cancel()
        scenarioJob = scope.launch {
            val curr = _simState.value
            _simState.value = curr.copy(
                activeScenarioName = "⚠️ Instabilité de Tension",
                isAcPowerPresent = true,
                isInstabilityScenarioRunning = true
            )

            addLog(LogCategory.SCENARIO, "SCÉNARIO 2 : Lancement d'une vague d'instabilité de tension (150V à 265V)", null)

            // Trigger initial event stream notice
            val eventId = "${curr.config.deviceId}-${System.currentTimeMillis() % 10000}"
            val event = EventData(
                eventId = eventId,
                type = EventType.VOLTAGE_UNSTABLE,
                lastGasp = false,
                voltageBeforeLoss = curr.voltage,
                batteryPercent = curr.batteryPercent,
                summaryText = "Instabilité et fluctuations de tension observées sur le réseau local."
            )
            val json = event.toJsonString()
            bleManager.sendEventIndication(json)
            localServer.broadcastJson(json)

            // Fluctuate voltage sinusoidally for 10 seconds
            val startTime = System.currentTimeMillis()
            while (isActive && System.currentTimeMillis() - startTime < 10000) {
                val elapsedSec = (System.currentTimeMillis() - startTime) / 1000.0
                val oscillatedV = 205.0 + 55.0 * sin(elapsedSec * 2.5) // swings between 150V and 260V
                _simState.value = _simState.value.copy(voltage = oscillatedV)
                emitTelemetryStep()
                delay(800)
            }

            // Return to nominal
            _simState.value = _simState.value.copy(
                voltage = 220.0,
                isInstabilityScenarioRunning = false,
                activeScenarioName = null
            )
            addLog(LogCategory.SCENARIO, "SCÉNARIO 2 Terminé : Retour à la tension nominale (220V)", null)
            emitTelemetryStep()
        }
    }

    /**
     * Scenario 3: Restauration du courant (Power Restored)
     */
    fun runScenarioPowerRestored() {
        scenarioJob?.cancel()
        scenarioJob = scope.launch {
            val curr = _simState.value
            _simState.value = curr.copy(
                activeScenarioName = "🟢 Restauration du Courant",
                isAcPowerPresent = true,
                voltage = 220.0,
                current = 2.5,
                frequency = 50.0,
                isBatteryCharging = true,
                isInstabilityScenarioRunning = false
            )

            val eventId = "${curr.config.deviceId}-${System.currentTimeMillis() % 10000}"
            val event = EventData(
                eventId = eventId,
                type = EventType.RESTORED,
                lastGasp = false,
                voltageBeforeLoss = 220.0,
                batteryPercent = _simState.value.batteryPercent,
                summaryText = "Alimentation électrique restaurée et stabilisée à 220V 50Hz."
            )
            val json = event.toJsonString()

            addLog(LogCategory.SCENARIO, "SCÉNARIO 3 : Émission de l'événement RESTORED (Retour du secteur)", json)

            bleManager.sendEventIndication(json)
            localServer.broadcastJson(json)

            emitTelemetryStep()
        }
    }

    /**
     * Scenario 4: Déconnexion suspecte / Disconnect without Last Gasp (Tamper)
     */
    fun runScenarioTamperDisconnect() {
        scenarioJob?.cancel()
        scenarioJob = scope.launch {
            val curr = _simState.value
            _simState.value = curr.copy(
                activeScenarioName = "🕵️ Déconnexion Suspecte",
                isAcPowerPresent = false,
                isTelemetryActive = false
            )

            val eventId = "${curr.config.deviceId}-${System.currentTimeMillis() % 10000}"
            val event = EventData(
                eventId = eventId,
                type = EventType.DEVICE_DISCONNECTED,
                lastGasp = false, // Crucial: no last gasp indicates unexpected hardware disconnection
                voltageBeforeLoss = curr.voltage,
                batteryPercent = curr.batteryPercent,
                summaryText = "Arrêt brutal sans dernier souffle. Présomption de déconnexion suspecte ou sabotage matériel."
            )
            val json = event.toJsonString()

            addLog(LogCategory.SCENARIO, "SCÉNARIO 4 : Arrêt brutal sans Dernier Souffle -> DEVICE_DISCONNECTED", json)

            bleManager.sendEventIndication(json)
            localServer.broadcastJson(json)
        }
    }

    /**
     * Scenario 5: Mode Nominal Continu
     */
    fun runScenarioNominalContinuous() {
        scenarioJob?.cancel()
        _simState.value = _simState.value.copy(
            activeScenarioName = "⚡ Mode Nominal Continu",
            isAcPowerPresent = true,
            voltage = 220.0,
            current = 2.4,
            frequency = 50.0,
            batteryPercent = 95,
            isBatteryCharging = true,
            isTelemetryActive = true,
            isNoiseEnabled = true,
            isInstabilityScenarioRunning = false
        )
        addLog(LogCategory.SCENARIO, "SCÉNARIO 5 : Réinitialisation en mode nominal continu (220V 50Hz)", null)
        emitTelemetryStep()
    }

    fun stopAllServices() {
        telemetryJob?.cancel()
        heartbeatJob?.cancel()
        batteryLoopJob?.cancel()
        scenarioJob?.cancel()
        uptimeJob?.cancel()

        bleManager.stopBleServer()
        localServer.stopServer()
    }
}
