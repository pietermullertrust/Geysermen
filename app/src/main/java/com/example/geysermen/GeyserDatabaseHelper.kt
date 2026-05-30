package com.example.geysermen

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class GeyserDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, "geyser_logs.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS geyser_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp TEXT UNIQUE,
                pv REAL,
                grid REAL,
                battery REAL,
                soc REAL,
                ups REAL,
                load REAL,
                temp1 REAL
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_timestamp ON geyser_log(timestamp)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS geyser_log")
        onCreate(db)
    }

    fun insertLogLine(line: String): Boolean {
        val parts = line.trim().split(",")

        if (parts.size < 8) return false

        return try {
            val values = ContentValues().apply {
                put("timestamp", parts[0].trim())
                put("pv", parts[1].trim().toDouble())
                put("grid", parts[2].trim().toDouble())
                put("battery", parts[3].trim().toDouble())
                put("soc", parts[4].trim().toDouble())
                put("ups", parts[5].trim().toDouble())
                put("load", parts[6].trim().toDouble())
                put("temp1", parts[7].trim().toDouble())
            }

            writableDatabase.insertWithOnConflict(
                "geyser_log",
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE
            ) != -1L

        } catch (e: Exception) {
            false
        }
    }

    fun insertLogLines(lines: List<String>): Int {
        var count = 0
        val db = writableDatabase

        db.beginTransaction()
        try {
            for (line in lines) {
                if (insertLogLine(line)) count++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        return count
    }

    fun getLastTimestamp(): String? {
        val cursor = readableDatabase.rawQuery(
            "SELECT timestamp FROM geyser_log ORDER BY timestamp DESC LIMIT 1",
            null
        )

        return cursor.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }

    fun getLinesForDate(date: String): List<String> {
        val lines = mutableListOf<String>()

        val cursor = readableDatabase.rawQuery(
            """
            SELECT timestamp, pv, grid, battery, soc, ups, load, temp1
            FROM geyser_log
            WHERE timestamp LIKE ?
            ORDER BY timestamp ASC
            """.trimIndent(),
            arrayOf("$date%")
        )

        cursor.use {
            while (it.moveToNext()) {
                lines.add(
                    "${it.getString(0)}," +
                            "${it.getDouble(1)}," +
                            "${it.getDouble(2)}," +
                            "${it.getDouble(3)}," +
                            "${it.getDouble(4)}," +
                            "${it.getDouble(5)}," +
                            "${it.getDouble(6)}," +
                            "${it.getDouble(7)}"
                )
            }
        }

        return lines
    }

    fun clearAll() {
        writableDatabase.delete("geyser_log", null, null)
    }
}