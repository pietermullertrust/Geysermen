package com.example.geysermen

data class Device(
    var slot: Int,
    var name: String,
    var type: String,
    var role: String,
    var priority: Int = 0,
    var relationship: String = "",
    var ip: String = "",
    var deviceId: String = "",
    var localKey: String = "",
    var online: Boolean = false,
    var firmware: String = "",
    var lastSeen: Long = 0
)
