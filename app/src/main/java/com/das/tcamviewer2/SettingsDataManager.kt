package com.das.tcamviewer2

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Explicitly bound to your package context namespace
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tcam_preferences")

class SettingsDataManager(context: Context) {
    private val appContext = context.applicationContext

    companion object {
        val CAMERA_IP_KEY = stringPreferencesKey("camera_ip")
        val EXPORT_PICTURE_KEY = booleanPreferencesKey("export_picture")
        val EXPORT_METADATA_KEY = booleanPreferencesKey("export_metadata")
        val EXPORT_RESOLUTION_KEY = stringPreferencesKey("export_resolution")
        val MANUAL_RANGE_KEY = booleanPreferencesKey("manual_range")
        val MIN_VALUE_KEY = stringPreferencesKey("min_value")
        val MAX_VALUE_KEY = stringPreferencesKey("max_value")
        val SELECTED_PALETTE_KEY = stringPreferencesKey("selected_palette")
        val SHUTTER_SOUND_KEY = booleanPreferencesKey("shutter_sound")
        val SPOTMETER_KEY = booleanPreferencesKey("spotmeter")
        val TEMPERATURE_UNIT_KEY = stringPreferencesKey("temperature_unit")
        val CAMERA_AGC_KEY       = booleanPreferencesKey("camera_agc")
        val CAMERA_EMISSIVITY_KEY = stringPreferencesKey("camera_emissivity")
        val CAMERA_GAIN_MODE_KEY = intPreferencesKey("camera_gain_mode")
    }

    val cameraIpFlow: Flow<String> = appContext.dataStore.data.map { preferences ->
        preferences[CAMERA_IP_KEY] ?: "192.168.4.1"
    }

    val exportPictureFlow: Flow<Boolean> = appContext.dataStore.data.map { preferences ->
        preferences[EXPORT_PICTURE_KEY] == true
    }

    val exportMetadataFlow: Flow<Boolean> = appContext.dataStore.data.map { preferences ->
        preferences[EXPORT_METADATA_KEY] == true
    }

    val exportResolutionFlow: Flow<String> = appContext.dataStore.data.map { preferences ->
        preferences[EXPORT_RESOLUTION_KEY] ?: "320x240"
    }

    val manualRangeFlow: Flow<Boolean> = appContext.dataStore.data.map { prefs ->
        prefs[MANUAL_RANGE_KEY] == true // Default off
    }

    val minValueFlow: Flow<String> = appContext.dataStore.data.map { prefs ->
        prefs[MIN_VALUE_KEY] ?: "0" // Default min
    }

    val maxValueFlow: Flow<String> = appContext.dataStore.data.map { prefs ->
        prefs[MAX_VALUE_KEY] ?: "100" // Default max
    }

    val selectedPaletteFlow: Flow<String> = appContext.dataStore.data.map { prefs ->
        prefs[SELECTED_PALETTE_KEY] ?: "Rainbow" // Default palette
    }

    val shutterSoundFlow: Flow<Boolean> = appContext.dataStore.data.map { prefs ->
        prefs[SHUTTER_SOUND_KEY] != false // Default palette
    }

    val spotmeterFlow: Flow<Boolean> = appContext.dataStore.data.map { prefs ->
        prefs[SPOTMETER_KEY] != false // Default palette
    }

    val temperatureUnitFlow: Flow<String> = appContext.dataStore.data.map { prefs ->
        prefs[TEMPERATURE_UNIT_KEY] ?: "Celsius"
    }

    val cameraAgcFlow: Flow<Boolean> = appContext.dataStore.data.map { prefs ->
        prefs[CAMERA_AGC_KEY] == true
    }

    val cameraEmissivityFlow: Flow<String> = appContext.dataStore.data.map { prefs ->
        prefs[CAMERA_EMISSIVITY_KEY] ?: "90"
    }

    val cameraGainModeFlow: Flow<Int> = appContext.dataStore.data.map { prefs ->
        prefs[CAMERA_GAIN_MODE_KEY] ?: 0
    }

    // Write tasks
    suspend fun saveCameraIp(ip: String) {
        appContext.dataStore.edit { preferences -> preferences[CAMERA_IP_KEY] = ip }
    }

    suspend fun saveExportPicture(enabled: Boolean) {
        appContext.dataStore.edit { preferences -> preferences[EXPORT_PICTURE_KEY] = enabled }
    }

    suspend fun saveExportMetadata(enabled: Boolean) {
        appContext.dataStore.edit { preferences -> preferences[EXPORT_METADATA_KEY] = enabled }
    }

    suspend fun saveExportResolution(quality: String) {
        appContext.dataStore.edit { preferences -> preferences[EXPORT_RESOLUTION_KEY] = quality }
    }

    suspend fun saveManualRange(enabled: Boolean) {
        appContext.dataStore.edit { prefs -> prefs[MANUAL_RANGE_KEY] = enabled }
    }

    suspend fun saveMinValue(value: String) {
        appContext.dataStore.edit { prefs -> prefs[MIN_VALUE_KEY] = value }
    }

    suspend fun saveMaxValue(value: String) {
        appContext.dataStore.edit { prefs -> prefs[MAX_VALUE_KEY] = value }
    }

    suspend fun saveSelectedPalette(palette: String) {
        appContext.dataStore.edit { prefs -> prefs[SELECTED_PALETTE_KEY] = palette }
    }

    suspend fun saveShutterSound(enabled: Boolean) {
        appContext.dataStore.edit { prefs -> prefs[SHUTTER_SOUND_KEY] = enabled }
    }

    suspend fun saveSpotmeter(enabled: Boolean) {
        appContext.dataStore.edit { prefs -> prefs[SPOTMETER_KEY] = enabled }
    }

    suspend fun saveTemperatureUnit(unit: String) {
        appContext.dataStore.edit { prefs -> prefs[TEMPERATURE_UNIT_KEY] = unit }
    }

    suspend fun saveCameraAgc(enabled: Boolean) {
        appContext.dataStore.edit { prefs -> prefs[CAMERA_AGC_KEY] = enabled }
    }

    suspend fun saveCameraEmissivity(value: String) {
        appContext.dataStore.edit { prefs -> prefs[CAMERA_EMISSIVITY_KEY] = value }
    }

    suspend fun saveCameraGainMode(mode: Int) {
        appContext.dataStore.edit { prefs -> prefs[CAMERA_GAIN_MODE_KEY] = mode }
    }

    // Get methods (one-shot retrieval)
    suspend fun getCameraIp(): String {
        return cameraIpFlow.first()
    }

    suspend fun getExportPicture(): Boolean {
        return exportPictureFlow.first()
    }

    suspend fun getExportMetadata(): Boolean {
        return exportMetadataFlow.first()
    }

    suspend fun getExportResolution(): String {
        return exportResolutionFlow.first()
    }

    suspend fun getManualRange(): Boolean {
        return manualRangeFlow.first()
    }

    suspend fun getMinValue(): String {
        return minValueFlow.first()
    }

    suspend fun getMaxValue(): String {
        return maxValueFlow.first()
    }

    suspend fun getSelectedPalette(): String {
        return selectedPaletteFlow.first()
    }

    suspend fun getShutterSound(): Boolean {
        return shutterSoundFlow.first()
    }

    suspend fun getSpotmeter(): Boolean {
        return spotmeterFlow.first()
    }

    suspend fun getTemperatureUnit(): String {
        return temperatureUnitFlow.first()
    }

    suspend fun isUnitsCelsius(): Boolean {
        return temperatureUnitFlow.first() == "Celsius"
    }

    suspend fun isUnitsFahrenheit(): Boolean {
        return temperatureUnitFlow.first() == "Fahrenheit"
    }

    suspend fun isManualRange(): Boolean {
        return manualRangeFlow.first()
    }

    suspend fun getManualMinTemperature(): Float {
        return minValueFlow.first().toFloat()
    }

    suspend fun getManualMaxTemperature(): Float {
        return maxValueFlow.first().toFloat()
    }
 }
