package com.example.geysermen

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class AddDeviceActivity : AppCompatActivity() {

    private lateinit var editDeviceName: EditText
    private lateinit var spinnerDeviceType: Spinner
    private lateinit var spinnerDeviceRole: Spinner
    private lateinit var spinnerDevicePriority: Spinner
    private lateinit var spinnerDeviceRelationship: Spinner
    private lateinit var btnImportTuya: Button
    private lateinit var btnSaveDevice: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_device)

        val editSlot = intent.getIntExtra("EDIT_SLOT", -1)

        editDeviceName = findViewById(R.id.editDeviceName)
        spinnerDeviceType = findViewById(R.id.spinnerDeviceType)
        spinnerDeviceRole = findViewById(R.id.spinnerDeviceRole)
        spinnerDevicePriority = findViewById(R.id.spinnerDevicePriority)
        spinnerDeviceRelationship = findViewById(R.id.spinnerDeviceRelationship)
        btnImportTuya = findViewById(R.id.btnImportTuya)
        btnSaveDevice = findViewById(R.id.btnSaveDevice)

        val types = arrayOf("ESP32-C6", "ESP8266", "Tuya", "Inverter")
        val roles = arrayOf("Controller", "Geyser 1", "Geyser 2", "Pool Pump", "Other")
        val priorities = arrayOf("0 - Controller", "1 - Shed First", "2", "3", "4", "5")
        val relationships = arrayOf("Master", "Slave", "Load", "Monitor")

        spinnerDeviceType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, types)
        spinnerDeviceRole.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roles)
        spinnerDevicePriority.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, priorities)
        spinnerDeviceRelationship.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, relationships)

        if (editSlot > 0) {
            title = "Edit Device"

            val existing = DeviceStorage.load(this).find { it.slot == editSlot }

            if (existing != null) {
                editDeviceName.setText(existing.name)

                spinnerDeviceType.setSelection(types.indexOf(existing.type).coerceAtLeast(0))
                spinnerDeviceRole.setSelection(roles.indexOf(existing.role).coerceAtLeast(0))
                spinnerDevicePriority.setSelection(existing.priority.coerceIn(0, priorities.size - 1))
                spinnerDeviceRelationship.setSelection(relationships.indexOf(existing.relationship).coerceAtLeast(0))
            }
        } else {
            title = "Add Device"
        }

        btnSaveDevice.setOnClickListener {

            val name = editDeviceName.text.toString().trim()
            val type = spinnerDeviceType.selectedItem.toString()
            val role = spinnerDeviceRole.selectedItem.toString()
            val priority = spinnerDevicePriority.selectedItemPosition
            val relationship = spinnerDeviceRelationship.selectedItem.toString()

            if (name.isEmpty()) {
                Toast.makeText(this, "Enter device name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val slot = if (editSlot > 0) editSlot else DeviceStorage.nextSlot(this)

            val device = Device(
                slot = slot,
                name = name,
                type = type,
                role = role,
                priority = priority,
                relationship = relationship
            )

            if (editSlot > 0) {
                DeviceStorage.update(this, device)
                MasterSync.syncToMaster(this)
                Toast.makeText(this, "Device updated", Toast.LENGTH_LONG).show()
            } else {
                DeviceStorage.add(this, device)
                MasterSync.syncToMaster(this)
                Toast.makeText(this, "Device added to slot $slot", Toast.LENGTH_LONG).show()
            }

            finish()
        }
    }
}