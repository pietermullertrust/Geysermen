package com.example.geysermen

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

object MasterSync {

    private const val TAG = "MASTER_SYNC"

    private const val MASTER_IP = "154.66.153.194"
    private const val PORT = 9000

    fun syncFromMaster(
        context: Context,
        onDone: () -> Unit
    ) {

        Thread {

            try {

                val socket = Socket(MASTER_IP, PORT)

                val out = PrintWriter(
                    socket.getOutputStream(),
                    true
                )

                val input = BufferedReader(
                    InputStreamReader(
                        socket.inputStream
                    )
                )

                out.println("GET_DEVICES")

                val reply = input.readLine()

                socket.close()

                if (reply != null &&
                    reply.startsWith("DEVICES|")
                ) {

                    val json = reply.substringAfter("DEVICES|")
                    val arr = JSONArray(json)

                    if (arr.length() == 0) {
                        val localDevices = DeviceStorage.load(context)

                        if (localDevices.isNotEmpty()) {
                            syncToMaster(context)
                            Log.i(TAG, "Master empty - uploaded local devices")
                        }

                        onDone()
                        return@Thread
                    }

                    val list = ArrayList<Device>()

                    for (i in 0 until arr.length()) {

                        val o = arr.getJSONObject(i)

                        list.add(
                            Device(
                                slot = o.getInt("slot"),
                                name = o.getString("name"),
                                type = o.getString("type"),
                                role = o.getString("role"),
                                priority = o.optInt("priority", 0),
                                relationship = o.optString("relationship", "")
                            )
                        )
                    }

                    DeviceStorage.save(context, list)

                    Log.i(TAG, "Sync complete")
                }

            } catch (e: Exception) {

                Log.e(TAG, "Sync failed", e)

            } finally {

                onDone()
            }

        }.start()
    }

    fun syncToMaster(context: Context) {

        Thread {
            try {
                val devices = DeviceStorage.load(context)
                val arr = JSONArray()

                for (d in devices) {
                    val o = org.json.JSONObject()

                    o.put("slot", d.slot)
                    o.put("name", d.name)
                    o.put("type", d.type)
                    o.put("role", d.role)
                    o.put("priority", d.priority)
                    o.put("relationship", d.relationship)
                    o.put("ip", d.ip)
                    o.put("deviceId", d.deviceId)
                    o.put("localKey", d.localKey)
                    o.put("online", d.online)
                    o.put("firmware", d.firmware)
                    o.put("lastSeen", d.lastSeen)

                    arr.put(o)
                }

                val socket = Socket(MASTER_IP, PORT)

                val out = PrintWriter(
                    socket.getOutputStream(),
                    true
                )

                out.println("SAVE_DEVICES|${arr}")

                socket.close()

                Log.i(TAG, "Synced devices to master")

            } catch (e: Exception) {
                Log.e(TAG, "Sync to master failed", e)
            }
        }.start()
    }
}