package com.example.geysermen

import android.graphics.Typeface
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket

class SetupActivity : AppCompatActivity() {

    private lateinit var btnDiscover: Button
    private lateinit var deviceContainer: LinearLayout

    data class DeviceEntry(
        val type: String,
        val controller: String,
        val functionName: String,
        val ip: String,
        val port: Int,
        val fw: String,
        val enabled: Boolean,
        val deviceId: String = "",
        val localKey: String = ""
    )

    data class DiscoveredDevice(
        val ip: String,
        val type: String,
        val name: String,
        val port: Int,
        val fw: String,
        val raw: String,
        val deviceId: String = "",
        val localKey: String = ""
    )

    private val savedDevices = ArrayList<DeviceEntry>()
    private val newDevices = ArrayList<DiscoveredDevice>()
    private val remoteMasterIp = "154.66.153.194"
    private val useRemoteMaster = true
    private var masterIp: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        btnDiscover = findViewById(R.id.btnDiscover)

        deviceContainer = findViewById(R.id.deviceContainer)

        btnDiscover.text = "DISCOVER NEW DEVICES"

        btnDiscover.setOnClickListener {
            discoverNewDevices()
        }

        loadCurrentDevices()
    }

    private fun isEmulator(): Boolean {
        return android.os.Build.FINGERPRINT.contains("generic") ||
                android.os.Build.MODEL.contains("Emulator") ||
                android.os.Build.MODEL.contains("Android SDK")
    }

    private fun emulatorMasterDevice(): ArrayList<DiscoveredDevice> {
        val list = ArrayList<DiscoveredDevice>()

        list.add(
            DiscoveredDevice(
                ip = "10.0.0.23",
                type = "ESP32",
                name = "UNKNOWN",
                port = 9000,
                fw = "1.0",
                raw = "EMULATOR_DIRECT"
            )
        )

        return list
    }

    private fun loadCurrentDevices() {
        deviceContainer.removeAllViews()
        showText("Loading current devices...")

        Thread {
            try {

                if (useRemoteMaster) {

                    masterIp = remoteMasterIp

                    val json =
                        readDevicesFile(masterIp)

                    parseSavedDevices(json)

                    runOnUiThread {
                        renderCurrentDevices()
                    }

                    return@Thread
                }

                val discovered =
                    ArrayList<DiscoveredDevice>()

                if (isEmulator()) {
                    discovered.add(
                        DiscoveredDevice(
                            ip = "10.0.0.23",
                            type = "ESP32",
                            name = "UNKNOWN",
                            port = 9000,
                            fw = "1.0",
                            raw = "EMULATOR_DIRECT"
                        )
                    )
                } else {
                    discovered.addAll(
                        udpDiscover()
                    )
                }

                val master = discovered.firstOrNull {
                    it.type.equals("ESP32", true)
                }

                if (master == null) {
                    runOnUiThread {
                        showText("No ESP32 master found.")
                    }
                    return@Thread
                }

                masterIp = master.ip

                val json =
                    readDevicesFile(masterIp)

                parseSavedDevices(json)

                runOnUiThread {
                    renderCurrentDevices()
                }

            } catch (e: Exception) {
                runOnUiThread {
                    showText("Load failed: ${e.message}")
                }
            }
        }.start()
    }

    private fun discoverNewDevices() {

        deviceContainer.removeAllViews()

        showText(
            "Discovering new devices..."
        )

        Thread {

            try {

                val discovered =
                    ArrayList<DiscoveredDevice>()

                val esp =
                    if (isEmulator()) {
                        emulatorMasterDevice()
                    } else {
                        udpDiscover()
                    }

                discovered.addAll(
                    esp
                )

                val master =
                    esp.firstOrNull {

                        it.type.equals(
                            "ESP32",
                            true
                        )
                    }

                if (
                    master != null
                ) {

                    masterIp =
                        master.ip

                    discovered.addAll(
                        discoverTuyaDevices()
                    )
                }

                val savedKeys =
                    savedDevices.map {

                        deviceKey(
                            it.type,
                            it.ip,
                            it.port,
                            it.deviceId
                        )

                    }.toSet()

                newDevices.clear()

                for (
                d in discovered
                ) {

                    val key =
                        deviceKey(
                            d.type,
                            d.ip,
                            d.port,
                            d.deviceId
                        )

                    if (
                        !savedKeys.contains(
                            key
                        )
                    ) {

                        newDevices.add(
                            d
                        )
                    }
                }

                runOnUiThread {

                    renderNewDevices()
                }

            } catch (
                e: Exception
            ) {

                runOnUiThread {

                    showText(
                        "Discovery failed: ${e.message}"
                    )
                }
            }

        }.start()
    }

    private fun udpDiscover(): ArrayList<DiscoveredDevice> {
        val found = ArrayList<DiscoveredDevice>()
        val seenIps = HashSet<String>()

        var lock: WifiManager.MulticastLock? = null

        try {
            val wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager

            lock = wifi.createMulticastLock("geyserman_udp_lock")
            lock.setReferenceCounted(false)
            lock.acquire()

            val dhcp = wifi.dhcpInfo
            val broadcast = (dhcp.ipAddress and dhcp.netmask) or dhcp.netmask.inv()

            val broadcastBytes = byteArrayOf(
                (broadcast and 0xff).toByte(),
                (broadcast shr 8 and 0xff).toByte(),
                (broadcast shr 16 and 0xff).toByte(),
                (broadcast shr 24 and 0xff).toByte()
            )

            val broadcastAddress = InetAddress.getByAddress(broadcastBytes)

            val socket = DatagramSocket()
            socket.broadcast = true
            socket.soTimeout = 500

            val message = "GEYSERMAN_DISCOVER".toByteArray()

            val packet = DatagramPacket(
                message,
                message.size,
                broadcastAddress,
                10200
            )

            socket.send(packet)

            val buffer = ByteArray(1024)
            val start = System.currentTimeMillis()

            while (System.currentTimeMillis() - start < 3000) {
                try {
                    val responsePacket = DatagramPacket(buffer, buffer.size)
                    socket.receive(responsePacket)

                    val reply = String(
                        responsePacket.data,
                        0,
                        responsePacket.length
                    ).trim()

                    val ip = responsePacket.address.hostAddress ?: ""

                    if (reply.startsWith("GEYSERMAN_DEVICE") && !seenIps.contains(ip)) {
                        seenIps.add(ip)

                        val info = parseDiscoveryReply(reply)

                        found.add(
                            DiscoveredDevice(
                                ip = ip,
                                type = info["TYPE"] ?: "ESP",
                                name = info["NAME"] ?: "UNKNOWN",
                                port = (info["PORT"] ?: "9000").toInt(),
                                fw = info["FW"] ?: "",
                                raw = reply
                            )
                        )
                    }

                } catch (_: java.net.SocketTimeoutException) {
                    continue
                } catch (_: Exception) {
                    break
                }
            }

            socket.close()

        } finally {
            lock?.release()
        }

        return found
    }

    private fun discoverTuyaDevices(): ArrayList<DiscoveredDevice> {

        val devices = ArrayList<DiscoveredDevice>()

        if (masterIp.isEmpty())
            return devices

        try {

            val socket =
                Socket(masterIp, 9000)

            val out =
                PrintWriter(
                    socket.getOutputStream(),
                    true
                )

            val input =
                BufferedReader(
                    InputStreamReader(
                        socket.inputStream
                    )
                )

            val cmd = "GET_TUYA_DISCOVERED"

            android.util.Log.i(
                "TUYA_TX",
                "Sending=[$cmd]"
            )

            out.println(cmd)
            out.flush()

            val builder =
                StringBuilder()

            var started = false

            while (true) {

                val line =
                    input.readLine()
                        ?: break

                android.util.Log.i(
                    "TUYA_RX",
                    "[$line]"
                )

                builder.append(line)

                if (
                    line.contains("END")
                ) {
                    break
                }
            }

            socket.close()

            val imported =
                readTuyaImport()

            val importedById =
                imported.associateBy {

                    it.deviceId
                }

            val json =
                builder
                    .toString()
                    .replace(
                        "END",
                        ""
                    )
                    .trim()

            android.util.Log.i(
                "TUYA_JSON",
                json
            )

            if (
                !json.startsWith("[")
            ) {

                runOnUiThread {

                    showText(
                        "Invalid Tuya reply: $json"
                    )
                }

                return devices
            }

            val arr =
                JSONArray(
                    json
                )


            for (
            i in 0 until arr.length()
            ) {

                val o =
                    arr.getJSONObject(i)

                val id =
                    o.optString(
                        "device_id"
                    )

                val importedDev =
                    importedById[id]

                devices.add(

                    DiscoveredDevice(

                        ip =
                            o.getString("ip"),

                        type =
                            "TUYA",

                        name =
                            importedDev?.name
                                ?: o.getString("name"),

                        port =
                            o.getInt("port"),

                        fw =
                            o.getString("fw"),

                        raw =
                            o.toString(),

                        deviceId =
                            id
                    )
                )
            }

        } catch (_: Exception) {}

        return devices
    }

    private fun readTuyaImport():
            ArrayList<DiscoveredDevice> {

        val list =
            ArrayList<DiscoveredDevice>()

        try {

            val socket =
                Socket(
                    masterIp,
                    9000
                )

            val out =
                PrintWriter(
                    socket.getOutputStream(),
                    true
                )

            val input =
                BufferedReader(
                    InputStreamReader(
                        socket.inputStream
                    )
                )

            out.println(
                "READ|TUYA.JSN"
            )

            val json =
                input.readText()

            socket.close()

            val arr =
                JSONArray(
                    json
                )

            for (
            i in 0 until arr.length()
            ) {

                val o =
                    arr.getJSONObject(i)

                list.add(

                    DiscoveredDevice(

                        ip =
                            o.optString("ip"),

                        type =
                            "TUYA",

                        name =
                            o.optString("name"),

                        port =
                            6668,

                        fw =
                            o.optString("fw"),

                        raw =
                            o.toString(),

                        deviceId =
                            o.optString(
                                "device_id"
                            )
                    )
                )
            }

        } catch (
            _: Exception
        ) {}

        return list
    }


    private fun readDevicesFile(ip: String): String {
        val socket = Socket(ip, 9000)
        socket.soTimeout = 10000

        val out = PrintWriter(socket.getOutputStream(), true)
        val input = BufferedReader(InputStreamReader(socket.inputStream))

        out.println("READ|DEVICES.JSN")

        val builder = StringBuilder()

        while (true) {
            val line = input.readLine() ?: break

            if (line == "END") {
                break
            }

            builder.append(line)
            builder.append("\n")
        }

        socket.close()

        return builder.toString().trim()
    }

    private fun parseSavedDevices(jsonText: String) {
        savedDevices.clear()

        if (jsonText.isEmpty() || jsonText.startsWith("ERR|")) {
            return
        }

        val root = JSONObject(jsonText)
        val arr = root.optJSONArray("devices") ?: JSONArray()

        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)

            savedDevices.add(
                DeviceEntry(
                    type = o.optString("type"),
                    controller = o.optString("controller"),
                    functionName = o.optString("function_name"),
                    ip = o.optString("ip"),
                    port = o.optInt("port"),
                    fw = o.optString("fw"),
                    enabled = o.optBoolean("enabled", true),
                    deviceId = o.optString("device_id"),
                    localKey = o.optString("local_key")
                )
            )
        }
    }

    private fun renderCurrentDevices() {
        deviceContainer.removeAllViews()

        val title = TextView(this)
        title.text = "CURRENT DEVICES"
        title.textSize = 20f
        title.setTypeface(null, Typeface.BOLD)
        deviceContainer.addView(title)

        if (savedDevices.isEmpty()) {
            showText("No saved devices found. Press DISCOVER NEW DEVICES.")
            return
        }

        for ((index, d) in savedDevices.withIndex()) {

            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.setPadding(0, 16, 0, 16)

            val left = LinearLayout(this)
            left.orientation = LinearLayout.VERTICAL

            var text =
                "${d.type}\n" +
                        "Controller: ${d.controller}\n" +
                        "Function: ${d.functionName}\n" +
                        "IP: ${d.ip}:${d.port}\n"

            if (d.type.equals("TUYA", true)) {
                text += "Device ID: ${d.deviceId}\n"
            }

            text += "Firmware: ${d.fw}\n"

            val tv = TextView(this)
            tv.text = text
            tv.textSize = 16f

            left.addView(tv)

            val actions = LinearLayout(this)
            actions.orientation = LinearLayout.VERTICAL
            actions.gravity = android.view.Gravity.TOP

            val edit = ImageButton(this)
            edit.setImageResource(android.R.drawable.ic_menu_edit)
            edit.background = null
            edit.setOnClickListener {
                editSingleDevice(index)
            }

            val delete = ImageButton(this)
            delete.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            delete.background = null
            delete.setOnClickListener {
                savedDevices.removeAt(index)

                val root = buildDevicesJson()

                if (masterIp.isNotEmpty()) {
                    MasterSync.syncToMaster(
                        this,
                        root.toString(),
                        masterIp
                    )
                }

                renderCurrentDevices()
            }

            actions.addView(edit)
            actions.addView(delete)

            row.addView(
                left,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )

            row.addView(
                actions,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            deviceContainer.addView(row)
        }
    }

    private fun editSingleDevice(index: Int) {

        deviceContainer.removeAllViews()

        val d = savedDevices[index]

        val title = TextView(this)
        title.text = "EDIT DEVICE"
        title.textSize = 20f
        title.setTypeface(null, Typeface.BOLD)
        deviceContainer.addView(title)

        val label = TextView(this)
        label.text =
            "${d.type}\n" +
                    "IP: ${d.ip}:${d.port}\n" +
                    if (d.type.equals("TUYA", true))
                        "Device ID: ${d.deviceId}\n"
                    else
                        ""

        deviceContainer.addView(label)

        val editName = EditText(this)
        editName.setText(d.functionName)
        deviceContainer.addView(editName)

        val save = Button(this)
        save.text = "SAVE"

        save.setOnClickListener {
            savedDevices[index] =
                savedDevices[index].copy(
                    functionName = editName.text.toString().trim()
                )

            val root = buildDevicesJson()

            if (masterIp.isNotEmpty()) {
                MasterSync.syncToMaster(
                    this,
                    root.toString(),
                    masterIp
                )
            }

            renderCurrentDevices()
        }

        deviceContainer.addView(save)

        val cancel = Button(this)
        cancel.text = "CANCEL"
        cancel.setOnClickListener {
            renderCurrentDevices()
        }

        deviceContainer.addView(cancel)
    }

    private fun renderNewDevices() {
        deviceContainer.removeAllViews()

        val title = TextView(this)
        title.text = "NEW DEVICES FOUND"
        title.textSize = 20f
        title.setTypeface(null, Typeface.BOLD)
        deviceContainer.addView(title)

        if (newDevices.isEmpty()) {
            showText("No new devices found.")
            return
        }

        for ((index, d) in newDevices.withIndex()) {
            addNewDeviceCard(index, d)
        }

        val saveButton = Button(this)
        saveButton.text = "SAVE NEW DEVICES"
        saveButton.setOnClickListener {
            saveNewDevices()
        }

        deviceContainer.addView(saveButton)
    }

    private fun addNewDeviceCard(index: Int, d: DiscoveredDevice) {

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0, 0, 0, 0)

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(12, 20, 12, 20)

        val title = TextView(this)
        title.text = "${d.type}  ${d.ip}:${d.port}"
        title.textSize = 18f
        title.setTypeface(null, Typeface.BOLD)
        box.addView(title)

        val role = if (d.type.equals("ESP32", true)) "master" else "slave"

        val details = TextView(this)
        details.text =
            "Name: ${d.name}\n" +
                    "Controller: $role\n" +
                    "Device ID: ${d.deviceId}\n" +
                    "IP: ${d.ip}:${d.port}\n" +
                    "Firmware: ${d.fw}"
        box.addView(details)

        val functionName = EditText(this)
        functionName.id = 5000 + index

        if (d.type.equals("ESP32", true)) {
            functionName.setText("Main Controller")
        } else {
            functionName.hint = "Enter function name e.g. Main House Geyser"
        }

        box.addView(functionName)

        val check = CheckBox(this)
        check.id = 9000 + index
        check.isChecked = false

        row.addView(
            box,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        row.addView(check)

        deviceContainer.addView(row)
    }

    private fun saveNewDevices() {
        if (masterIp.isEmpty()) {
            showText("No master ESP32 found.")
            return
        }

        var savedCount = 0

        for ((index, d) in newDevices.withIndex()) {

            val check = findViewById<CheckBox>(9000 + index)

            if (!check.isChecked) {
                continue
            }

            val functionText = findViewById<EditText>(5000 + index)
            val functionName = functionText.text.toString().trim()

            if (functionName.isEmpty()) {
                showText("Enter function name for ${d.ip}")
                return
            }

            val controller =
                if (d.type.equals("ESP32", true)) "master" else "slave"

            savedDevices.add(
                DeviceEntry(
                    type = d.type,
                    controller = controller,
                    functionName = functionName,
                    ip = d.ip,
                    port = d.port,
                    fw = d.fw,
                    enabled = true,
                    deviceId = d.deviceId,
                    localKey = d.localKey
                )
            )

            savedCount++
        }

        if (savedCount == 0) {
            showText("Tick at least one device to save.")
            return
        }

        val root = buildDevicesJson()

        openFileOutput("DEVICES.JSN", MODE_PRIVATE).use {
            it.write(root.toString(4).toByteArray())
        }

        MasterSync.syncToMaster(
            this,
            root.toString(),
            masterIp
        )

        showText("Saved $savedCount device(s) and uploaded to master.")
    }

    private fun buildDevicesJson(): JSONObject {
        val arr = JSONArray()

        for (d in savedDevices) {
            val o = JSONObject()

            o.put("type", d.type)
            o.put("controller", d.controller)
            o.put("function_name", d.functionName)
            o.put("ip", d.ip)
            o.put("port", d.port)
            o.put("fw", d.fw)
            o.put("enabled", d.enabled)
            o.put("device_id", d.deviceId)
            o.put("local_key", d.localKey)

            arr.put(o)
        }

        val root = JSONObject()
        root.put("system", "GeyserMan")
        root.put("version", 1)
        root.put("devices", arr)

        return root
    }

    private fun renderEditSavedDevices() {

        deviceContainer.removeAllViews()

        val title = TextView(this)

        title.text = "EDIT DEVICES"

        title.textSize = 20f

        deviceContainer.addView(title)

        for ((index, d) in savedDevices.withIndex()) {

            val box = LinearLayout(this)

            box.orientation =
                LinearLayout.VERTICAL

            val label =
                TextView(this)

            label.text =
                d.type

            box.addView(label)

            val edit =
                EditText(this)

            edit.id =
                8000 + index

            edit.setText(
                d.functionName
            )

            box.addView(edit)

            deviceContainer.addView(box)
        }

        val save =
            Button(this)

        save.text =
            "SAVE CHANGES"

        save.setOnClickListener {

            saveEditedDevices()
        }

        deviceContainer.addView(save)
    }

    private fun saveEditedDevices() {

        for (
        i in savedDevices.indices
        ) {

            val edit =
                findViewById<EditText>(
                    8000 + i
                )

            savedDevices[i] =
                savedDevices[i].copy(

                    functionName =
                        edit.text
                            .toString()
                )
        }

        val root =
            buildDevicesJson()

        if (
            masterIp.isNotEmpty()
        ) {

            MasterSync.syncToMaster(
                this,
                root.toString(),
                masterIp
            )
        }

        renderCurrentDevices()
    }

    private fun parseDiscoveryReply(reply: String): Map<String, String> {
        val map = HashMap<String, String>()

        val parts = reply.split("|")

        for (p in parts) {
            if (p.contains("=")) {
                val kv = p.split("=", limit = 2)
                map[kv[0]] = kv[1]
            }
        }

        return map
    }

    private fun deviceKey(
        type: String,
        ip: String,
        port: Int,
        deviceId: String = ""
    ): String {

        return if (type.equals("TUYA", true)) {
            "TUYA|$deviceId"
        } else {
            "${type.uppercase()}|$ip|$port"
        }
    }

    private fun showText(msg: String) {
        deviceContainer.removeAllViews()

        val tv = TextView(this)
        tv.text = msg
        tv.textSize = 16f
        tv.setPadding(0, 16, 0, 16)

        deviceContainer.addView(tv)
    }
}