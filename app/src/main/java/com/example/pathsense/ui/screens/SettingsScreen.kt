package com.example.pathsense.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.pathsense.accessibility.AccessibilityPreferences
import com.example.pathsense.accessibility.AudioFeedbackManager
import com.example.pathsense.accessibility.HapticFeedbackManager
import com.example.pathsense.accessibility.HapticPattern
import com.example.pathsense.ui.components.AccessibleButton
import com.example.pathsense.ui.components.AccessibleButtonStyle
import com.example.pathsense.ui.theme.LocalAccessibilitySettings
import kotlinx.coroutines.launch

/**
 * Settings screen for accessibility preferences.
 * Allows users to configure audio, haptic, and visual settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: AccessibilityPreferences,
    audioManager: AudioFeedbackManager,
    hapticManager: HapticFeedbackManager,
    onNavigateBack: () -> Unit,
    onHighContrastChanged: (Boolean) -> Unit,
    onLargeTextChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val accessibilitySettings = LocalAccessibilitySettings.current

    // Collect all preferences
    val speechRate by preferences.speechRate.collectAsState(initial = 1.0f)
    val speechPitch by preferences.speechPitch.collectAsState(initial = 1.0f)
    val hapticEnabled by preferences.hapticEnabled.collectAsState(initial = true)
    val autoReadText by preferences.autoReadText.collectAsState(initial = true)
    val highContrast by preferences.highContrast.collectAsState(initial = false)
    val largeText by preferences.largeText.collectAsState(initial = false)
    val confidenceThreshold by preferences.confidenceThreshold.collectAsState(initial = 0.35f)
    val showBoundingBoxes by preferences.showBoundingBoxes.collectAsState(initial = true)
    val showDepthVisualization by preferences.showDepthVisualization.collectAsState(initial = false)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.semantics {
                            contentDescription = "Go back"
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Audio Settings Section
            SettingsSection(title = "Audio") {
                // Speech Rate
                SliderSetting(
                    label = "Speech Rate",
                    value = speechRate,
                    valueRange = 0.5f..2.0f,
                    valueLabel = "${String.format("%.1f", speechRate)}x",
                    onValueChange = { newRate ->
                        scope.launch {
                            preferences.setSpeechRate(newRate)
                            audioManager.setSpeechRate(newRate)
                        }
                    },
                    contentDescription = "Speech rate: ${String.format("%.1f", speechRate)} times normal speed"
                )

                // Speech Pitch
                SliderSetting(
                    label = "Speech Pitch",
                    value = speechPitch,
                    valueRange = 0.5f..2.0f,
                    valueLabel = "${String.format("%.1f", speechPitch)}x",
                    onValueChange = { newPitch ->
                        scope.launch {
                            preferences.setSpeechPitch(newPitch)
                            audioManager.setSpeechPitch(newPitch)
                        }
                    },
                    contentDescription = "Speech pitch: ${String.format("%.1f", speechPitch)} times normal"
                )

                // Test Voice Button
                AccessibleButton(
                    text = "Test Voice",
                    onClick = {
                        audioManager.announce("This is a test of the voice settings.", bypassDebounce = true)
                    },
                    icon = Icons.Default.VolumeUp,
                    style = AccessibleButtonStyle.OUTLINED,
                    highContrast = accessibilitySettings.highContrast
                )
            }

            // Feedback Settings Section
            SettingsSection(title = "Feedback") {
                // Haptic Feedback Toggle
                SwitchSetting(
                    label = "Haptic Feedback",
                    description = "Vibration for alerts and confirmations",
                    checked = hapticEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            preferences.setHapticEnabled(enabled)
                            hapticManager.setEnabled(enabled)
                            if (enabled) {
                                hapticManager.trigger(HapticPattern.SUCCESS)
                            }
                        }
                    }
                )

                // Auto-Read Text Toggle
                SwitchSetting(
                    label = "Auto-Read Text",
                    description = "Automatically read detected text in Text mode",
                    checked = autoReadText,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            preferences.setAutoReadText(enabled)
                        }
                    }
                )
            }

            // Display Settings Section
            SettingsSection(title = "Display") {
                // High Contrast Toggle
                SwitchSetting(
                    label = "High Contrast",
                    description = "Yellow text on black background",
                    checked = highContrast,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            preferences.setHighContrast(enabled)
                            onHighContrastChanged(enabled)
                        }
                    }
                )

                // Large Text Toggle
                SwitchSetting(
                    label = "Large Text",
                    description = "Increase text size by 50%",
                    checked = largeText,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            preferences.setLargeText(enabled)
                            onLargeTextChanged(enabled)
                        }
                    }
                )

                // Show Bounding Boxes Toggle
                SwitchSetting(
                    label = "Show Bounding Boxes",
                    description = "Display detection boxes in Explore mode",
                    checked = showBoundingBoxes,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            preferences.setShowBoundingBoxes(enabled)
                        }
                    }
                )

                // Show Depth Visualization Toggle
                SwitchSetting(
                    label = "Show Depth Map",
                    description = "Display depth overlay in Navigate mode",
                    checked = showDepthVisualization,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            preferences.setShowDepthVisualization(enabled)
                        }
                    }
                )
            }

            // Detection Settings Section
            SettingsSection(title = "Detection") {
                // Confidence Threshold
                SliderSetting(
                    label = "Confidence Threshold",
                    value = confidenceThreshold,
                    valueRange = 0.1f..0.9f,
                    valueLabel = "${(confidenceThreshold * 100).toInt()}%",
                    onValueChange = { newThreshold ->
                        scope.launch {
                            preferences.setConfidenceThreshold(newThreshold)
                        }
                    },
                    contentDescription = "Detection confidence threshold: ${(confidenceThreshold * 100).toInt()} percent"
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        content()
    }
}

@Composable
private fun SwitchSetting(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "$label, $description, ${if (checked) "enabled" else "disabled"}"
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SliderSetting(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    contentDescription: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                this.contentDescription = contentDescription
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
