package com.example.bettergmaps.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template

class MainScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val row = Row.Builder()
            .setTitle("BetterGMaps'e Hoşgeldiniz")
            .addText("Android Auto desteği aktif!")
            .build()

        val pane = Pane.Builder()
            .addRow(row)
            .addAction(
                Action.Builder()
                    .setTitle("Çıkış")
                    .setOnClickListener { val exit = 0; } // Dummy listener
                    .build()
            )
            .build()

        return PaneTemplate.Builder(pane)
            .setTitle("BetterGMaps Auto")
            .build()
    }
}
