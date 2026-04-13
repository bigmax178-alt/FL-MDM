package com.example.firebaselabelapp.bluetoothprinter

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.*
import org.snmp4j.CommunityTarget
import org.snmp4j.PDU
import org.snmp4j.Snmp
import org.snmp4j.mp.SnmpConstants
import org.snmp4j.smi.OID
import org.snmp4j.smi.OctetString
import org.snmp4j.smi.UdpAddress
import org.snmp4j.smi.VariableBinding
import org.snmp4j.transport.DefaultUdpTransportMapping
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Collections

/**
 * Fallback mechanism: Scans the local network IP by IP for printers on port 9100.
 * Much slower than mDNS but can find non-compliant devices.
 */
class WiFiPrinterDiscovery(private val context: Context) {

    companion object {
        private const val TAG = "WiFiPrinterDiscovery"
        const val PRINTER_PORT = 9100
        private const val CONNECTION_TIMEOUT_MS = 1000
        private const val SCAN_TIMEOUT_MS = 30000L
    }

    /**
     * Scan the local network for printers on the default port
     */
    suspend fun scanForPrinters(
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        onPrinterFound: (DiscoveredPrinter) -> Unit = {}
    ): List<DiscoveredPrinter> = withContext(Dispatchers.IO) {

        val discoveredPrinters = Collections.synchronizedList(mutableListOf<DiscoveredPrinter>())
        val baseIp = getLocalIpBase() ?: run {
            Log.e(TAG, "Could not determine local IP range")
            return@withContext emptyList()
        }
        Log.d(TAG, "Scanning IP range: $baseIp.1 - $baseIp.254")

        val hostsToScan = (1..254).toList()
        var scannedHosts = 0

        withTimeoutOrNull(SCAN_TIMEOUT_MS) {
            coroutineScope {
                val batchSize = 50
                hostsToScan.chunked(batchSize).forEach { batch ->
                    val jobs = batch.map { host ->
                        async {
                            val ip = "$baseIp.$host"
                            val printer = scanIpForPrinter(ip)
                            synchronized(this) { scannedHosts++ }
                            onProgress(scannedHosts, 254)

                            if (printer != null) {
                                discoveredPrinters.add(printer)
                                onPrinterFound(printer)
                            }
                            printer
                        }
                    }
                    jobs.awaitAll()
                }
            }
        }
        discoveredPrinters.sortedBy { it.responseTime }
    }

    /**
     * Quick scan - only scan +/- 10 IPs from the current device's IP.
     */
    suspend fun quickScan(
        onPrinterFound: (DiscoveredPrinter) -> Unit = {}
    ): List<DiscoveredPrinter> = withContext(Dispatchers.IO) {
        val discoveredPrinters = Collections.synchronizedList(mutableListOf<DiscoveredPrinter>())
        val ipInfo = getCurrentLocalIpInfo() ?: return@withContext emptyList()
        val baseIp = ipInfo.baseIp
        val currentHost = ipInfo.currentHost

        val startHost = maxOf(1, currentHost - 10)
        val endHost = minOf(254, currentHost + 10)
        val ipsToScan = (startHost..endHost).map { "$baseIp.$it" }

        coroutineScope {
            val jobs = ipsToScan.map { ip ->
                async {
                    val printer = scanIpForPrinter(ip)
                    if (printer != null) {
                        discoveredPrinters.add(printer)
                        onPrinterFound(printer)
                    }
                    printer
                }
            }
            jobs.awaitAll()
        }
        discoveredPrinters.sortedBy { it.responseTime }
    }

    /**
     * Scan a specific IP address for a printer on port 9100.
     */
    private suspend fun scanIpForPrinter(ip: String): DiscoveredPrinter? =
        withContext(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                val socket = Socket()

                withTimeoutOrNull(CONNECTION_TIMEOUT_MS.toLong()) {
                    socket.connect(InetSocketAddress(ip, PRINTER_PORT), CONNECTION_TIMEOUT_MS)
                }

                if (socket.isConnected) {
                    val responseTime = System.currentTimeMillis() - startTime
                    socket.close()
                    val hostName = getHostName(ip)

                    return@withContext DiscoveredPrinter(
                        ipAddress = ip,
                        port = PRINTER_PORT,
                        responseTime = responseTime,
                        hostName = if (hostName == ip) null else hostName,
                        discoveryMethod = "IP Scan"
                    )
                }
                socket.close()
            } catch (_: Exception) {
                // Failed to connect, not a printer
            }
            null
        }

    /**
     * Verify if a specific printer is still reachable
     */
    suspend fun verifyPrinter(ip: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val socket = Socket()
            withTimeoutOrNull(CONNECTION_TIMEOUT_MS.toLong()) {
                socket.connect(InetSocketAddress(ip, port), CONNECTION_TIMEOUT_MS)
            }
            val isConnected = socket.isConnected
            socket.close()
            isConnected
        } catch (e: Exception) {
            false
        }
    }

    // --- Hostname Discovery Waterfall ---
    private suspend fun getHostName(ip: String): String? {
        var hostName: String? = null
        // 1. Try SNMP (most reliable for names)
        hostName = getHostNameViaSnmp(ip)
        // 2. Try HTTP (scrapes web page)
        if (hostName.isNullOrBlank()) {
            hostName = getHostNameViaHttp(ip)
        }
        // 3. Final fallback: Reverse DNS
        if (hostName.isNullOrBlank()) {
            hostName = try {
                withTimeoutOrNull(500) { InetAddress.getByName(ip).canonicalHostName }
            } catch (e: Exception) { null }
        }
        return hostName
    }

    private suspend fun getHostNameViaSnmp(ip: String): String? = withContext(Dispatchers.IO) {
        try {
            val transport = DefaultUdpTransportMapping()
            transport.listen()
            val snmp = Snmp(transport)
            val address = UdpAddress("$ip/161")
            val pdu = PDU().apply {
                add(VariableBinding(OID("1.3.6.1.2.1.1.5.0"))) // sysName
                type = PDU.GET
            }
            val target = CommunityTarget(address, OctetString("public")).apply {
                version = SnmpConstants.version2c
                timeout = 800
                retries = 1
            }
            val responseEvent = snmp.send(pdu, target)
            snmp.close()
            val name = responseEvent?.response?.get(0)?.variable?.toString()
            if (!name.isNullOrBlank() && name != "null") {
                return@withContext name
            }
        } catch (e: Exception) {
            Log.d(TAG, "SNMP query failed for $ip: ${e.message}")
        }
        return@withContext null
    }

    private suspend fun getHostNameViaHttp(ip: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL("http://$ip")
            val connection = withTimeoutOrNull(1500) {
                url.openConnection() as java.net.HttpURLConnection
            } ?: return@withContext null

            connection.apply {
                requestMethod = "GET"
                connectTimeout = 1000
                readTimeout = 1000
            }
            if (connection.responseCode == 200) {
                val content = connection.inputStream.bufferedReader().use { it.readText() }
                val titleMatch = "<title>([^<]+)</title>".toRegex(RegexOption.IGNORE_CASE).find(content)
                val name = titleMatch?.groupValues?.get(1)?.trim()
                if (name!!.isNotBlank() && name.length > 1) {
                    return@withContext name
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "HTTP request failed for $ip: ${e.message}")
        }
        return@withContext null
    }

    // --- IP Range Helpers ---
    private data class IpInfo(val baseIp: String, val currentHost: Int)

    private fun getLocalIpBase(): String? {
        return getCurrentLocalIpInfo()?.baseIp
    }

    private fun getCurrentLocalIpInfo(): IpInfo? {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ipInt = wifiManager.connectionInfo.ipAddress
            if (ipInt == 0) return null
            val ip = String.format(
                "%d.%d.%d.%d",
                ipInt and 0xff,
                ipInt shr 8 and 0xff,
                ipInt shr 16 and 0xff,
                ipInt shr 24 and 0xff
            )
            val parts = ip.split(".")
            if (parts.size != 4) return null
            return IpInfo(baseIp = "${parts[0]}.${parts[1]}.${parts[2]}", currentHost = parts[3].toInt())
        } catch (e: Exception) {
            return null
        }
    }
}