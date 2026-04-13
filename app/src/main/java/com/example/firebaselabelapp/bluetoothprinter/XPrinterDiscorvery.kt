package com.example.firebaselabelapp.bluetoothprinter

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class XPrinterDiscovery {

    companion object {
        private const val TAG = "XPrinterDiscovery"
        private const val TARGET_PORT = 22368 // The port found in Wireshark
        private const val DISCOVERY_TIMEOUT = 2000 // 2 seconds is usually enough

        // The exact hex payload found in Packet 220
        private const val MAGIC_HEX = "00200001000108000002000000060000010000000000ffffffffffff00000000"
    }

    suspend fun scan(): List<DiscoveredPrinter> = withContext(Dispatchers.IO) {
        val foundPrinters = mutableListOf<DiscoveredPrinter>()
        var socket: DatagramSocket? = null

        try {
            // 1. Prepare the socket
            socket = DatagramSocket(null)
            socket.reuseAddress = true
            socket.broadcast = true
            socket.soTimeout = DISCOVERY_TIMEOUT
            socket.bind(null) // Bind to any available local port

            // 2. Prepare the packet
            val payload = hexToBytes(MAGIC_HEX)
            val broadcastAddress = InetAddress.getByName("255.255.255.255")
            val packet = DatagramPacket(payload, payload.size, broadcastAddress, TARGET_PORT)

            // 3. Send Broadcast
            Log.d(TAG, "Sending UDP Broadcast to $TARGET_PORT")
            socket.send(packet)

            // 4. Listen for responses
            val buffer = ByteArray(1024)
            val startTime = System.currentTimeMillis()

            while (true) {
                val receivePacket = DatagramPacket(buffer, buffer.size)
                socket.receive(receivePacket) // Blocks until packet received or timeout

                val ip = receivePacket.address.hostAddress

                // Filter out our own echo if necessary (check against local IP)
                // For now, we process everything.

                val responseData = receivePacket.data.copyOf(receivePacket.length)
                val macAddress = parseMacAddress(responseData)

                Log.d(TAG, "Found XPrinter at $ip with MAC: $macAddress")

                foundPrinters.add(
                    DiscoveredPrinter(
                        ipAddress = ip,
                        port = 9100, // Standard printing port, even if discovered on 22368
                        hostName = "XPrinter $macAddress",
                        responseTime = System.currentTimeMillis() - startTime,
                        discoveryMethod = "UDP Broadcast"
                    )
                )
            }

        } catch (e: java.net.SocketTimeoutException) {
            Log.d(TAG, "UDP Scan finished (timeout). Found: ${foundPrinters.size}")
        } catch (e: Exception) {
            Log.e(TAG, "UDP Scan failed", e)
        } finally {
            socket?.close()
        }

        return@withContext foundPrinters
    }

    /**
     * Helper to parse MAC address from the XPrinter response.
     * Based on the request structure, the MAC is usually at offset 22-28.
     */
    private fun parseMacAddress(data: ByteArray): String {
        // Safety check
        if (data.size < 28) return "Unknown"

        // Extract 6 bytes starting at index 22 (Based on the 'ff ff ff' position in request)
        val sb = StringBuilder()
        for (i in 22..27) {
            if (i > 22) sb.append(":")
            sb.append(String.format("%02X", data[i]))
        }
        return sb.toString()
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}