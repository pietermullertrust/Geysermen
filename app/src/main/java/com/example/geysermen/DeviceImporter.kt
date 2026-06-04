package com.example.geysermen

import org.json.JSONArray

interface DeviceImporter {

    fun getDevices(): JSONArray
}