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
                    tv_log.text = "$msg\n${tv_log.text}"
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accessory)

        Log.d(TAG, "${intent?.toString()}")

        setViews()

        manager.start()

        if (intent != null) {
            manager.onIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d(TAG, "${intent?.toString()}")

        if (intent != null) {
            manager.onIntent(intent)
        }
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
