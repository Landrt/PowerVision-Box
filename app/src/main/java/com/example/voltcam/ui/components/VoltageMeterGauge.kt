package com.example.voltcam.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voltcam.model.VoltageQualityState
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun VoltageMeterGauge(
    voltage: Double,
    isAcPresent: Boolean,
    qualityState: VoltageQualityState,
    modifier: Modifier = Modifier
) {
    val animatedVoltage by animateFloatAsState(
        targetValue = voltage.toFloat(),
        animationSpec = tween(durationMillis = 350),
        label = "VoltageGaugeAnimation"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF0F172A),
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(220.dp)
            ) {
                Canvas(modifier = Modifier.size(200.dp)) {
                    val strokeWidth = 16.dp.toPx()
                    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                    val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                    // Background Track Arc (140° to 400°, total 260°)
                    drawArc(
                        color = Color(0xFF1E293B),
                        startAngle = 140f,
                        sweepAngle = 260f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Color zones (Under-voltage red/orange, Nominal green, Over-voltage red/orange)
                    // 0 to 300V mapped to 260° sweep
                    // Low Zone: 0-180V (0 to 156°) -> Orange/Red
                    // Nominal Zone: 180-250V (156° to 216°) -> Green
                    // High Zone: 250-300V (216° to 260°) -> Red

                    val activeBrush = when {
                        !isAcPresent || animatedVoltage < 50f -> Brush.horizontalGradient(
                            listOf(Color(0xFFEF4444), Color(0xFFDC2626))
                        )
                        animatedVoltage < 180f -> Brush.horizontalGradient(
                            listOf(Color(0xFFF97316), Color(0xFFEAB308))
                        )
                        animatedVoltage > 250f -> Brush.horizontalGradient(
                            listOf(Color(0xFFEAB308), Color(0xFFEF4444))
                        )
                        else -> Brush.horizontalGradient(
                            listOf(Color(0xFF10B981), Color(0xFF06B6D4))
                        )
                    }

                    val normalizedVoltage = (animatedVoltage.coerceIn(0f, 300f) / 300f)
                    val activeSweep = normalizedVoltage * 260f

                    if (activeSweep > 0f) {
                        drawArc(
                            brush = activeBrush,
                            startAngle = 140f,
                            sweepAngle = activeSweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    // Needle pointer
                    val needleAngleRad = Math.toRadians((140f + activeSweep).toDouble())
                    val center = Offset(size.width / 2, size.height / 2)
                    val needleLength = size.width / 2 - 28.dp.toPx()
                    val needleEnd = Offset(
                        x = center.x + (needleLength * cos(needleAngleRad)).toFloat(),
                        y = center.y + (needleLength * sin(needleAngleRad)).toFloat()
                    )

                    drawLine(
                        color = Color.White,
                        start = center,
                        end = needleEnd,
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    drawCircle(
                        color = Color(0xFF38BDF8),
                        radius = 8.dp.toPx(),
                        center = center
                    )
                }

                // Digital Voltage Readout inside gauge
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isAcPresent) String.format(Locale.US, "%.1f", animatedVoltage) else "0.0",
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAcPresent) Color.White else Color(0xFFEF4444),
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "VOLTS AC",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // State Pill Badge
            val (badgeText, badgeBg, badgeTextColor) = when {
                !isAcPresent -> Triple("⚡ COUPURE SECTEUR", Color(0xFF7F1D1D), Color(0xFFFCA5A5))
                qualityState == VoltageQualityState.STABLE -> Triple("🟢 STABLE (NOMINAL 220V)", Color(0xFF064E3B), Color(0xFF6EE7B7))
                qualityState == VoltageQualityState.LOW_VOLTAGE -> Triple("⚠️ SOUS-TENSION (<180V)", Color(0xFF7C2D12), Color(0xFFFDBA74))
                qualityState == VoltageQualityState.HIGH_VOLTAGE -> Triple("⚠️ SURTENSION (>250V)", Color(0xFF701A75), Color(0xFFF5D0FE))
                qualityState == VoltageQualityState.UNSTABLE -> Triple("〰️ TENSION INSTABLE", Color(0xFF78350F), Color(0xFFFDE68A))
                else -> Triple("OUTAGE", Color.DarkGray, Color.White)
            }

            Box(
                modifier = Modifier
                    .background(badgeBg, RoundedCornerShape(30.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = badgeText,
                    color = badgeTextColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
