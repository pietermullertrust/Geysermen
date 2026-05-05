package com.example.geysermen

import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class NewPageActivityGeyser : AppCompatActivity() {

    // Based on your latest LOG.TXT lines:
    // 0 timestamp
    // 1 PV
    // 2 Grid
    // 3 Bat power
    // 4 SOC
    // 5 ...
    // 6 ...
    // 7 Temp1  (only present after you added it)
    // 8 Temp2  (only if you later add it)
    private val COL_GEYSER1_TEMP = 7
    private val COL_GEYSER2_TEMP = -1  // optional

    private lateinit var geyser1Chart: LineChart
    private lateinit var geyser2Chart: LineChart
    private lateinit var txtDate: TextView
    private lateinit var selectedDate: String
    private lateinit var progressBar: ProgressBar

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_page_geyser)

        geyser1Chart = findViewById(R.id.Geyser1TempChart)
        geyser2Chart = findViewById(R.id.Geyser2TempChart)
        txtDate = findViewById(R.id.txtChartDateGeyser)
        progressBar = findViewById(R.id.progressBar)

        val cal = Calendar.getInstance()
        selectedDate = intent.getStringExtra("extra_date") ?: dateFormat.format(cal.time)
        txtDate.text = "Date: $selectedDate"

        txtDate.setOnClickListener {
            val y = cal.get(Calendar.YEAR)
            val m = cal.get(Calendar.MONTH)
            val d = cal.get(Calendar.DAY_OF_MONTH)
            DatePickerDialog(this, { _, year, month, day ->
                cal.set(year, month, day)
                selectedDate = dateFormat.format(cal.time)
                txtDate.text = "Date: $selectedDate"
                fetchLogsFromLocalFile()
            }, y, m, d).show()
        }

        fetchLogsFromLocalFile()
    }

    // --- Read from local cached LOG.TXT ---
    private fun fetchLogsFromLocalFile() {
        progressBar.visibility = View.VISIBLE

        Thread {
            try {
                val cacheFile = File(filesDir, "LOG.TXT")
                if (!cacheFile.exists()) {
                    Log.w("GEYSER_LOG", "⚠️ No cached LOG.TXT at ${cacheFile.absolutePath}")
                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this, "No cached LOG.TXT found yet.", Toast.LENGTH_LONG).show()
                        renderGeyserTempGraphs(emptyList())
                    }
                    return@Thread
                }

                val allLines = cacheFile.readLines().map { it.trim() }.filter { it.isNotEmpty() }
                val todays = allLines.filter { it.startsWith(selectedDate) }

                Log.d("GEYSER_LOG", "✅ Loaded ${todays.size} cached lines for $selectedDate")
                if (todays.isNotEmpty()) {
                    Log.d("GEYSER_LOG", "First line: ${todays.first()}")
                    Log.d("GEYSER_LOG", "Last  line: ${todays.last()}")
                }

                runOnUiThread {
                    renderGeyserTempGraphs(todays)
                    progressBar.visibility = View.GONE
                }

            } catch (e: Exception) {
                Log.e("GEYSER_LOG", "❌ Error reading LOG.TXT: ${e.message}")
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Error reading LOG.TXT: ${e.message}", Toast.LENGTH_LONG).show()
                    renderGeyserTempGraphs(emptyList())
                }
            }
        }.start()
    }

    private fun renderGeyserTempGraphs(lines: List<String>) {

        val geyser1Entries = ArrayList<Entry>()
        val geyser2Entries = ArrayList<Entry>()

        var t1Min = Float.POSITIVE_INFINITY
        var t1Max = Float.NEGATIVE_INFINITY
        var t2Min = Float.POSITIVE_INFINITY
        var t2Max = Float.NEGATIVE_INFINITY

        val bucket = HashMap<Int, Float>()  // minute-of-day -> last temp in that minute

        for (l in lines) {
            val p = l.split(',').map { it.trim() }
            if (p.isEmpty()) continue

            if (p.size <= COL_GEYSER1_TEMP) continue

            val ts = p[0]
            if (!ts.startsWith(selectedDate)) continue

            val time = ts.substringAfter(' ', "")
            val hh = time.substringBefore(':').toIntOrNull() ?: continue
            val mm = time.substringAfter(':').substringBefore(':').toIntOrNull() ?: 0
            val ss = time.substringAfterLast(':').toIntOrNull() ?: 0
            val x = hh + (mm / 60f) + (ss / 3600f)

            // Temp1 -> bucket by minute
            val minuteOfDay = hh * 60 + mm
            p[COL_GEYSER1_TEMP].toFloatOrNull()?.let { t1 ->
                if (t1.isFinite() && t1 in 10f..100f) {
                    bucket[minuteOfDay] = t1
                }
            }

            // Temp2 (only if you have it)
            if (COL_GEYSER2_TEMP >= 0 && p.size > COL_GEYSER2_TEMP) {
                p[COL_GEYSER2_TEMP].toFloatOrNull()?.let { t2 ->
                    if (t2.isFinite() && t2 in 10f..100f) {
                        geyser2Entries.add(Entry(x, t2))
                        t2Min = min(t2Min, t2)
                        t2Max = max(t2Max, t2)
                    }
                }
            }
        }

        // Convert bucket -> geyser1Entries
        val g1 = bucket.entries
            .sortedBy { it.key }
            .map { (minute, temp) ->
                val x = minute / 60f
                Entry(x, temp)
            }

        geyser1Entries.addAll(g1)

        // Calculate min/max for geyser1 from bucketed entries
        for (e in geyser1Entries) {
            t1Min = min(t1Min, e.y)
            t1Max = max(t1Max, e.y)
        }

        // Optional debug
        Log.d("GEYSER_LOG", "G1 entries=${geyser1Entries.size}, t1Min=$t1Min, t1Max=$t1Max")

        // Geyser1 chart
        configureChartBase(geyser1Chart)
        geyser1Chart.axisLeft.apply {
            isEnabled = true
            axisMinimum = if (t1Min.isFinite()) t1Min - 1f else 0f
            axisMaximum = if (t1Max.isFinite()) t1Max + 1f else 1f
        }
        plotLine(geyser1Chart, geyser1Entries, "Temp1 (°C)", Color.parseColor("#FF5722"))

        // Geyser2 chart
        configureChartBase(geyser2Chart)
        geyser2Chart.axisLeft.apply {
            isEnabled = true
            axisMinimum = if (t2Min.isFinite()) t2Min - 1f else 0f
            axisMaximum = if (t2Max.isFinite()) t2Max + 1f else 1f
        }
        plotLine(geyser2Chart, geyser2Entries, "Temp2 (°C)", Color.parseColor("#03A9F4"))
    }

    private fun configureChartBase(chart: LineChart) {
        chart.description.isEnabled = false
        chart.axisRight.isEnabled = false
        chart.setTouchEnabled(true)
        chart.setPinchZoom(true)
        chart.setAutoScaleMinMaxEnabled(true)
        chart.setExtraBottomOffset(14f)

        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            isGranularityEnabled = true
            labelRotationAngle = -45f
            setLabelCount(10, false)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(v: Float): String {
                    val h = v.toInt()
                    val m = ((v - h) * 60).toInt()
                    return String.format("%02d:%02d", h % 24, m)
                }
            }
        }
    }

    private fun plotLine(chart: LineChart, entries: List<Entry>, label: String, color: Int) {
        val set = LineDataSet(entries, label).apply {
            this.color = color
            setCircleColor(color)
            lineWidth = 2f
            setDrawCircles(false)
            setDrawValues(false)
        }
        chart.data = LineData(set)
        chart.data.notifyDataChanged()
        chart.notifyDataSetChanged()
        chart.post { chart.fitScreen(); chart.invalidate() }
    }
}