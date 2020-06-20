package com.firemaples.aoa.host

import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.firemaples.aoa.common.Logger
import com.firemaples.aoa.common.getLogger
import com.firemaples.aoa.host.managers.HostModeManager
import kotlinx.android.synthetic.main.activity_host.*

class HostActivity : AppCompatActivity() {
    private val logger = getLogger()

    private val hostManager: HostModeManager by lazy {
        HostModeManager(this)
    }

    private val logInterceptor: (Logger.Level, String, Throwable?) -> Unit = { level, msg, t ->
        runOnUiThread {
            val text = "${tv_log.text}\n* $msg"
            tv_log.text = text
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_host)

        Logger.logInterceptor.add(logInterceptor)

        setViews()

        val intent: Intent? = intent
        logger.debug("onCreate(): ${intent?.toString()}")

        hostManager.registerReceiver()

        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            val usbDevice = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
            if (usbDevice != null) {
                hostManager.connectToUsbDevice(usbDevice)
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        logger.debug("onNewIntent(): ${intent?.toString()}")
    }

    override fun onDestroy() {
        super.onDestroy()
        hostManager.unregisterReceiver()
        Logger.logInterceptor.remove(logInterceptor)
    }

    private fun setViews() {
        bt_listDevices.setOnClickListener {
            val deviceList = hostManager.listDevices()

            logger.debug("Find ${deviceList.size} devices")

            deviceList.forEach {
                logger.debug("Device: ${it.key}, ${it.value}")
            }
        }

        bt_connect.setOnClickListener {
            val usbDevice = hostManager.listDevices().values.firstOrNull()
            if (usbDevice == null) {
                logger.debug("No device found")
            } else {
                hostManager.connectToUsbDevice(usbDevice)
            }
        }

        bt_clearLog.setOnClickListener {
            tv_log.text = null
        }
    }
}
