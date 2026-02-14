package com.example.pathsense.accessibility

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "accessibility_settings")

/**
 * Manages accessibility preferences using DataStore.
 * Provides reactive flows for all settings and methods to update them.
 */
class AccessibilityPreferences(private val context: Context) {

    private object Keys {
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val SPEECH_PITCH = floatPreferencesKey("speech_pitch")
        val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        val AUTO_READ_TEXT = booleanPreferencesKey("auto_read_text")
        val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        val LARGE_TEXT = booleanPreferencesKey("large_text")
        val CONFIDENCE_THRESHOLD = floatPreferencesKey("confidence_threshold")
        val SHOW_BOUNDING_BOXES = booleanPreferencesKey("show_bounding_boxes")
        val SHOW_DEPTH_VISUALIZATION = booleanPreferencesKey("show_depth_visualization")
    }

    // Default values
    companion object {
        const val DEFAULT_SPEECH_RATE = 1.0f
        const val DEFAULT_SPEECH_PITCH = 1.0f
        const val DEFAULT_HAPTIC_ENABLED = true
        const val DEFAULT_AUTO_READ_TEXT = true
        const val DEFAULT_HIGH_CONTRAST = false
        const val DEFAULT_LARGE_TEXT = false
        const val DEFAULT_CONFIDENCE_THRESHOLD = 0.35f
        const val DEFAULT_SHOW_BOUNDING_BOXES = true
        const val DEFAULT_SHOW_DEPTH_VISUALIZATION = false
    }

    // Speech rate (0.5x - 2.0x)
    val speechRate: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[Keys.SPEECH_RATE] ?: DEFAULT_SPEECH_RATE
    }

    suspend fun setSpeechRate(rate: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SPEECH_RATE] = rate.coerceIn(0.5f, 2.0f)
        }
    }

    // Speech pitch (0.5x - 2.0x)
    val speechPitch: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[Keys.SPEECH_PITCH] ?: DEFAULT_SPEECH_PITCH
    }

    suspend fun setSpeechPitch(pitch: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SPEECH_PITCH] = pitch.coerceIn(0.5f, 2.0f)
        }
    }

    // Haptic feedback enabled
    val hapticEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.HAPTIC_ENABLED] ?: DEFAULT_HAPTIC_ENABLED
    }

    suspend fun setHapticEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HAPTIC_ENABLED] = enabled
        }
    }

    // Auto-read detected text
    val autoReadText: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.AUTO_READ_TEXT] ?: DEFAULT_AUTO_READ_TEXT
    }

    suspend fun setAutoReadText(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTO_READ_TEXT] = enabled
        }
    }

    // High contrast mode
    val highContrast: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.HIGH_CONTRAST] ?: DEFAULT_HIGH_CONTRAST
    }

    suspend fun setHighContrast(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HIGH_CONTRAST] = enabled
        }
    }

    // Large text mode
    val largeText: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.LARGE_TEXT] ?: DEFAULT_LARGE_TEXT
    }

    suspend fun setLargeText(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LARGE_TEXT] = enabled
        }
    }

    // Detection confidence threshold (0.0 - 1.0)
    val confidenceThreshold: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[Keys.CONFIDENCE_THRESHOLD] ?: DEFAULT_CONFIDENCE_THRESHOLD
    }

    suspend fun setConfidenceThreshold(threshold: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CONFIDENCE_THRESHOLD] = threshold.coerceIn(0.1f, 0.9f)
        }
    }

    // Show bounding boxes overlay
    val showBoundingBoxes: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.SHOW_BOUNDING_BOXES] ?: DEFAULT_SHOW_BOUNDING_BOXES
    }

    suspend fun setShowBoundingBoxes(show: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SHOW_BOUNDING_BOXES] = show
        }
    }

    // Show depth visualization
    val showDepthVisualization: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.SHOW_DEPTH_VISUALIZATION] ?: DEFAULT_SHOW_DEPTH_VISUALIZATION
    }

    suspend fun setShowDepthVisualization(show: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SHOW_DEPTH_VISUALIZATION] = show
        }
    }
}
