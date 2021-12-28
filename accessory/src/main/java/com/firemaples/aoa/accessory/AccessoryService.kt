package com.firemaples.aoa.accessory

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.charset.Charset

class AccessoryService : Service() {
    companion object {
        private val TAG: String = AccessoryService::class.java.simpleName
        private const val INTENT_WRAPPED_INTENT = "wrapped_intent"
        private const val RECEIVE_BUFFER_SIZE = 10 * 1024
        private const val THREAD_MESSAGE_RECEIVER = "thread_message_receiver"

        private const val NOTIFICATION_ID = 999
        private const val NOTIFICATION_CHANNEL_ID = "accessory_notification_channel"

        fun start(context: Context, wrappedIntent: Intent) {
            context.startService(
                Intent(context, AccessoryService::class.java).putExtra(
                    INTENT_WRAPPED_INTENT,
                    wrappedIntent
                )
            )
        }
    }

    private val usbManager: UsbManager by lazy { getSystemService(Context.USB_SERVICE) as UsbManager }
    private val notificationManager: NotificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null) handleIntent(intent)
        }
    }

    private var messageReceiver: MessageReceiver? = null
    private var messageReceiverThread: Thread? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var outputStream: OutputStream? = null

    override fun onCreate() {
        super.onCreate()
        registerReceiver(receiver, IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)
            addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED)
        })
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        val wrappedIntent = intent?.getParcelableExtra<Intent>(INTENT_WRAPPED_INTENT)
        if (wrappedIntent != null) handleIntent(wrappedIntent)

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            UsbManager.ACTION_USB_ACCESSORY_ATTACHED -> {
                val accessory = intent.getParcelableExtra<UsbAccessory>(UsbManager.EXTRA_ACCESSORY)
                if (accessory != null) {
                    startCommunication(accessory)
                }
            }
            UsbManager.ACTION_USB_ACCESSORY_DETACHED -> {
                val accessory = intent.getParcelableExtra<UsbAccessory>(UsbManager.EXTRA_ACCESSORY)
                if (accessory != null) {
                    stopCommunication(accessory)
                }
            }
        }
    }

    private fun startCommunication(accessory: UsbAccessory) {
        fileDescriptor = usbManager.openAccessory(accessory).apply {
            val fd = fileDescriptor
            val inputStream = FileInputStream(fd)
            outputStream = FileOutputStream(fd)

            messageReceiver = MessageReceiver(inputStream) {
                this@AccessoryService.onReceiveMessage(it)
            }
            messageReceiverThread = Thread(messageReceiver, THREAD_MESSAGE_RECEIVER).apply {
                start()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            var channel = notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID)
            if (channel == null) {
                channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    NOTIFICATION_CHANNEL_ID,
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                notificationManager.createNotificationChannel(channel)
            }
        }

        val intent = PendingIntent.getActivity(
            this, 0,
            Intent(this, AccessoryActivity::class.java), 0
        )
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Accessory service is running")
            .setContentText("Accessory service is running")
            .setContentIntent(intent)
            .build()
        startForeground(NOTIFICATION_ID, notification)
        Log.i(TAG, "startForeground()")
    }

    private fun stopCommunication(accessory: UsbAccessory) {
        messageReceiver?.running = false
        fileDescriptor?.close()

        stopForeground(true)
        Log.i(TAG, "stopForeground()")
    }

    private fun onReceiveMessage(msg: String) {
        Log.i(TAG, "onReceiveMessage(), msg: $msg")

        sendMessage("I received your message: $msg")
    }

    private fun sendMessage(msg: String) {
        Log.i(TAG, "sendMessage(), msg: $msg")

        outputStream?.apply {
            write(msg.toByteArray())
            flush()
        }
    }

    private class MessageReceiver(
        private val inputStream: FileInputStream,
        private val onReceiveMessage: (msg: String) -> Unit
    ) : Runnable {
        var running = true

        override fun run() {
            val receiveBuffer = ByteArray(RECEIVE_BUFFER_SIZE)
            while (running) {
                try {
                    val size = inputStream.read(receiveBuffer, 0, RECEIVE_BUFFER_SIZE)

                    val firstZero = receiveBuffer.indexOfFirst { it.toInt() == 0 }.coerceAtMost(RECEIVE_BUFFER_SIZE)
                    val trimmed = ByteArray(firstZero)
                    System.arraycopy(receiveBuffer, 0, trimmed, 0, trimmed.size)

                    val msg = trimmed.toString(Charset.defaultCharset())

                    onReceiveMessage.invoke(msg)
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
