package com.example.geysermen

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton

class SetupActivity : AppCompatActivity() {

    private lateinit var listDevices: ListView
    private lateinit var btnAddDevice: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        listDevices = findViewById(R.id.listDevices)
        btnAddDevice = findViewById(R.id.btnAddDevice)

        val devices = loadDeviceList()

        listDevices.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            devices
        )

        btnAddDevice.setOnClickListener {
            startActivity(Intent(this, AddDeviceActivity::class.java))
        }

        listDevices.setOnItemClickListener { _, _, position, _ ->

            val devices = DeviceStorage.load(this)

            if (position < devices.size) {
                val intent = Intent(this, DeviceSetupActivity::class.java)
                intent.putExtra("slot", devices[position].slot)
                startActivity(intent)
            }
        }
    }

    private fun loadDeviceList(): ArrayList<String> {

        val devices = DeviceStorage.load(this)

        val list = ArrayList<String>()

        for (d in devices) {

            list.add(
                "${d.name}\n${d.type} • ${d.relationship} • Priority ${d.priority}"
            )
        }

        if (list.isEmpty()) {
            list.add("No devices added yet")
        }

        return list
    }

    override fun onResume() {
        super.onResume()

        MasterSync.syncFromMaster(this) {
            runOnUiThread {
                if (::listDevices.isInitialized) {
                    listDevices.adapter = ArrayAdapter(
                        this,
                        android.R.layout.simple_list_item_1,
                        loadDeviceList()
                    )
                }

            }
        }
    }

}