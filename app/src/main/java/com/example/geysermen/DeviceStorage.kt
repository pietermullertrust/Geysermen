package com.example.geysermen

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object DeviceStorage {

    private const val PREFS = "devices"
    private const val KEY_LIST = "device_list"

    fun load(context: Context): ArrayList<Device> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_LIST, "[]") ?: "[]"

        val arr = JSONArray(json)
        val list = ArrayList<Device>()

        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)

            list.add(
                Device(
                    slot = o.optInt("slot"),
                    name = o.optString("name"),
                    type = o.optString("type"),
                    role = o.optString("role"),
                    ip = o.optString("ip"),
                    deviceId = o.optString("deviceId"),
                    localKey = o.optString("localKey")
                )
            )
        }

        return list
    }

    fun save(context: Context, devices: List<Device>) {
        val arr = JSONArray()

        for (d in devices) {
            val o = JSONObject()
            o.put("slot", d.slot)
            o.put("name", d.name)
            o.put("type", d.type)
            o.put("role", d.role)
            o.put("ip", d.ip)
            o.put("deviceId", d.deviceId)
            o.put("localKey", d.localKey)
            arr.put(o)
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LIST, arr.toString())
            .apply()
    }

    fun nextSlot(context: Context): Int {
        val devices = load(context)
        return (devices.maxOfOrNull { it.slot } ?: 0) + 1
    }

    fun add(context: Context, device: Device) {
        val devices = load(context)
        devices.add(device)
        save(context, devices)
    }
}