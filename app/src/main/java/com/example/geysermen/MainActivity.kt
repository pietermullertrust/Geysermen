package com.example.geysermen

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.ProgressBar
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import android.content.Intent
import android.view.MotionEvent
import android.view.View
//import java.lang.reflect.TypeVariable
//import com.example.geysermen.NewPageActivity

class MainActivity : AppCompatActivity() {

    private val masterIP = "10.0.0.23"
    private val port = 9000
    private lateinit var progressBar: ProgressBar
    private lateinit var layout: ConstraintLayout
    private var socValue: Int = 10  // The normal variable to drive the progress bar

    private lateinit var txtBATP: TextView
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
    var userChangingSwitch = true

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d("ResourceCheck", "Trying to load imageView: activity_main")
        val invisibleZone1 = findViewById<View>(R.id.invisibleZone)
        Log.d("ResourceCheck", "Trying to load imageView: invisibleZone1")

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
        Log.d("ResourceCheck", "Trying to load imageView: invisibleZone2")

        invisibleZone2.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val intent = Intent(this, NewPageActivity::class.java)
                startActivity(intent)
                true
            } else {
                false
            }
        }

        // Initialize the progressBar
        progressBar = findViewById(R.id.progress_bar_fill)
        //val progressBar: ProgressBar = findViewById(R.id.progressBar)

        updateProgressBar(socValue)
        //simulateSOCChange()


        try {

            txtBATP = findViewById(R.id.txtBATP)
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
                if (userChangingSwitch) {
                    val command = if (isChecked) "ON1" else "OFF1"
                    sendCommand(masterIP, command)
                    sendCommand(masterIP, "STATUS")  // <-- ask for fresh status
                }
            }

            switchGeyser2.setOnCheckedChangeListener { _, isChecked ->
                if (userChangingSwitch) {
                    val command = if (isChecked) "ON2" else "OFF2"
                    sendCommand(masterIP, command)
                    sendCommand(masterIP, "STATUS")  // <-- ask for fresh status
                }
            }

            startPolling()
        } catch (e: Exception) {
            Log.e("DEBUG", "Error initializing views", e)
            txtDebug.text = "⚠️ Init error: ${e.message}"
        }
    }

    // Simulate SOC value change (could be triggered by user, sensor, etc.)
    private fun simulateSOCChange() {
        // Example: Increase SOC value over time
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (socValue < 100) {
                    socValue += 10  // Increment the SOC value by 10
                    updateProgressBar(socValue)  // Update the progress bar
                    handler.postDelayed(this, 1000)  // Repeat every 1 second
                }
            }
        }, 1000)  // Start after 1 second
    }

    // Function to update progress bar based on socValue
    private fun updateProgressBar(socValue: Int) {
        progressBar.progress = socValue.coerceIn(0, 100)  // Ensures progress is always between 0 and 100
    }

    private fun updateSOC(soc: Int) {
        // Update the progress of the ProgressBar based on SOC
        //progressBar.progress = soc

        // Optional: Animate the ProgressBar smoothly
        val maxHeight = layout.minimumHeight // Maximum height based on layout height (the height of the layout container)
        val targetHeight = (maxHeight * soc) / 100

        // Animate the progress bar
        //progressBar.layoutParams.height = targetHeight
        //progressBar.requestLayout()  // Apply the new height
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
                txtBATP.text = " ${map["BAT"]}W"
                txtSOC.text = " ${map["SOC"]}%"
                txtPV.text = " ${map["PV"]} W"
                txtGrid.text = " ${map["GRID"]} W"
                txtUPS.text = " UPS:${map["UPS"]} W"
                txtLoad.text = " Load:${map["Load"]} W"
                txtTemp1.text = "1: ${map["TEMP1"]}°C"
                txtTemp2.text = "2: ${map["TEMP2"]}°C"
                //switchGeyser1.text = ": ${map["GEYSER1"]}"
                //switchGeyser2.text = ": ${map["GEYSER2"]}"

                // Update the ProgressBar with the SOC value
                //val socValue = map["SOC"]?.trim()?.toIntOrNull() ?: 0
                //val socValue = map["SOC"]?.toString()?.trim()?.toIntOrNull() ?: 0
                val socValue = map["SOC"]?.toFloatOrNull()?.toInt() ?: 0

                // Safely get the SOC value as an integer (default to 0 if it's not available)
                Log.d("SOC Value", "Received SOC value: ${map["SOC"]}")
                Log.d("SOC Value", "Received value: ${map["SOC"]}, type: ${map["SOC"]?.javaClass}")

                progressBar.progress = socValue.coerceIn(0, len)  // Ensure the value is within 0 to 100
                updateProgressBar(socValue)

                updateSwitchStates(response)



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

    fun updateSwitchStates(status: String) {
        val cleanStatus = status.replace(" ", "")
        val geyser1On = cleanStatus.contains("GEYSER1=ON", ignoreCase = true)
        val geyser2On = cleanStatus.contains("GEYSER2=ON", ignoreCase = true)

        userChangingSwitch = false  // block triggering listeners

        switchGeyser1.isChecked = geyser1On  // ✅ moves the slider bar
        switchGeyser2.isChecked = geyser2On

        userChangingSwitch = true   // re-enable listeners
    }

}
