package com.example.firebaselabelapp.ui.viewmodel

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import com.dantsu.escposprinter.connection.usb.UsbPrintersConnections
import com.example.firebaselabelapp.bluetoothprinter.DiscoveredPrinter
import com.example.firebaselabelapp.bluetoothprinter.NsdPrinterDiscovery
import com.example.firebaselabelapp.bluetoothprinter.WiFiPrinterDiscovery
import com.example.firebaselabelapp.bluetoothprinter.XPrinterDiscovery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Data classes for different printer types
 */
data class BluetoothPrinterInfo(
    val name: String,
    val address: String
)

data class UsbPrinterInfo(
    val name: String,
    val deviceId: Int
)

/**
 * Unified ViewModel that manages discovery and selection for all printer types:
 * - Bluetooth (paired devices)
 * - USB (connected devices)
 * - WiFi (mDNS + IP scan)
 */
class UnifiedPrinterDiscoveryViewModel(app: Application) : AndroidViewModel(app) {

    private val xPrinterDiscovery = XPrinterDiscovery()

    private val context: Context get() = getApplication()

    // Discovery mechanisms
    private val nsdDiscovery = NsdPrinterDiscovery(app, viewModelScope)
    private val ipScanDiscovery = WiFiPrinterDiscovery(app)

    private var wifiScanJob: Job? = null

    // === STATE FLOWS ===

    // Bluetooth printers
    private val _bluetoothPrinters = MutableStateFlow<List<BluetoothPrinterInfo>>(emptyList())
    val bluetoothPrinters = _bluetoothPrinters.asStateFlow()

    // USB printers
    private val _usbPrinters = MutableStateFlow<List<UsbPrinterInfo>>(emptyList())
    val usbPrinters = _usbPrinters.asStateFlow()

    // WiFi printers (combined mDNS + IP scan)
    private val _wifiPrintersMap = MutableStateFlow<Map<String, DiscoveredPrinter>>(emptyMap())
    val wifiPrinters: StateFlow<List<DiscoveredPrinter>> = _wifiPrintersMap
        .map { map ->
            map.values.sortedWith(
                compareBy(
                    { it.discoveryMethod != "mDNS" },
                    { it.hostName == null },
                    { it.responseTime }
                ))
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Status and progress
    private val _statusMessage = MutableStateFlow("Готово к поиску")
    val statusMessage = _statusMessage.asStateFlow()

    private val _scanProgress = MutableStateFlow(0 to 0)
    val scanProgress = _scanProgress.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    // Permissions
    private val _hasBluetoothPermission = MutableStateFlow(false)
    val hasBluetoothPermission = _hasBluetoothPermission.asStateFlow()

    // === INITIALIZATION ===
    init {
        checkBluetoothPermissions()
        startMdnsDiscovery()

        // Listen to mDNS discoveries
        viewModelScope.launch {
            nsdDiscovery.printers.collect { mDNSPrinters ->
                if (mDNSPrinters.isNotEmpty()) {
                    _statusMessage.value = "Найдено принтеров (mDNS): ${mDNSPrinters.size}"
                }
                mergeWifiPrinterResults(mDNSPrinters.values.toList())
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopMdnsDiscovery()
        wifiScanJob?.cancel()
    }

    // === PUBLIC METHODS ===

    /**
     * Refresh all printer types (Bluetooth, USB, WiFi mDNS)
     */
    fun refreshAllPrinters() {
        refreshBluetoothPrinters()
        refreshUsbPrinters()
        startMdnsDiscovery()
    }

    /**
     * Refresh Bluetooth paired devices
     */
    fun refreshBluetoothPrinters() {
        if (!hasBluetoothPermissions()) {
            _bluetoothPrinters.value = emptyList()
            _hasBluetoothPermission.value = false
            return
        }

        _hasBluetoothPermission.value = true

        try {
            val pairedPrinters = BluetoothPrintersConnections().list
            if (pairedPrinters == null) {
                _bluetoothPrinters.value = emptyList()
                return
            }

            val printersList = mutableListOf<BluetoothPrinterInfo>()

            pairedPrinters.forEach { connection ->
                try {
                    val device = connection.device
                    val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        "Принтер (${device.address})"
                    } else {
                        device.name ?: "Неизвестный (${device.address})"
                    }
                    printersList.add(
                        BluetoothPrinterInfo(
                            name = name,
                            address = device.address
                        )
                    )
                } catch (e: SecurityException) {
                    // Skip this printer if we can't access it
                }
            }

            _bluetoothPrinters.value = printersList
        } catch (e: Exception) {
            _bluetoothPrinters.value = emptyList()
        }
    }

    /**
     * Refresh USB connected devices
     */
    fun refreshUsbPrinters() {
        try {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val usbPrinters = UsbPrintersConnections(context).list
            if (usbPrinters == null) {
                _usbPrinters.value = emptyList()
                return
            }

            val printersList = mutableListOf<UsbPrinterInfo>()

            usbPrinters.forEach { usbConnection ->
                val device = usbConnection.device
                val usbDevice = usbManager.deviceList.values.find {
                    it.vendorId == device.vendorId && it.productId == device.productId
                }

                if (usbDevice != null) {
                    printersList.add(
                        UsbPrinterInfo(
                            name = usbDevice.productName ?: usbDevice.deviceName,
                            deviceId = usbDevice.deviceId
                        )
                    )
                }
            }

            _usbPrinters.value = printersList
        } catch (e: Exception) {
            _usbPrinters.value = emptyList()
        }
    }

    /**
     * Request Bluetooth permissions
     */
    fun requestBluetoothPermissions(context: Context) {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        } else {
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH)
            }
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADMIN)
            }
            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        if (permissionsToRequest.isNotEmpty() && context is Activity) {
            ActivityCompat.requestPermissions(
                context,
                permissionsToRequest.toTypedArray(),
                1001
            )
        }
    }

    /**
     * Start mDNS discovery for WiFi printers
     */
    fun startMdnsDiscovery() {
        _statusMessage.value = "Поиск принтеров (mDNS)..."
        nsdDiscovery.startDiscovery()

        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            if (_wifiPrintersMap.value.isEmpty()) {
                _statusMessage.value = "mDNS поиск активен. Попробуйте быстрое сканирование."
            }
        }
    }

    /**
     * Stop mDNS discovery
     */
    fun stopMdnsDiscovery() {
        nsdDiscovery.stopDiscovery()
    }

    /**
     * Run quick WiFi scan (common IPs)
     */
//    fun runQuickWifiScan() {
//        if (_isScanning.value) return
//        _isScanning.value = true
//        _statusMessage.value = "Выполняется быстрое сканирование..."
//
//        wifiScanJob = viewModelScope.launch(Dispatchers.IO) {
//            val printers = ipScanDiscovery.quickScan { printer ->
//                mergeWifiPrinterResults(listOf(printer.copy(discoveryMethod = "IP Scan")))
//            }
//
//            _isScanning.value = false
//            _statusMessage.value = if (printers.isEmpty() && _wifiPrintersMap.value.isEmpty()) {
//                "Принтеры не найдены. Попробуйте полное сканирование."
//            } else {
//                "Быстрое сканирование завершено. Найдено: ${printers.size}"
//            }
//        }
//    }
    fun runQuickWifiScan() {
        if (_isScanning.value) return
        _isScanning.value = true
        _statusMessage.value = "Searching (Broadcast)..."

        wifiScanJob = viewModelScope.launch(Dispatchers.IO) {

            // Step 1: Launch XPrinter UDP Broadcast (The proprietary method)
            // This replaces the need to scan IPs for these specific printers.
            val udpJob = launch {
                try {
                    val udpPrinters = xPrinterDiscovery.scan() // Takes ~2 seconds
                    if (udpPrinters.isNotEmpty()) {
                        mergeWifiPrinterResults(udpPrinters)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Step 2: Ensure mDNS is active (The standard method)
            // You usually don't need to restart it if it's already running in init{},
            // but restarting ensures we trigger a fresh query.
            launch {
                stopMdnsDiscovery()
                startMdnsDiscovery()
            }

            // Wait for the UDP scan to finish (mDNS runs continuously in background)
            udpJob.join()

            _isScanning.value = false

            // Feedback to user
            val count = _wifiPrintersMap.value.size
            if (count == 0) {
                _statusMessage.value = "No printers found. Try 'Deep Scan'."
            } else {
                _statusMessage.value = "Found $count printers."
            }
        }
    }

    /**
     * RENAMED from runFullWifiScan to runDeepScan
     * Only call this if the user specifically clicks "My printer is not listed"
     */
    fun runDeepScan() {
        if (_isScanning.value) return
        _isScanning.value = true
        _statusMessage.value = "Deep scanning network (1-254)..."
        _scanProgress.value = 0 to 0

        wifiScanJob = viewModelScope.launch(Dispatchers.IO) {
            // This is your existing slow loop (1..254)
            val printers = ipScanDiscovery.scanForPrinters(
                onProgress = { scanned, total ->
                    _scanProgress.value = scanned to total
                },
                onPrinterFound = { printer ->
                    mergeWifiPrinterResults(listOf(printer.copy(discoveryMethod = "Deep Scan")))
                }
            )

            _isScanning.value = false
            _scanProgress.value = 0 to 0
            _statusMessage.value = "Deep scan complete. Found: ${printers.size}"
        }
    }


    /**
     * Run full WiFi scan (entire subnet)
     */
//    fun runFullWifiScan() {
//        if (_isScanning.value) return
//        _isScanning.value = true
//        _statusMessage.value = "Сканирование всей сети..."
//        _scanProgress.value = 0 to 0
//
//        wifiScanJob = viewModelScope.launch(Dispatchers.IO) {
//            val printers = ipScanDiscovery.scanForPrinters(
//                onProgress = { scanned, total ->
//                    _scanProgress.value = scanned to total
//                },
//                onPrinterFound = { printer ->
//                    mergeWifiPrinterResults(listOf(printer.copy(discoveryMethod = "IP Scan")))
//                }
//            )
//
//            _isScanning.value = false
//            _scanProgress.value = 0 to 0
//            _statusMessage.value = if (printers.isEmpty() && _wifiPrintersMap.value.isEmpty()) {
//                "Принтеры не найдены. Используйте ручной ввод."
//            } else {
//                "Сканирование завершено. Найдено: ${printers.size}"
//            }
//        }
//    }

    /**
     * Verify and select a WiFi printer
     */
    fun verifyAndSelectWifiPrinter(
        printer: DiscoveredPrinter,
        onVerified: (String, Int) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val isReachable = ipScanDiscovery.verifyPrinter(printer.ipAddress, printer.port)
            if (isReachable) {
                withContext(Dispatchers.Main) {
                    onVerified(printer.ipAddress, printer.port)
                }
            } else {
                _statusMessage.value = "Ошибка: принтер ${printer.ipAddress} недоступен"
            }
        }
    }

    // === PRIVATE HELPERS ===

    private fun checkBluetoothPermissions() {
        _hasBluetoothPermission.value = hasBluetoothPermissions()
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH) ==
                    PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private fun mergeWifiPrinterResults(newPrinters: List<DiscoveredPrinter>) {
        _wifiPrintersMap.update { currentMap ->
            val newMap = currentMap.toMutableMap()
            for (printer in newPrinters) {
                val existing = newMap[printer.ipAddress]
                if (existing == null) {
                    newMap[printer.ipAddress] = printer
                } else if (printer.discoveryMethod == "mDNS" &&
                    existing.discoveryMethod != "mDNS") {
                    // mDNS result is better (has a name)
                    newMap[printer.ipAddress] = printer
                }
            }
            newMap
        }
    }
}