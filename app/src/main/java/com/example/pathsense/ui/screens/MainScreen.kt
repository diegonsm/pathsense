package com.example.pathsense.ui.screens

import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.pathsense.accessibility.AccessibilityPreferences
import com.example.pathsense.accessibility.AudioFeedbackManager
import com.example.pathsense.accessibility.HapticFeedbackManager
import com.example.pathsense.accessibility.HapticPattern
import com.example.pathsense.accessibility.SpatialDescriber
import com.example.pathsense.pipelines.PipelineCoordinator
import com.example.pathsense.pipelines.depth.DepthSampler
import com.example.pathsense.ui.components.AppMode
import com.example.pathsense.ui.components.ModeSelector
import com.example.pathsense.ui.theme.LocalAccessibilitySettings

/**
 * Main screen that hosts the camera preview and mode-based screens.
 * Manages navigation between Explore, Text, and Navigate modes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    previewView: PreviewView,
    coordinator: PipelineCoordinator,
    audioManager: AudioFeedbackManager,
    hapticManager: HapticFeedbackManager,
    preferences: AccessibilityPreferences,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accessibilitySettings = LocalAccessibilitySettings.current
    val highContrast = accessibilitySettings.highContrast

    var currentMode by remember { mutableStateOf(AppMode.EXPLORE) }

    // Collect preference states
    val showBoundingBoxes by preferences.showBoundingBoxes.collectAsState(initial = true)
    val showDepthVisualization by preferences.showDepthVisualization.collectAsState(initial = false)

    // Create shared utilities
    val spatialDescriber = remember { SpatialDescriber() }
    val depthSampler = remember { DepthSampler() }

    // Announce mode changes
    LaunchedEffect(currentMode) {
        hapticManager.trigger(HapticPattern.DOUBLE_TAP)
        audioManager.announceHigh("${currentMode.label} mode")
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { },
                actions = {
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.semantics {
                            contentDescription = "Settings"
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = if (highContrast) Color.Yellow else Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        bottomBar = {
            ModeSelector(
                currentMode = currentMode,
                onModeSelected = { newMode ->
                    if (newMode != currentMode) {
                        currentMode = newMode
                    }
                },
                highContrast = highContrast
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (currentMode) {
                AppMode.EXPLORE -> {
                    ExploreScreen(
                        previewView = previewView,
                        coordinator = coordinator,
                        audioManager = audioManager,
                        hapticManager = hapticManager,
                        spatialDescriber = spatialDescriber,
                        depthSampler = depthSampler,
                        showBoundingBoxes = showBoundingBoxes,
                        highContrast = highContrast
                    )
                }
                AppMode.TEXT -> {
                    TextScreen(
                        previewView = previewView,
                        coordinator = coordinator,
                        audioManager = audioManager,
                        hapticManager = hapticManager,
                        preferences = preferences,
                        highContrast = highContrast
                    )
                }
                AppMode.NAVIGATE -> {
                    NavigateScreen(
                        previewView = previewView,
                        coordinator = coordinator,
                        audioManager = audioManager,
                        hapticManager = hapticManager,
                        spatialDescriber = spatialDescriber,
                        depthSampler = depthSampler,
                        showDepthVisualization = showDepthVisualization,
                        highContrast = highContrast
                    )
                }
            }
        }
    }
}
