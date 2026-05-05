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

class NewPageActivityBattery : AppCompatActivity() {

    // ---- Correct column mapping for your LOG.TXT ----
    // timestamp, pv, grid, bat_power, bat_soc, extra
    private val COL_BAT_POWER = 3
    private val COL_BAT_SOC   = 4
    // -------------------------------------------------

    private lateinit var powerChart: LineChart
    private lateinit var socChart: LineChart
    private lateinit var txtDate: TextView
    private lateinit var selectedDate: String
    private lateinit var progressBar: ProgressBar
    private lateinit var txtBatterySummary: TextView

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_page_battery)

        powerChart = findViewById(R.id.batteryPowerChart)
        socChart   = findViewById(R.id.batterySocChart)
        txtDate    = findViewById(R.id.txtChartDateBattery)
        progressBar = findViewById(R.id.progressBar)
        txtBatterySummary = findViewById(R.id.txtBatterySummary)

        txtBatterySummary.text = "Battery summary loading…"

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
                    Log.d("BAT_SUM", "▶ Entering battery summary calculation")

                    renderBatteryGraphs(todays)

                    val (dis_kWh, chg_kWh) = calculateDailyBatteryTotals(todays)
                    val net_kWh = dis_kWh - chg_kWh

                    Log.d(
                        "BAT_SUM",
                        "✔ Battery summary: Dis=%.2f kWh, Chg=%.2f kWh, Net=%.2f kWh"
                            .format(dis_kWh, chg_kWh, net_kWh)
                    )

                    txtBatterySummary.text = """
                    Battery Discharge: ${"%.2f".format(dis_kWh)} kWh
                    Battery Charge:    ${"%.2f".format(chg_kWh)} kWh
                    Battery Net:       ${"%.2f".format(net_kWh)} kWh
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

    data class BatteryTotals(
        val discharge_kWh: Double,
        val charge_kWh: Double
    )

    private fun calculateDailyBatteryTotals(lines: List<String>): BatteryTotals {
        if (lines.size < 2) return BatteryTotals(0.0, 0.0)

        var dischargeWh = 0.0
        var chargeWh = 0.0

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        for (i in 1 until lines.size) {
            val p1 = lines[i - 1].split(',').map { it.trim() }
            val p2 = lines[i].split(',').map { it.trim() }

            if (p1.size < 5 || p2.size < 5) continue

            val t1 = sdf.parse(p1[0])?.time ?: continue
            val t2 = sdf.parse(p2[0])?.time ?: continue

            val dtHours = (t2 - t1) / 3_600_000.0
            if (dtHours <= 0) continue

            val pwr1 = p1[COL_BAT_POWER].toDoubleOrNull() ?: continue
            val pwr2 = p2[COL_BAT_POWER].toDoubleOrNull() ?: continue

            val avgPower = (pwr1 + pwr2) / 2.0

            if (avgPower > 0) {
                dischargeWh += avgPower * dtHours
            } else {
                chargeWh += -avgPower * dtHours
            }
        }

        return BatteryTotals(
            discharge_kWh = dischargeWh / 1000.0,
            charge_kWh = chargeWh / 1000.0
        )
    }

    private fun renderBatteryGraphs(lines: List<String>) {
        val powerEntries = ArrayList<Entry>()
        val socEntries   = ArrayList<Entry>()
        var powerMin = Float.POSITIVE_INFINITY
        var powerMax = Float.NEGATIVE_INFINITY

        for (l in lines) {
            val p = l.split(',').map { it.trim() }
            if (p.size < 5) continue  // skip malformed lines

            val ts = p[0]
            if (!ts.startsWith(selectedDate)) continue

            val timePart = ts.substringAfter(' ', "")
            val parts = timePart.split(":")
            if (parts.size < 2) continue

            val h = parts[0].toFloatOrNull() ?: continue
            val m = parts[1].toFloatOrNull() ?: continue
            val x = h + (m / 60f)

            // Battery Power
            p[COL_BAT_POWER].toFloatOrNull()?.let { w ->
                if (w.isFinite()) {
                    powerEntries.add(Entry(x, w))
                    powerMin = min(powerMin, w)
                    powerMax = max(powerMax, w)
                }
            }

            // Battery SOC
            p[COL_BAT_SOC].toFloatOrNull()?.let { s ->
                if (s.isFinite()) socEntries.add(Entry(x, s.coerceIn(0f, 100f)))
            }
        }

        // Remove duplicates & sort chronologically
        val uniquePower = powerEntries.distinctBy { it.x }.sortedBy { it.x }
        val uniqueSoc   = socEntries.distinctBy { it.x }.sortedBy { it.x }

        // Render Battery Power
        configureChartBase(powerChart)
        powerChart.axisLeft.apply {
            isEnabled = true
            axisMinimum = if (powerMin.isFinite()) powerMin - (0.1f * kotlin.math.abs(powerMin)) else 0f
            axisMaximum = if (powerMax.isFinite()) powerMax + (0.1f * kotlin.math.abs(powerMax)) else 1f
        }
        plotLine(powerChart, uniquePower, "Battery Power (W)", Color.parseColor("#FF5722"))

        // Render SOC
        configureChartBase(socChart)
        socChart.axisLeft.apply {
            isEnabled = true
            axisMinimum = 0f
            axisMaximum = 100f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String = "${value.toInt()}%"
            }
        }
        plotLine(socChart, uniqueSoc, "Battery SOC (%)", Color.MAGENTA)
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
