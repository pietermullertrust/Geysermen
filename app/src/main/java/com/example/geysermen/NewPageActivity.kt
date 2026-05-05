package com.example.geysermen

import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.io.File
import android.view.View
import android.widget.ProgressBar

class NewPageActivity : AppCompatActivity() {

    private lateinit var pvChart: LineChart
    private lateinit var txtDate: TextView
    private lateinit var selectedDate: String
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_page)

        pvChart = findViewById(R.id.pvLineChart)
        txtDate = findViewById(R.id.txtChartDate)
        progressBar = findViewById(R.id.progressBar)

        val calendar = Calendar.getInstance()
        // accept optional date from caller to keep pages in sync
        selectedDate = intent.getStringExtra("extra_date")
            ?: dateFormat.format(calendar.time)
        txtDate.text = "Date: $selectedDate"

        txtDate.setOnClickListener {
            val y = calendar.get(Calendar.YEAR)
            val m = calendar.get(Calendar.MONTH)
            val d = calendar.get(Calendar.DAY_OF_MONTH)
            DatePickerDialog(this, { _, year, month, day ->
                calendar.set(year, month, day)
                selectedDate = dateFormat.format(calendar.time)
                txtDate.text = "Date: $selectedDate"
                requestPvLogFromCache()
            }, y, m, d).show()
        }

        requestPvLogFromCache()
    }
    private fun convertToSigned16Bit(raw: Float): Float {
        val intVal = raw.toInt()
        return if (intVal > 32767) (intVal - 65536).toFloat() else raw
    }

    private fun requestPvLogFromEsp() {
        progressBar.visibility = View.VISIBLE
        Thread {
            val lines = mutableListOf<String>()
            try {
                Log.d("PV_LOG", "⚡ Connecting to ESP32...")
                val addr = java.net.InetSocketAddress("10.0.0.23", 9000)
                java.net.Socket().use { socket ->
                    socket.connect(addr, 4000)
                    socket.tcpNoDelay = true
                    socket.soTimeout = 60000  // read timeout

                    val out = java.io.PrintWriter(
                        java.io.BufferedWriter(java.io.OutputStreamWriter(socket.getOutputStream())),
                        true
                    )
                    val reader = java.io.BufferedReader(java.io.InputStreamReader(socket.getInputStream()))

                    Log.d("PV_LOG", "📤 Sending GET_PV_LOG")
                    out.print("GET_PV_LOG DATE=$selectedDate\n")
                    out.flush()
                    socket.shutdownOutput()

                    var count = 0
                    while (true) {
                        val raw = reader.readLine() ?: break    // EOF
                        if (raw.isBlank()) continue
                        if (raw == "PV_LOG|BEGIN") continue
                        if (raw == "PV_LOG|END") break

                        val payload = if (raw.startsWith("PV_LOG|")) raw.substring(7) else raw
                        lines.add(payload.trim())
                        count++
                        if (count == 1) Log.d("PV_LOG", "📥 first line: $payload")
                        if (count % 100 == 0) Log.d("PV_LOG", "📥 $count lines…")
                        if (count >= 5000) break // safety: don’t read forever
                    }
                }

                Log.d("PV_LOG", "✅ total lines read: ${lines.size}")
                runOnUiThread {
                    val filtered = lines.filter { it.startsWith(selectedDate) }
                    handlePvLogLines (filtered)
                    progressBar.visibility = View.GONE   // hide spinner here
                }

            } catch (e: java.net.SocketTimeoutException) {
                Log.e("PV_LOG", "⏱️ Timeout while reading PV log (${e.message})")
                if (lines.isNotEmpty()) {
                    runOnUiThread { handlePvLogLines(lines.filter { it.startsWith(selectedDate) }) }
                } else {
                    runOnUiThread { Toast.makeText(this, "Timeout reading PV log", Toast.LENGTH_LONG).show() }
                }
            } catch (e: Exception) {
                Log.e("PV_LOG", "❌ Socket error: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this, "Socket error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun requestPvLogFromCache() {
        progressBar.visibility = View.VISIBLE

        Thread {
            try {
                val cacheFile = File(filesDir, "LOG.TXT")
                if (!cacheFile.exists()) {
                    Log.w("PV_LOG", "⚠️ No cached LOG.TXT found in ${cacheFile.absolutePath}")
                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this, "No cached log file found yet.", Toast.LENGTH_LONG).show()
                    }
                    return@Thread
                }

                val allLines = cacheFile.readLines()
                val filtered = allLines.filter { it.startsWith(selectedDate) }

                Log.d("PV_LOG", "✅ Loaded ${filtered.size} cached log lines for $selectedDate")

                runOnUiThread {
                    handlePvLogLines(filtered)
                    progressBar.visibility = View.GONE
                }

            } catch (e: Exception) {
                Log.e("PV_LOG", "❌ Error reading LOG.TXT: ${e.message}")
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Error reading cached log: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()

    }


    private fun handlePvLogLines(lines: List<String>) {
        data class PvPoint(val secOfDay: Int, val xHour: Float, val pv: Float)

        val points = mutableListOf<PvPoint>()

        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (!line.startsWith(selectedDate)) continue

            val parts = line.split(',')
            if (parts.size < 2) continue

            val ts = parts[0].trim()                 // "YYYY-MM-DD HH:MM:SS"
            val time = ts.substringAfter(' ')        // "HH:MM:SS"
            val h = time.substringBefore(':').toIntOrNull() ?: continue
            val m = time.substringAfter(':').substringBefore(':').toIntOrNull() ?: 0
            val s = time.substringAfterLast(':').toIntOrNull() ?: 0
            val secOfDay = h * 3600 + m * 60 + s

            val pv = parts[1].trim().toFloatOrNull() ?: continue

            // X for chart (with seconds)
            val x = h + (m / 60f) + (s / 3600f)

            points.add(PvPoint(secOfDay = secOfDay, xHour = x, pv = pv))
        }

        if (points.isEmpty()) {
            pvChart.clear()
            findViewById<TextView>(R.id.tvPeakPv).text = "Peak PV: — W"
            findViewById<TextView>(R.id.tvEnergyPv).text = "Energy from PV: — kWh"
            return
        }

        // Ensure chronological order
        points.sortBy { it.secOfDay }

        // Chart entries
        val pvEntries = points.map { Entry(it.xHour, it.pv) }

        // Peak PV
        val peakPv = points.maxOf { it.pv }

        // Energy via trapezoidal rule over irregular time steps
        var energyWh = 0.0
        for (i in 1 until points.size) {
            val dtSec = points[i].secOfDay - points[i - 1].secOfDay
            if (dtSec <= 0 || dtSec > 3600) continue // skip duplicates or huge gaps (>1h)
            val avgPowerW = (points[i - 1].pv + points[i].pv) / 2.0
            energyWh += avgPowerW * (dtSec / 3600.0) // W * h = Wh
        }
        val energyKWh = energyWh / 1000.0

        // === Chart setup (unchanged, uses pvEntries) ===
        val pvSet = LineDataSet(pvEntries, "PV (W)").apply {
            color = Color.BLUE
            setCircleColor(Color.BLUE)
            lineWidth = 2f
            setDrawCircles(false)
            setDrawValues(false)
        }
        pvChart.data = LineData(pvSet)
        pvChart.description.isEnabled = false
        pvChart.setTouchEnabled(true)
        pvChart.setPinchZoom(true)
        pvChart.axisRight.isEnabled = false
        pvChart.axisLeft.axisMinimum = 0f
        pvChart.setAutoScaleMinMaxEnabled(true)
        pvChart.setExtraBottomOffset(14f)
        pvChart.legend.isEnabled = true
        pvChart.legend.isWordWrapEnabled = true
        pvChart.legend.yOffset = 6f
        pvChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            isGranularityEnabled = true
            labelRotationAngle = -45f
            setLabelCount(10, false)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val h = value.toInt()
                    val m = ((value - h) * 60).toInt()
                    return String.format("%02d:%02d", h % 24, m)
                }
            }
        }
        pvChart.data.notifyDataChanged()
        pvChart.notifyDataSetChanged()
        pvChart.post { pvChart.fitScreen(); pvChart.invalidate() }

        // === Update summary TextViews ===
        findViewById<TextView>(R.id.tvPeakPv).text =
            "Peak PV: ${peakPv.toInt()} W"
        findViewById<TextView>(R.id.tvEnergyPv).text =
            "Energy from PV: %,.2f kWh".format(Locale.getDefault(), energyKWh)
    }


    // If showing PV + GRID, use this instead:
    // pvChart.data = LineData(pvSet, gridSet)


    // identical look/behavior to Grid page
    private fun plotChart(chart: LineChart, entries: List<Entry>, label: String, color: Int) {
        val set = LineDataSet(entries, label).apply {
            this.color = color
            setCircleColor(color)
            lineWidth = 2f
            setDrawCircles(false)
            setDrawValues(false)
        }
        chart.data = LineData(set)
        chart.description.isEnabled = false
        chart.setTouchEnabled(true)
        chart.setPinchZoom(true)
        chart.axisRight.isEnabled = false
        chart.axisLeft.axisMinimum = 0f
        chart.setAutoScaleMinMaxEnabled(true)
        chart.setExtraBottomOffset(14f)

        chart.legend.apply {
            isEnabled = true
            isWordWrapEnabled = true
            yOffset = 6f
        }
        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            isGranularityEnabled = true
            labelRotationAngle = -45f
            setLabelCount(10, false)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val h = value.toInt()
                    val m = ((value - h) * 60).toInt()
                    return String.format("%02d:%02d", h % 24, m)
                }
            }
        }
        chart.data.notifyDataChanged()
        chart.notifyDataSetChanged()
        chart.post { chart.fitScreen(); chart.invalidate() }
    }

}