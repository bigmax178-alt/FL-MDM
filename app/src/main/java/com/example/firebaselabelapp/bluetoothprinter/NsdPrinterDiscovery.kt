package com.example.firebaselabelapp.bluetoothprinter

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Collections

/**
 * Discovers printers on the network using mDNS/NSD (Network Service Discovery).
 */
class NsdPrinterDiscovery(
    context: Context,
    private val coroutineScope: CoroutineScope
) {
    companion object {
        private const val TAG = "NsdPrinterDiscovery"
        private val SERVICE_TYPES = arrayOf("_ipp._tcp.", "_printer._tcp.", "_pdl-datastream._tcp.")
    }

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var multicastLock: WifiManager.MulticastLock? = null

    private val _printers = MutableStateFlow<Map<String, DiscoveredPrinter>>(emptyMap())
    val printers = _printers.asStateFlow()

    private val discoveryListeners = Collections.synchronizedList(
        mutableListOf<NsdManager.DiscoveryListener>()
    )

    private val resolvingServices = Collections.synchronizedSet(mutableSetOf<String>())

    fun startDiscovery() {
        stopDiscovery() // Clear any previous listeners and locks
        Log.d(TAG, "=== STARTING mDNS DISCOVERY ===")

        // Check WiFi state
        val wifiInfo = wifiManager.connectionInfo
        Log.d(TAG, "WiFi SSID: ${wifiInfo.ssid}")
        Log.d(TAG, "WiFi IP: ${formatIp(wifiInfo.ipAddress)}")
        Log.d(TAG, "WiFi Link Speed: ${wifiInfo.linkSpeed} Mbps")

        // Acquire Multicast Lock
        try {
            multicastLock = wifiManager.createMulticastLock("nsd_multicast_lock").apply {
                setReferenceCounted(true)
                acquire()
            }
            Log.d(TAG, "✓ Multicast lock acquired: ${multicastLock?.isHeld}")
        } catch (e: Exception) {
            Log.e(TAG, "✗ Failed to acquire multicast lock", e)
        }

        // Start discovery for each service type
        SERVICE_TYPES.forEach { serviceType ->
            Log.d(TAG, "Starting discovery for: $serviceType")
            val listener = createDiscoveryListener(serviceType)
            discoveryListeners.add(listener)
            try {
                nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
                Log.d(TAG, "✓ Discovery started for: $serviceType")
            } catch (e: Exception) {
                Log.e(TAG, "✗ Failed to start discovery for $serviceType", e)
                discoveryListeners.remove(listener)
            }
        }

        Log.d(TAG, "Total active listeners: ${discoveryListeners.size}")
    }

    fun stopDiscovery() {
        Log.d(TAG, "=== STOPPING mDNS DISCOVERY ===")

        synchronized(discoveryListeners) {
            Log.d(TAG, "Stopping ${discoveryListeners.size} listeners...")
            discoveryListeners.forEach { listener ->
                try {
                    nsdManager.stopServiceDiscovery(listener)
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping discovery", e)
                }
            }
            discoveryListeners.clear()
        }

        try {
            multicastLock?.takeIf { it.isHeld }?.release()
            multicastLock = null
            Log.d(TAG, "✓ Multicast lock released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing multicast lock", e)
        }

        resolvingServices.clear()
        _printers.update { emptyMap() }
    }

    private fun createDiscoveryListener(serviceType: String): NsdManager.DiscoveryListener {
        return object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.i(TAG, "✓✓✓ DISCOVERY STARTED: $regType")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.i(TAG, "🎉🎉🎉 SERVICE FOUND!!!")
                Log.i(TAG, "  Service Name: ${service.serviceName}")
                Log.i(TAG, "  Service Type: ${service.serviceType}")
                Log.i(TAG, "  Full info: $service")

                val serviceName = service.serviceName
                if (resolvingServices.add(serviceName)) {
                    Log.d(TAG, "Attempting to resolve: $serviceName")
                    try {
                        nsdManager.resolveService(service, createResolveListener())
                        Log.d(TAG, "✓ Resolve request sent for: $serviceName")
                    } catch (e: Exception) {
                        Log.e(TAG, "✗ Failed to resolve service: $serviceName", e)
                        resolvingServices.remove(serviceName)
                    }
                } else {
                    Log.w(TAG, "Service already being resolved: $serviceName")
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.w(TAG, "⚠️ SERVICE LOST: ${service.serviceName}")
                resolvingServices.remove(service.serviceName)
                _printers.update { currentMap ->
                    currentMap.filterValues { it.hostName != service.serviceName }
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.i(TAG, "Discovery stopped: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "✗✗✗ START DISCOVERY FAILED!")
                Log.e(TAG, "  Service Type: $serviceType")
                Log.e(TAG, "  Error Code: $errorCode")
                Log.e(TAG, "  Error Meaning: ${getErrorCodeMeaning(errorCode)}")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Stop discovery failed: $serviceType, Code: $errorCode")
            }
        }
    }

    private fun createResolveListener(): NsdManager.ResolveListener {
        return object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "✗ RESOLVE FAILED!")
                Log.e(TAG, "  Service: ${serviceInfo.serviceName}")
                Log.e(TAG, "  Error Code: $errorCode")
                Log.e(TAG, "  Error Meaning: ${getErrorCodeMeaning(errorCode)}")
                resolvingServices.remove(serviceInfo.serviceName)
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "✓✓✓ SERVICE RESOLVED!!!")
                Log.i(TAG, "  Name: ${serviceInfo.serviceName}")
                Log.i(TAG, "  Host: ${serviceInfo.host}")
                Log.i(TAG, "  Port: ${serviceInfo.port}")
                Log.i(TAG, "  Type: ${serviceInfo.serviceType}")

                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val ipAddress = serviceInfo.host?.hostAddress
                        if (ipAddress == null) {
                            Log.e(TAG, "✗ No IP address in resolved service!")
                            return@launch
                        }

                        Log.d(TAG, "Creating printer object for: $ipAddress:${serviceInfo.port}")
                        val printer = DiscoveredPrinter(
                            ipAddress = ipAddress,
                            port = serviceInfo.port,
                            responseTime = -1,
                            hostName = serviceInfo.serviceName,
                            discoveryMethod = "mDNS"
                        )

                        _printers.update { currentMap ->
                            val newMap = currentMap + (ipAddress to printer)
                            Log.d(TAG, "✓ Printer added to map. Total printers: ${newMap.size}")
                            newMap
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing resolved service", e)
                    } finally {
                        resolvingServices.remove(serviceInfo.serviceName)
                    }
                }
            }
        }
    }

    private fun formatIp(ipInt: Int): String {
        return String.format(
            "%d.%d.%d.%d",
            ipInt and 0xff,
            ipInt shr 8 and 0xff,
            ipInt shr 16 and 0xff,
            ipInt shr 24 and 0xff
        )
    }

    private fun getErrorCodeMeaning(errorCode: Int): String {
        return when (errorCode) {
            NsdManager.FAILURE_ALREADY_ACTIVE -> "ALREADY_ACTIVE (3) - Discovery already running"
            NsdManager.FAILURE_INTERNAL_ERROR -> "INTERNAL_ERROR (0) - Internal system error"
            NsdManager.FAILURE_MAX_LIMIT -> "MAX_LIMIT (4) - Max outstanding requests reached"
            else -> "UNKNOWN ($errorCode)"
        }
    }
}