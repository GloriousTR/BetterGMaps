package com.example.bettergmaps.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator

class MyCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator {
        return HostValidator.Builder(applicationContext)
            .addAllowedHost("androidx.car.app.sample")
            .addAllowedHost("com.google.android.projection.gearhead")
            .addAllowedHost("com.android.car")
            .build()
    }

    override fun onCreateSession(sessionInfo: SessionInfo): Session {
        return MyCarSession()
    }
}
