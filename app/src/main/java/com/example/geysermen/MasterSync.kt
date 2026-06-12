package com.example.geysermen

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

object MasterSync {

    private const val TAG = "MASTER_SYNC"
    private const val PORT = 9000

    fun syncToMaster(
        context: Context,
        jsonText: String,
        masterIp: String
    ) {
        Thread {
            try {
                val socket = Socket(masterIp, PORT)
                socket.soTimeout = 10000

                val out = PrintWriter(
                    socket.getOutputStream(),
                    true
                )

                val input = BufferedReader(
                    InputStreamReader(socket.inputStream)
                )

                out.println("SAVE_DEVICES|$jsonText")

                val reply = input.readLine()

                socket.close()

                Log.i(TAG, "Master reply=$reply")

            } catch (e: Exception) {
                Log.e(TAG, "Sync to master failed", e)
            }
        }.start()
    }
}