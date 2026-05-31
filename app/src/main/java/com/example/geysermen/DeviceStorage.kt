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
                    priority = o.optInt("priority", 0),
                    relationship = o.optString("relationship", ""),
                    ip = o.optString("ip"),
                    deviceId = o.optString("deviceId"),
                    localKey = o.optString("localKey"),
                    online = o.optBoolean("online", false),
                    firmware = o.optString("firmware", ""),
                    lastSeen = o.optLong("lastSeen", 0)
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

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LIST, arr.toString())
            .apply()
    }

    fun nextSlot(context: Context): Int {
        val usedSlots = load(context).map { it.slot }.toSet()

        for (slot in 1..20) {
            if (!usedSlots.contains(slot)) {
                return slot
            }
        }

        return (usedSlots.maxOrNull() ?: 0) + 1
    }

    fun add(context: Context, device: Device) {
        val devices = load(context)
        devices.add(device)
        save(context, devices)
    }

    fun update(context: Context, device: Device) {
        val devices = load(context)

        val index = devices.indexOfFirst {
            it.slot == device.slot
        }

        if (index >= 0) {
            devices[index] = device
            save(context, devices)
        }
    }

    fun delete(context: Context, slot: Int) {
        val devices = load(context)

        val filtered = devices.filter {
            it.slot != slot
        }

        save(context, filtered)
    }

}