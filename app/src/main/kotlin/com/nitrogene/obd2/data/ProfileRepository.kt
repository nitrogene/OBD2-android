package com.nitrogene.obd2.data

import android.content.Context
import com.nitrogene.obd2.core.obd.recorder.DefaultProfiles
import com.nitrogene.obd2.core.obd.recorder.DtcPidProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists editable DTC-to-PID rule profiles as local JSON in `filesDir/profiles.json`.
 */
@Singleton
class ProfileRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val file = File(context.filesDir, "profiles.json")
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _profiles = MutableStateFlow<List<DtcPidProfile>>(loadInitial())
    val profiles: StateFlow<List<DtcPidProfile>> = _profiles.asStateFlow()

    suspend fun saveProfile(profile: DtcPidProfile) = withContext(Dispatchers.IO) {
        val updated = _profiles.value
            .filterNot { it.id == profile.id } + profile
        writeProfiles(updated)
    }

    suspend fun deleteProfile(profileId: String) = withContext(Dispatchers.IO) {
        val updated = _profiles.value.filterNot { it.id == profileId }
        writeProfiles(updated.ifEmpty { DefaultProfiles.ALL })
    }

    private fun loadInitial(): List<DtcPidProfile> {
        if (!file.exists()) {
            runCatching { file.writeText(json.encodeToString(DefaultProfiles.ALL)) }
            return DefaultProfiles.ALL
        }
        return runCatching {
            json.decodeFromString<List<DtcPidProfile>>(file.readText())
                .ifEmpty { DefaultProfiles.ALL }
        }.getOrElse { DefaultProfiles.ALL }
    }

    private fun writeProfiles(list: List<DtcPidProfile>) {
        file.writeText(json.encodeToString(list))
        _profiles.value = list
    }
}
