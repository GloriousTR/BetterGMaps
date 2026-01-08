package com.example.bettergmaps.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

class MyCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        return MainScreen(carContext)
    }
}
