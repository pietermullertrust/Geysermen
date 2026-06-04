package com.example.geysermen

data class Device(
    val slot: Int,
    var name: String,
    var type: String,
    var role: String,
    var priority: Int,
    var relationship: String,
    var ip: String = "",
    var deviceId: String = "",
    var localKey: String = "",
    var firmware: String = "",
    var mac: String = "",
    var online: Boolean = false,
    var lastSeen: Long = 0
)