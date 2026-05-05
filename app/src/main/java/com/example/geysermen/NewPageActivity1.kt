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
import android.view.View
import android.widget.ProgressBar
import java.io.File


class NewPageActivity1 : AppCompatActivity() {

    private lateinit var gridChart: LineChart
    private lateinit var txtDate: TextView
    private lateinit var tvPeakGrid: TextView
    private lateinit var tvEnergyGrid: TextView
    private lateinit var selectedDate: String
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_page1)

        gridChart = findViewById(R.id.gridLineChart1)
        txtDate = findViewById(R.id.txtChartDate1)
        tvPeakGrid   = findViewById(R.id.tvPeakGrid)
        tvEnergyGrid = findViewById(R.id.tvEnergyGrid)
        progressBar = findViewById(R.id.progressBar)


        val calendar = Calendar.getInstance()
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
                requestGridLogFromCache()
            }, y, m, d).show()
        }

        requestGridLogFromCache()
    }

    private fun convertToSigned16Bit(raw: Float): Float {
        val intVal = raw.toInt()
        return if (intVal > 32767) (intVal - 65536).toFloat() else intVal.toFloat()
    }

    private fun requestGridLogFromEsp() {
        progressBar.visibility = View.VISIBLE
        Thread {
            val lines = mutableListOf<String>()
            try {
                Log.d("GRID_LOG", "⚡ Connecting to ESP32...")
                val addr = java.net.InetSocketAddress("10.0.0.23", 9000)
                java.net.Socket().use { socket ->
                    socket.connect(addr, 4000)
                    socket.tcpNoDelay = true
                    socket.soTimeout = 60000  // read timeout

                    val out = java.io.PrintWriter(
                        java.io.BufferedWriter(java.io.OutputStreamWriter(socket.getOutputStream())),
                        true
                    )
                    val reader =
                        java.io.BufferedReader(java.io.InputStreamReader(socket.getInputStream()))


                    Log.d("GRID_LOG", "📤 Sending GET_PV_LOG")
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
                    handleGridLogLines(filtered)
                    progressBar.visibility = View.GONE   // hide spinner here
                }
            } catch (e: java.net.SocketTimeoutException) {
                Log.e("PV_LOG", "⏱️ Timeout while reading PV log (${e.message})")
                if (lines.isNotEmpty()) {
                    runOnUiThread {
                        handleGridLogLines(lines.filter { it.startsWith(selectedDate) })
                        Toast.makeText(this, "Socket error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            "Timeout reading PV log",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("GRID_LOG", "❌ Socket error: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this, "Socket error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun requestGridLogFromCache() {
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
                    handleGridLogLines(filtered)
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

    private fun handleGridLogLines(lines: List<String>) {
        data class GridPoint(val secOfDay: Int, val xHour: Float, val gridW: Float)

        val points = mutableListOf<GridPoint>()

        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (!line.startsWith(selectedDate)) continue

            val parts = line.split(',')
            if (parts.size < 3) continue

            // timestamp
            val ts = parts[0].trim()                 // "YYYY-MM-DD HH:MM:SS"
            val time = ts.substringAfter(' ')        // "HH:MM:SS"
            val h = time.substringBefore(':').toIntOrNull() ?: continue
            val m = time.substringAfter(':').substringBefore(':').toIntOrNull() ?: 0
            val s = time.substringAfterLast(':').toIntOrNull() ?: 0
            val secOfDay = h * 3600 + m * 60 + s

            // GRID column is index 2 in "timestamp,pv,grid,..." format
            val rawGrid = parts[2].trim().toFloatOrNull() ?: continue
            val grid = convertToSigned16Bit(rawGrid)


            // If you want "energy drawn from grid" only, ignore export (negative):
            // grid = max(0f, grid)

            val x = h + (m / 60f) + (s / 3600f)
            points.add(GridPoint(secOfDay = secOfDay, xHour = x, gridW = grid))
        }

        if (points.isEmpty()) {
            gridChart.clear()
            tvPeakGrid.text = "Peak Grid: — W"
            tvEnergyGrid.text = "Energy from Grid: — kWh"
            return
        }

        points.sortBy { it.secOfDay }

        val entries = points.map { Entry(it.xHour, it.gridW) }
        val peakGrid = points.maxOf { it.gridW }

        // Trapezoidal integration (Wh)
        var energyWh = 0.0
        for (i in 1 until points.size) {
            val dtSec = points[i].secOfDay - points[i - 1].secOfDay
            if (dtSec <= 0 || dtSec > 3600) continue
            val avgW = (points[i - 1].gridW + points[i].gridW) / 2.0
            energyWh += avgW * (dtSec / 3600.0)
        }
        val energyKWh = energyWh / 1000.0

        val set = LineDataSet(entries, "Grid (W)").apply {
            color = Color.RED
            setCircleColor(Color.RED)
            lineWidth = 2f
            setDrawCircles(false)
            setDrawValues(false)
        }
        gridChart.data = LineData(set)

        gridChart.description.isEnabled = false
        gridChart.setTouchEnabled(true)
        gridChart.setPinchZoom(true)
        gridChart.axisRight.isEnabled = false
        gridChart.axisLeft.axisMinimum = 0f
        gridChart.setAutoScaleMinMaxEnabled(true)
        gridChart.setExtraBottomOffset(14f)
        gridChart.legend.isEnabled = true
        gridChart.legend.isWordWrapEnabled = true
        gridChart.legend.yOffset = 6f
        gridChart.xAxis.apply {
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
        gridChart.data.notifyDataChanged()
        gridChart.notifyDataSetChanged()
        gridChart.post { gridChart.fitScreen(); gridChart.invalidate() }

        tvPeakGrid.text = "Peak Grid: ${peakGrid.toInt()} W"
        tvEnergyGrid.text = "Energy from Grid: %,.2f kWh".format(Locale.getDefault(), energyKWh)
    }


    // same plotter to keep format identical
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
