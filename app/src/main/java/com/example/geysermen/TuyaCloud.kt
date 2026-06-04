package com.example.geysermen

import org.json.JSONArray

object TuyaCloud {

    private var loggedIn = false

    fun login(
        username: String,
        password: String
    ): Boolean {

        // TEMP
        loggedIn =
            username.isNotBlank()
                    &&
                    password.isNotBlank()

        return loggedIn
    }

    fun isLoggedIn(): Boolean {
        return loggedIn
    }

    fun getDevices(): JSONArray {

        if (!loggedIn)
            return JSONArray()

        return TuyaApi.getDevices()
    }
}