package com.example.pathsense.ui.screens

import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pathsense.accessibility.AudioFeedbackManager
import com.example.pathsense.accessibility.HapticFeedbackManager
import com.example.pathsense.accessibility.HapticPattern
import com.example.pathsense.accessibility.SpatialDescriber
import com.example.pathsense.pipelines.PipelineCoordinator
import com.example.pathsense.pipelines.depth.DepthSampler
import com.example.pathsense.pipelines.detection.cocoLabel
import com.example.pathsense.pipelines.results.Detection
import com.example.pathsense.pipelines.results.Proximity
import com.example.pathsense.ui.components.CameraViewWithOverlay
import com.example.pathsense.ui.components.DetectionCountIndicator
import com.example.pathsense.ui.components.FeedbackChip
import com.example.pathsense.ui.components.ModeIndicator
import com.example.pathsense.ui.components.SpeakingIndicator
import kotlinx.coroutines.delay

/**
 * Explore mode screen for object detection with spatial audio feedback.
 * Announces detected objects using clock orientation (12 o'clock = ahead).
 */
@Composable
fun ExploreScreen(
    previewView: PreviewView,
    coordinator: PipelineCoordinator,
    audioManager: AudioFeedbackManager,
    hapticManager: HapticFeedbackManager,
    spatialDescriber: SpatialDescriber,
    depthSampler: DepthSampler,
    showBoundingBoxes: Boolean,
    highContrast: Boolean,
    modifier: Modifier = Modifier
) {
    // Collect detection results
    val detResult by coordinator.detState.collectAsState(initial = null)
    val depthMap by coordinator.depthMapState.collectAsState(initial = null)
    val isSpeaking by audioManager.isSpeaking.collectAsState()

    // Track last announced detection to avoid repetition
    var lastAnnouncement by remember { mutableStateOf("") }
    var enrichedDetections by remember { mutableStateOf<List<Detection>>(emptyList()) }

    // Enrich detections with depth info
    LaunchedEffect(detResult, depthMap) {
        val detections = detResult?.detections ?: emptyList()
        val depth = depthMap

        enrichedDetections = if (depth != null && detections.isNotEmpty()) {
            depthSampler.enrichDetections(depth, detections)
        } else {
            detections
        }
    }

    // Announce detections with spatial orientation
    LaunchedEffect(enrichedDetections) {
        if (enrichedDetections.isEmpty()) return@LaunchedEffect

        // Get spatial descriptions
        val descriptions = spatialDescriber.describeDetections(
            detections = enrichedDetections,
            labelProvider = ::cocoLabel
        )

        if (descriptions.isEmpty()) return@LaunchedEffect

        // Generate announcement
        val announcement = spatialDescriber.generateSummaryAnnouncement(descriptions)

        // Only announce if different from last announcement (debouncing handled by AudioFeedbackManager)
        if (announcement != lastAnnouncement) {
            lastAnnouncement = announcement
            audioManager.announce(announcement)

            // Haptic feedback based on closest proximity
            val closestProximity = descriptions.firstOrNull()?.proximity
            when (closestProximity) {
                Proximity.NEAR -> hapticManager.trigger(HapticPattern.PROXIMITY_NEAR)
                Proximity.MED -> hapticManager.trigger(HapticPattern.PROXIMITY_MEDIUM)
                else -> hapticManager.trigger(HapticPattern.DETECTION)
            }
        }
    }

    // Periodic cleanup of audio manager debounce cache
    LaunchedEffect(Unit) {
        while (true) {
            delay(10000)
            audioManager.cleanupDebounceCache()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Camera with detection overlay
        CameraViewWithOverlay(
            previewView = previewView,
            detections = enrichedDetections,
            showBoundingBoxes = showBoundingBoxes,
            labelProvider = ::cocoLabel,
            modifier = Modifier.fillMaxSize()
        )

        // Mode indicator (top left)
        ModeIndicator(
            modeName = "Explore",
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

        // Detection count (bottom left, above nav bar)
        DetectionCountIndicator(
            count = enrichedDetections.size,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            highContrast = highContrast
        )

        // Last announcement chip (bottom center)
        if (lastAnnouncement.isNotEmpty()) {
            FeedbackChip(
                text = lastAnnouncement,
                isSpeaking = isSpeaking,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                highContrast = highContrast
            )
        }
    }
}
