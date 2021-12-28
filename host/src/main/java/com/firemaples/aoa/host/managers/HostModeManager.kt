package com.firemaples.aoa.host.managers

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.*
import android.util.Log
import com.firemaples.aoa.common.UsbAccessoryConstants
import com.firemaples.aoa.common.getLogger
import java.nio.charset.Charset

class HostModeManager(private val context: Context) {
    companion object {
        private const val MANUFACTURER = "Android"
        private const val MODEL = "Accessory Host Demo"
        private const val DESCRIPTION = "AOA host demo application"
        private const val VERSION = "1.0"
        private const val URI = "http://www.android.com/"
        private const val SERIAL = "0000000012345678"

        private const val ACTION_USB_DEVICE_PERMISSION =
            "com.firemaples.aoa.host.ACTION_USB_DEVICE_PERMISSION"
        private const val REQUEST_ID = 111

        private val logger = getLogger()
    }

    private val usbManager: UsbManager by lazy { context.getSystemService(Context.USB_SERVICE) as UsbManager }

    private val permissionIntent: PendingIntent by lazy {
        PendingIntent.getBroadcast(
            context, 0, Intent(ACTION_USB_DEVICE_PERMISSION), PendingIntent.FLAG_ONE_SHOT
        )
    }

    private var isConnected: Boolean = false
    private var connectedAccessoryInfo: AccessoryInfo? = null
    private var usbAccessoryTransporter: UsbAccessoryTransporter? = null

    @Synchronized
    fun registerReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_DEVICE_PERMISSION)
            addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        context.registerReceiver(usbReceiver, filter)
    }

    @Synchronized
    fun unregisterReceiver() {
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (e: Exception) {
            //Ignore
        }
    }

    fun listDevices(): HashMap<String, UsbDevice> = usbManager.deviceList

    fun connectToUsbDevice(device: UsbDevice) {
        if (isConnected) return

        logger.info("connectToUsbDevice(): $device")
        doConnectDevice(device)
    }

    fun isConnected(): Boolean = isConnected

    private fun doConnectDevice(device: UsbDevice) {
        if (isConnected) {
            disconnect()
        }

        if (!usbManager.hasPermission(device)) {
            logger.info("Requesting permission to connect the device.")

            usbManager.requestPermission(device, permissionIntent)
        }

        val conn = usbManager.openDevice(device)
        if (conn == null) {
            logger.error("Cannot obtain device connection")
            return
        }

        val intf = device.getInterface(0)
        val controlEndpoint = intf.getEndpoint(0)
        if (!conn.claimInterface(intf, true)) {
            logger.error("Claim interface failed")
            return
        }

        try {
            if (isAccessory(device)) {
                doConnectAccessory(device, conn, intf, controlEndpoint)
            } else {
                attemptToSwitchToAccessoryMode(conn)
            }
        } finally {
            if (!isConnected) conn.releaseInterface(intf)
        }
    }

    private fun attemptToSwitchToAccessoryMode(conn: UsbDeviceConnection) {
        logger.debug("Attempting to switch device to accessory mode")

        val protocolVersion = getProtocol(conn)
        if (protocolVersion < 1) {
            logger.error("Device does not support accessory protocol")
            return
        }

        logger.debug("Protocol version: $protocolVersion")

        // Send identifying strings.

        // Send identifying strings.
        sendString(conn, UsbAccessoryConstants.ACCESSORY_STRING_MANUFACTURER, MANUFACTURER)
        sendString(conn, UsbAccessoryConstants.ACCESSORY_STRING_MODEL, MODEL)
        sendString(conn, UsbAccessoryConstants.ACCESSORY_STRING_DESCRIPTION, DESCRIPTION)
        sendString(conn, UsbAccessoryConstants.ACCESSORY_STRING_VERSION, VERSION)
        sendString(conn, UsbAccessoryConstants.ACCESSORY_STRING_URI, URI)
        sendString(conn, UsbAccessoryConstants.ACCESSORY_STRING_SERIAL, SERIAL)

        // Send start.
        // The device should re-enumerate as an accessory.

        // Send start.
        // The device should re-enumerate as an accessory.
        logger.debug("Sending accessory start request.")
        val len = conn.controlTransfer(
            UsbConstants.USB_DIR_OUT or UsbConstants.USB_TYPE_VENDOR,
            UsbAccessoryConstants.ACCESSORY_START, 0, 0, null, 0, 10000
        )
        if (len != 0) {
            logger.error("Device refused to switch to accessory mode.")
        } else {
            logger.debug("Waiting for device to re-enumerate...")
        }
    }

    private fun doConnectAccessory(
        device: UsbDevice,
        conn: UsbDeviceConnection,
        intf: UsbInterface,
        controlEndpoint: UsbEndpoint
    ) {
        logger.info("Connecting to accessory: $device")

        val protocolVersion = getProtocol(conn)
        if (protocolVersion < 1) {
            logger.error("Device does not support accessory protocol")
            return
        }

        logger.debug("Protocol version: $protocolVersion")

        // Setup bulk endpoints.
        var bulkIn: UsbEndpoint? = null
        var bulkOut: UsbEndpoint? = null
        for (i in 0 until intf.endpointCount) {
            val ep: UsbEndpoint = intf.getEndpoint(i)
            if (ep.direction == UsbConstants.USB_DIR_IN) {
                if (bulkIn == null) {
                    logger.debug("Bulk IN endpoint: $i")
                    bulkIn = ep
                }
            } else {
                if (bulkOut == null) {
                    logger.debug("Bulk OUT endpoint: $i")
                    bulkOut = ep
                }
            }
        }
        if (bulkIn == null || bulkOut == null) {
            logger.error("Unable to find bulk endpoints")
            return
        }

        logger.info("Connected")
        isConnected = true
        connectedAccessoryInfo = AccessoryInfo(
            device = device,
            protocolVersion = protocolVersion,
            intf = intf,
            conn = conn,
            controlEndPoint = controlEndpoint
        )
        usbAccessoryTransporter = UsbAccessoryTransporter(conn, bulkIn, bulkOut).apply {
            startReading()
        }
    }

    private fun disconnect() {

    }

    private fun isAccessory(device: UsbDevice): Boolean {
        val vid = device.vendorId
        val pid = device.productId

        return vid == UsbAccessoryConstants.USB_ACCESSORY_VENDOR_ID
                && (pid == UsbAccessoryConstants.USB_ACCESSORY_PRODUCT_ID
                || pid == UsbAccessoryConstants.USB_ACCESSORY_ADB_PRODUCT_ID)
    }

    private fun getProtocol(conn: UsbDeviceConnection): Int {
        val buffer = ByteArray(2)
        val len = conn.controlTransfer(
            UsbConstants.USB_DIR_IN or UsbConstants.USB_TYPE_VENDOR,
            UsbAccessoryConstants.ACCESSORY_GET_PROTOCOL, 0, 0, buffer, 2, 10000
        )
        return if (len != 2) {
            -1
        } else buffer[1].toInt() shl 8 or buffer[0].toInt()
    }

    private fun sendString(conn: UsbDeviceConnection, index: Int, string: String) {
        val buffer = (string + "\u0000").toByteArray()
        val len = conn.controlTransfer(
            UsbConstants.USB_DIR_OUT or UsbConstants.USB_TYPE_VENDOR,
            UsbAccessoryConstants.ACCESSORY_SEND_STRING, 0, index,
            buffer, buffer.size, 10000
        )
        if (len != buffer.size) {
            logger.error("Failed to send string $index: \"$string\"")
        } else {
            logger.debug("Sent string $index: \"$string\"")
        }
    }

    private val usbReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            when (intent.action) {
                ACTION_USB_DEVICE_PERMISSION -> {
                    val granted =
                        intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (device != null && granted) {
                        logger.info("UsbDevice granted: $device")
                        connectToUsbDevice(device)
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    logger.debug("onAccessoryAttached, device: $device")

                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    logger.debug("onAccessoryDetached, device: $device")

                }
            }
        }
    }

    class Worker(
        private val usbDeviceConnection: UsbDeviceConnection,
        private val usbInterface: UsbInterface,
        private val logInfo: (String) -> Unit
    ) : Runnable {
        override fun run() {
            try {
                while (true) {
                    val msg = receive(REQUEST_ID)
                    logInfo("Received msg: $msg")

                    val send = "$msg ECHO"
                    logInfo("Send msg: $send")
                    send(REQUEST_ID, 0, send)

                    Thread.sleep(2000)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                logInfo(Log.getStackTraceString(e))
            } finally {
                usbDeviceConnection.releaseInterface(usbInterface)
            }
        }

        private fun send(request: Int, index: Int, msg: String) {
            val bytes = msg.toByteArray()
            usbDeviceConnection.controlTransfer(
                UsbConstants.USB_DIR_OUT or UsbConstants.USB_TYPE_VENDOR,
                request, 0, index, bytes, bytes.size, 30_000
            )
        }

        private fun receive(request: Int): String {
            val bytes = ByteArray(100)
            usbDeviceConnection.controlTransfer(
                UsbConstants.USB_DIR_IN or UsbConstants.USB_TYPE_VENDOR,
                request, 0, 0, bytes, bytes.size, 30_000
            )

            return bytes.toString(Charset.defaultCharset())
        }
    }

    class AccessoryInfo(
        val device: UsbDevice,
        val protocolVersion: Int,
        val intf: UsbInterface,
        val conn: UsbDeviceConnection,
        val controlEndPoint: UsbEndpoint
    )
}
