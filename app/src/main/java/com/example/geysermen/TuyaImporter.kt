package com.example.geysermen

import org.json.JSONArray

class TuyaImporter :
    DeviceImporter {

    override fun getDevices(): JSONArray {

        return TuyaApi.getDevices()

    }
}