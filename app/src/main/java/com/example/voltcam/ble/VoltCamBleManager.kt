package com.example.voltcam.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import com.example.voltcam.model.HardwareConfig
import com.example.voltcam.model.VoltCamGattUuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets

private const val TAG = "VoltCamBleManager"

data class BleServerState(
    val isSupported: Boolean = true,
    val isEnabled: Boolean = false,
    val isAdvertising: Boolean = false,
    val isGattServerRunning: Boolean = false,
    val connectedDevicesCount: Int = 0,
    val statusMessage: String = "Prêt à démarrer"
)

class VoltCamBleManager(private val context: Context) {

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var bluetoothGattServer: BluetoothGattServer? = null
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null

    private val _serverState = MutableStateFlow(BleServerState())
    val serverState: StateFlow<BleServerState> = _serverState.asStateFlow()

    private val connectedDevices = mutableSetOf<BluetoothDevice>()

    // Subscribed CCCD descriptors for notifications/indications
    private val subscribedDevicesForTelemetry = mutableSetOf<BluetoothDevice>()
    private val subscribedDevicesForEvents = mutableSetOf<BluetoothDevice>()
    private val subscribedDevicesForHealth = mutableSetOf<BluetoothDevice>()

    // Characteristics references
    private var charDeviceInfo: BluetoothGattCharacteristic? = null
    private var charLiveTelemetry: BluetoothGattCharacteristic? = null
    private var charEventStream: BluetoothGattCharacteristic? = null
    private var charDeviceHealth: BluetoothGattCharacteristic? = null
    private var charConfiguration: BluetoothGattCharacteristic? = null

    // Callbacks to engine for UI logs and incoming writes
    var onLogListener: ((category: String, message: String, payload: String?) -> Unit)? = null
    var onConfigWrittenListener: ((configJson: String) -> Unit)? = null

    init {
        checkBluetoothStatus()
    }

    fun checkBluetoothStatus() {
        val isSupported = bluetoothAdapter != null
        val isEnabled = bluetoothAdapter?.isEnabled == true
        val msg = when {
            !isSupported -> "Bluetooth non supporté sur cet appareil/émulateur"
            !isEnabled -> "Bluetooth désactivé - Veuillez l'activer dans les paramètres"
            else -> "Bluetooth actif - Serveur BLE prêt"
        }
        _serverState.value = _serverState.value.copy(
            isSupported = isSupported,
            isEnabled = isEnabled,
            statusMessage = msg
        )
    }

    @SuppressLint("MissingPermission")
    fun startBleServer(config: HardwareConfig) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            checkBluetoothStatus()
            onLogListener?.invoke("BLE_GATT", "Échec démarrage : Bluetooth non disponible", null)
            return
        }

        try {
            // 1. Setup Gatt Server
            bluetoothGattServer = bluetoothManager?.openGattServer(context, gattServerCallback)
            if (bluetoothGattServer == null) {
                _serverState.value = _serverState.value.copy(
                    isGattServerRunning = false,
                    statusMessage = "Erreur ouverture serveur GATT"
                )
                onLogListener?.invoke("BLE_GATT", "Impossible d'ouvrir le serveur GATT", null)
                return
            }

            // 2. Build Service & Characteristics
            val service = BluetoothGattService(
                VoltCamGattUuid.SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )

            // Device Info (Read)
            charDeviceInfo = BluetoothGattCharacteristic(
                VoltCamGattUuid.CHAR_DEVICE_INFO_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            ).apply {
                value = config.toJsonString().toByteArray(StandardCharsets.UTF_8)
            }

            // Live Telemetry (Notify)
            charLiveTelemetry = BluetoothGattCharacteristic(
                VoltCamGattUuid.CHAR_LIVE_TELEMETRY_UUID,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            ).apply {
                addDescriptor(createCccd())
            }

            // Event Stream (Indicate & Notify)
            charEventStream = BluetoothGattCharacteristic(
                VoltCamGattUuid.CHAR_EVENT_STREAM_UUID,
                BluetoothGattCharacteristic.PROPERTY_INDICATE or BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            ).apply {
                addDescriptor(createCccd())
            }

            // Device Health (Read & Notify)
            charDeviceHealth = BluetoothGattCharacteristic(
                VoltCamGattUuid.CHAR_DEVICE_HEALTH_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ
            ).apply {
                addDescriptor(createCccd())
            }

            // Configuration (Read & Write)
            charConfiguration = BluetoothGattCharacteristic(
                VoltCamGattUuid.CHAR_CONFIGURATION_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
            ).apply {
                value = config.toJsonString().toByteArray(StandardCharsets.UTF_8)
            }

            service.addCharacteristic(charDeviceInfo)
            service.addCharacteristic(charLiveTelemetry)
            service.addCharacteristic(charEventStream)
            service.addCharacteristic(charDeviceHealth)
            service.addCharacteristic(charConfiguration)

            bluetoothGattServer?.addService(service)
            _serverState.value = _serverState.value.copy(
                isGattServerRunning = true,
                statusMessage = "Serveur GATT démarré"
            )
            onLogListener?.invoke("BLE_GATT", "Service VoltCam GATT créé (5 caractéristiques)", null)

            // 3. Start Advertising
            startAdvertising(config.bleAdvName)

        } catch (e: Exception) {
            Log.e(TAG, "Exception starting GATT server", e)
            _serverState.value = _serverState.value.copy(
                isGattServerRunning = false,
                statusMessage = "Erreur: ${e.localizedMessage}"
            )
            onLogListener?.invoke("BLE_GATT", "Erreur exception: ${e.message}", null)
        }
    }

    private fun createCccd(): BluetoothGattDescriptor {
        return BluetoothGattDescriptor(
            VoltCamGattUuid.CCCD_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        ).apply {
            value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        }
    }

    @SuppressLint("MissingPermission")
    fun startAdvertising(advName: String) {
        bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (bluetoothLeAdvertiser == null) {
            onLogListener?.invoke("BLE_GATT", "BLE Advertiser non disponible sur cet appareil", null)
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(VoltCamGattUuid.SERVICE_UUID))
            .build()

        try {
            bluetoothAdapter?.name = advName
            bluetoothLeAdvertiser?.startAdvertising(settings, data, advertiseCallback)
            onLogListener?.invoke("BLE_GATT", "Lancement de l'annonce BLE GATT sous '$advName'...", null)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting advertising", e)
            onLogListener?.invoke("BLE_GATT", "Erreur lancement annonce BLE: ${e.message}", null)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopBleServer() {
        try {
            bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
            bluetoothGattServer?.close()
            bluetoothGattServer = null
            connectedDevices.clear()
            subscribedDevicesForTelemetry.clear()
            subscribedDevicesForEvents.clear()
            subscribedDevicesForHealth.clear()

            _serverState.value = _serverState.value.copy(
                isAdvertising = false,
                isGattServerRunning = false,
                connectedDevicesCount = 0,
                statusMessage = "Serveur BLE arrêté"
            )
            onLogListener?.invoke("BLE_GATT", "Serveur BLE GATT arrêté", null)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping BLE server", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun sendTelemetryNotification(jsonPayload: String) {
        val char = charLiveTelemetry ?: return
        val bytes = jsonPayload.toByteArray(StandardCharsets.UTF_8)
        char.value = bytes

        val server = bluetoothGattServer ?: return
        val targets = subscribedDevicesForTelemetry.ifEmpty { connectedDevices }

        for (device in targets) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    server.notifyCharacteristicChanged(device, char, false, bytes)
                } else {
                    @Suppress("DEPRECATION")
                    server.notifyCharacteristicChanged(device, char, false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to notify telemetry to ${device.address}", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun sendEventIndication(jsonPayload: String) {
        val char = charEventStream ?: return
        val bytes = jsonPayload.toByteArray(StandardCharsets.UTF_8)
        char.value = bytes

        val server = bluetoothGattServer ?: return
        val targets = subscribedDevicesForEvents.ifEmpty { connectedDevices }

        for (device in targets) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    server.notifyCharacteristicChanged(device, char, true, bytes)
                } else {
                    @Suppress("DEPRECATION")
                    server.notifyCharacteristicChanged(device, char, true)
                }
                onLogListener?.invoke("BLE_GATT", "Événement BLE envoyé à ${device.address}", jsonPayload)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to indicate event to ${device.address}", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun sendHealthNotification(jsonPayload: String) {
        val char = charDeviceHealth ?: return
        val bytes = jsonPayload.toByteArray(StandardCharsets.UTF_8)
        char.value = bytes

        val server = bluetoothGattServer ?: return
        val targets = subscribedDevicesForHealth.ifEmpty { connectedDevices }

        for (device in targets) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    server.notifyCharacteristicChanged(device, char, false, bytes)
                } else {
                    @Suppress("DEPRECATION")
                    server.notifyCharacteristicChanged(device, char, false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to notify health to ${device.address}", e)
            }
        }
    }

    fun updateHardwareConfig(config: HardwareConfig) {
        val bytes = config.toJsonString().toByteArray(StandardCharsets.UTF_8)
        charDeviceInfo?.value = bytes
        charConfiguration?.value = bytes
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            _serverState.value = _serverState.value.copy(
                isAdvertising = true,
                statusMessage = "Annonce BLE active (Découvrable)"
            )
            onLogListener?.invoke("BLE_GATT", "Annonce BLE démarrée avec succès. Boîtier découvrable !", null)
        }

        override fun onStartFailure(errorCode: Int) {
            val errName = when (errorCode) {
                ADVERTISE_FAILED_ALREADY_STARTED -> "Déjà démarré"
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "Données trop grandes"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Annonce non supportée"
                ADVERTISE_FAILED_INTERNAL_ERROR -> "Erreur interne"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Trop d'annonceurs"
                else -> "Code $errorCode"
            }
            _serverState.value = _serverState.value.copy(
                isAdvertising = false,
                statusMessage = "Échec annonce BLE: $errName"
            )
            onLogListener?.invoke("BLE_GATT", "Échec annonce BLE: $errName", null)
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectedDevices.add(device)
                val count = connectedDevices.size
                _serverState.value = _serverState.value.copy(connectedDevicesCount = count)
                onLogListener?.invoke("BLE_GATT", "Client connecté : ${device.name ?: "App Flutter"} [${device.address}]", null)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectedDevices.remove(device)
                subscribedDevicesForTelemetry.remove(device)
                subscribedDevicesForEvents.remove(device)
                subscribedDevicesForHealth.remove(device)
                val count = connectedDevices.size
                _serverState.value = _serverState.value.copy(connectedDevicesCount = count)
                onLogListener?.invoke("BLE_GATT", "Client déconnecté : ${device.address}", null)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            val valBytes = characteristic.value ?: ByteArray(0)
            val subValue = if (offset < valBytes.size) {
                valBytes.copyOfRange(offset, valBytes.size)
            } else {
                ByteArray(0)
            }
            bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, subValue)
            val charName = when (characteristic.uuid) {
                VoltCamGattUuid.CHAR_DEVICE_INFO_UUID -> "device-info"
                VoltCamGattUuid.CHAR_LIVE_TELEMETRY_UUID -> "live-telemetry"
                VoltCamGattUuid.CHAR_EVENT_STREAM_UUID -> "event-stream"
                VoltCamGattUuid.CHAR_DEVICE_HEALTH_UUID -> "device-health"
                VoltCamGattUuid.CHAR_CONFIGURATION_UUID -> "configuration"
                else -> characteristic.uuid.toString()
            }
            onLogListener?.invoke("BLE_GATT", "Lecture de '$charName' par ${device.address}", String(valBytes, StandardCharsets.UTF_8))
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (value != null) {
                characteristic.value = value
                val textValue = String(value, StandardCharsets.UTF_8)
                if (characteristic.uuid == VoltCamGattUuid.CHAR_CONFIGURATION_UUID) {
                    onConfigWrittenListener?.invoke(textValue)
                }
                onLogListener?.invoke("BLE_GATT", "Écriture reçue sur '${characteristic.uuid}'", textValue)
            }

            if (responseNeeded) {
                bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (descriptor.uuid == VoltCamGattUuid.CCCD_UUID && value != null) {
                val charUuid = descriptor.characteristic.uuid
                val isEnableNotify = value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                val isEnableIndicate = value.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)

                when (charUuid) {
                    VoltCamGattUuid.CHAR_LIVE_TELEMETRY_UUID -> {
                        if (isEnableNotify) subscribedDevicesForTelemetry.add(device)
                        else subscribedDevicesForTelemetry.remove(device)
                        onLogListener?.invoke("BLE_GATT", "Abonnement télémétrie ${if (isEnableNotify) "activé" else "désactivé"} pour ${device.address}", null)
                    }
                    VoltCamGattUuid.CHAR_EVENT_STREAM_UUID -> {
                        if (isEnableNotify || isEnableIndicate) subscribedDevicesForEvents.add(device)
                        else subscribedDevicesForEvents.remove(device)
                        onLogListener?.invoke("BLE_GATT", "Abonnement événements ${if (isEnableNotify || isEnableIndicate) "activé" else "désactivé"} pour ${device.address}", null)
                    }
                    VoltCamGattUuid.CHAR_DEVICE_HEALTH_UUID -> {
                        if (isEnableNotify) subscribedDevicesForHealth.add(device)
                        else subscribedDevicesForHealth.remove(device)
                        onLogListener?.invoke("BLE_GATT", "Abonnement santé/heartbeat ${if (isEnableNotify) "activé" else "désactivé"} pour ${device.address}", null)
                    }
                }
            }

            if (responseNeeded) {
                bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }
    }
}
