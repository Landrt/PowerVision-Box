package com.example.voltcam.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voltcam.ble.BleServerState
import com.example.voltcam.model.HardwareConfig
import com.example.voltcam.server.LocalServerState

@Composable
fun BleServerStatusCard(
    bleState: BleServerState,
    serverState: LocalServerState,
    config: HardwareConfig,
    onStartBleServer: () -> Unit,
    onStopBleServer: () -> Unit,
    onStartLocalServer: () -> Unit,
    onStopLocalServer: () -> Unit,
    onShowQrCodeDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {

            // Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                if (bleState.isAdvertising) Color(0xFF3B82F6) else Color(0xFF64748B),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (bleState.connectedDevicesCount > 0) Icons.Default.BluetoothConnected else Icons.Default.BluetoothSearching,
                            contentDescription = "BLE",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "SERVEUR BLE GATT & RÉSEAU LOCAL",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = bleState.statusMessage,
                            fontSize = 11.sp,
                            color = if (bleState.isAdvertising) Color(0xFF38BDF8) else Color(0xFF94A3B8)
                        )
                    }
                }

                Button(
                    onClick = onShowQrCodeDialog,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = "QR",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Appairer", fontSize = 11.sp, color = Color(0xFF38BDF8))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // BLE Contract Info Table
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                InfoRow("Nom BLE d'annonce :", config.bleAdvName, Color(0xFF38BDF8))
                InfoRow("ID Boîtier :", config.deviceId, Color.White)
                InfoRow("UUID Service Primary :", "4F4C5443-1000-8000-8000-00805F9B34FB", Color(0xFFFBBF24))
                InfoRow("Clients BLE Connectés :", "${bleState.connectedDevicesCount} appareil(s)", Color(0xFF34D399))
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Local WebSocket/HTTP Info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lan,
                            contentDescription = "LAN",
                            tint = Color(0xFFA78BFA),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Serveur WebSocket / HTTP Local :",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    Text(
                        text = if (serverState.isRunning) "ACTIF" else "INACTIF",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (serverState.isRunning) Color(0xFF10B981) else Color(0xFFEF4444)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))

                val wsUrl = "ws://${serverState.localIpAddress}:${serverState.port}/ws"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = wsUrl,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFA78BFA),
                        fontFamily = FontFamily.Monospace
                    )
                    OutlinedButton(
                        onClick = { clipboardManager.setText(AnnotatedString(wsUrl)) },
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 8.dp,
                            vertical = 2.dp
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copier", fontSize = 10.sp, color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (bleState.isAdvertising) {
                    Button(
                        onClick = onStopBleServer,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7F1D1D)),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Arrêter BLE", fontSize = 12.sp)
                    }
                } else {
                    Button(
                        onClick = onStartBleServer,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Relancer BLE", fontSize = 12.sp)
                    }
                }

                if (serverState.isRunning) {
                    OutlinedButton(
                        onClick = onStopLocalServer,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Stop WS", fontSize = 12.sp, color = Color(0xFFFCA5A5))
                    }
                } else {
                    OutlinedButton(
                        onClick = onStartLocalServer,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Start WS", fontSize = 12.sp, color = Color(0xFF38BDF8))
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 11.sp, color = Color(0xFF94A3B8))
        Text(
            value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            fontFamily = FontFamily.Monospace
        )
    }
}
