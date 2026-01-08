package com.example.bettergmaps.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

import androidx.car.app.SessionInfo

class MyCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator {
        return HostValidator.ALLOW_ALL_HOSTS_IN_DEBUG
    }

    override fun onCreateSession(sessionInfo: SessionInfo): Session {
        return MyCarSession()
    }
}
