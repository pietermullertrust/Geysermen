package com.example.geysermen

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import android.content.Intent
import android.view.MotionEvent
import android.view.View

class MainActivity : AppCompatActivity() {

    private val masterIP = "10.0.0.23"
    private val port = 9000

    private lateinit var txtSOC: TextView
    private lateinit var txtPV: TextView
    private lateinit var txtGrid: TextView
    private lateinit var txtLoad: TextView
    private lateinit var txtUPS: TextView
    private lateinit var txtTemp1: TextView
    private lateinit var txtTemp2: TextView
    private lateinit var txtDebug: TextView

    private lateinit var switchGeyser1: SwitchCompat
    private lateinit var switchGeyser2: SwitchCompat

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Touch zone to new page
        val invisibleZone1 = findViewById<View>(R.id.invisibleZone)
        invisibleZone1.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val intent = Intent(this, NewPageActivity::class.java)
                startActivity(intent)
                true
            } else {
                false
            }
        }

        val invisibleZone2 = findViewById<View>(R.id.invisibleZone1)
        invisibleZone2.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val intent = Intent(this, NewPageActivity::class.java)
                startActivity(intent)
                true
            } else {
                false
            }
        }



        try {
            txtSOC = findViewById(R.id.txtSOC)
            txtPV = findViewById(R.id.txtPV)
            txtGrid = findViewById(R.id.txtGrid)
            txtLoad = findViewById(R.id.txtLoad)
            txtUPS = findViewById(R.id.txtUPS)
            txtTemp1 = findViewById(R.id.txtTemp1)
            txtTemp2 = findViewById(R.id.txtTemp2)
            txtDebug = findViewById(R.id.txtDebug)

            switchGeyser1 = findViewById(R.id.switchGeyser1)
            switchGeyser2 = findViewById(R.id.switchGeyser2)

            switchGeyser1.setOnCheckedChangeListener { _, isChecked ->
                val command = if (isChecked) "ON1" else "OFF1"
                sendCommand(masterIP, command)
            }

            switchGeyser2.setOnCheckedChangeListener { _, isChecked ->
                val command = if (isChecked) "ON2" else "OFF2"
                sendCommand(masterIP, command)
            }

            startPolling()
        } catch (e: Exception) {
            Log.e("DEBUG", "Error initializing views", e)
            txtDebug.text = "⚠️ Init error: ${e.message}"
        }
    }

    private fun startPolling() {
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed(object : Runnable {
            override fun run() {
                Thread { fetchStatus() }.start()
                handler.postDelayed(this, 5000)
            }
        }, 1000)
    }

    @SuppressLint("SetTextI18n")
    private fun fetchStatus() {
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(masterIP, port), 2000)
            val os = socket.getOutputStream()
            os.write("STATUS".toByteArray())
            os.flush()

            val input: InputStream = socket.getInputStream()
            val buffer = ByteArray(256)
            val len = input.read(buffer)
            val response = String(buffer, 0, len)

            runOnUiThread {
                txtDebug.text = "📥 STATUS: $response"
            }

            val map = response.split(",").associate {
                val parts = it.split("=")
                parts[0].trim() to parts.getOrElse(1) { "?" }.trim()
            }

            runOnUiThread {
                txtSOC.text = " ${map["SOC"]}%"
                txtPV.text = " ${map["PV"]} W"
                txtGrid.text = " ${map["GRID"]} W"
                txtUPS.text = " UPS:${map["UPS"]} W"
                txtLoad.text = " Load:${map["Load"]} W"
                txtTemp1.text = "1: ${map["TEMP1"]}°C"
                txtTemp2.text = "2: ${map["TEMP2"]}°C"
            }

            socket.close()

        } catch (e: Exception) {
            runOnUiThread {
                txtDebug.text = "⚠️ STATUS fetch failed: ${e.message}"
            }
        }
    }

    private fun sendCommand(ip: String, command: String) {
        Thread {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(ip, port), 2000)
                Thread.sleep(100)
                val os: OutputStream = socket.getOutputStream()
                os.write(command.toByteArray())
                os.flush()
                socket.close()

                runOnUiThread {
                    txtDebug.text = "📤 Sent: $command → $ip"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    txtDebug.text = "❌ Send error: ${e.message}"
                }
            }
        }.start()
    }
}
