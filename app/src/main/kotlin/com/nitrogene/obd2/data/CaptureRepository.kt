package com.nitrogene.obd2.data

import android.content.Context
import com.nitrogene.obd2.core.obd.recorder.CaptureExporter
import com.nitrogene.obd2.core.obd.recorder.FaultCapture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists completed [FaultCapture] incidents as self-contained JSON files in `filesDir/captures/`.
 */
@Singleton
class CaptureRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val capturesDir = File(context.filesDir, "captures").apply { mkdirs() }
    private val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }

    private val _captures = MutableStateFlow<List<FaultCapture>>(loadAllFromDisk())
    val captures: StateFlow<List<FaultCapture>> = _captures.asStateFlow()

    suspend fun saveCapture(capture: FaultCapture) = withContext(Dispatchers.IO) {
        val target = File(capturesDir, "${capture.metadata.captureId}.json")
        target.writeText(CaptureExporter.toJson(capture))
        _captures.value = loadAllFromDisk()
    }

    suspend fun deleteCapture(captureId: String) = withContext(Dispatchers.IO) {
        File(capturesDir, "$captureId.json").delete()
        _captures.value = loadAllFromDisk()
    }

    suspend fun writeExportFiles(capture: FaultCapture): Pair<File, File> = withContext(Dispatchers.IO) {
        exportsDir.mkdirs()
        val baseName = "obd2_capture_${capture.metadata.captureId.take(8)}"
        val jsonFile = File(exportsDir, "$baseName.json")
        val csvFile = File(exportsDir, "$baseName.csv")
        jsonFile.writeText(CaptureExporter.toJson(capture))
        csvFile.writeText(CaptureExporter.toCsv(capture))
        jsonFile to csvFile
    }

    private fun loadAllFromDisk(): List<FaultCapture> {
        val files = capturesDir.listFiles { f -> f.extension == "json" } ?: return emptyList()
        return files.mapNotNull { file ->
            runCatching { CaptureExporter.fromJson(file.readText()) }.getOrNull()
        }.sortedByDescending { it.metadata.capturedAtIso }
    }
}
