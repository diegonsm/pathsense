package com.example.pathsense.ui.screens

import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import com.example.pathsense.accessibility.AccessibilityPreferences
import com.example.pathsense.accessibility.AnnouncementPriority
import com.example.pathsense.accessibility.AudioFeedbackManager
import com.example.pathsense.accessibility.HapticFeedbackManager
import com.example.pathsense.accessibility.HapticPattern
import com.example.pathsense.accessibility.NavigationAnalysis
import com.example.pathsense.accessibility.SpatialDescriber
import com.example.pathsense.pipelines.DepthStatus
import com.example.pathsense.pipelines.PipelineCoordinator
import com.example.pathsense.pipelines.depth.DepthSampler
import com.example.pathsense.pipelines.detection.cocoLabel
import com.example.pathsense.pipelines.results.Detection
import com.example.pathsense.pipelines.results.Proximity
import com.example.pathsense.ui.components.CameraViewWithOverlay
import com.example.pathsense.ui.components.DetectionCountIndicator
import com.example.pathsense.ui.components.FeedbackChip
import com.example.pathsense.ui.components.ModeIndicator
import com.example.pathsense.ui.components.NavigationZoneOverlay
import com.example.pathsense.ui.components.SpeakingIndicator
import com.example.pathsense.ui.components.ZoneDisplay
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * "All" mode: runs OCR + object detection + (when available) depth navigation together.
 *
 * Navigation guidance is depth-prioritized:
 * - when an obstacle is detected in the navigation center zone, detection/text announcements are suppressed
 *   so the user gets clear navigation commands for impaired mobility.
 */
@Composable
fun AllScreen(
    previewView: PreviewView,
    coordinator: PipelineCoordinator,
    audioManager: AudioFeedbackManager,
    hapticManager: HapticFeedbackManager,
    preferences: AccessibilityPreferences,
    spatialDescriber: SpatialDescriber,
    depthSampler: DepthSampler,
    showBoundingBoxes: Boolean,
    showDepthVisualization: Boolean,
    highContrast: Boolean,
    modifier: Modifier = Modifier
) {
    val detResult by coordinator.detState.collectAsState(initial = null)
    val ocrResult by coordinator.ocrState.collectAsState(initial = null)
    val depthMap by coordinator.depthMapState.collectAsState(initial = null)
    val depthResult by coordinator.depthState.collectAsState(initial = null)
    val depthStatus by coordinator.depthStatus.collectAsState()
    val isSpeaking by audioManager.isSpeaking.collectAsState()

    val autoReadText by preferences.autoReadText.collectAsState(initial = true)
    val depthVisualization: Bitmap? = depthResult?.depthViz

    val depthAvailable = depthStatus is DepthStatus.Ready && depthMap != null

    // --- Navigation state (depth guidance) ---
    var navigationAnalysis by remember { mutableStateOf<NavigationAnalysis?>(null) }
    var zoneDisplays by remember { mutableStateOf<List<ZoneDisplay>>(emptyList()) }
    var wasPathClear by remember { mutableStateOf(true) }
    var lastGuidanceAnnouncement by remember { mutableStateOf("Clear path ahead. Continue forward.") }

    LaunchedEffect(depthStatus) {
        when (val status = depthStatus) {
            DepthStatus.Loading -> {
                audioManager.announce("Loading depth guidance", AnnouncementPriority.HIGH, bypassDebounce = true)
                hapticManager.trigger(HapticPattern.DETECTION)
            }

            DepthStatus.Ready -> {
                audioManager.announce("Depth guidance active", AnnouncementPriority.HIGH, bypassDebounce = true)
                hapticManager.trigger(HapticPattern.SUCCESS)
            }

            is DepthStatus.Failed -> {
                val msg = status.reason.ifBlank { "Depth model failed." }
                audioManager.announce(
                    "Depth guidance unavailable. $msg. Using detection and text feedback.",
                    AnnouncementPriority.IMMEDIATE,
                    bypassDebounce = true
                )
                hapticManager.trigger(HapticPattern.ALERT)
            }
        }
    }

    LaunchedEffect(depthMap, depthStatus) {
        if (depthStatus !is DepthStatus.Ready) return@LaunchedEffect
        val depth = depthMap ?: return@LaunchedEffect

        val zoneResults = depthSampler.sampleNavigationZones(depth)
        zoneDisplays = zoneResults.map { result ->
            ZoneDisplay(
                proximity = result.proximity,
                closeness = result.closenessValue
            )
        }

        val analysis = spatialDescriber.analyzeNavigationZones(zoneResults)
        navigationAnalysis = analysis
    }

    LaunchedEffect(navigationAnalysis, depthStatus) {
        if (depthStatus !is DepthStatus.Ready) return@LaunchedEffect
        val analysis = navigationAnalysis ?: return@LaunchedEffect

        val announcement = analysis.toSpokenText()
        val changed = analysis.clearPath != wasPathClear || announcement != lastGuidanceAnnouncement
        if (!changed) return@LaunchedEffect

        wasPathClear = analysis.clearPath
        lastGuidanceAnnouncement = announcement

        if (analysis.clearPath) {
            audioManager.announce(announcement, AnnouncementPriority.NORMAL)
            hapticManager.trigger(HapticPattern.SUCCESS)
        } else {
            val priority = when (analysis.primaryObstacle?.proximity) {
                Proximity.NEAR -> AnnouncementPriority.IMMEDIATE
                Proximity.MED -> AnnouncementPriority.HIGH
                else -> AnnouncementPriority.NORMAL
            }
            audioManager.announce(announcement, priority, bypassDebounce = true)

            when (analysis.primaryObstacle?.proximity) {
                Proximity.NEAR -> hapticManager.trigger(HapticPattern.ALERT)
                Proximity.MED -> hapticManager.trigger(HapticPattern.WARNING)
                else -> hapticManager.trigger(HapticPattern.PROXIMITY_FAR)
            }
        }
    }

    // Keep giving haptic warning while very near obstacle remains.
    LaunchedEffect(navigationAnalysis, depthStatus) {
        if (depthStatus !is DepthStatus.Ready) return@LaunchedEffect
        val analysis = navigationAnalysis ?: return@LaunchedEffect
        if (analysis.primaryObstacle?.proximity != Proximity.NEAR) return@LaunchedEffect

        while (isActive) {
            delay(1500)
            val current = navigationAnalysis
            if (current?.primaryObstacle?.proximity == Proximity.NEAR) {
                hapticManager.trigger(HapticPattern.WARNING)
            } else {
                break
            }
        }
    }

    // --- Enriched object detections (depth-proximity enrichment) ---
    var enrichedDetections by remember { mutableStateOf<List<Detection>>(emptyList()) }
    LaunchedEffect(detResult, depthMap) {
        val detections = detResult?.detections ?: emptyList()
        val depth = depthMap
        enrichedDetections = if (depth != null && detections.isNotEmpty()) {
            depthSampler.enrichDetections(depth, detections)
        } else {
            detections
        }
    }

    // --- OCR auto-read (suppressed when obstacle guidance is active) ---
    var currentText by remember { mutableStateOf("") }
    var lastAutoReadText by remember { mutableStateOf("") }

    // Suppress detection/text announcements only when guidance explicitly says "NOT clear".
    val obstacleActive = depthAvailable && navigationAnalysis?.clearPath == false
    LaunchedEffect(ocrResult, autoReadText, obstacleActive) {
        val newText = ocrResult?.text?.trim().orEmpty()
        if (newText.isEmpty() || newText == currentText) return@LaunchedEffect

        currentText = newText
        if (!autoReadText) return@LaunchedEffect
        if (newText == lastAutoReadText || newText.length <= 3) return@LaunchedEffect

        if (obstacleActive) return@LaunchedEffect

        lastAutoReadText = newText
        audioManager.announce(newText, AnnouncementPriority.NORMAL)
        hapticManager.trigger(HapticPattern.SUCCESS)
    }

    // --- Object detection announcements (suppressed when obstacle guidance is active) ---
    var lastDetectionAnnouncement by remember { mutableStateOf("") }
    LaunchedEffect(enrichedDetections, obstacleActive) {
        if (obstacleActive) return@LaunchedEffect

        if (enrichedDetections.isEmpty()) return@LaunchedEffect

        val descriptions = spatialDescriber.describeDetections(
            detections = enrichedDetections,
            labelProvider = ::cocoLabel
        )

        val announcement = spatialDescriber.generateSummaryAnnouncement(descriptions)
        if (announcement.isBlank() || announcement == lastDetectionAnnouncement) return@LaunchedEffect

        lastDetectionAnnouncement = announcement
        audioManager.announce(announcement, AnnouncementPriority.LOW)

        val closestProximity = descriptions.firstOrNull()?.proximity
        when (closestProximity) {
            Proximity.NEAR -> hapticManager.trigger(HapticPattern.PROXIMITY_NEAR)
            Proximity.MED -> hapticManager.trigger(HapticPattern.PROXIMITY_MEDIUM)
            else -> hapticManager.trigger(HapticPattern.DETECTION)
        }
    }

    // Periodic cleanup of the TTS debounce cache (matches Explore mode behavior).
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(10000)
            audioManager.cleanupDebounceCache()
        }
    }

    // --- UI ---
    val chipText = when (depthStatus) {
        DepthStatus.Loading -> "Loading guidance..."
        DepthStatus.Ready -> {
            navigationAnalysis?.toSpokenText() ?: "Waiting for depth frames..."
        }
        is DepthStatus.Failed -> "Depth guidance unavailable"
    }

    Box(modifier = modifier.fillMaxSize()) {
        CameraViewWithOverlay(
            previewView = previewView,
            detections = enrichedDetections,
            showBoundingBoxes = showBoundingBoxes,
            labelProvider = ::cocoLabel,
            depthVisualization = depthVisualization,
            showDepthVisualization = showDepthVisualization && depthAvailable,
            depthOverlayAlpha = 0.4f,
            modifier = Modifier.fillMaxSize()
        )

        if (zoneDisplays.isNotEmpty() && depthAvailable) {
            NavigationZoneOverlay(
                zones = zoneDisplays,
                modifier = Modifier.fillMaxSize()
            )
        }

        ModeIndicator(
            modeName = "All",
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            highContrast = highContrast
        )

        SpeakingIndicator(
            isSpeaking = isSpeaking,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            highContrast = highContrast
        )

        DetectionCountIndicator(
            count = enrichedDetections.size,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            highContrast = highContrast
        )

        FeedbackChip(
            text = chipText,
            isSpeaking = isSpeaking,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            highContrast = highContrast
        )

        // If depth is unavailable, show a quick hint on-screen as well.
        if (depthStatus is DepthStatus.Failed) {
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Navigation guidance unavailable",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (highContrast) Color.Yellow else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Using object detection + OCR feedback instead.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (highContrast) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}
