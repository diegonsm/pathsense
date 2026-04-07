package com.example.pathsense.ui.screens

import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.pathsense.accessibility.AccessibilityPreferences
import com.example.pathsense.accessibility.AnnouncementPriority
import com.example.pathsense.accessibility.AudioFeedbackManager
import com.example.pathsense.accessibility.HapticFeedbackManager
import com.example.pathsense.accessibility.HapticPattern
import com.example.pathsense.pipelines.PipelineCoordinator
import com.example.pathsense.ui.components.AccessibleButton
import com.example.pathsense.ui.components.AccessibleButtonStyle
import com.example.pathsense.ui.components.CameraViewWithOverlay
import com.example.pathsense.ui.components.ModeIndicator
import com.example.pathsense.ui.components.MuteTtsButton
import com.example.pathsense.ui.components.SpeakingIndicator

/**
 * Text mode screen for OCR with auto-read functionality.
 * Reads detected text aloud and displays it for sighted companions.
 */
@Composable
fun TextScreen(
    previewView: PreviewView,
    coordinator: PipelineCoordinator,
    audioManager: AudioFeedbackManager,
    hapticManager: HapticFeedbackManager,
    preferences: AccessibilityPreferences,
    highContrast: Boolean,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current

    // Collect OCR results
    val ocrResult by coordinator.ocrState.collectAsState(initial = null)
    val isSpeaking by audioManager.isSpeaking.collectAsState()
    val isMuted by audioManager.isMuted.collectAsState()

    // Collect preferences
    val autoReadText by preferences.autoReadText.collectAsState(initial = true)

    // Track detected text
    var currentText by remember { mutableStateOf("") }
    var lastAutoReadText by remember { mutableStateOf("") }

    // Update current text when OCR result changes
    LaunchedEffect(ocrResult) {
        val newText = ocrResult?.text?.trim() ?: ""
        if (newText.isNotEmpty() && newText != currentText) {
            currentText = newText

            // Auto-read if enabled and text has changed significantly
            // Normalize before comparing to avoid re-reads from whitespace/case jitter
            val normalizedNew = newText.trim().replace(Regex("\\s+"), " ").lowercase()
            val normalizedLast = lastAutoReadText.trim().replace(Regex("\\s+"), " ").lowercase()
            if (autoReadText && normalizedNew != normalizedLast && newText.length > 3) {
                lastAutoReadText = newText
                audioManager.announce(newText, AnnouncementPriority.NORMAL)
                hapticManager.trigger(HapticPattern.SUCCESS)
            }
        }
    }

    val backgroundColor = if (highContrast) Color.Black else MaterialTheme.colorScheme.surface
    val textColor = if (highContrast) Color.Yellow else MaterialTheme.colorScheme.onSurface

    // Full-screen camera with compact text panel overlay at the bottom
    Box(modifier = modifier.fillMaxSize()) {
        // Camera fills the entire screen (consistent with other tabs)
        CameraViewWithOverlay(
            previewView = previewView,
            showBoundingBoxes = false,
            modifier = Modifier.fillMaxSize()
        )

        // Mode indicator
        ModeIndicator(
            modeName = "Text",
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            highContrast = highContrast
        )

        // Speaking indicator
        SpeakingIndicator(
            isSpeaking = isSpeaking,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            highContrast = highContrast
        )

        // Compact text panel anchored to the bottom, sized to its content
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            color = backgroundColor.copy(alpha = 0.93f),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Mute button row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    MuteTtsButton(
                        isMuted = isMuted,
                        onToggle = { audioManager.toggleMute() },
                        highContrast = highContrast
                    )
                }

                // Detected text (scrollable, capped height so it doesn't take over the screen)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp, max = 120.dp)
                        .background(
                            color = if (highContrast) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                        .semantics {
                            contentDescription = if (currentText.isEmpty()) {
                                "No text detected. Point camera at text to read."
                            } else {
                                "Detected text: $currentText"
                            }
                        }
                ) {
                    if (currentText.isEmpty()) {
                        Text(
                            text = "Point camera at text to read",
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor.copy(alpha = 0.6f)
                        )
                    } else {
                        Text(
                            text = currentText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor,
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        )
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Restabilize button — clears current OCR state so next frame is treated as fresh
                    AccessibleButton(
                        text = "Restabilize",
                        onClick = {
                            audioManager.stop()
                            currentText = ""
                            lastAutoReadText = ""
                            hapticManager.trigger(HapticPattern.DOUBLE_TAP)
                        },
                        icon = Icons.Default.Refresh,
                        style = AccessibleButtonStyle.TONAL,
                        enabled = true,
                        highContrast = highContrast,
                        modifier = Modifier.weight(1f)
                    )

                    // Read Again button
                    AccessibleButton(
                        text = "Read Again",
                        onClick = {
                            if (currentText.isNotEmpty()) {
                                audioManager.stop()
                                audioManager.announce(currentText, AnnouncementPriority.HIGH, bypassDebounce = true)
                                hapticManager.trigger(HapticPattern.TAP)
                            }
                        },
                        icon = Icons.Default.VolumeUp,
                        style = AccessibleButtonStyle.FILLED,
                        enabled = currentText.isNotEmpty(),
                        highContrast = highContrast,
                        modifier = Modifier.weight(1f)
                    )

                    // Copy button
                    AccessibleButton(
                        text = "Copy",
                        onClick = {
                            if (currentText.isNotEmpty()) {
                                clipboardManager.setText(AnnotatedString(currentText))
                                audioManager.announce("Text copied", AnnouncementPriority.HIGH)
                                hapticManager.trigger(HapticPattern.SUCCESS)
                            }
                        },
                        icon = Icons.Default.ContentCopy,
                        style = AccessibleButtonStyle.TONAL,
                        enabled = currentText.isNotEmpty(),
                        highContrast = highContrast,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
