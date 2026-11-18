package com.nitrogene.obd2.diag

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import com.nitrogene.obd2.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-memory ring buffer of timestamped BLE / Wi-Fi / ELM327 diagnostic events for field debugging.
 */
object AppDiagLog {
    private const val MAX_LINES = 400
    private val lines = ArrayDeque<String>(MAX_LINES)
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun log(tag: String, message: String) {
        val ts = timeFormat.format(Date())
        if (lines.size >= MAX_LINES) {
            lines.removeFirst()
        }
        lines.addLast("$ts [$tag] $message")
    }

    @Synchronized
    fun dump(): String {
        val header = buildString {
            appendLine("=== OBD2 Companion Diagnostic Log ===")
            appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) built ${BuildConfig.BUILD_TIME}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})")
            appendLine("=====================================")
        }
        return header + lines.joinToString("\n")
    }

    @Synchronized
    fun clear() {
        lines.clear()
    }

    fun copyToClipboard(context: Context) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("OBD2 Diagnostic Log", dump()))
    }

    fun shareText(context: Context) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "OBD2 Companion Diagnostic Log")
            putExtra(Intent.EXTRA_TEXT, dump())
        }
        context.startActivity(Intent.createChooser(intent, "Share Diagnostic Logs"))
    }
}
