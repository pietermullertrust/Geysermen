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
            Toast.makeText(
                this,
                "Open device slot ${position + 1}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun loadDeviceList(): ArrayList<String> {

        val devices = DeviceStorage.load(this)

        val list = ArrayList<String>()

        for (d in devices) {

            list.add(
                "Slot ${d.slot} - ${d.name} | ${d.type} | ${d.role}"
            )
        }

        if (list.isEmpty()) {
            list.add("No devices added yet")
        }

        return list
    }

    override fun onResume() {
        super.onResume()

        if (::listDevices.isInitialized) {
            listDevices.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_list_item_1,
                loadDeviceList()
            )
        }
    }

}