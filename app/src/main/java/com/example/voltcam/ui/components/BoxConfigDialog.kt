package com.example.voltcam.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voltcam.model.HardwareConfig

@Composable
fun BoxConfigDialog(
    config: HardwareConfig,
    onDismiss: () -> Unit,
    onSave: (HardwareConfig) -> Unit
) {
    var deviceId by remember { mutableStateOf(config.deviceId) }
    var zoneId by remember { mutableStateOf(config.zoneId) }
    var model by remember { mutableStateOf(config.model) }
    var firmware by remember { mutableStateOf(config.firmwareVersion) }
    var bleName by remember { mutableStateOf(config.bleAdvName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E293B),
        title = {
            Text(
                text = "Configuration du Boîtier VoltCam",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CustomTextField("Identifiant Boîtier (deviceId)", deviceId) { deviceId = it }
                CustomTextField("Zone GridTrust (zoneId)", zoneId) { zoneId = it }
                CustomTextField("Nom Annonce BLE", bleName) { bleName = it }
                CustomTextField("Modèle Matériel", model) { model = it }
                CustomTextField("Version Firmware", firmware) { firmware = it }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        config.copy(
                            deviceId = deviceId.ifEmpty { "VTC-2026-DEMO-001" },
                            zoneId = zoneId.ifEmpty { "yaounde-vi-biyem-assi" },
                            model = model.ifEmpty { "VoltCam-Standard-v1" },
                            firmwareVersion = firmware.ifEmpty { "1.0.0" },
                            bleAdvName = bleName.ifEmpty { "VoltCam-001" }
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = Color(0xFF94A3B8))
            }
        }
    )
}

@Composable
private fun CustomTextField(label: String, value: String, onValueChange: (String) -> Unit) {
    Column {
        Text(label, fontSize = 11.sp, color = Color(0xFF94A3B8))
        Spacer(modifier = Modifier.height(2.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF0F172A),
                unfocusedContainerColor = Color(0xFF0F172A),
                focusedBorderColor = Color(0xFF38BDF8),
                unfocusedBorderColor = Color(0xFF334155),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )
    }
}
