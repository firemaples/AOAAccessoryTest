package com.firemaples.aoa.accessory.managers

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*

private const val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"

class AccessoryModeManager(private val context: Context) {
    companion object {
        private val TAG = AccessoryModeManager::class.java.simpleName

        private const val RECEIVE_BUF_SIZE = 1024 * 10
        private val dateFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    }

    var onAddingLog: ((String) -> Unit)? = null

    private val manager: UsbManager by lazy { context.getSystemService(Context.USB_SERVICE) as UsbManager }

    private val permissionIntent =
        PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), 0)

    private var connectedUsbAccessory: UsbAccessory? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var inputStream: FileInputStream? = null
    private var outputStream: FileOutputStream? = null
    private var workerThread: Thread? = null
    private var worker: Worker? = null

    @Synchronized
    fun start() {
        val filter = IntentFilter(ACTION_USB_PERMISSION).apply {
            addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED)
            addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)
        }
        context.registerReceiver(usbReceiver, filter)
    }

    @Synchronized
    fun stop() {
        context.unregisterReceiver(usbReceiver)
    }

    @Synchronized
    fun onIntent(intent: Intent) {
        usbReceiver.onReceive(context, intent)
    }

    fun log(msg: String) {
        Log.d(TAG, msg)
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
            worker = Worker(inputStream, onReceived)
            workerThread = Thread(worker, "InputStream-Worker").apply {
                start()
            }
            this.connectedUsbAccessory = usbAccessory
            this.inputStream = inputStream
            this.outputStream = outputStream

//            send("Hello")
        }
    }

    fun stopCommunication() {
        worker?.running = false
        fileDescriptor?.close()
    }

    fun send(msg: String) {
        log("Send msg [$msg] at ${dateFormatter.format(System.currentTimeMillis())}")
        outputStream?.write(msg.toByteArray())
        outputStream?.flush()
    }

    private val onReceived: (String) -> Unit = { msg ->
        log("Received msg [$msg] at ${dateFormatter.format(System.currentTimeMillis())}")
        send("Roger that, [$msg]")
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // ACTION_USB_PERMISSION == intent.action
            if (UsbManager.ACTION_USB_ACCESSORY_ATTACHED == intent.action) {
                val usbAccessory: UsbAccessory? =
                    intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY)

                if (usbAccessory != null
                //&& intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                ) {
                    log("onAccessoryAttached: $usbAccessory")
                    synchronized(this@AccessoryModeManager) {
                        startCommunication(usbAccessory)
                    }
                }
            } else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED == intent.action) {
                val usbAccessory: UsbAccessory? =
                    intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY)
                log("onAccessoryDetached, device: $usbAccessory")

                if (usbAccessory != null) { // && usbAccessory == connectedUsbAccessory
                    synchronized(this@AccessoryModeManager) {
                        stopCommunication()
                    }
                }
            }
        }
    }

    class Worker(private val inputStream: FileInputStream, private val onReceived: (String) -> Unit) :
        Runnable {

        var running = true

        override fun run() {
            val receiveBuffer = ByteArray(RECEIVE_BUF_SIZE)

            while (running) {
                try {
                    val receivedSize = inputStream.read(receiveBuffer, 0, RECEIVE_BUF_SIZE)

                    val firstZero = receiveBuffer.indexOfFirst { it.toInt() == 0 }.coerceAtMost(RECEIVE_BUF_SIZE)
                    val trimmed = ByteArray(firstZero)
                    System.arraycopy(receiveBuffer, 0, trimmed, 0, trimmed.size)

                    val msg = trimmed.toString(Charset.defaultCharset())

                    onReceived(msg)
                } catch (e: Exception) {
                    e.printStackTrace()

                    try {
                        Thread.sleep(5000)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }
}
