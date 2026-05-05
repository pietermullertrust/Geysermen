package com.example.geysermen

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class LogCacheManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("geyser_cache", Context.MODE_PRIVATE)

    private val tsFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val logsFile: File by lazy {
        File(context.getExternalFilesDir(null), "LOG.txt").apply {
            if (!exists()) createNewFile()
        }
    }

    /** Delete the cached log file + reset timestamps. */
    fun clearAll() {
        if (logsFile.exists()) logsFile.delete()
        prefs.edit().clear().apply()
    }

    fun getLastTimestamp(date: String): String? {
        val key = "last_ts_$date"
        return prefs.getString(key, null)
    }

    fun setLastTimestamp(date: String, ts: String) {
        val key = "last_ts_$date"
        prefs.edit().putString(key, ts).apply()
    }

    /** Load the full cached log file (already trimmed to last 30 days). */
    fun loadCache(): List<String> {
        if (!logsFile.exists()) return emptyList()
        return logsFile.readLines()
    }

    /** Append new lines from ESP32 to the cache, then trim to last 30 days. */
    fun updateCache(newLines: List<String>): List<String> {
        if (newLines.isEmpty()) return loadCache()

        // 1. Load existing cache
        val existing = if (logsFile.exists()) logsFile.readLines() else emptyList()

        // 2. Merge: keep all old + new, but deduplicate by timestamp
        val merged = (existing + newLines)
            .mapNotNull { line ->
                val ts = line.split(",").firstOrNull()?.trim()
                if (ts != null) ts to line else null
            }
            .toMap()   // deduplicate by timestamp (last occurrence wins)
            .values
            .sorted()  // keep chronological order

        // 3. Trim to last 30 days
        val cutoff = System.currentTimeMillis() - 30L * 24L * 60L * 60L * 1000L
        val trimmed = merged.filter { line ->
            val ts = line.split(",").firstOrNull()?.trim() ?: return@filter false
            val date = try { tsFmt.parse(ts) } catch (_: Exception) { null }
            date != null && date.time >= cutoff
        }

        // 4. Save back to file
        logsFile.writeText(trimmed.joinToString("\n", postfix = "\n"))

        return trimmed
    }

    /** Build the right request string for ESP32. */
    fun buildRequest(date: String, hasCache: Boolean): String {
        return if (isToday(date)) {
            // Today → always fetch fresh data
            "GET_PV_LOG DATE=$date"
        } else {
            if (hasCache) {
                // Past date and cache exists → skip ESP
                "CACHE_ONLY"
            } else {
                // Past date but cache cleared → fetch from ESP
                "GET_PV_LOG DATE=$date"
            }
        }
    }

    private fun isToday(date: String): Boolean {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = fmt.format(System.currentTimeMillis())
        return date == today
    }


}
