package com.openhab.auto

import android.app.Application

class OpenHabAutoApp : Application() {
    lateinit var settingsManager: SettingsManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settingsManager = SettingsManager(this)
    }

    companion object {
        lateinit var instance: OpenHabAutoApp
            private set
    }
}
