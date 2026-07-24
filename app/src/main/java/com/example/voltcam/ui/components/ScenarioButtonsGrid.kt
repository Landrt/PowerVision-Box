package com.example.voltcam.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voltcam.simulator.BoxSimulatorState

@Composable
fun ScenarioButtonsGrid(
    simState: BoxSimulatorState,
    onRunOutageLastGasp: () -> Unit,
    onRunVoltageInstability: () -> Unit,
    onRunPowerRestored: () -> Unit,
    onRunTamperDisconnect: () -> Unit,
    onRunNominal: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "SCÉNARIOS DE TEST SIMULATEUR (DÉMO FLUTTER)",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF94A3B8),
            modifier = Modifier.padding(bottom = 10.dp)
        )

        // Active Scenario Banner
        if (simState.activeScenarioName != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0369A1), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "▶️ En cours: ${simState.activeScenarioName}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Scenario Card 1: Outage + Last Gasp
        ScenarioCard(
            title = "Scénario 1 : Coupure avec Dernier Souffle",
            description = "Simule la perte de secteur (220V -> 0V) et émet immédiatement l'événement 'OUTAGE' avec lastGasp=true via BLE Event Stream.",
            icon = Icons.Default.PowerOff,
            accentColor = Color(0xFFEF4444),
            onClick = onRunOutageLastGasp
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Scenario Card 2: Instabilité de Tension
        ScenarioCard(
            title = "Scénario 2 : Instabilité de Tension (150V à 265V)",
            description = "Lance une vague d'oscillations sinusoidales de tension sur 10s et émet l'événement 'VOLTAGE_UNSTABLE' pour tester le Protect Mode.",
            icon = Icons.Default.ElectricalServices,
            accentColor = Color(0xFFF59E0B),
            onClick = onRunVoltageInstability
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Scenario Card 3: Power Restored
        ScenarioCard(
            title = "Scénario 3 : Restauration du Courant",
            description = "Rétablit la tension à 220V 50Hz stable et émet l'événement 'RESTORED' à destination de l'application Flutter.",
            icon = Icons.Default.CheckCircle,
            accentColor = Color(0xFF10B981),
            onClick = onRunPowerRestored
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Scenario Card 4: Disconnect without Last Gasp (Tamper)
        ScenarioCard(
            title = "Scénario 4 : Déconnexion Suspecte / Sabotage",
            description = "Coupe le flux de télémétrie et de heartbeat sans émettre de dernier souffle -> Génère la détection 'DEVICE_DISCONNECTED'.",
            icon = Icons.Default.Warning,
            accentColor = Color(0xFFC084FC),
            onClick = onRunTamperDisconnect
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Scenario Card 5: Nominal
        ScenarioCard(
            title = "Scénario 5 : Mode Nominal Continu (220V 50Hz)",
            description = "Réinitialise le boîtier en mode d'exploitation normale stable avec de légères variations aléatoires du réseau.",
            icon = Icons.Default.Build,
            accentColor = Color(0xFF38BDF8),
            onClick = onRunNominal
        )
    }
}

@Composable
private fun ScenarioCard(
    title: String,
    description: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(accentColor.copy(alpha = 0.15f), CircleShape)
                    .border(1.dp, accentColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    lineHeight = 15.sp
                )
            }
        }
    }
}
