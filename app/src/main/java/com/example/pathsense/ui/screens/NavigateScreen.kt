package com.example.pathsense.ui.screens

import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.pathsense.accessibility.AnnouncementPriority
import com.example.pathsense.accessibility.AudioFeedbackManager
import com.example.pathsense.accessibility.HapticFeedbackManager
import com.example.pathsense.accessibility.HapticPattern
import com.example.pathsense.accessibility.NavigationAnalysis
import com.example.pathsense.accessibility.SpatialDescriber
import com.example.pathsense.pipelines.PipelineCoordinator
import com.example.pathsense.pipelines.depth.DepthAnythingRunner
import com.example.pathsense.pipelines.depth.DepthSampler
import com.example.pathsense.pipelines.results.Proximity
import com.example.pathsense.ui.components.CameraViewWithOverlay
import com.example.pathsense.ui.components.FeedbackChip
import com.example.pathsense.ui.components.ModeIndicator
import com.example.pathsense.ui.components.NavigationZoneOverlay
import com.example.pathsense.ui.components.SpeakingIndicator
import com.example.pathsense.ui.components.ZoneDisplay
import kotlinx.coroutines.delay

/**
 * Navigate mode screen for depth-based obstacle detection.
 * Provides zone-based proximity alerts and path guidance.
 */
@Composable
fun NavigateScreen(
    previewView: PreviewView,
    coordinator: PipelineCoordinator,
    audioManager: AudioFeedbackManager,
    hapticManager: HapticFeedbackManager,
    spatialDescriber: SpatialDescriber,
    depthSampler: DepthSampler,
    showDepthVisualization: Boolean,
    highContrast: Boolean,
    modifier: Modifier = Modifier
) {
    // Collect depth results
    val depthMap by coordinator.depthMapState.collectAsState(initial = null)
    val depthResult by coordinator.depthState.collectAsState(initial = null)
    val isSpeaking by audioManager.isSpeaking.collectAsState()

    // Navigation state
    var navigationAnalysis by remember { mutableStateOf<NavigationAnalysis?>(null) }
    var zoneDisplays by remember { mutableStateOf<List<ZoneDisplay>>(emptyList()) }
    var lastAnnouncement by remember { mutableStateOf("Clear path ahead") }
    var wasPathClear by remember { mutableStateOf(true) }

    // Get depth visualization bitmap
    val depthVisualization: Bitmap? = depthResult?.depthViz

    // Analyze navigation zones when depth map updates
    LaunchedEffect(depthMap) {
        val depth = depthMap ?: return@LaunchedEffect

        // Sample all navigation zones
        val zoneResults = depthSampler.sampleNavigationZones(depth)

        // Convert to display format
        zoneDisplays = zoneResults.map { result ->
            ZoneDisplay(
                proximity = result.proximity,
                closeness = result.closenessValue
            )
        }

        // Analyze for navigation guidance
        val analysis = spatialDescriber.analyzeNavigationZones(zoneResults)
        navigationAnalysis = analysis

        // Generate announcement based on path status
        val announcement = analysis.toSpokenText()

        // Announce changes in path status
        if (analysis.clearPath != wasPathClear || announcement != lastAnnouncement) {
            wasPathClear = analysis.clearPath
            lastAnnouncement = announcement

            if (analysis.clearPath) {
                // Path cleared - announce with low priority
                audioManager.announce(announcement, AnnouncementPriority.NORMAL)
                hapticManager.trigger(HapticPattern.SUCCESS)
            } else {
                // Obstacle detected - announce with high priority
                val priority = when (analysis.primaryObstacle?.proximity) {
                    Proximity.NEAR -> AnnouncementPriority.IMMEDIATE
                    Proximity.MED -> AnnouncementPriority.HIGH
                    else -> AnnouncementPriority.NORMAL
                }
                audioManager.announce(announcement, priority, bypassDebounce = true)

                // Haptic warning based on proximity
                when (analysis.primaryObstacle?.proximity) {
                    Proximity.NEAR -> hapticManager.trigger(HapticPattern.ALERT)
                    Proximity.MED -> hapticManager.trigger(HapticPattern.WARNING)
                    else -> hapticManager.trigger(HapticPattern.PROXIMITY_FAR)
                }
            }
        }
    }

    // Periodic haptic feedback for very close obstacles
    LaunchedEffect(navigationAnalysis) {
        val analysis = navigationAnalysis ?: return@LaunchedEffect
        if (analysis.primaryObstacle?.proximity == Proximity.NEAR) {
            while (true) {
                delay(1500)
                // Re-check if still near
                val currentAnalysis = navigationAnalysis
                if (currentAnalysis?.primaryObstacle?.proximity == Proximity.NEAR) {
                    hapticManager.trigger(HapticPattern.WARNING)
                } else {
                    break
                }
            }
        }
    }

    // Track if depth is available (received at least one depth frame)
    var depthAvailable by remember { mutableStateOf(false) }
    var depthCheckDone by remember { mutableStateOf(false) }

    // Check for depth availability after a delay
    LaunchedEffect(Unit) {
        delay(3000)  // Wait 3 seconds for depth to initialize
        depthCheckDone = true
        if (depthMap == null) {
            audioManager.announce("Navigate mode requires depth sensor. Using Explore mode for object detection.", AnnouncementPriority.HIGH)
        }
    }

    LaunchedEffect(depthMap) {
        if (depthMap != null) {
            depthAvailable = true
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Camera with optional depth visualization
        CameraViewWithOverlay(
            previewView = previewView,
            showBoundingBoxes = false,
            depthVisualization = depthVisualization,
            showDepthVisualization = showDepthVisualization && depthAvailable,
            depthOverlayAlpha = 0.4f,
            modifier = Modifier.fillMaxSize()
        )

        // Navigation zone overlay (only if depth available)
        if (zoneDisplays.isNotEmpty() && depthAvailable) {
            NavigationZoneOverlay(
                zones = zoneDisplays,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Mode indicator (top left)
        ModeIndicator(
            modeName = "Navigate",
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            highContrast = highContrast
        )

        // Speaking indicator (top right)
        SpeakingIndicator(
            isSpeaking = isSpeaking,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            highContrast = highContrast
        )

        // Show unavailable message if depth not working
        if (depthCheckDone && !depthAvailable) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
                    .background(
                        color = if (highContrast) Color.Black.copy(alpha = 0.9f)
                               else MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Navigate Mode Unavailable",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (highContrast) Color.Yellow else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Depth estimation is not available on this device.\n\nUse Explore mode for object detection.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (highContrast) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        } else {
            // Navigation status chip (bottom center)
            FeedbackChip(
                text = if (depthAvailable) lastAnnouncement else "Initializing depth...",
                isSpeaking = isSpeaking,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                highContrast = highContrast
            )
        }
    }
}
