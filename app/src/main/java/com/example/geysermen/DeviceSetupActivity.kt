package com.example.geysermen

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.widget.ImageButton

class DeviceSetupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_setup)

        val slot = intent.getIntExtra("slot", -1)

        val titleView = findViewById<TextView>(R.id.textDeviceTitle)
        val btnEdit = findViewById<ImageButton>(R.id.btnEdit)
        val txtSlot = findViewById<TextView>(R.id.txtSlot)
        val txtType = findViewById<TextView>(R.id.txtType)
        val txtRole = findViewById<TextView>(R.id.txtRole)
        val txtRelationship = findViewById<TextView>(R.id.txtRelationship)
        val txtPriority = findViewById<TextView>(R.id.txtPriority)

        val txtOnline = findViewById<TextView>(R.id.txtOnline)
        val txtFirmware = findViewById<TextView>(R.id.txtFirmware)
        val txtLastSeen = findViewById<TextView>(R.id.txtLastSeen)

        val txtIp = findViewById<TextView>(R.id.txtIp)
        val txtDeviceId = findViewById<TextView>(R.id.txtDeviceId)

        val device = DeviceStorage.load(this).find { it.slot == slot }

        if (device != null) {

            titleView.text = device.name

            txtSlot.text = "Slot: ${device.slot}"
            txtType.text = "Type: ${device.type}"
            txtRole.text = "Role: ${device.role}"
            txtRelationship.text = "Relationship: ${device.relationship}"
            txtPriority.text = "Priority: ${device.priority}"

            txtOnline.text = "Online: ${device.online}"
            txtFirmware.text = "Firmware: ${device.firmware}"
            txtLastSeen.text = "Last Seen: ${device.lastSeen}"

            txtIp.text = "IP Address: ${device.ip}"
            txtDeviceId.text = "Device ID: ${device.deviceId}"
            btnEdit.setOnClickListener {

                val intent = Intent(
                    this,
                    AddDeviceActivity::class.java
                )

                intent.putExtra(
                    "EDIT_SLOT",
                    device.slot
                )

                startActivity(intent)
            }
        } else {
            titleView.text = "Device not found"
        }
    }
}