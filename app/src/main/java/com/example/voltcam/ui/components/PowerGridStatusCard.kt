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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voltcam.simulator.BoxSimulatorState
import java.util.Locale

@Composable
fun PowerGridStatusCard(
    simState: BoxSimulatorState,
    onToggleAcPower: (Boolean) -> Unit,
    onVoltageChanged: (Double) -> Unit,
    onCurrentChanged: (Double) -> Unit,
    onBatteryChanged: (Int) -> Unit,
    onFrequencyChanged: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {

            // Header with Main AC Switch
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
                                if (simState.isAcPowerPresent) Color(0xFF0284C7) else Color(0xFFDC2626),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Power,
                            contentDescription = "Power",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "ALIMENTATION SECTEUR",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (simState.isAcPowerPresent) "Secteur Connecté (AC 220V)" else "Coupure Généralisée",
                            fontSize = 12.sp,
                            color = if (simState.isAcPowerPresent) Color(0xFF38BDF8) else Color(0xFFFCA5A5)
                        )
                    }
                }

                Switch(
                    checked = simState.isAcPowerPresent,
                    onCheckedChange = onToggleAcPower,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF10B981),
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFEF4444)
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Live Key Indicators Grid (4 mini cards)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricChip(
                    label = "PUISSANCE",
                    value = String.format(Locale.US, "%.0f W", simState.power),
                    color = Color(0xFF38BDF8),
                    modifier = Modifier.weight(1f)
                )
                MetricChip(
                    label = "COURANT",
                    value = String.format(Locale.US, "%.1f A", simState.current),
                    color = Color(0xFFA78BFA),
                    modifier = Modifier.weight(1f)
                )
                MetricChip(
                    label = "FRÉQUENCE",
                    value = String.format(Locale.US, "%.1f Hz", simState.frequency),
                    color = Color(0xFFFBBF24),
                    modifier = Modifier.weight(1f)
                )
                MetricChip(
                    label = "BATTERIE",
                    value = "${simState.batteryPercent}%",
                    color = if (simState.batteryPercent > 20) Color(0xFF34D399) else Color(0xFFF87171),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Quick Voltage Presets Row
            Text(
                text = "PRÉRÉGLAGES RAPIDES DE TENSION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PresetButton("220V Normal", Color(0xFF10B981), Modifier.weight(1f)) {
                    onToggleAcPower(true)
                    onVoltageChanged(220.0)
                }
                PresetButton("155V Sous-t.", Color(0xFFF59E0B), Modifier.weight(1f)) {
                    onToggleAcPower(true)
                    onVoltageChanged(155.0)
                }
                PresetButton("265V Sur-t.", Color(0xFFEC4899), Modifier.weight(1f)) {
                    onToggleAcPower(true)
                    onVoltageChanged(265.0)
                }
                PresetButton("0V Coupure", Color(0xFFEF4444), Modifier.weight(1f)) {
                    onToggleAcPower(false)
                    onVoltageChanged(0.0)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Slider 1: Tension / Voltage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tension Réseau",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = String.format(Locale.US, "%.1f V", simState.voltage),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF38BDF8),
                    fontFamily = FontFamily.Monospace
                )
            }
            Slider(
                value = simState.voltage.toFloat(),
                onValueChange = { onVoltageChanged(it.toDouble()) },
                valueRange = 0f..300f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF38BDF8),
                    activeTrackColor = Color(0xFF0284C7),
                    inactiveTrackColor = Color(0xFF334155)
                )
            )

            // Slider 2: Courant / Current
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Courant Absorbé",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = String.format(Locale.US, "%.1f A", simState.current),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFA78BFA),
                    fontFamily = FontFamily.Monospace
                )
            }
            Slider(
                value = simState.current.toFloat(),
                onValueChange = { onCurrentChanged(it.toDouble()) },
                valueRange = 0f..30f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFA78BFA),
                    activeTrackColor = Color(0xFF7C3AED),
                    inactiveTrackColor = Color(0xFF334155)
                )
            )

            // Slider 3: Batterie de Secours
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Batterie de Secours (Boîtier)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = "${simState.batteryPercent}% ${if (simState.isBatteryCharging) "⚡" else ""}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF34D399),
                    fontFamily = FontFamily.Monospace
                )
            }
            Slider(
                value = simState.batteryPercent.toFloat(),
                onValueChange = { onBatteryChanged(it.toInt()) },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF34D399),
                    activeTrackColor = Color(0xFF059669),
                    inactiveTrackColor = Color(0xFF334155)
                )
            )
        }
    }
}

@Composable
private fun MetricChip(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF64748B)
            )
        }
    }
}

@Composable
private fun PresetButton(
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = accentColor.copy(alpha = 0.15f),
            contentColor = accentColor
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
