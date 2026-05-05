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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.io.File
import kotlin.math.max
import kotlin.math.min
import android.view.View
import android.widget.ProgressBar

class NewPageActivityInverter : AppCompatActivity() {
    // ---- Correct column mapping for your LOG.TXT ----
    // timestamp, pv, grid, bat_power, bat_soc, extra
    private val COL_GRID_LOAD = 5
    private val COL_INV_LOAD  = 6
    // -------------------------------------------------

    private lateinit var loadChart: LineChart
    private lateinit var upsChart: LineChart
    private lateinit var txtDate: TextView
    private lateinit var selectedDate: String
    private lateinit var progressBar: ProgressBar
    private lateinit var txtSummary: TextView


    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_page_inverter)

        loadChart = findViewById(R.id.loadChart)
        upsChart   = findViewById(R.id.upsChart)
        txtDate    = findViewById(R.id.txtChartDateInverter)
        progressBar = findViewById(R.id.progressBar)
        txtSummary = findViewById(R.id.txtSummary)

        val cal = Calendar.getInstance()
        selectedDate = intent.getStringExtra("extra_date")
            ?: dateFormat.format(cal.time)
        txtDate.text = "Date: $selectedDate"

        // --- Date picker ---
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
                    Log.w("BAT_LOG", "⚠️ No cached LOG.TXT found at ${cacheFile.absolutePath}")
                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this, "No cached LOG.TXT found yet.", Toast.LENGTH_LONG).show()
                    }
                    return@Thread
                }

                val allLines = cacheFile.readLines().filter { it.isNotBlank() }
                val todays = allLines.filter { it.startsWith(selectedDate) }

                Log.d("BAT_LOG", "✅ Loaded ${todays.size} cached lines for $selectedDate")
                if (todays.isNotEmpty()) {
                    Log.d("BAT_LOG", "First line: ${todays.first()}")
                    Log.d("BAT_LOG", "Last line: ${todays.last()}")
                }

                runOnUiThread {
                    renderInverterGraphs(todays)

                    // --- NEW: Calculate summary ---
                    val (total_kWh, grid_kWh, inv_kWh) = calculateDailyTotals(todays)

                    txtSummary.text = """
                        Total Load: ${"%.2f".format(total_kWh)} kWh
                        Grid Load:  ${"%.2f".format(grid_kWh)} kWh
                        Inverter Load: ${"%.2f".format(inv_kWh)} kWh
                    """.trimIndent()

                    progressBar.visibility = View.GONE
                }

            } catch (e: Exception) {
                Log.e("BAT_LOG", "❌ Error reading LOG.TXT: ${e.message}")
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Error reading LOG.TXT: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun calculateDailyTotals(lines: List<String>): Triple<Double, Double, Double> {

        var total_kWh = 0.0
        var grid_kWh = 0.0
        var inv_kWh = 0.0

        var lastTs: Long? = null

        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        for (l in lines) {
            val p = l.split(',').map { it.trim() }
            if (p.size < 7) continue
            if (!p[0].startsWith(selectedDate)) continue

            val totalLoad = p[5].toDoubleOrNull() ?: continue
            val invLoad   = p[6].toDoubleOrNull() ?: continue
            val gridLoad  = totalLoad - invLoad

            // Parse timestamp
            val ts = try { format.parse(p[0])?.time ?: continue }
            catch (e: Exception) { continue }

            if (lastTs != null) {
                val dtHours = (ts - lastTs!!) / 3600000.0  // ms → hours

                total_kWh += totalLoad * dtHours / 1000.0
                grid_kWh  += gridLoad  * dtHours / 1000.0
                inv_kWh   += invLoad   * dtHours / 1000.0
            }

            lastTs = ts
        }

        return Triple(total_kWh, grid_kWh, inv_kWh)
    }

    private fun renderInverterGraphs(lines: List<String>) {

        val totalEntries = ArrayList<Entry>()   // Column 5
        val gridEntries  = ArrayList<Entry>()   // Total − Inverter
        val invEntries   = ArrayList<Entry>()   // Column 6

        var minValue = Float.POSITIVE_INFINITY
        var maxValue = Float.NEGATIVE_INFINITY

        for (l in lines) {
            val p = l.split(',').map { it.trim() }
            if (p.size < 7) continue
            if (!p[0].startsWith(selectedDate)) continue

            // ---- Parse time as hour fraction ----
            val ts = p[0]
            val time = ts.substringAfter(" ")
            val hhmm = time.split(":")
            if (hhmm.size < 2) continue

            val h = hhmm[0].toFloatOrNull() ?: continue
            val m = hhmm[1].toFloatOrNull() ?: continue
            val x = h + (m / 60f)

            // ---- Columns ----
            val totalLoad = p[5].toFloatOrNull()
            val invLoad   = p[6].toFloatOrNull()

            // ---- Total Load ----
            if (totalLoad != null && totalLoad.isFinite()) {
                totalEntries.add(Entry(x, totalLoad))
                minValue = min(minValue, totalLoad)
                maxValue = max(maxValue, totalLoad)
            }

            // ---- Inverter Load ----
            if (invLoad != null && invLoad.isFinite()) {
                invEntries.add(Entry(x, invLoad))
                minValue = min(minValue, invLoad)
                maxValue = max(maxValue, invLoad)
            }

            // ---- Grid Load = Total − Inverter ----
            if (totalLoad != null && invLoad != null &&
                totalLoad.isFinite() && invLoad.isFinite()) {

                val gridLoad = totalLoad - invLoad
                gridEntries.add(Entry(x, gridLoad))

                minValue = min(minValue, gridLoad)
                maxValue = max(maxValue, gridLoad)
            }
        }

        // ---- Build sorted datasets ----
        val total = totalEntries.distinctBy { it.x }.sortedBy { it.x }
        val inv   = invEntries.distinctBy { it.x }.sortedBy { it.x }
        val grid  = gridEntries.distinctBy { it.x }.sortedBy { it.x }

        // ============================================================
        // 🟦 TOP GRAPH: Total Load only
        // ============================================================
        configureChartBase(loadChart)
        loadChart.axisLeft.apply {
            axisMinimum = if (minValue.isFinite()) minValue * 0.9f else 0f
            axisMaximum = if (maxValue.isFinite()) maxValue * 1.1f else 1000f
        }

        val setTotal = LineDataSet(total, "Total Load (W)").apply {
            color = Color.parseColor("#03A9F4")  // Light Blue
            lineWidth = 2f
            setDrawCircles(false)
            setDrawValues(false)
        }

        loadChart.data = LineData(setTotal)
        loadChart.invalidate()

        // ============================================================
        // 🟪 BOTTOM GRAPH: Inverter + Grid Load
        // ============================================================
        configureChartBase(upsChart)
        upsChart.axisLeft.apply {
            axisMinimum = if (minValue.isFinite()) minValue * 0.9f else 0f
            axisMaximum = if (maxValue.isFinite()) maxValue * 1.1f else 1000f
        }

        val setInv = LineDataSet(inv, "Inverter Load (W)").apply {
            color = Color.MAGENTA      // Magenta
            lineWidth = 2f
            setDrawCircles(false)
            setDrawValues(false)
        }

        val setGrid = LineDataSet(grid, "Grid Load (W)").apply {
            color = Color.parseColor("#FF9800") // Orange
            lineWidth = 2f
            setDrawCircles(false)
            setDrawValues(false)
        }

        upsChart.data = LineData(setInv, setGrid)
        upsChart.invalidate()
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