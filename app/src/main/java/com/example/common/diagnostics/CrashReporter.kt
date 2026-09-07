package com.example.common.diagnostics

import android.app.Application
import android.content.Intent
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashReporter {

    /**
     * Installs an uncaught exception handler that generates a standardized
     * crash report and launches the Android Share Sheet.
     *
     * Call as the very first line of Application.onCreate() or attachBaseContext().
     */
    fun install(app: Application) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val versionName = try {
                    app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "Unknown"
                } catch (_: Exception) {
                    "Unknown"
                }

                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val stackTrace = throwable.stackTraceToString()
                val messageText = throwable.message ?: "None"

                // Construct standardized plaintext payload
                val reportBody = buildString {
                    appendLine("=== APP CRASH REPORT ===")
                    appendLine("Package: ${app.packageName}")
                    appendLine("Version: $versionName")
                    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT}, Android ${Build.VERSION.RELEASE})")
                    appendLine("Thread: ${thread.name} (ID: ${thread.id})")
                    appendLine("Timestamp: $timestamp")
                    appendLine("Exception: ${throwable.javaClass.name}")
                    appendLine("Message: $messageText")
                    appendLine("--- STACK TRACE ---")
                    appendLine(stackTrace)
                    appendLine("=== END CRASH REPORT ===")
                }

                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Crash: ${app.packageName} ($versionName)")
                    putExtra(Intent.EXTRA_TEXT, reportBody)
                    // Explicit extras for zero-ambiguity ingestion by Orchestrator
                    putExtra("EXTRA_CRASH_PAYLOAD", reportBody)
                    putExtra("EXTRA_PACKAGE_NAME", app.packageName)
                    putExtra("EXTRA_STACK_TRACE", stackTrace)
                }

                val chooser = Intent.createChooser(sendIntent, "Share Crash Report...").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }

                app.startActivity(chooser)

                // Yield briefly to ensure the WindowManager schedules the chooser display
                Thread.sleep(800)
            } catch (_: Throwable) {
                // Fail silently to guarantee default termination executes
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
