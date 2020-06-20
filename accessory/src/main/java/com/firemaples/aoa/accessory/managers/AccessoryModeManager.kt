package com.firemaples.aoa.accessory.managers

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.Charset

private const val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"

class AccessoryModeManager(private val context: Context) {
    var onAddingLog: ((String) -> Unit)? = null

    private val manager: UsbManager by lazy { context.getSystemService(Context.USB_SERVICE) as UsbManager }

    private val permissionIntent =
        PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), 0)

    private var connectedUsbAccessory: UsbAccessory? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var inputStream: FileInputStream? = null
    private var outputStream: FileOutputStream? = null
    private var workerThread: Thread? = null

    @Synchronized
    fun start() {
        val filter = IntentFilter(ACTION_USB_PERMISSION).apply {
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        context.registerReceiver(usbReceiver, filter)
    }

    @Synchronized
    fun stop() {
        context.unregisterReceiver(usbReceiver)
    }

    fun log(msg: String) {
        onAddingLog?.invoke(msg)
    }

    fun listDevices() {
        val accessoryList = manager.accessoryList

        log("Find ${accessoryList?.size} accessories")

        accessoryList?.forEach {
            log("Accessory: $it")
        }
    }

    fun connectToFirstAccessory() {
        val accessory = manager.accessoryList?.firstOrNull()
        if (accessory == null) {
            log("No accessory found")
            return
        }

        log("Request permission")
        manager.requestPermission(accessory, permissionIntent)
    }

    fun startCommunication(usbAccessory: UsbAccessory) {
        fileDescriptor = manager.openAccessory(usbAccessory)
        fileDescriptor?.fileDescriptor?.also { fd ->
            val inputStream = FileInputStream(fd)
            val outputStream = FileOutputStream(fd)
            workerThread = Thread(Worker(inputStream, received), "Thread-Worker").apply {
                start()
            }
            this.connectedUsbAccessory = usbAccessory
            this.inputStream = inputStream
            this.outputStream = outputStream

            send("Hello")
        }
    }

    fun send(msg: String) {
        log("Send msg: $msg")
        outputStream?.write(msg.toByteArray())
        outputStream?.flush()
    }

    private val received: (String) -> Unit = { msg ->
        log("Received msg: $msg")
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                val usbAccessory: UsbAccessory? =
                    intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY)

                if (usbAccessory != null &&
                    intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                ) {
                    log("UsbDevice granted: $usbAccessory")
                    synchronized(this@AccessoryModeManager) {
                        startCommunication(usbAccessory)
                    }
                }
            } else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED == intent.action) {
                val usbAccessory: UsbAccessory? =
                    intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY)
                log("onAccessoryDetached, device: $usbAccessory")
            }
        }
    }

    class Worker(private val inputStream: FileInputStream, private val received: (String) -> Unit) :
        Runnable {
        override fun run() {
            while (true) {
                val bytes = inputStream.readBytes()
                val msg = bytes.toString(Charset.defaultCharset())

                received(msg)
            }
        }
    }
}
