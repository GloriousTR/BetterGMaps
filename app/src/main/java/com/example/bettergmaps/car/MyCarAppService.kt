package com.example.bettergmaps.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

class MyCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator {
        // In debug/development, allow all hosts. For production, use ALLOW_ALL_HOSTS_IN_DEBUG equivalent if possible or strict signature.
        return HostValidator.ALLOW_ALL_HOSTS_IN_DEBUG
    }

    override fun onCreateSession(): Session {
        return MyCarSession()
    }
}
