package com.example.geysermen

import android.app.Application
import android.util.Log
import com.thingclips.smart.home.sdk.ThingHomeSdk

class GeyserMenApp : Application() {

    override fun onCreate() {
        super.onCreate()

        Log.i("TUYA", "Before SDK init")


        ThingHomeSdk.init(
            this,
            "9j3uj7q73s4hgewwvfvn",
            "xf9gqayh3d545ujuptxt4uumackufd8e"
        )

        Log.i(
            "TUYA",
            "UserInstance = ${ThingHomeSdk.getUserInstance() != null}"
        )

        Log.i("TUYA", "SDK init completed")

        Log.i(
            "TUYA",
            "Need login = ${!ThingHomeSdk.getUserInstance().isLogin()}"
        )
    }
}