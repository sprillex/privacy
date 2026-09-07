package com.example.privacy

import android.app.Application
import com.example.common.diagnostics.CrashReporter

class PrivacyApplication : Application() {
    override fun onCreate() {
        CrashReporter.install(this)
        super.onCreate()
    }
}
