package com.nitrogene.obd2.export

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.nitrogene.obd2.core.obd.recorder.CaptureExporter
import com.nitrogene.obd2.core.obd.recorder.FaultCapture
import com.nitrogene.obd2.data.CaptureRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates self-contained JSON + tabular CSV export files via [FileProvider] and provides
 * one-tap clipboard copy for AI assistant analysis.
 */
@Singleton
class CaptureShareHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val captureRepository: CaptureRepository,
) {
    fun copyJsonForAi(capture: FaultCapture) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        val jsonText = CaptureExporter.toJson(capture)
        clipboard.setPrimaryClip(ClipData.newPlainText("OBD2 Fault Capture JSON", jsonText))
    }

    suspend fun createShareBundleIntent(capture: FaultCapture): Intent {
        val (jsonFile, csvFile) = captureRepository.writeExportFiles(capture)
        val authority = "${context.packageName}.fileprovider"
        val jsonUri = FileProvider.getUriForFile(context, authority, jsonFile)
        val csvUri = FileProvider.getUriForFile(context, authority, csvFile)

        val uris = arrayListOf(jsonUri, csvUri)
        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(
                Intent.EXTRA_SUBJECT,
                "OBD2 Fault Capture ${capture.metadata.capturedAtIso}",
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(shareIntent, "Share OBD2 Fault Capture (JSON + CSV)")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
