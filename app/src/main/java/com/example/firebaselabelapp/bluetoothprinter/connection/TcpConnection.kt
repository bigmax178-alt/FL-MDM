package com.example.firebaselabelapp.bluetoothprinter.connection

import com.dantsu.escposprinter.connection.DeviceConnection
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

class TcpConnection(private val address: String, private val port: Int, private val timeout: Int = 30) : DeviceConnection() {
    private var socket: Socket? = null

    /**
     * Check if the TCP socket is connected.
     * This implementation is more specific for a socket than the base class one.
     */
    override fun isConnected(): Boolean {
        return socket != null && socket!!.isConnected && !socket!!.isClosed
    }

    /**
     * Start socket connection.
     */
    @Throws(IOException::class)
    override fun connect(): DeviceConnection {
        if (this.isConnected) {
            return this
        }
        try {
            socket = Socket()
            // The timeout is in milliseconds. The user passes seconds.
            socket!!.connect(InetSocketAddress(this.address, this.port), this.timeout * 1000)
            this.outputStream = socket!!.outputStream
//            this.inputStream = socket!!.inputStream
        } catch (e: IOException) {
            e.printStackTrace()
            this.disconnect()
            throw IOException("failed to connect to printer at ${this.address}:${this.port}. Check IP, port and network connection.", e)
        }
        return this
    }

    /**
     * Close the socket connection.
     */
    override fun disconnect(): DeviceConnection {
        if (this.outputStream != null) {
            try {
                this.outputStream.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            this.outputStream = null
        }
//        if (this.inputStream != null) {
//            try {
//                this.inputStream.close()
//            } catch (e: IOException) {
//                e.printStackTrace()
//            }
//            this.inputStream = null
//        }
        if (this.socket != null) {
            try {
                this.socket!!.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            this.socket = null
        }
        return this
    }
}
