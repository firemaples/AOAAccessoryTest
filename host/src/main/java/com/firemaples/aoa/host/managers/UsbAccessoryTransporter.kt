package com.firemaples.aoa.host.managers

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import com.firemaples.aoa.common.Transporter
import java.io.IOException

class UsbAccessoryTransporter(
    var conn: UsbDeviceConnection?,
    var bulkIn: UsbEndpoint?,
    var bulkOut: UsbEndpoint?
) : Transporter(16384) {
    private val timeout = 1000

    override fun ioClose() {
        conn = null
        bulkIn = null
        bulkOut = null
    }

    override fun ioRead(buffer: ByteArray, offset: Int, count: Int): Int {
        val conn = conn ?: throw IOException("Connection was closed")

        return conn.bulkTransfer(bulkIn, buffer, offset, count, -1)
    }

    override fun ioWrite(buffer: ByteArray, offset: Int, count: Int) {
        val conn = conn ?: throw IOException("Connection was closed")
        val transferred = conn.bulkTransfer(bulkOut, buffer, offset, count, timeout)
        if (transferred < 0) throw IOException("Bulk transferred failed, result: $transferred")
    }
}
