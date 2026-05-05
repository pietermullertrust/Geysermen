package com.example.geysermen

/**
 * One row from your ESP32 log.
 * We keep both the original timestamp string and a parsed epochMillis for fast comparisons.
 */
data class LogEntry(
    val timestamp: String,      // "yyyy-MM-dd HH:mm:ss"
    val epochMillis: Long,      // parsed from timestamp
    val pv: Int?,               // PV power (W) if present
    val grid: Int?,             // Grid power (W) if present
    val batteryPct: Int?,       // Battery SOC (%) if present
    val extras: Map<String, String> = emptyMap() // any additional fields
)

