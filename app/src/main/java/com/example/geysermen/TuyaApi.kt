package com.example.geysermen

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

object TuyaApi {

    private val SERVER =
        if (
            android.os.Build.FINGERPRINT
                .contains("generic")
        )

            "http://10.0.2.2:5050"

        else

            "http://10.0.0.27:5050"


    fun getDevices(): JSONArray {

        val conn =

            URL(

                "$SERVER/tuya/devices"

            )
                .openConnection()

                    as HttpURLConnection


        conn.connectTimeout = 5000
        conn.readTimeout = 15000


        val code =
            conn.responseCode


        val stream =

            if (
                code in 200..299
            )

                conn.inputStream

            else

                conn.errorStream


        val text =
            stream
                .bufferedReader()
                .readText()


        conn.disconnect()


        if (
            code !in 200..299
        ) {

            throw Exception(
                text
            )

        }


        return JSONArray(
            text
        )
    }
}