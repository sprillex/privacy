package com.example.common.diagnostics

import android.app.Application
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class CrashReporterTest {

    @Test
    fun install_setsUncaughtExceptionHandler() {
        val app = mockk<Application>(relaxed = true)
        val initialHandler = Thread.getDefaultUncaughtExceptionHandler()

        CrashReporter.install(app)

        val installedHandler = Thread.getDefaultUncaughtExceptionHandler()
        assertNotNull(installedHandler)
        assertNotEquals(initialHandler, installedHandler)
    }
}
