package com.example.voltcam.server

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.example.voltcam.model.HardwareConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList

private const val TAG = "VoltCamLocalServer"

data class LocalServerState(
    val isRunning: Boolean = false,
    val port: Int = 8080,
    val localIpAddress: String = "127.0.0.1",
    val connectedClientsCount: Int = 0,
    val statusMessage: String = "Serveur réseau arrêté"
)

/**
 * Embedded WebSocket & HTTP Server for local simulation & testing with Flutter app
 * over Wi-Fi / LAN / ADB port forwarding (ws://<ip>:8080/ws and http://<ip>:8080/api/info)
 */
class VoltCamLocalServer(private val context: Context) {

    private var serverSocket: ServerSocket? = null
    private var isServerActive = false

    private val _serverState = MutableStateFlow(LocalServerState())
    val serverState: StateFlow<LocalServerState> = _serverState.asStateFlow()

    private val activeSockets = CopyOnWriteArrayList<Socket>()
    private val webSocketClients = CopyOnWriteArrayList<Socket>()

    var onLogListener: ((category: String, message: String, payload: String?) -> Unit)? = null
    var latestHardwareConfig: HardwareConfig = HardwareConfig()

    fun startServer(port: Int = 8080) {
        if (isServerActive) return
        isServerActive = true

        val ip = getLocalIpAddress()
        _serverState.value = LocalServerState(
            isRunning = true,
            port = port,
            localIpAddress = ip,
            statusMessage = "Écoute sur http://$ip:$port"
        )
        onLogListener?.invoke("SERVER", "Lancement du serveur HTTP/WebSocket sur http://$ip:$port", null)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = ServerSocket(port)
                while (isServerActive) {
                    val socket = serverSocket?.accept() ?: break
                    activeSockets.add(socket)
                    handleClientSocket(socket)
                }
            } catch (e: Exception) {
                if (isServerActive) {
                    Log.e(TAG, "Server socket error", e)
                    onLogListener?.invoke("SERVER", "Erreur serveur HTTP: ${e.message}", null)
                }
            } finally {
                stopServer()
            }
        }
    }

    fun stopServer() {
        isServerActive = false
        try {
            for (socket in activeSockets) {
                try { socket.close() } catch (_: Exception) {}
            }
            activeSockets.clear()
            webSocketClients.clear()

            serverSocket?.close()
            serverSocket = null

            _serverState.value = _serverState.value.copy(
                isRunning = false,
                connectedClientsCount = 0,
                statusMessage = "Serveur réseau arrêté"
            )
            onLogListener?.invoke("SERVER", "Serveur local arrêté", null)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server", e)
        }
    }

    fun broadcastJson(jsonPayload: String) {
        if (webSocketClients.isEmpty()) return

        val frame = createWebSocketFrame(jsonPayload)
        for (client in webSocketClients) {
            try {
                if (!client.isClosed) {
                    val out = client.getOutputStream()
                    out.write(frame)
                    out.flush()
                } else {
                    webSocketClients.remove(client)
                }
            } catch (e: Exception) {
                webSocketClients.remove(client)
                activeSockets.remove(client)
                updateClientCount()
            }
        }
    }

    private fun handleClientSocket(socket: Socket) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val input = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                val output = socket.getOutputStream()

                val firstLine = input.readLine() ?: return@launch
                val headers = mutableMapOf<String, String>()

                var line: String? = input.readLine()
                while (!line.isNullOrEmpty()) {
                    val currentLine = line ?: break
                    val parts = currentLine.split(":", limit = 2)
                    if (parts.size == 2) {
                        headers[parts[0].trim().lowercase()] = parts[1].trim()
                    }
                    line = input.readLine()
                }

                // WebSocket Upgrade Handshake
                if (headers["upgrade"]?.lowercase() == "websocket" && headers.containsKey("sec-websocket-key")) {
                    performWebSocketHandshake(socket, headers["sec-websocket-key"]!!, output)
                } else if (firstLine.startsWith("GET /api/device-info") || firstLine.startsWith("GET /api/info")) {
                    sendHttpResponse(output, "200 OK", "application/json", latestHardwareConfig.toJsonString())
                    socket.close()
                    activeSockets.remove(socket)
                } else {
                    // Default HTTP Welcome / API Root
                    val welcomeJson = """
                        {
                            "name": "VoltCam Box Simulator",
                            "status": "ONLINE",
                            "deviceId": "${latestHardwareConfig.deviceId}",
                            "zoneId": "${latestHardwareConfig.zoneId}",
                            "websocketEndpoint": "ws://${_serverState.value.localIpAddress}:${_serverState.value.port}/ws"
                        }
                    """.trimIndent()
                    sendHttpResponse(output, "200 OK", "application/json", welcomeJson)
                    socket.close()
                    activeSockets.remove(socket)
                }

            } catch (e: Exception) {
                activeSockets.remove(socket)
                webSocketClients.remove(socket)
            } finally {
                updateClientCount()
            }
        }
    }

    private fun performWebSocketHandshake(socket: Socket, key: String, output: OutputStream) {
        val magicString = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        val acceptKey = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-1").digest((key + magicString).toByteArray(StandardCharsets.UTF_8))
        )

        val response = "HTTP/1.1 101 Switching Protocols\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: $acceptKey\r\n\r\n"

        output.write(response.toByteArray(StandardCharsets.UTF_8))
        output.flush()

        webSocketClients.add(socket)
        updateClientCount()
        onLogListener?.invoke("SERVER", "Client WebSocket connecté [${socket.inetAddress.hostAddress}]", null)
    }

    private fun sendHttpResponse(output: OutputStream, status: String, contentType: String, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val response = "HTTP/1.1 $status\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"

        output.write(response.toByteArray(StandardCharsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun createWebSocketFrame(payload: String): ByteArray {
        val bytes = payload.toByteArray(StandardCharsets.UTF_8)
        val length = bytes.size

        val frameHeader: ByteArray = when {
            length <= 125 -> byteArrayOf(0x81.toByte(), length.toByte())
            length <= 65535 -> byteArrayOf(
                0x81.toByte(),
                126.toByte(),
                (length shr 8 and 0xFF).toByte(),
                (length and 0xFF).toByte()
            )
            else -> byteArrayOf(
                0x81.toByte(),
                127.toByte(),
                0, 0, 0, 0,
                (length shr 24 and 0xFF).toByte(),
                (length shr 16 and 0xFF).toByte(),
                (length shr 8 and 0xFF).toByte(),
                (length and 0xFF).toByte()
            )
        }

        val result = ByteArray(frameHeader.size + bytes.size)
        System.arraycopy(frameHeader, 0, result, 0, frameHeader.size)
        System.arraycopy(bytes, 0, result, frameHeader.size, bytes.size)
        return result
    }

    private fun updateClientCount() {
        _serverState.value = _serverState.value.copy(
            connectedClientsCount = webSocketClients.size
        )
    }

    fun getLocalIpAddress(): String {
        try {
            val interfaces: List<NetworkInterface> = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs: List<InetAddress> = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr = addr.hostAddress
                        val isIPv4 = sAddr.indexOf(':') < 0
                        if (isIPv4) {
                            return sAddr
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }
}
