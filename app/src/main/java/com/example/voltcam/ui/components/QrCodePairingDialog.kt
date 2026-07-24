package com.example.voltcam.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voltcam.model.HardwareConfig
import com.example.voltcam.server.LocalServerState

@Composable
fun QrCodePairingDialog(
    config: HardwareConfig,
    serverState: LocalServerState,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val connectionString = """
        {
          "bleName": "${config.bleAdvName}",
          "serviceUuid": "4F4C5443-1000-8000-8000-00805F9B34FB",
          "deviceId": "${config.deviceId}",
          "zoneId": "${config.zoneId}",
          "websocket": "ws://${serverState.localIpAddress}:${serverState.port}/ws"
        }
    """.trimIndent()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E293B),
        title = {
            Text(
                text = "Appairage de l'App Flutter",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Ce boîtier virtuel émet sur BLE GATT & WebSocket Local. Scannez ou copiez la configuration :",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )

                // Visual Custom Grid (Simulated QR pattern)
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(136.dp)) {
                        val gridSize = 8
                        val cellSize = size.width / gridSize
                        for (r in 0 until gridSize) {
                            for (c in 0 until gridSize) {
                                val isFilled = (r == 0 && c < 3) || (r < 3 && c == 0) ||
                                        (r == 0 && c > 4) || (r < 3 && c == 7) ||
                                        (r > 4 && c == 0) || (r == 7 && c < 3) ||
                                        ((r + c) % 2 == 0)
                                if (isFilled) {
                                    drawRect(
                                        color = Color(0xFF0F172A),
                                        topLeft = Offset(c * cellSize, r * cellSize),
                                        size = Size(cellSize, cellSize)
                                    )
                                }
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    PairingDetail("Nom BLE :", config.bleAdvName)
                    PairingDetail("UUID Service :", "4F4C5443-1000...")
                    PairingDetail("WebSocket :", "ws://${serverState.localIpAddress}:${serverState.port}/ws")
                }

                OutlinedButton(
                    onClick = { clipboardManager.setText(AnnotatedString(connectionString)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = Color(0xFF38BDF8)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copier Configuration JSON", color = Color(0xFF38BDF8), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
            ) {
                Text("Fermer")
            }
        }
    )
}

@Composable
private fun PairingDetail(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 11.sp, color = Color(0xFF94A3B8))
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
    }
}
