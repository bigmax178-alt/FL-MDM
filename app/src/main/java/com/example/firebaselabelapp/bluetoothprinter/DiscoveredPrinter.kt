package com.example.firebaselabelapp.bluetoothprinter

/**
 * The single source of truth for a printer, regardless of how it was found.
 */
data class DiscoveredPrinter(
    val ipAddress: String,
    val port: Int,
    val hostName: String? = null,
    val responseTime: Long, // Time in MS for IP scan, -1 for mDNS
    val discoveryMethod: String // "mDNS" or "IP Scan"
)