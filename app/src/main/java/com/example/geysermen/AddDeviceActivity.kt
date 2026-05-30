package com.example.geysermen

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

private lateinit var editDeviceName: EditText
private lateinit var spinnerDeviceType: Spinner
private lateinit var spinnerDeviceRole: Spinner
private lateinit var btnImportTuya: Button
private lateinit var btnSaveDevice: Button

class AddDeviceActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_device)
        editDeviceName = findViewById(R.id.editDeviceName)
        spinnerDeviceType = findViewById(R.id.spinnerDeviceType)
        spinnerDeviceRole = findViewById(R.id.spinnerDeviceRole)
        btnImportTuya = findViewById(R.id.btnImportTuya)
        btnSaveDevice = findViewById(R.id.btnSaveDevice)

        spinnerDeviceType.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            arrayOf("ESP32-C6", "ESP8266", "Tuya", "Inverter")
        )

        spinnerDeviceRole.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            arrayOf("Controller", "Geyser 1", "Geyser 2", "Pool Pump", "Other")
        )
        btnSaveDevice.setOnClickListener {

            val name = editDeviceName.text.toString()
            val type = spinnerDeviceType.selectedItem.toString()
            val role = spinnerDeviceRole.selectedItem.toString()

            Toast.makeText(
                this,
                "Saved: $name | $type | $role",
                Toast.LENGTH_LONG
            ).show()

            finish()
        }

        btnSaveDevice.setOnClickListener {

            val name = editDeviceName.text.toString().trim()
            val type = spinnerDeviceType.selectedItem.toString()
            val role = spinnerDeviceRole.selectedItem.toString()

            if (name.isEmpty()) {
                Toast.makeText(this, "Enter device name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val slot = DeviceStorage.nextSlot(this)

            val device = Device(
                slot = slot,
                name = name,
                type = type,
                role = role
            )

            DeviceStorage.add(this, device)

            Toast.makeText(
                this,
                "Device added to slot $slot",
                Toast.LENGTH_LONG
            ).show()

            finish()
        }

    }

}