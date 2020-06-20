package com.firemaples.aoa.accessory

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.firemaples.aoa.accessory.managers.AccessoryModeManager
import kotlinx.android.synthetic.main.activity_accessory.*

class AccessoryActivity : AppCompatActivity() {

    private val TAG = "AOATest"

    private val manager: AccessoryModeManager by lazy {
        AccessoryModeManager(this).apply {
        onAddingLog = { msg ->
            Log.d(TAG, msg)
            runOnUiThread {
                tv_log.text = "${tv_log.text}\n$msg"
            }
        }
    }}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accessory)

        setViews()

        manager.start()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d(TAG, "${intent?.toString()}")
    }

    override fun onDestroy() {
        super.onDestroy()
        manager.stop()
    }

    private fun setViews() {
        bt_listDevices.setOnClickListener {
            manager.listDevices()
        }

        bt_connect.setOnClickListener {
            manager.connectToFirstAccessory()
        }

        bt_clearLog.setOnClickListener {
            tv_log.text = null
        }
    }
}
