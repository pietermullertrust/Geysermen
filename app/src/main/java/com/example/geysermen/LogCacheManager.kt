package com.example.geysermen

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Locale

class LogCacheManager(private val context: Context) {

    private val db = GeyserDatabaseHelper(context)

    /** Delete SQLite cached log data. */
    fun clearAll() {
        db.clearAll()
    }

    /** Get last timestamp for selected date. */
    fun getLastTimestamp(date: String): String? {
        val lines = db.getLinesForDate(date)
        return lines.lastOrNull()?.split(",")?.firstOrNull()?.trim()
    }

    /** Kept for compatibility with your old code. SQLite handles this now. */
    fun setLastTimestamp(date: String, ts: String) {
        // No longer needed because timestamp is stored in SQLite.
    }

    /** Load today's cached log data by default. */
    fun loadCache(): List<String> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(System.currentTimeMillis())

        return db.getLinesForDate(today)
    }

    /** Insert new ESP32 log lines into SQLite and return today's cached data. */
    fun updateCache(newLines: List<String>): List<String> {
        if (newLines.isNotEmpty()) {
            db.insertLogLines(newLines)
        }

        return loadCache()
    }

    /** Build the right request string for ESP32. */
    fun buildRequest(date: String, hasCache: Boolean): String {
        return if (isToday(date)) {
            val lastTs = getLastTimestamp(date)

            if (lastTs != null) {
                "GET_PV_LOG FROM=$lastTs"
            } else {
                "GET_PV_LOG DATE=$date"
            }
        } else {
            if (hasCache) {
                "CACHE_ONLY"
            } else {
                "GET_PV_LOG DATE=$date"
            }
        }
    }

    fun loadCacheForDate(date: String): List<String> {
        return db.getLinesForDate(date)
    }

    private fun isToday(date: String): Boolean {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = fmt.format(System.currentTimeMillis())
        return date == today
    }
}
