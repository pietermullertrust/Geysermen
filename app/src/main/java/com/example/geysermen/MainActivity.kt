package com.example.geysermen

import android.Manifest
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.widget.Toolbar
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.io.FileWriter
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.io.File   // ✅ for LOG.TXT cache handling
import java.text.SimpleDateFormat
import java.util.*
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class MainActivity : AppCompatActivity() {

    // --- Discovery config (match your ESP firmware) ---
    private val discoveryPort = 10200
    private val discoveryMagic = "DISCOVER_ESP"
    private val desiredDeviceId: String? = "GEYSERMAN-MASTER" // set to null to accept first device

    // --- Your TCP/Alert service port on the ESP ---
    private val port = 9000
    private val port2 = 9001
    private val alertPort = 9002

    @Volatile private var alertListenerRunning = false
    private val REQ_POST_NOTIFICATIONS = 1001

    // Will be updated by discovery / prefs
    private var masterIP: String = "154.66.153.194"

    private lateinit var layout: ConstraintLayout

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
    private lateinit var imgBatSoc: ImageView

    var userChangingSwitch = true

    data class EspDevice(
        val ip: String,
        val port: Int,
        val model: String,
        val deviceId: String?,
        val role: String?
    )

    // --- LOG CACHE -----------------------------------------------------
    private fun loadCachedLog(): List<String> {
        val cacheFile = File(filesDir, "LOG.TXT")
        return if (cacheFile.exists()) cacheFile.readLines().filter { it.isNotBlank() && it.split(",").size >= 6 } else emptyList()
    }

    // --- LOG CACHE ---------------------------------------------------------
    private fun getLastCachedTimestamp(): String {
        val cacheFile = File(filesDir, "LOG.TXT")
        if (!cacheFile.exists()) {
            Log.d("Timestamp", "Cache file not found")
            return ""
        }

        var lastLine: String? = null

        cacheFile.bufferedReader().useLines { seq ->
            seq.forEach { line ->
                val t = line.trim()
                if (t.isNotEmpty()) lastLine = t
            }
        }

        val line = lastLine ?: run {
            Log.d("Timestamp", "No last line found")
            return ""
        }

        Log.d("Timestamp", "Last line: $line")

        val timestamp = line.substringBefore(',').trim()
        if (timestamp.isEmpty()) {
            Log.d("Timestamp", "No timestamp found in $line")
            return ""
        }

        return timestamp
    }

    private fun startWatchdogService() {
        val intent = Intent(this, WatchdogService::class.java).apply {
            putExtra("MASTER_IP", masterIP)
        }

        Log.d("MainActivity", "Starting WatchdogService with MASTER_IP=$masterIP")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun startAlertListener() {
        if (alertListenerRunning) return
        alertListenerRunning = true

        Thread {
            while (alertListenerRunning) {
                try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(masterIP, alertPort), 5000)

                    val reader = socket.getInputStream().bufferedReader()

                    while (alertListenerRunning && !socket.isClosed) {
                        val line = reader.readLine() ?: break
                        handleWatchdogMessage(line)
                    }

                    socket.close()
                } catch (e: Exception) {
                    runOnUiThread {
                        txtDebug.text = "ALERT listener error: ${e.message}"
                    }
                    Thread.sleep(3000)
                }
            }
        }.start()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "watchdog_alerts",
                "Watchdog Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts from ESP32 watchdog"
            }

            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun showNotification(title: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                txtDebug.text = "Notification permission not granted"
                return
            }
        }

        val builder = NotificationCompat.Builder(this, "watchdog_alerts")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        NotificationManagerCompat.from(this)
            .notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQ_POST_NOTIFICATIONS
                )
            }
        }
    }

    private fun handleWatchdogMessage(line: String) {
        val p = line.split("|")
        if (p.size < 6) return

        val type = p[0]      // ALERT / RECOVERY
        val source = p[1]    // WATCHDOG
        val device = p[2]
        val ip = p[3]
        val event = p[4]
        val count = p[5]

        val title = if (type == "ALERT") "Watchdog Alert" else "Watchdog Recovery"
        val body = "$device ($ip) - $event"

        runOnUiThread {
            txtDebug.text = line
        }

        showNotification(title, body)
    }

    private fun appendNewLogData(newLines: List<String>) {
        val cacheFile = File(filesDir,"LOG.TXT")
        if (newLines.isNotEmpty()) {
            val validLines = newLines.filter { it.isNotBlank() && it.split(",").size >= 6 }
            if (validLines.isNotEmpty()) {
                if (!cacheFile.exists()) cacheFile.createNewFile()
                val orderedLines = validLines.sortedBy { it.split(",")[0] } // Sort by timestamp
                cacheFile.appendText(orderedLines.joinToString("\n") + "\n")
                Log.d("AppendLog", "Appended: $orderedLines")
            }
        }
    }

    private fun clearCache() {
        val cacheFile = File(filesDir, "LOG.TXT")
        if (cacheFile.exists()) cacheFile.delete()
    }

    // -------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        createNotificationChannel()
        requestNotificationPermissionIfNeeded()

        val toolbar = findViewById<Toolbar>(R.id.mainToolbar)
        setSupportActionBar(toolbar)
        // Read ESP12F STATUS
        //requestStatusFromEsp()

        // Initialize views & listeners
        initViewsAndListeners()

        //Create LOG.TXT
        createLogFileIfNeeded()

        createNotificationChannel()
        //startAlertListener()
        startWatchdogService()


        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }

       // ✅ Example: load cached log & get last timestamp
        val cachedLines = loadCachedLog()
        val lastTimestamp = getLastCachedTimestamp()
        Log.d("CACHE", "Loaded ${cachedLines.size} cached log lines, last timestamp: $lastTimestamp")

        // Try auto-discovery first; falls back to last-known IP; then to the hardcoded default
        Thread {
            autoDiscover()  // sets masterIP and stores it
            // Optionally do an immediate GET_PV_LOG once we know the IP
            fetchPvLogOnce()
           //sendCommand(masterIP, "GET_PV_LOG FROM=$lastTimestamp")
            // Start periodic STATUS polling after discovery
            runOnUiThread { startPolling() }
        }.start()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        println("DEBUG: onCreateOptionsMenu CALLED")
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_setup -> {
                startActivity(Intent(this, SetupActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ----------------- Discovery -----------------

    //private fun autoDiscover() {
    //    val prefs = getSharedPreferences("geysermen", MODE_PRIVATE)
    //    val lastIp = prefs.getString("last_master_ip", null)

        // 1) UDP broadcast (fast, ~1–1.5s window)
    //    val found = discover(windowMs = 1500L)

    //    val chosen = when {
    //        desiredDeviceId != null ->
    //            found.firstOrNull { it.deviceId?.equals(desiredDeviceId, ignoreCase = true) == true }
    //        else ->
    //            found.firstOrNull()
    //    }

    //    masterIP = when {
    //        chosen != null -> {
    //            val ip = chosen.ip
    //            prefs.edit().putString("last_master_ip", ip).apply()
    //            postDebug("✅ Discovered: ${chosen.deviceId ?: chosen.model} @ $ip:${chosen.port}")
    //            ip
    //        }
    //        lastIp != null -> {
    //            postDebug("ℹ️ Using last-known IP: $lastIp")
    //            lastIp
    //        }
    //        else -> {
    //            postDebug("⚠️ Discovery failed — using default $masterIP")
    //            masterIP // keep default
    //        }
    //    }
    //}

    private fun autoDiscover() {
        val prefs = getSharedPreferences("geysermen", MODE_PRIVATE)

        // Your fixed static public IP (never changes)
        val staticIp = "154.66.153.194"

        // Check if you previously saved an IP (optional)
        val lastIp = prefs.getString("last_master_ip", staticIp)

        // Always use static IP as the master endpoint
        masterIP = staticIp

        // Save it to preferences (keeps code behaviour clean)
        prefs.edit().putString("last_master_ip", staticIp).apply()

        postDebug("🌍 Using static Internet IP: $staticIp (Port forwarded to ESP32)")
    }

    private fun discover(windowMs: Long = 1200L): List<EspDevice> {
        val found = mutableListOf<EspDevice>()
        var socket: DatagramSocket? = null

        try {
            socket = DatagramSocket(null).apply {
                reuseAddress = true
                soTimeout = 250
                bind(InetSocketAddress(0))
            }

            val broadcast = InetAddress.getByName("255.255.255.255")
            val out = DatagramPacket(discoveryMagic.toByteArray(), discoveryMagic.length, broadcast, discoveryPort)
            socket.send(out)

            val start = System.currentTimeMillis()
            val buf = ByteArray(512)

            while (System.currentTimeMillis() - start < windowMs) {
                val inPkt = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(inPkt)
                    val s = String(inPkt.data, 0, inPkt.length).trim()
                    parseReply(s)?.let { dev ->
                        val ip = if (dev.ip.isBlank()) inPkt.address.hostAddress else dev.ip
                        found.add(dev.copy(ip = ip))
                    }
                } catch (_: SocketTimeoutException) {
                    // keep listening until window expires
                }
            }
        } catch (e: Exception) {
            postDebug("❌ Discovery error: ${e.message}")
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }

        return found
    }

    private fun parseReply(s: String): EspDevice? {
        // Accept either:
        //  A) "ESP32,device_id=GEYSERMAN-MASTER,ip=10.0.0.23,port=9000"
        //  B) "ESP32,10.0.0.23,9000"
        val parts = s.split(',').map { it.trim() }
        if (parts.isEmpty()) return null

        val model = parts.firstOrNull()?.uppercase() ?: "ESP32"
        var ip = ""
        var portLocal = port // default to your TCP port
        var deviceId: String? = null
        var role: String? = null

        for (p in parts.drop(1)) {
            when {
                p.startsWith("ip=") -> ip = p.substringAfter("ip=").trim()
                p.startsWith("port=") -> portLocal = p.substringAfter("port=").trim().toIntOrNull() ?: portLocal
                p.startsWith("device_id=") -> deviceId = p.substringAfter("device_id=").trim()
                p.startsWith("role=") -> role = p.substringAfter("role=").trim()
                // minimal CSV fallback
                ip.isBlank() && Regex("""^\d{1,3}(\.\d{1,3}){3}$""").matches(p) -> ip = p
                p.toIntOrNull() != null -> portLocal = p.toInt()
            }
        }

        return if (ip.isNotBlank()) EspDevice(ip, portLocal, model, deviceId, role) else null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initViewsAndListeners() {
        findViewById<View>(R.id.invisibleZone).setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                startActivity(Intent(this, NewPageActivity::class.java)); true
            } else false
        }

        findViewById<View>(R.id.invisibleZone1).setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                startActivity(Intent(this, NewPageActivity1::class.java)); true
            } else false
        }

        findViewById<View>(R.id.invisibleZone2).setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                startActivity(Intent(this, NewPageActivityBattery::class.java)); true
            } else false
        }

        findViewById<View>(R.id.invisibleZone3).setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                startActivity(Intent(this, NewPageActivityGeyser::class.java)); true
            } else false
        }

        findViewById<View>(R.id.invisibleZone4).setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                startActivity(Intent(this, NewPageActivityInverter::class.java)); true
            } else false
        }

        txtBATP = findViewById(R.id.txtBATP)
        txtSOC = findViewById(R.id.txtSOC)
        txtPV = findViewById(R.id.txtPV)
        txtGrid = findViewById(R.id.txtGrid)
        txtLoad = findViewById(R.id.txtLoad)
        txtUPS = findViewById(R.id.txtUPS)
        txtTemp1 = findViewById(R.id.txtTemp1)
        txtTemp2 = findViewById(R.id.txtTemp2)
        txtDebug = findViewById(R.id.txtDebug)
        imgBatSoc = findViewById(R.id.imgBatSoc)

        switchGeyser1 = findViewById(R.id.switchGeyser1)
        switchGeyser2 = findViewById(R.id.switchGeyser2)

        switchGeyser1.setOnCheckedChangeListener { _, isChecked ->
            if (userChangingSwitch) {
                sendCommand(if (isChecked) "ON1" else "OFF1")
                sendCommand("STATUS")
            }
        }

        switchGeyser2.setOnClickListener {
            val cmd = if (switchGeyser2.isChecked) "TUYA_ON" else "TUYA_OFF"

            Log.d("TUYA_SWITCH", "Sending: $cmd")
            sendCommand(cmd)
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
            os.write("STATUS\n".toByteArray())
            os.flush()

            val input = socket.getInputStream()
            val response = input.bufferedReader().readLine()?.trim() ?: ""

            if (response.isEmpty()) {
                runOnUiThread {
                    txtDebug.text = "⚠️ Empty STATUS response"
                }
                socket.close()
                return
            }

            // Ignore log messages here
            if (response.startsWith("LOG|")) {
                val logLines = response.split("\n")
                    .map { it.removePrefix("LOG|").trim() }
                    .filter { it.matches(Regex("""\d{4}-\d{2}-\d{2}.*""")) }

                appendNewLogData(logLines)

                runOnUiThread {
                    txtDebug.text = "📥 LOG update received: ${logLines.size} lines"
                }

                socket.close()
                return
            }

            // Ignore PV log messages here
            if (response.startsWith("PV_LOG|")) {
                runOnUiThread {
                    txtDebug.text = "PV_LOG received — ignored in MainActivity."
                }
                socket.close()
                return
            }

            // Expected format:
            // STATUS|ON1|OFF2|BAT=2955,SOC=97,PV=1540,...
            val cleanResponse = if (response.startsWith("STATUS|")) {
                val parts = response.split('|')
                if (parts.size >= 4) {
                    parts.drop(3).joinToString("|")
                } else {
                    ""
                }
            } else {
                response
            }

            val map = cleanResponse.split(",")
                .mapNotNull { item ->
                    val parts = item.split("=", limit = 2)
                    if (parts.size == 2) {
                        parts[0].trim().uppercase() to parts[1].trim()
                    } else {
                        null
                    }
                }
                .toMap()

            runOnUiThread {
                txtDebug.text = "📥 STATUS: $response"

                txtBATP.text = " ${map["BAT"] ?: "--"}W"
                txtSOC.text = " ${map["SOC"] ?: "--"}%"
                txtPV.text = " ${map["PV"] ?: "--"} W"
                txtGrid.text = " ${map["GRID"] ?: "--"} W"
                txtUPS.text = " UPS:${map["UPS"] ?: "--"} W"
                txtLoad.text = " Load:${map["LOAD"] ?: "--"} W"
                txtTemp1.text = "1: ${map["TEMP1"] ?: "--"}°C"
                txtTemp2.text = "2: ${map["TEMP2"] ?: "--"}°C"

                val soc = map["SOC"]?.toFloatOrNull() ?: -100f
                val batLevel = when {
                    soc >= 100f -> 5
                    soc >= 95f -> 4
                    soc >= 75f -> 3
                    soc >= 50f -> 2
                    soc >= 25f -> 1
                    soc >= 0f -> 0
                    else -> 0
                }

                val resId = when (batLevel) {
                    0 -> R.drawable.bat_00
                    1 -> R.drawable.bat_01
                    2 -> R.drawable.bat_02
                    3 -> R.drawable.bat_03
                    4 -> R.drawable.bat_04
                    5 -> R.drawable.bat_05
                    else -> R.drawable.bat_00
                }

                imgBatSoc.setImageResource(resId)

                updateSwitchStates(response)

                map["RSSI"]?.toIntOrNull()?.let { rssi ->
                    val wifiImage = when {
                        rssi > -60 -> R.drawable.wifi_3
                        rssi > -70 -> R.drawable.wifi_2
                        rssi > -80 -> R.drawable.wifi_1
                        rssi > -90 -> R.drawable.wifi_0
                        else -> R.drawable.wifi_0
                    }
                    findViewById<ImageView>(R.id.imageViewWifi).setImageResource(wifiImage)
                }
            }

            socket.close()

        } catch (e: Exception) {
            runOnUiThread {
                txtDebug.text = "⚠️ STATUS fetch failed: ${e.message} (IP=$masterIP)"
            }
        }
    }


    /** Create LOG.TXT if missing, then fetch logs from ESP. */
    private fun createLogFileIfNeeded() {
        Thread {
            try {
                val logFile = File(filesDir, "LOG.TXT")

                if (!logFile.exists()) {
                    logFile.createNewFile()
                    Log.i("GeyserMen", "✅ LOG.TXT created at ${logFile.absolutePath}")

                    // 🟢 Write a dummy first log line dated 4 months ago
                    val cal = Calendar.getInstance()
                    cal.add(Calendar.MONTH, -4)
                    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    val oldDate = fmt.format(cal.time)
                    val dummyLine = "$oldDate,PV=0,GRID=0,BAT=0,SOC=0,TEMP=0.0C"
                    logFile.writeText("$dummyLine\n")
                    Log.i("GeyserMen", "📄 Dummy first log line added: $dummyLine")
                }


                // ✅ Simply call your existing fetch function
                Log.i("GeyserMen", "📡 Syncing ESP log data via fetchPvLogOnce()...")
                fetchPvLogOnce()

            } catch (e: Exception) {
                Log.e("GeyserMen", "❌ createLogFileIfNeeded() failed: ${e.message}", e)
            }
        }.start()
    }

    @SuppressLint("NewApi")
    private fun fetchPvLogOnce() {
        //Thread {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(masterIP, port), 3000)

                val writer = PrintWriter(socket.getOutputStream(), true)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                // Get the last timestamp in phone’s LOG.TXT (if any)
                val lastTs = getLastCachedTimestamp()

                // Build request: if we have a timestamp → ask for new data only
                val cmd = if (lastTs != null) {
                    "GET_PV_LOG FROM=$lastTs"
                } else {
                    // first time or empty file: just pull today's data
                    val today = try {
                        java.time.LocalDate.now().toString()
                    } catch (_: Throwable) {
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd")
                        sdf.format(java.util.Date())
                    }
                    "GET_PV_LOG DATE=$today LAST=500"
                }

                writer.println(cmd)
                Log.d("FETCH_LOG", "📤 Request: $cmd")

                val newLines = mutableListOf<String>()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val trimmed = line!!.trim()
                    if (trimmed.startsWith("PV_LOG|") || trimmed.startsWith("LOG|")) {
                        val raw = trimmed.removePrefix("PV_LOG|").removePrefix("LOG|").trim()
                        if (raw.matches(Regex("""\d{4}-\d{2}-\d{2}.*"""))) {
                            newLines.add(raw)
                        }
                    }
                }

                if (newLines.isNotEmpty()) {
                    appendNewLogData(newLines)
                    Log.d("FETCH_LOG", "✅ Appended ${newLines.size} new lines.")
                    runOnUiThread { txtDebug.text = "📥 Synced ${newLines.size} new lines" }
                } else {
                    runOnUiThread { txtDebug.text = "ℹ️ No new data found" }
                }

                socket.close()

            } catch (e: Exception) {
                postDebug("⚠️ GET_PV_LOG error: ${e.message}")
            }
        //}.start()
    }

    private fun sendCommand(command: String) {
        Thread {
            try {
                val cmd = if (command.endsWith("\n")) command else "$command\n"

                Log.d("TCP_SEND", "Sending command: $cmd to $masterIP:$port")

                val socket = Socket()
                socket.connect(InetSocketAddress(masterIP, port), 2000)

                val os: OutputStream = socket.getOutputStream()
                os.write(cmd.toByteArray())
                os.flush()

                socket.close()

                runOnUiThread {
                    txtDebug.text = "📤 Sent: ${cmd.trim()} → $masterIP"
                }

            } catch (e: Exception) {
                Log.e("TCP_SEND", "Send error", e)

                runOnUiThread {
                    txtDebug.text = "❌ Send error: ${e.message} (IP=$masterIP)"
                }
            }
        }.start()
    }

    private fun updateSwitchStates(status: String) {

        val parts = status.trim().uppercase().split('|')

        val geyser1On = parts.contains("ON1")
        val geyser2On = parts.contains("ON2")

        Log.d("SWITCH", "PARTS=$parts")
        Log.d("SWITCH", "GEYSER1=$geyser1On, GEYSER2=$geyser2On")

        userChangingSwitch = false

        switchGeyser1.isChecked = geyser1On
        switchGeyser2.isChecked = geyser2On

        userChangingSwitch = true
    }

    // Convenience to safely post to the debug TextView from background threads
    private fun postDebug(msg: String) {
        runOnUiThread { findViewById<TextView>(R.id.txtDebug).text = msg }
        Log.d("MainActivity", msg)
    }
}
