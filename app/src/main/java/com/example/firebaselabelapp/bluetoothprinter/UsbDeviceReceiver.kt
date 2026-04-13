package com.example.firebaselabelapp.bluetoothprinter

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.edit
import com.example.firebaselabelapp.MyApplication

class UsbDeviceReceiver() : BroadcastReceiver() {

    private var usbPrinterConnected = false

    override fun onReceive(context: Context?, intent: Intent?) {
        context?.let { ctx ->
            val myApp = ctx.applicationContext as MyApplication
            val printerManager = myApp.printerManager
            val kioskManager = myApp.kioskManager  // Get KioskManager reference

            val action = intent?.action
            val usbDevice = intent?.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
            val usbManager = ctx.getSystemService(Context.USB_SERVICE) as UsbManager

            when (action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    usbDevice?.let { device ->
                        Log.i("UsbDeviceReceiver", "Device attached: ${device.productName} (VID:${device.vendorId})")

                        // 1. Check if this is actually a printer (Class 7)
                        if (isPrinterDevice(device)) {
                            Log.i("UsbDeviceReceiver", "Device identified as a PRINTER.")

                            // 2. Universal Auto-Grant Logic
                            if (kioskManager.isDeviceOwner()) {
                                // If we are Device Owner, this call is SILENT (no dialog)
                                if (!usbManager.hasPermission(device)) {
                                    Log.i("UsbDeviceReceiver", "Device Owner: Auto-granting permission silently.")
                                    val permissionIntent = PendingIntent.getBroadcast(
                                        ctx,
                                        0,
                                        Intent(PrinterManager.ACTION_USB_PERMISSION),
                                        PendingIntent.FLAG_IMMUTABLE
                                    )
                                    usbManager.requestPermission(device, permissionIntent)
                                }
                            } else {
                                // Fallback for non-kiosk mode (Developers/Testing)
                                if (!usbManager.hasPermission(device)) {
                                    val permissionIntent = PendingIntent.getBroadcast(
                                        ctx,
                                        0,
                                        Intent(PrinterManager.ACTION_USB_PERMISSION),
                                        PendingIntent.FLAG_IMMUTABLE
                                    )
                                    usbManager.requestPermission(device, permissionIntent)
                                }
                            }

                            // 3. Select this printer immediately (Optional: depends on your UX)
                            // You might want to only select it if no printer is currently selected.
                            printerManager.setSelectedUsbDevice(device)
                            Toast.makeText(ctx, "Принтер подключен: ${device.productName}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    usbDevice?.let {
                        val prefs = printerManager.context.getSharedPreferences("LabelAppPrefs", Context.MODE_PRIVATE)
                        val savedDeviceId = prefs.getInt("SelectedPrinterUsbDeviceId", -1)
                        if (it.deviceId == savedDeviceId) {
                            printerManager.setSelectedUsbDevice(null)
                            prefs.edit {
                                remove("SelectedPrinterType")
                                remove("SelectedPrinterUsbDeviceId")
                            }
                            Toast.makeText(
                                ctx,
                                "USB Device Detached: ${it.productName}. Selection cleared.",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                ctx,
                                "USB Device Detached: ${it.productName}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        val remainingPrinters = usbManager.deviceList.values.filter { device ->
                            true
                        }
                        usbPrinterConnected = remainingPrinters.isNotEmpty()
                        PrinterManager.usbDeviceActive = usbPrinterConnected
                    }
                }

                PrinterManager.ACTION_USB_PERMISSION -> {
                    usbDevice?.let { device ->
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            Log.d("UsbDeviceReceiver", "USB permission granted for ${device.productName}")
                            Toast.makeText(
                                ctx,
                                "USB permission granted for ${device.productName}",
                                Toast.LENGTH_SHORT
                            ).show()

                            val prefs = printerManager.context.getSharedPreferences("LabelAppPrefs", Context.MODE_PRIVATE)
                            val savedDeviceId = prefs.getInt("SelectedPrinterUsbDeviceId", -1)
                            if (device.deviceId == savedDeviceId) {
                                Log.d("UsbDeviceReceiver", "Permission granted for currently selected device. Refreshing state.")
                                printerManager.setSelectedUsbDevice(device)
                            }
                        } else {
                            Log.w("UsbDeviceReceiver", "USB permission denied for ${device.productName}")
                            Toast.makeText(
                                ctx,
                                "USB permission denied for ${device.productName}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }

    fun usbPrinterConnected(): Boolean {
        return usbPrinterConnected
    }

    /**
     * Helper to check if a USB device has a Printer Interface (Class 7)
     */
    private fun isPrinterDevice(device: UsbDevice): Boolean {
        val count = device.interfaceCount
        for (i in 0 until count) {
            val inter = device.getInterface(i)
            // USB_CLASS_PRINTER is constant 7
            if (inter.interfaceClass == UsbConstants.USB_CLASS_PRINTER) {
                return true
            }
        }
        return false
    }
}