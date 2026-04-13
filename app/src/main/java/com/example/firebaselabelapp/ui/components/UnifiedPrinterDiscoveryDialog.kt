package com.example.firebaselabelapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.firebaselabelapp.bluetoothprinter.DiscoveredPrinter
import com.example.firebaselabelapp.ui.viewmodel.UnifiedPrinterDiscoveryViewModel

/**
 * Unified printer discovery dialog that shows all printer types:
 * - Bluetooth (auto-detected, shows paired devices)
 * - USB (auto-detected, shows connected devices)
 * - WiFi (scannable + manual entry)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedPrinterDiscoveryDialog(
    onDismiss: () -> Unit,
    onPrinterSelected: (printerType: String, address: String, port: Int?) -> Unit,
    viewModel: UnifiedPrinterDiscoveryViewModel = viewModel()
) {
    val context = LocalContext.current

    // Collect state from ViewModel
    val bluetoothPrinters by viewModel.bluetoothPrinters.collectAsState()
    val usbPrinters by viewModel.usbPrinters.collectAsState()
    val wifiPrinters by viewModel.wifiPrinters.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val scanProgress by viewModel.scanProgress.collectAsState()
    val hasBluetoothPermission by viewModel.hasBluetoothPermission.collectAsState()

    // Manual WiFi entry state
    var showManualEntry by remember { mutableStateOf(false) }
    var manualIp by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("9100") }

    // Initialize discovery on first composition
    LaunchedEffect(Unit) {
        viewModel.refreshAllPrinters()
    }

    val isManualEntryValid = showManualEntry &&
            manualIp.isNotBlank() &&
            manualPort.isNotBlank() &&
            manualPort.toIntOrNull() != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Выбор принтера",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // === STATUS CARD ===
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (isScanning && scanProgress.second > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val progressValue = if (scanProgress.second > 0) {
                                scanProgress.first.toFloat() / scanProgress.second.toFloat()
                            } else 0f
                            LinearProgressIndicator(
                                progress = { progressValue },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "Сканировано: ${scanProgress.first}/${scanProgress.second}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // === BLUETOOTH SECTION ===
                PrinterSectionHeader(
                    title = "📱 Bluetooth принтеры",
                    icon = Icons.Default.Bluetooth
                )

                if (!hasBluetoothPermission) {
                    PermissionRequiredCard(
                        message = "Требуются разрешения Bluetooth",
                        onRequestPermission = { viewModel.requestBluetoothPermissions(context) }
                    )
                } else if (bluetoothPrinters.isEmpty()) {
                    EmptyStateCard("Нет сопряженных Bluetooth принтеров")
                } else {
                    bluetoothPrinters.forEach { printer ->
                        PrinterCard(
                            name = printer.name,
                            subtitle = printer.address,
                            icon = Icons.Default.Bluetooth,
                            onClick = {
                                onPrinterSelected("Bluetooth", printer.address, null)
                                onDismiss()
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // === USB SECTION ===
                PrinterSectionHeader(
                    title = "🔌 USB принтеры",
                    icon = Icons.Default.Usb
                )

                if (usbPrinters.isEmpty()) {
                    EmptyStateCard("Нет подключенных USB принтеров")
                } else {
                    usbPrinters.forEach { printer ->
                        PrinterCard(
                            name = printer.name,
                            subtitle = "ID: ${printer.deviceId}",
                            icon = Icons.Default.Usb,
                            onClick = {
                                onPrinterSelected("USB", printer.deviceId.toString(), null)
                                onDismiss()
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // === WIFI SECTION ===
                PrinterSectionHeader(
                    title = "📡 WiFi принтеры",
                    icon = Icons.Default.Wifi
                )

                // WiFi scan buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.runQuickWifiScan() },
                        enabled = !isScanning,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Быстро")
                    }
                    Button(
                        onClick = { viewModel.runDeepScan() },
                        enabled = !isScanning,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Полное")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // WiFi printers list
                if (wifiPrinters.isEmpty() && !isScanning) {
                    EmptyStateCard("Нажмите 'Быстро' или 'Полное' для поиска")
                } else {
                    wifiPrinters.forEach { printer ->
                        WifiPrinterCard(
                            printer = printer,
                            onClick = {
                                viewModel.verifyAndSelectWifiPrinter(printer) { ip, port ->
                                    onPrinterSelected("WiFi", ip, port)
                                    onDismiss()
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Manual WiFi entry
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { showManualEntry = !showManualEntry },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        if (showManualEntry) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        null
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (showManualEntry) "Скрыть ручной ввод" else "Ручной ввод IP")
                }

                if (showManualEntry) {
                    OutlinedTextField(
                        value = manualIp,
                        onValueChange = { manualIp = it },
                        label = { Text("IP адрес") },
                        placeholder = { Text("192.168.1.100") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = manualPort,
                        onValueChange = { manualPort = it },
                        label = { Text("Порт") },
                        placeholder = { Text("9100") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val port = manualPort.toIntOrNull()
                    if (manualIp.isNotBlank() && port != null) {
                        onPrinterSelected("WiFi", manualIp, port)
                        onDismiss()
                    }
                },
                enabled = isManualEntryValid
            ) {
                Text("Добавить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

// === COMPOSABLE COMPONENTS ===

@Composable
private fun PrinterSectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
    HorizontalDivider()
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun PrinterCard(
    name: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WifiPrinterCard(
    printer: DiscoveredPrinter,
    onClick: () -> Unit
) {
    val hasValidHostName = !printer.hostName.isNullOrBlank() &&
            printer.hostName != printer.ipAddress
    val primaryText = if (hasValidHostName) printer.hostName!! else printer.ipAddress
    val secondaryText = if (hasValidHostName)
        "${printer.ipAddress}:${printer.port}"
    else
        "Порт: ${printer.port}"

    val responseText = when {
        printer.discoveryMethod == "mDNS" -> "Найдено (mDNS)"
        printer.responseTime > -1 -> "Отклик: ${printer.responseTime}ms"
        else -> ""
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Print,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = primaryText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = secondaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (responseText.isNotBlank()) {
                    Text(
                        text = responseText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyStateCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun PermissionRequiredCard(
    message: String,
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Запросить разрешения")
            }
        }
    }
}