package com.example.geysermen

data class Device(
    var slot: Int,
    var name: String,
    var type: String,
    var role: String,
    var ip: String = "",
    var deviceId: String = "",
    var localKey: String = ""
)
