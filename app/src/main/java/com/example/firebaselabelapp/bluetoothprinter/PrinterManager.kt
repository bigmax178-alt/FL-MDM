package com.example.firebaselabelapp.bluetoothprinter

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.text.InputType
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.core.graphics.withTranslation
import com.dantsu.escposprinter.connection.DeviceConnection
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import com.dantsu.escposprinter.connection.usb.UsbConnection
import com.dantsu.escposprinter.connection.usb.UsbPrintersConnections
import com.example.firebaselabelapp.R
import com.example.firebaselabelapp.bluetoothprinter.connection.TcpConnection
import com.example.firebaselabelapp.kiosk.KioskManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.threeten.bp.LocalDateTime
import org.threeten.bp.format.DateTimeFormatter
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.ceil
import androidx.core.view.isVisible

// Define a CustomCoroutineScope interface
interface CustomCoroutineScope {
    val coroutineScope: CoroutineScope
    fun cancelScope()
}

class PrinterManager(val context: Context, val kioskManager: KioskManager) : CustomCoroutineScope {

    // 1. Create a Job for the CoroutineScope
    private val job = Job()

    // 2. Initialize the CoroutineScope with Dispatchers.Main + job
    override val coroutineScope = CoroutineScope(Dispatchers.Main + job)

    // 3. Implement cancelScope to cancel the job when the PrinterManager is no longer needed
    override fun cancelScope() {
        job.cancel()
    }

    // --- NEW ---
    /**
     * Helper function to check if a string contains only numeric digits.
     */
    private fun String.isNumeric(): Boolean {
        if (this.isEmpty()) return false
        return this.all { it.isDigit() }
    }
    // --- END NEW ---

    companion object {
        private const val TAG = "LabelPrinter"
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val PREFS_NAME = "LabelAppPrefs"

        // Printer Types
        private const val PRINTER_TYPE_BLUETOOTH = "Bluetooth"
        private const val PRINTER_TYPE_USB = "USB"
        private const val PRINTER_TYPE_WIFI = "WiFi"

        // Preference Keys
        private const val SELECTED_PRINTER_TYPE = "SelectedPrinterType"
        private const val SELECTED_PRINTER_MAC = "SelectedPrinterMAC"
        private const val SELECTED_PRINTER_USB_DEVICE_ID = "SelectedPrinterUsbDeviceId"
        private const val SELECTED_PRINTER_IP = "SelectedPrinterIP"
        private const val SELECTED_PRINTER_PORT = "SelectedPrinterPort"

        // TSPL Configuration (in dots - 203 DPI: 1mm = 8 dots)
        private var LABEL_HEIGHT_MM = 20f
        private var LABEL_WIDTH_MM = 30f
        private var LABEL_GAP_MM = 2f
        private var DOTS_PER_MM = 8f    // 8 = 203, 12 dots/mm = 300dpi

        // Action for USB permission request
        const val ACTION_USB_PERMISSION = "com.example.firebaselabelapp.USB_PERMISSION"

        var usbDeviceActive: Boolean = false
    }

    private var selectedUsbDevice: UsbDevice? = null


    suspend fun printLabel(
        density: Int?,
        labelName: String,
        description: String,
        manufactureTime: LocalDateTime,
        expiryDateTime: LocalDateTime,
        labelCount: Int,
        labelSize: String,
        textSize: Float = 20f
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            var printerConnection: DeviceConnection? = null
            try {
                if (manufactureTime == expiryDateTime) {
                    throw Exception(context.getString(R.string.toast_duration_greater_than_zero))
                }

                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val printerType = prefs.getString(SELECTED_PRINTER_TYPE, null)

                if (printerType == PRINTER_TYPE_BLUETOOTH && !hasBluetoothPermissions()) {
                    requestBluetoothPermissions()
                    throw Exception(context.getString(R.string.toast_bluetooth_permissions_not_granted))
                }

                val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

                // --- REMOVED ---
                // Removed the permission grant from here. It should be done earlier.
                // if (printerType == PRINTER_TYPE_USB && kioskManager.isKioskModeEnabled() && kioskManager.isDeviceOwner()) {
                //     kioskManager.grantUsbPermissions()
                // }


                printerConnection = when (printerType) {
                    PRINTER_TYPE_BLUETOOTH -> {
                        val savedMac = prefs.getString(SELECTED_PRINTER_MAC, null)
                            ?: throw Exception(context.getString(R.string.toast_bluetooth_printer_not_selected))
                        BluetoothPrintersConnections().list?.find { it.device.address == savedMac }
                            ?: throw Exception(context.getString(R.string.toast_selected_bluetooth_printer_not_found))
                    }

                    PRINTER_TYPE_USB -> {
                        val usbToUse = selectedUsbDevice ?: run {
                            val savedDeviceId =
                                prefs.getInt(SELECTED_PRINTER_USB_DEVICE_ID, -1).takeIf { it != -1 }
                                    ?: throw Exception(context.getString(R.string.toast_usb_printer_not_selected))
                            usbManager.deviceList.values.find { it.deviceId == savedDeviceId }
                        }
                        ?: throw Exception(context.getString(R.string.toast_usb_printer_not_found))

                        // This final check is crucial. If permission is not granted by now, printing will fail.
                        if (!usbManager.hasPermission(usbToUse)) {
                            // We attempt one last request, which might work silently for device owners.
                            requestUsbPermission(usbToUse)
                            throw Exception(context.getString(R.string.toast_usb_permission_not_granted))
                        }
                        UsbConnection(usbManager, usbToUse)
                    }

                    PRINTER_TYPE_WIFI -> {
                        val ipAddress = prefs.getString(SELECTED_PRINTER_IP, null)
                            ?: throw Exception(context.getString(R.string.toast_wifi_printer_not_selected))
                        val port = prefs.getInt(SELECTED_PRINTER_PORT, 9100)
                        TcpConnection(ipAddress, port, 15)
                    }

                    else -> {
                        throw Exception(context.getString(R.string.toast_no_printer_selected))
                    }
                }

                val now = manufactureTime
                val formatter = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm")
                val expiry = expiryDateTime
                val currentTime = now.format(formatter)
                val expiryTime = expiry.format(formatter)

                try {
                    printerConnection.connect()
                } catch (e: Exception) {
                    throw Exception(
                        context.getString(R.string.toast_connection_error, e.message),
                        e
                    )
                }

                // New function to generate and print bitmap
//                sendBoxDiagnostic(printerConnection, labelCount)
                when (labelSize) {
                    "40x30" -> {
                        LABEL_WIDTH_MM = 40f
                        LABEL_HEIGHT_MM = 30f
                    }

                    "30x20" -> {
                        LABEL_WIDTH_MM = 30f
                        LABEL_HEIGHT_MM = 20f
                    }
                }

                sendTextAsBitmap(
                    density,
                    printerConnection,
                    labelName,
                    "От: $currentTime",
                    "До: $expiryTime",
                    description,
                    labelCount,
                    textSize
                )
                Log.d(TAG, "От: $currentTime\nДо: $expiryTime\nРазмер: $LABEL_WIDTH_MM x $LABEL_HEIGHT_MM\nШрифт: $textSize\nПлотность: $density")

                Result.success(Unit)

            } catch (e: Exception) {
                Log.e(TAG, "Printing error", e)
                Result.failure(e)
            } finally {
                printerConnection?.disconnect()
            }
        }
    }

    /**
     * Converts an Android Bitmap to a monochrome 1-bit per pixel byte array required by the TSPL BITMAP command.
     */
    private fun convertBitmapToTSPL(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val widthInBytes = ceil(width / 8.0).toInt()
        val imageData = ByteArray(widthInBytes * height)
        var byteIndex = 0

        for (y in 0 until height) {
            for (xByte in 0 until widthInBytes) {
                var currentByte: Byte = 0
                for (xBit in 0..7) {
                    val x = xByte * 8 + xBit
                    if (x < width) {
                        val color = bitmap[x, y]
                        // If pixel is dark (e.g., black text), set the bit to 0 (representing white on print)
                        // If pixel is light (e.g., white background), set the bit to 1 (representing black on print)
                        // This inverts the colors for printers that expect 1 for white, 0 for black.
                        // OR: More commonly, invert the condition so white becomes '1' and black becomes '0'
                        // for printers that expect 1 for black and 0 for white when the original image is dark text on light bg.

                        // The original logic: if Color.red(color) < 128, it's considered black.
                        // To invert, we want the opposite: if Color.red(color) >= 128, it's considered black on the output.
                        // This means if the original pixel is white, we want to set the bit.
                        if (Color.red(color) >= 128) {
                            currentByte = (currentByte.toInt() or (1 shl (7 - xBit))).toByte()
                        }
                    }
                }
                imageData[byteIndex++] = currentByte
            }
        }
        return imageData
    }

    /**
     * Creates a bitmap with the label text, converts it to TSPL format, and sends it to the printer.
     * --- MODIFIED ---
     * If the description is purely numeric, it will be rendered as a BARCODE command instead of text.
     */
    private fun sendTextAsBitmap(
        density: Int?,
        printerConnection: DeviceConnection?,
        labelName: String,
        currentTime: String,
        expiryTime: String,
        description: String,
        labelCopies: Int,
        textSize: Float = 20f
    ) {
        // 1. Calculate dimensions in pixels (dots)
        val labelWidthInDots = (LABEL_WIDTH_MM * DOTS_PER_MM).toInt()
        val labelHeightInDots = (LABEL_HEIGHT_MM * DOTS_PER_MM).toInt()

        // 2. Create Bitmap and Canvas
        val bitmap = createBitmap(labelWidthInDots, labelHeightInDots)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE) // Set background to white

        // 3. Prepare Paint and Text Elements
        val textPaint = TextPaint().apply {
            color = Color.BLACK
            isAntiAlias = true
            this.textSize = textSize // Use the provided textSize parameter
        }

        val padding = (2 * DOTS_PER_MM).toInt()
//        val padding = 0
        var currentY = padding

        // --- Draw Label Name ---
        val nameLayout = StaticLayout.Builder.obtain(
            labelName, 0, labelName.length, textPaint, labelWidthInDots - 2 * padding
        ).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
        canvas.withTranslation(padding.toFloat(), currentY.toFloat()) {
            nameLayout.draw(this)
        }
        currentY += nameLayout.height + 4 // Add some spacing

        // --- Draw Current Time ---
        val timeLayout = StaticLayout.Builder.obtain(
            currentTime, 0, currentTime.length, textPaint, labelWidthInDots - 2 * padding
        ).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
        canvas.withTranslation(padding.toFloat(), currentY.toFloat()) {
            timeLayout.draw(this)
        }
        currentY += timeLayout.height + 4

        // --- Draw Expiry Time ---
        val expiryLayout = StaticLayout.Builder.obtain(
            expiryTime, 0, expiryTime.length, textPaint, labelWidthInDots - 2 * padding
        ).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
        canvas.withTranslation(padding.toFloat(), currentY.toFloat()) {
            expiryLayout.draw(this)
        }
        currentY += expiryLayout.height + 4

        // --- MODIFIED: Check if description is a barcode ---
        val trimmedDescription = description.trim() // <-- FIX 1: Trim whitespace
        val isNumericBarcode = trimmedDescription.isNotBlank() && trimmedDescription.isNumeric()

        // --- Draw Description (if not a barcode) ---
        if (description.isNotBlank() && !isNumericBarcode) { // Use original 'description' to show text with spaces if it's not a barcode
            val descLayout = StaticLayout.Builder.obtain(
                description, 0, description.length, textPaint, labelWidthInDots - 2 * padding
            ).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
            canvas.withTranslation(padding.toFloat(), currentY.toFloat()) {
                descLayout.draw(this)
            }
            // Update currentY in case we add more items later
            currentY += descLayout.height + 4
        }
        // --- END MODIFIED ---

        // 4. Convert bitmap to TSPL byte array
        val bitmapData = convertBitmapToTSPL(bitmap)

        // 5. Build and Send TSPL Commands
        val widthInBytes = ceil(labelWidthInDots / 8.0).toInt() // 8.0 bits/byte
        val commandsBuilder = StringBuilder()
        commandsBuilder.append('\n')
        commandsBuilder.append("DENSITY ${density}\n")
        commandsBuilder.append("GAP $LABEL_GAP_MM mm,0.0 mm\n")
        commandsBuilder.append("SIZE $LABEL_WIDTH_MM mm,${LABEL_HEIGHT_MM} mm\n")
        // Place bitmap at the top-left corner (adjust x,y if padding is needed on the printer side)
        commandsBuilder.append("CLS\n")
        commandsBuilder.append("BITMAP 0,0,$widthInBytes,$labelHeightInDots,0,")

        val fullCommand = ByteArrayOutputStream().apply {
            write(commandsBuilder.toString().toByteArray(Charsets.US_ASCII))
            write(bitmapData) // Write the bitmap data (which contains the text)

            // --- NEW: Add BARCODE command if description was numeric ---
            if (isNumericBarcode) {

                // --- FIX 3: Dynamic Height Calculation ---
                val barcodeY = currentY
                // Calculate remaining space. Leave 1mm (8 dots) bottom margin.
                val bottomMargin = (1 * DOTS_PER_MM).toInt()
                val remainingHeight = labelHeightInDots - barcodeY - bottomMargin

                var barcodeHeightDots = (5 * DOTS_PER_MM).toInt() // Try 5mm default
                var humanReadable = 1

                if (remainingHeight < (2 * DOTS_PER_MM).toInt()) {
                    // If less than 2mm (16 dots) remaining, don't print barcode
                    barcodeHeightDots = 0
                } else if (remainingHeight < barcodeHeightDots) {
                    // Not enough space for 5mm, use remaining space
                    barcodeHeightDots = remainingHeight
                    // If remaining space is tight (e.g., < 3mm / 24 dots), hide text
                    if (remainingHeight < (3 * DOTS_PER_MM).toInt()) {
                        humanReadable = 0
                    }
                }
                // --- END FIX 3 ---

                if (barcodeHeightDots > 0) {
                    val barcodeX = padding
                    val rotation = 0
                    val narrow = 2
                    val wide = 4

                    // --- FIX 2: Use "128" instead of "CODEAUTO" ---
                    val barcodeCommand = "BARCODE $barcodeX,$barcodeY,\"128\",$barcodeHeightDots,$humanReadable,$rotation,$narrow,$wide,\"$trimmedDescription\"\n"
                    write(barcodeCommand.toByteArray(Charsets.US_ASCII))
                }
            }
            // --- END NEW ---

            write("\nPRINT $labelCopies,1\n".toByteArray(Charsets.US_ASCII))
        }.toByteArray()

        try {
            printerConnection?.write(fullCommand)
            printerConnection?.send()
            Log.d(TAG, "Bitmap label sent successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending bitmap command: ${e.message}", e)
            throw Exception(context.getString(R.string.error_sending_commands_to_printer), e)
        }
    }


    fun getSelectedPrinterAddress(): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val printerType = prefs.getString(SELECTED_PRINTER_TYPE, null)
        return when (printerType) {
            PRINTER_TYPE_BLUETOOTH -> prefs.getString(SELECTED_PRINTER_MAC, null)
            PRINTER_TYPE_USB -> prefs.getInt(SELECTED_PRINTER_USB_DEVICE_ID, -1).takeIf { it != -1 }
                ?.toString()

            PRINTER_TYPE_WIFI -> prefs.getString(SELECTED_PRINTER_IP, null)
            else -> null
        }
    }


    fun getSelectedPrinterName(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val printerType = prefs.getString(SELECTED_PRINTER_TYPE, null)
        val address = getSelectedPrinterAddress()

        return when (printerType) {
            PRINTER_TYPE_BLUETOOTH -> {
                if (address == null) return context.getString(R.string.none_selected)
                val pairedPrinters = try {
                    BluetoothPrintersConnections().list
                } catch (e: SecurityException) {
                    Log.e(TAG, "Error accessing Bluetooth", e)
                    null
                }
                pairedPrinters?.find { it.device.address == address }?.let {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && context.checkSelfPermission(
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            context.getString(
                                R.string.bluetooth_printer_permissions_missing,
                                it.device.address
                            )
                        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && context.checkSelfPermission(
                                Manifest.permission.BLUETOOTH
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            context.getString(
                                R.string.bluetooth_printer_permissions_missing,
                                it.device.address
                            )
                        } else {
                            it.device.name ?: context.getString(
                                R.string.unknown_bluetooth_printer,
                                it.device.address
                            )
                        }
                    } catch (e: SecurityException) {
                        Log.e(
                            TAG,
                            "SecurityException: Cannot access device name without BLUETOOTH_CONNECT",
                            e
                        )
                        context.getString(
                            R.string.bluetooth_printer_permissions_missing,
                            it.device.address
                        )
                    }
                } ?: context.getString(R.string.unknown_bluetooth_printer, address)
            }

            PRINTER_TYPE_USB -> {
                if (address == null) return context.getString(R.string.none_selected) // Assuming "Не Выбран" should be mapped to none_selected
                usbDeviceActive = true
                val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
                val usbDevice = usbManager.deviceList.values.find { it.deviceId == address.toInt() }
                usbDevice?.let {
                    it.productName ?: it.deviceName
                } ?: context.getString(R.string.unknown_usb_printer, address)
            }

            PRINTER_TYPE_WIFI -> {
                if (address == null) return context.getString(R.string.none_selected)
                val port = prefs.getInt(SELECTED_PRINTER_PORT, 9100)
                context.getString(R.string.wifi_printer_name, address, port)
            }

            else -> context.getString(R.string.none_selected)
        }
    }

    /**
     * Unified printer selection that shows all available printer types
     * in a single, organized dialog
     */
    /**
     * Show the new unified printer discovery dialog
     * This replaces the old AlertDialog-based selection
     */
    fun showUnifiedPrinterDiscoveryDialog(
        activity: Activity,
        onPrinterSelected: (String?) -> Unit
    ) {
        // This will be called from the Composable screen
        // The actual dialog is now handled in the Composable layer
        onPrinterSelected("__UNIFIED_DISCOVERY__")
    }
    fun savePrinterFromUnifiedDialog(
        printerType: String,
        address: String,
        port: Int?
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(SELECTED_PRINTER_TYPE, printerType)

            when (printerType) {
                PRINTER_TYPE_BLUETOOTH -> {
                    putString(SELECTED_PRINTER_MAC, address)
                    clearUsbSelection()
                    clearWifiSelection()
                }

                PRINTER_TYPE_USB -> {
                    val deviceId = address.toIntOrNull() ?: return@edit
                    putInt(SELECTED_PRINTER_USB_DEVICE_ID, deviceId)

                    val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
                    selectedUsbDevice = usbManager.deviceList.values.find { it.deviceId == deviceId }

                    // Request permission immediately
                    selectedUsbDevice?.let { device ->
                        if (!hasUsbPermission(device)) {
                            val permissionIntent = PendingIntent.getBroadcast(
                                context,
                                0,
                                Intent(ACTION_USB_PERMISSION),
                                PendingIntent.FLAG_IMMUTABLE
                            )
                            requestUsbPermission(device)
                        }
                    }

                    clearBluetoothSelection()
                    clearWifiSelection()
                }

                PRINTER_TYPE_WIFI -> {
                    putString(SELECTED_PRINTER_IP, address)
                    putInt(SELECTED_PRINTER_PORT, port ?: 9100)
                    clearBluetoothSelection()
                    clearUsbSelection()
                }
            }
        }
    }


    fun showPrinterSelection(onPrinterSelected: (String?) -> Unit) {
        val printerOptions = mutableListOf<String>()
        val printerActions = mutableListOf<() -> Unit>()

        // Add Bluetooth printers
        if (hasBluetoothPermissions()) {
            val pairedBluetoothPrinters = try {
                BluetoothPrintersConnections().list
            } catch (e: SecurityException) {
                Log.e(TAG, "Error accessing Bluetooth", e)
                null
            }

            pairedBluetoothPrinters?.forEach { connection ->
                try {
                    val name =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && context.checkSelfPermission(
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            context.getString(
                                R.string.bluetooth_printer_permissions_missing,
                                connection.device.address
                            )
                        } else {
                            connection.device.name
                                ?: context.getString(
                                    R.string.unknown_bluetooth_printer,
                                    connection.device.address
                                )
                        }
                    printerOptions.add("Bluetooth: $name")
                    printerActions.add {
                        savePrinterSelection(PRINTER_TYPE_BLUETOOTH, connection.device.address)
                        onPrinterSelected(connection.device.address)
                    }
                } catch (e: SecurityException) {
                    // Handle missing permissions
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_permissions_missing, e.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } else {
            printerOptions.add(context.getString(R.string.bluetooth_permissions_not_granted))
            printerActions.add { requestBluetoothPermissions() }
        }

        // Add USB printers
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val usbPrinters = UsbPrintersConnections(context).list
        usbPrinters?.forEach { usbConnection ->
            val usbDevice =
                usbManager.deviceList.values.find { it.vendorId == usbConnection.device.vendorId && it.productId == usbConnection.device.productId }
            usbDevice?.let {
                val name = it.productName ?: it.deviceName
                printerOptions.add("USB: $name")
                printerActions.add {
                    savePrinterSelection(PRINTER_TYPE_USB, it.deviceId)
                    if (!hasUsbPermission(it)) {
                        requestUsbPermission(it)
                    }
                    onPrinterSelected(it.deviceId.toString())
                }
            }
        }

        // Add Wi-Fi Printer option
        printerOptions.add(context.getString(R.string.add_wifi_printer))
        printerActions.add {
//            showWifiPrinterDialog(onPrinterSelected)
            showWifiPrinterDiscoveryDialog(onPrinterSelected)
        }


        if (printerOptions.isEmpty()) {
            Toast.makeText(
                context,
                context.getString(R.string.toast_no_printers_found),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.dialog_title_select_printer))
            .setItems(printerOptions.toTypedArray()) { _, which ->
                printerActions[which].invoke()
            }
            .setOnCancelListener { onPrinterSelected(null) }
            .show()
    }

    private fun savePrinterSelection(type: String, identifier: Any) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(SELECTED_PRINTER_TYPE, type)
            when (type) {
                PRINTER_TYPE_BLUETOOTH -> {
                    putString(SELECTED_PRINTER_MAC, identifier as String)
                    clearUsbSelection()
                    clearWifiSelection()
                }

                PRINTER_TYPE_USB -> {
                    putInt(SELECTED_PRINTER_USB_DEVICE_ID, identifier as Int)
                    val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
                    selectedUsbDevice =
                        usbManager.deviceList.values.find { it.deviceId == identifier }
                    clearBluetoothSelection()
                    clearWifiSelection()
                }
            }
        }
    }

    private fun saveWifiPrinterSelection(ip: String, port: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(SELECTED_PRINTER_TYPE, PRINTER_TYPE_WIFI)
            putString(SELECTED_PRINTER_IP, ip)
            putInt(SELECTED_PRINTER_PORT, port)
            clearBluetoothSelection()
            clearUsbSelection()
        }
    }

    fun setSelectedUsbDevice(device: UsbDevice?) {
        selectedUsbDevice = device
    }

    private fun android.content.SharedPreferences.Editor.clearBluetoothSelection() {
        remove(SELECTED_PRINTER_MAC)
    }

    private fun android.content.SharedPreferences.Editor.clearUsbSelection() {
        remove(SELECTED_PRINTER_USB_DEVICE_ID)
    }

    private fun android.content.SharedPreferences.Editor.clearWifiSelection() {
        remove(SELECTED_PRINTER_IP)
        remove(SELECTED_PRINTER_PORT)
    }


    fun requestBluetoothPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        } else {
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH)
            }
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADMIN)
            }
            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        if (permissionsToRequest.isNotEmpty() && context is Activity) {
            ActivityCompat.requestPermissions(
                context,
                permissionsToRequest.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    fun requestUsbPermission(usbDevice: UsbDevice) {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val permissionIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_USB_PERMISSION),
            PendingIntent.FLAG_IMMUTABLE
        )
        usbManager.requestPermission(usbDevice, permissionIntent)
    }

    fun hasUsbPermission(usbDevice: UsbDevice): Boolean {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return usbManager.hasPermission(usbDevice)
    }

    // --- NEW FUNCTION: For checking the currently selected USB device ---
    fun hasUsbPermissionForSelectedDevice(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val printerType = prefs.getString(SELECTED_PRINTER_TYPE, null)
        if (printerType != PRINTER_TYPE_USB) {
            return true // Not a USB printer, so no permission check needed.
        }

        val savedDeviceId = prefs.getInt(SELECTED_PRINTER_USB_DEVICE_ID, -1)
        if (savedDeviceId == -1) {
            return false // No USB printer selected
        }

        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val device = usbManager.deviceList.values.find { it.deviceId == savedDeviceId }
        return if (device != null) {
            usbManager.hasPermission(device)
        } else {
            false // Selected device not found/connected
        }
    }

    // --- NEW FUNCTION: For manually requesting permission from the UI ---
    fun requestUsbPermissionForSelectedDevice() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val printerType = prefs.getString(SELECTED_PRINTER_TYPE, null)
        if (printerType != PRINTER_TYPE_USB) {
            Toast.makeText(context, "Выбран не USB-принтер.", Toast.LENGTH_SHORT).show()
            return
        }

        val savedDeviceId = prefs.getInt(SELECTED_PRINTER_USB_DEVICE_ID, -1)
        if (savedDeviceId == -1) {
            Toast.makeText(context, "USB-принтер не выбран.", Toast.LENGTH_SHORT).show()
            return
        }

        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val device = usbManager.deviceList.values.find { it.deviceId == savedDeviceId }
        if (device != null) {
            if (!usbManager.hasPermission(device)) {
                requestUsbPermission(device)
            } else {
                Toast.makeText(context, "Разрешение для USB уже предоставлено.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Выбранный USB-принтер не подключен.", Toast.LENGTH_SHORT).show()
        }
    }

    fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    suspend fun pingPrinter(ipAddress: String, port: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(ipAddress, port), 2000) // 2-second timeout
                socket.close()
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Show WiFi printer discovery dialog with automatic scanning
     */
    fun showWifiPrinterDiscoveryDialog(onPrinterSelected: (String?) -> Unit) {
        val discovery = WiFiPrinterDiscovery(context)
        val builder = AlertDialog.Builder(context)
        builder.setTitle(context.getString(R.string.dialog_title_wifi_printer))

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
        }

        // Status text
        val statusText = TextView(context).apply {
            text = "Готово к сканированию..."
            setPadding(0, 0, 0, 16)
        }

        // Progress text
        val progressText = TextView(context).apply {
            text = ""
            setPadding(0, 0, 0, 8)
            visibility = android.view.View.GONE
        }

        // List of discovered printers
        val printersListView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 0)
        }

        // Manual entry section (collapsible)
        val manualEntryLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 0)
            visibility = android.view.View.GONE
        }

        val ipAddressInput = EditText(context).apply {
            hint = context.getString(R.string.hint_ip_address)
        }

        val portInput = EditText(context).apply {
            hint = context.getString(R.string.hint_port)
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("9100")
        }

        manualEntryLayout.addView(TextView(context).apply {
            text = "Ручной ввод:"
            textSize = 16f
            setPadding(0, 0, 0, 8)
        })
        manualEntryLayout.addView(ipAddressInput)
        manualEntryLayout.addView(portInput)

        // Buttons layout
        val buttonsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 0)
        }

        // Scan button
        val scanButton = Button(context).apply {
            text = "Сканировать сеть"
            setOnClickListener {
                isEnabled = false
                statusText.text = "Сканирование сети..."
                progressText.visibility = android.view.View.VISIBLE
                printersListView.removeAllViews()

                coroutineScope.launch {
                    val printers = discovery.scanForPrinters(
                        onProgress = { scanned, total ->
                            coroutineScope.launch(Dispatchers.Main) {
                                progressText.text = "Сканировано: $scanned/$total"
                            }
                        },
                        onPrinterFound = { printer ->
                            coroutineScope.launch(Dispatchers.Main) {
                                addPrinterToList(printer, printersListView, onPrinterSelected)
                            }
                        }
                    )

                    withContext(Dispatchers.Main) {
                        isEnabled = true
                        progressText.visibility = android.view.View.GONE
                        if (printers.isEmpty()) {
                            statusText.text = "Принтеры не найдены. Попробуйте ручной ввод."
                            manualEntryLayout.visibility = android.view.View.VISIBLE
                        } else {
                            statusText.text = "Найдено принтеров: ${printers.size}"
                        }
                    }
                }
            }
        }

        // Quick scan button
        val quickScanButton = Button(context).apply {
            text = "Быстрое сканирование"
            setOnClickListener {
                isEnabled = false
                statusText.text = "Быстрое сканирование..."
                printersListView.removeAllViews()

                coroutineScope.launch {
                    val printers = discovery.quickScan { printer ->
                        coroutineScope.launch(Dispatchers.Main) {
                            addPrinterToList(printer, printersListView, onPrinterSelected)
                        }
                    }

                    withContext(Dispatchers.Main) {
                        isEnabled = true
                        if (printers.isEmpty()) {
                            statusText.text = "Принтеры не найдены. Используйте полное сканирование."
                        } else {
                            statusText.text = "Найдено принтеров: ${printers.size}"
                        }
                    }
                }
            }
        }

        // Manual entry toggle button
        val manualButton = Button(context).apply {
            text = "Ручной ввод"
            setOnClickListener {
                manualEntryLayout.visibility = if (manualEntryLayout.isVisible) {
                    android.view.View.GONE
                } else {
                    android.view.View.VISIBLE
                }
            }
        }

        buttonsLayout.addView(scanButton)
        buttonsLayout.addView(quickScanButton)

        layout.addView(statusText)
        layout.addView(progressText)
        layout.addView(buttonsLayout)
        layout.addView(manualButton)
        layout.addView(manualEntryLayout)

        // Scroll view for printer list
        val scrollView = android.widget.ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                400 // Max height in dp
            )
            addView(printersListView)
        }
        layout.addView(scrollView)

        builder.setView(layout)

        builder.setPositiveButton("OK") { _, _ ->
            // Handle manual entry if needed
            val ip = ipAddressInput.text.toString()
            val portStr = portInput.text.toString()

            if (ip.isNotBlank() && portStr.isNotBlank()) {
                try {
                    val port = portStr.toInt()
                    saveWifiPrinterSelection(ip, port)
                    Toast.makeText(
                        context,
                        "Выбран WiFi принтер: $ip:$port",
                        Toast.LENGTH_LONG
                    ).show()
                    onPrinterSelected(ip)
                } catch (_: NumberFormatException) {
                    Toast.makeText(
                        context,
                        "Неверный порт",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        builder.setNegativeButton(context.getString(R.string.button_cancel)) { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }

    /**
     * Helper function to add discovered printer to the list
     */
    private fun addPrinterToList(
        printer: DiscoveredPrinter,
        container: LinearLayout,
        onPrinterSelected: (String?) -> Unit
    ) {
        val printerButton = Button(context).apply {
            text = buildString {
                append("${printer.ipAddress}:${printer.port}\n")
                if (printer.hostName != null && printer.hostName != printer.ipAddress) {
                    append("(${printer.hostName})\n")
                }
                append("Время отклика: ${printer.responseTime}ms")
            }
            setOnClickListener {
                // Verify printer before selecting
                coroutineScope.launch {
                    val isReachable = WiFiPrinterDiscovery(context).verifyPrinter(
                        printer.ipAddress,
                        printer.port
                    )

                    withContext(Dispatchers.Main) {
                        if (isReachable) {
                            saveWifiPrinterSelection(printer.ipAddress, printer.port)
                            Toast.makeText(
                                context,
                                "Выбран принтер: ${printer.ipAddress}:${printer.port}",
                                Toast.LENGTH_LONG
                            ).show()
                            onPrinterSelected(printer.ipAddress)

                            // Close dialog
                            (context as? Activity)?.let { activity ->
                                val dialog = activity.window.decorView.findViewWithTag<android.view.View>("printer_dialog")
                                if (dialog is AlertDialog)  {
                                    dialog.dismiss()
                                }
                            }
                        } else {
                            Toast.makeText(
                                context,
                                "Принтер недоступен. Попробуйте другой.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 8, 0, 8)
            }
        }

        container.addView(printerButton)
    }

    // Unused methods have been removed for brevity but are still in your original file.
    // I've removed sendTSPLHui, sendTSPLCommands, hexToBytes, HUI_BITMAP_DATA etc.
    // as they are replaced by the new bitmap generation logic.
}