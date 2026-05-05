package com.example.geysermen

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.PrintWriter
import java.net.Socket

class SetupActivity : AppCompatActivity() {

    private lateinit var editSsid: EditText
    private lateinit var editPassword: EditText
    private lateinit var editC6Ip: EditText
    private lateinit var editGateway: EditText
    private lateinit var editSubnet: EditText
    private lateinit var editOtaPass: EditText
    private lateinit var editGeyser1Ip: EditText
    private lateinit var editGeyser2Ip: EditText
    private lateinit var inverterIp: EditText
    private lateinit var editPollInterval: EditText

    //private lateinit var btnSaveLocal: Button
    private lateinit var btnSendToC6: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        // --- UI references ---
        editSsid        = findViewById(R.id.editSsid)
        editPassword    = findViewById(R.id.editPassword)
        editC6Ip        = findViewById(R.id.editC6Ip)
        editGateway     = findViewById(R.id.editGateway)
        editSubnet      = findViewById(R.id.editSubnet)
        editOtaPass     = findViewById(R.id.editOtaPass)
        editGeyser1Ip   = findViewById(R.id.editGeyser1Ip)
        editGeyser2Ip   = findViewById(R.id.editGeyser2Ip)
        inverterIp      = findViewById(R.id.inverterIp)
        editPollInterval = findViewById(R.id.editPollInterval)

        //btnSaveLocal    = findViewById(R.id.btnSaveLocal)
        btnSendToC6     = findViewById(R.id.btnSendToC6)

        loadSavedValues()

        //btnSaveLocal.setOnClickListener {
           // saveToApp()
       // }

        btnSendToC6.setOnClickListener {
            saveToApp()
            sendSetupToEsp32()
        }
    }

    // Load previously saved values
    private fun loadSavedValues() {
        val prefs = getSharedPreferences("setup", MODE_PRIVATE)

        editSsid.setText(prefs.getString("ssid", ""))
        editPassword.setText(prefs.getString("password", ""))
        editC6Ip.setText(prefs.getString("c6_ip", "10.0.0.23"))
        editGateway.setText(prefs.getString("gateway", "10.0.0.2"))
        editSubnet.setText(prefs.getString("subnet", "255.255.255.0"))
        editOtaPass.setText(prefs.getString("ota_pass", "otapass"))
        editGeyser1Ip.setText(prefs.getString("geyser1_ip", "10.0.0.24"))
        editGeyser2Ip.setText(prefs.getString("geyser2_ip", "10.0.0.25"))
        inverterIp.setText(prefs.getString("inverter_ip", "10.0.0.19"))
        editPollInterval.setText(prefs.getInt("poll_interval", 3).toString())
    }

    // Save setup config to SharedPreferences
    private fun saveToApp() {
        val prefs = getSharedPreferences("setup", MODE_PRIVATE)
        prefs.edit().apply {
            putString("ssid", editSsid.text.toString())
            putString("password", editPassword.text.toString())
            putString("c6_ip", editC6Ip.text.toString())
            putString("gateway", editGateway.text.toString())
            putString("subnet", editSubnet.text.toString())
            putString("ota_pass", editOtaPass.text.toString())
            putString("geyser1_ip", editGeyser1Ip.text.toString())
            putString("geyser2_ip", editGeyser2Ip.text.toString())
            putString("inverter_ip", inverterIp.text.toString())
            putInt("poll_interval", editPollInterval.text.toString().toIntOrNull() ?: 3)
        }.apply()

        Toast.makeText(this, "Saved locally ✔", Toast.LENGTH_SHORT).show()
    }

    // Format the command for the ESP32-C6
    private fun buildSetupCommand(): String {
        return "SETUP|WRITE|" +
                "ssid=${editSsid.text};" +
                "pwd=${editPassword.text};" +
                "ip=${editC6Ip.text};" +
                "gat=${editGateway.text};" +
                "sub=${editSubnet.text};" +
                "ota=${editOtaPass.text};" +
                "inv=${inverterIp.text};" +
                "g1=${editGeyser1Ip.text};" +
                "g2=${editGeyser2Ip.text};" +
                "poll=${editPollInterval.text}"
    }

    // Send TCP command to ESP32-C6 through port-forwarded WAN IP
    private fun sendSetupToEsp32() {

        // Your public static IP (same used in MainActivity)
        val publicIp = "154.66.153.194"       // TODO: replace with your actual WAN IP

        val command = buildSetupCommand()
        Log.d("SETUP", "Sending command: $command")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val socket = Socket(publicIp, 9000)   // << ALWAYS USE FORWARDED IP
                val out = PrintWriter(socket.getOutputStream(), true)

                out.println(command)
                out.flush()

                socket.close()

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@SetupActivity,
                        "SETUP sent to ESP32-C6 via WAN ✔",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@SetupActivity,
                        "Failed to send SETUP: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

}
