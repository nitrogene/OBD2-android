package com.nitrogene.obd2.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nitrogene.obd2.core.obd.recorder.DefaultProfiles
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.recorderDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "recorder_prefs",
)

data class RecorderSettings(
    val selectedProfileId: String = DefaultProfiles.P0131_O2_B1S1.id,
    val tPreSeconds: Int = 60,
    val tPostSeconds: Int = 30,
    val gpsContextEnabled: Boolean = true,
)

@Singleton
class RecorderPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val SELECTED_PROFILE_ID = stringPreferencesKey("selected_profile_id")
        val T_PRE_SECONDS = intPreferencesKey("t_pre_seconds")
        val T_POST_SECONDS = intPreferencesKey("t_post_seconds")
        val GPS_CONTEXT_ENABLED = booleanPreferencesKey("gps_context_enabled")
    }

    val settings: Flow<RecorderSettings> = context.recorderDataStore.data.map { prefs ->
        RecorderSettings(
            selectedProfileId = prefs[Keys.SELECTED_PROFILE_ID]
                ?: DefaultProfiles.P0131_O2_B1S1.id,
            tPreSeconds = prefs[Keys.T_PRE_SECONDS] ?: 60,
            tPostSeconds = prefs[Keys.T_POST_SECONDS] ?: 30,
            gpsContextEnabled = prefs[Keys.GPS_CONTEXT_ENABLED] ?: true,
        )
    }

    suspend fun updateSettings(
        selectedProfileId: String? = null,
        tPreSeconds: Int? = null,
        tPostSeconds: Int? = null,
        gpsContextEnabled: Boolean? = null,
    ) {
        context.recorderDataStore.edit { prefs ->
            selectedProfileId?.let { prefs[Keys.SELECTED_PROFILE_ID] = it }
            tPreSeconds?.let { prefs[Keys.T_PRE_SECONDS] = it.coerceIn(15, 300) }
            tPostSeconds?.let { prefs[Keys.T_POST_SECONDS] = it.coerceIn(10, 120) }
            gpsContextEnabled?.let { prefs[Keys.GPS_CONTEXT_ENABLED] = it }
        }
    }
}
