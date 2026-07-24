package com.example.voltcam.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voltcam.simulator.BoxSimulatorEngine
import com.example.voltcam.ui.components.BleServerStatusCard
import com.example.voltcam.ui.components.BoxConfigDialog
import com.example.voltcam.ui.components.LogViewerSheet
import com.example.voltcam.ui.components.PowerGridStatusCard
import com.example.voltcam.ui.components.QrCodePairingDialog
import com.example.voltcam.ui.components.ScenarioButtonsGrid
import com.example.voltcam.ui.components.VoltageMeterGauge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    engine: BoxSimulatorEngine,
    modifier: Modifier = Modifier
) {
    val simState by engine.simState.collectAsState()
    val bleState by engine.bleManager.serverState.collectAsState()
    val serverState by engine.localServer.serverState.collectAsState()
    val logs by engine.logs.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showConfigDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }

    // Request Bluetooth permissions on start (Android 12+)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        engine.bleManager.checkBluetoothStatus()
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = Color(0xFF0F172A),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF020617)),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFF0284C7), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = "VoltCam",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "VoltCam Box",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                            Text(
                                text = "${simState.config.deviceId} • ${simState.config.bleAdvName}",
                                color = Color(0xFF38BDF8),
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                actions = {
                    // Status dot
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                if (bleState.isAdvertising) Color(0xFF10B981) else Color(0xFFEF4444),
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(onClick = { showQrDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = "Pairing QR",
                            tint = Color(0xFF38BDF8)
                        )
                    }

                    IconButton(onClick = { showConfigDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF020617),
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                NavigationBarItem(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    icon = { Icon(Icons.Default.Speed, contentDescription = "Mesures") },
                    label = { Text("Tension") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF38BDF8),
                        selectedTextColor = Color(0xFF38BDF8),
                        indicatorColor = Color(0xFF1E293B),
                        unselectedIconColor = Color(0xFF64748B),
                        unselectedTextColor = Color(0xFF64748B)
                    )
                )

                NavigationBarItem(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    icon = { Icon(Icons.Default.ElectricalServices, contentDescription = "Scénarios") },
                    label = { Text("Scénarios") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF38BDF8),
                        selectedTextColor = Color(0xFF38BDF8),
                        indicatorColor = Color(0xFF1E293B),
                        unselectedIconColor = Color(0xFF64748B),
                        unselectedTextColor = Color(0xFF64748B)
                    )
                )

                NavigationBarItem(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    icon = { Icon(Icons.Default.Bluetooth, contentDescription = "BLE & Réseau") },
                    label = { Text("BLE / Network") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF38BDF8),
                        selectedTextColor = Color(0xFF38BDF8),
                        indicatorColor = Color(0xFF1E293B),
                        unselectedIconColor = Color(0xFF64748B),
                        unselectedTextColor = Color(0xFF64748B)
                    )
                )

                NavigationBarItem(
                    selected = selectedTabIndex == 3,
                    onClick = { selectedTabIndex = 3 },
                    icon = { Icon(Icons.Default.Terminal, contentDescription = "Logs") },
                    label = { Text("Console") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF38BDF8),
                        selectedTextColor = Color(0xFF38BDF8),
                        indicatorColor = Color(0xFF1E293B),
                        unselectedIconColor = Color(0xFF64748B),
                        unselectedTextColor = Color(0xFF64748B)
                    )
                )
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = Color(0xFF0F172A)
        ) {
            when (selectedTabIndex) {
                0 -> DashboardTabContent(simState = simState, engine = engine)
                1 -> ScenarioTabContent(simState = simState, engine = engine)
                2 -> BleTabContent(
                    bleState = bleState,
                    serverState = serverState,
                    simState = simState,
                    engine = engine,
                    onShowQrDialog = { showQrDialog = true }
                )
                3 -> LogsTabContent(logs = logs, onClearLogs = { engine.clearLogs() })
            }
        }

        if (showConfigDialog) {
            BoxConfigDialog(
                config = simState.config,
                onDismiss = { showConfigDialog = false },
                onSave = { newConfig ->
                    engine.updateHardwareConfig(newConfig)
                    showConfigDialog = false
                }
            )
        }

        if (showQrDialog) {
            QrCodePairingDialog(
                config = simState.config,
                serverState = serverState,
                onDismiss = { showQrDialog = false }
            )
        }
    }
}

@Composable
private fun DashboardTabContent(
    simState: com.example.voltcam.simulator.BoxSimulatorState,
    engine: BoxSimulatorEngine
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        VoltageMeterGauge(
            voltage = simState.voltage,
            isAcPresent = simState.isAcPowerPresent,
            qualityState = simState.qualityState
        )

        PowerGridStatusCard(
            simState = simState,
            onToggleAcPower = { engine.setAcPower(it) },
            onVoltageChanged = { engine.setVoltage(it) },
            onCurrentChanged = { engine.setCurrent(it) },
            onBatteryChanged = { engine.setBatteryPercent(it) },
            onFrequencyChanged = { engine.setFrequency(it) }
        )
    }
}

@Composable
private fun ScenarioTabContent(
    simState: com.example.voltcam.simulator.BoxSimulatorState,
    engine: BoxSimulatorEngine
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ScenarioButtonsGrid(
            simState = simState,
            onRunOutageLastGasp = { engine.runScenarioOutageWithLastGasp() },
            onRunVoltageInstability = { engine.runScenarioVoltageInstability() },
            onRunPowerRestored = { engine.runScenarioPowerRestored() },
            onRunTamperDisconnect = { engine.runScenarioTamperDisconnect() },
            onRunNominal = { engine.runScenarioNominalContinuous() }
        )
    }
}

@Composable
private fun BleTabContent(
    bleState: com.example.voltcam.ble.BleServerState,
    serverState: com.example.voltcam.server.LocalServerState,
    simState: com.example.voltcam.simulator.BoxSimulatorState,
    engine: BoxSimulatorEngine,
    onShowQrDialog: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        BleServerStatusCard(
            bleState = bleState,
            serverState = serverState,
            config = simState.config,
            onStartBleServer = { engine.bleManager.startBleServer(simState.config) },
            onStopBleServer = { engine.bleManager.stopBleServer() },
            onStartLocalServer = { engine.localServer.startServer(8080) },
            onStopLocalServer = { engine.localServer.stopServer() },
            onShowQrCodeDialog = onShowQrDialog
        )
    }
}

@Composable
private fun LogsTabContent(
    logs: List<com.example.voltcam.model.LogEntry>,
    onClearLogs: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        LogViewerSheet(logs = logs, onClearLogs = onClearLogs)
    }
}
