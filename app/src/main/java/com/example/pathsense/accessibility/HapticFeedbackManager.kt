package com.example.pathsense.accessibility

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Haptic feedback patterns for different events.
 */
enum class HapticPattern {
    /** Short tap for confirmations and selections */
    TAP,
    /** Double tap for mode changes */
    DOUBLE_TAP,
    /** Medium vibration for warnings */
    WARNING,
    /** Strong pulsing for critical alerts */
    ALERT,
    /** Pleasant pattern for success feedback */
    SUCCESS,
    /** Quick buzz for object detection */
    DETECTION,
    /** Proximity warning - intensity varies with distance */
    PROXIMITY_NEAR,
    PROXIMITY_MEDIUM,
    PROXIMITY_FAR
}

/**
 * Manages haptic (vibration) feedback for accessibility.
 * Provides various vibration patterns for different events.
 */
class HapticFeedbackManager(
    private val context: Context,
    private val preferences: AccessibilityPreferences
) {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private var isEnabled = true

    /**
     * Observe haptic enabled state from preferences.
     */
    val hapticEnabledFlow: Flow<Boolean> = preferences.hapticEnabled

    /**
     * Update the enabled state (called from preference changes).
     */
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    /**
     * Trigger haptic feedback with the specified pattern.
     * Does nothing if haptic feedback is disabled.
     */
    fun trigger(pattern: HapticPattern) {
        if (!isEnabled || !vibrator.hasVibrator()) return

        val effect = when (pattern) {
            HapticPattern.TAP -> createTapEffect()
            HapticPattern.DOUBLE_TAP -> createDoubleTapEffect()
            HapticPattern.WARNING -> createWarningEffect()
            HapticPattern.ALERT -> createAlertEffect()
            HapticPattern.SUCCESS -> createSuccessEffect()
            HapticPattern.DETECTION -> createDetectionEffect()
            HapticPattern.PROXIMITY_NEAR -> createProximityNearEffect()
            HapticPattern.PROXIMITY_MEDIUM -> createProximityMediumEffect()
            HapticPattern.PROXIMITY_FAR -> createProximityFarEffect()
        }

        vibrator.vibrate(effect)
    }

    /**
     * Trigger haptic feedback only if enabled in preferences.
     * This suspends to check the preference.
     */
    suspend fun triggerIfEnabled(pattern: HapticPattern) {
        val enabled = preferences.hapticEnabled.first()
        if (enabled) {
            trigger(pattern)
        }
    }

    private fun createTapEffect(): VibrationEffect {
        return VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
    }

    private fun createDoubleTapEffect(): VibrationEffect {
        val timings = longArrayOf(0, 50, 100, 50)
        val amplitudes = intArrayOf(0, 200, 0, 200)
        return VibrationEffect.createWaveform(timings, amplitudes, -1)
    }

    private fun createWarningEffect(): VibrationEffect {
        val timings = longArrayOf(0, 100, 50, 100)
        val amplitudes = intArrayOf(0, 150, 0, 200)
        return VibrationEffect.createWaveform(timings, amplitudes, -1)
    }

    private fun createAlertEffect(): VibrationEffect {
        val timings = longArrayOf(0, 150, 100, 150, 100, 200)
        val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
        return VibrationEffect.createWaveform(timings, amplitudes, -1)
    }

    private fun createSuccessEffect(): VibrationEffect {
        val timings = longArrayOf(0, 50, 50, 100)
        val amplitudes = intArrayOf(0, 100, 0, 200)
        return VibrationEffect.createWaveform(timings, amplitudes, -1)
    }

    private fun createDetectionEffect(): VibrationEffect {
        return VibrationEffect.createOneShot(30, 100)
    }

    private fun createProximityNearEffect(): VibrationEffect {
        // Strong, fast pulse for very close objects
        val timings = longArrayOf(0, 100, 50, 100, 50, 100)
        val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
        return VibrationEffect.createWaveform(timings, amplitudes, -1)
    }

    private fun createProximityMediumEffect(): VibrationEffect {
        // Medium intensity for medium distance
        val timings = longArrayOf(0, 75, 75, 75)
        val amplitudes = intArrayOf(0, 180, 0, 180)
        return VibrationEffect.createWaveform(timings, amplitudes, -1)
    }

    private fun createProximityFarEffect(): VibrationEffect {
        // Light tap for distant objects
        return VibrationEffect.createOneShot(40, 80)
    }

    /**
     * Cancel any ongoing vibration.
     */
    fun cancel() {
        vibrator.cancel()
    }

    /**
     * Check if the device has a vibrator.
     */
    fun hasVibrator(): Boolean = vibrator.hasVibrator()
}
