package com.das.tcamviewer2

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Explicitly bound to your package context namespace
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tcam_preferences")

class SettingsDataManager(private val context: Context) {

    companion object {
        val CAMERA_IP_KEY = stringPreferencesKey("camera_ip")
        val EXPORT_PICTURE_KEY = booleanPreferencesKey("export_picture")
        val EXPORT_METADATA_KEY = booleanPreferencesKey("export_metadata")
        val EXPORT_RESOLUTION_KEY = stringPreferencesKey("export_resolution")
        val MANUAL_RANGE_KEY = booleanPreferencesKey("manual_range")
        val MIN_VALUE_KEY = stringPreferencesKey("min_value")
        val MAX_VALUE_KEY = stringPreferencesKey("max_value")
    }

    val cameraIpFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[CAMERA_IP_KEY] ?: "192.168.4.1"
    }

    val exportPictureFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[EXPORT_PICTURE_KEY] ?: false
    }

    val exportMetadataFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[EXPORT_METADATA_KEY] ?: false
    }

    val exportResolutionFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[EXPORT_RESOLUTION_KEY] ?: "320x240"
    }

    val manualRangeFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[MANUAL_RANGE_KEY] ?: false // Default off
    }

    val minValueFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[MIN_VALUE_KEY] ?: "0" // Default min
    }

    val maxValueFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[MAX_VALUE_KEY] ?: "100" // Default max
    }

    // Write tasks
    suspend fun saveCameraIp(ip: String) {
        context.dataStore.edit { preferences -> preferences[CAMERA_IP_KEY] = ip }
    }

    suspend fun saveExportPicture(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[EXPORT_PICTURE_KEY] = enabled }
    }

    suspend fun saveExportMetadata(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[EXPORT_METADATA_KEY] = enabled }
    }

    suspend fun saveExportResolution(quality: String) {
        context.dataStore.edit { preferences -> preferences[EXPORT_RESOLUTION_KEY] = quality }
    }

    suspend fun saveManualRange(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[MANUAL_RANGE_KEY] = enabled }
    }

    suspend fun saveMinValue(value: String) {
        context.dataStore.edit { prefs -> prefs[MIN_VALUE_KEY] = value }
    }

    suspend fun saveMaxValue(value: String) {
        context.dataStore.edit { prefs -> prefs[MAX_VALUE_KEY] = value }
    }
}