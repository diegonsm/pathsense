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
import androidx.compose.runtime.mutableStateMapOf
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

    // Per-label cooldown: tracks when each label was last announced (ms)
    val labelCooldowns = remember { mutableStateMapOf<String, Long>() }
    val labelCooldownMs = 7_000L

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

        val now = System.currentTimeMillis()

        // Get spatial descriptions, then filter out labels announced within the cooldown window
        val allDescriptions = spatialDescriber.describeDetections(
            detections = enrichedDetections,
            labelProvider = ::cocoLabel
        )
        val readyDescriptions = allDescriptions.filter { desc ->
            val lastSpoken = labelCooldowns[desc.label] ?: 0L
            now - lastSpoken >= labelCooldownMs
        }

        if (readyDescriptions.isEmpty()) return@LaunchedEffect

        val announcement = spatialDescriber.generateSummaryAnnouncement(readyDescriptions)

        if (announcement != lastAnnouncement) {
            lastAnnouncement = announcement
            // Record cooldown for every label included in this announcement
            readyDescriptions.forEach { labelCooldowns[it.label] = now }
            audioManager.announce(announcement)

            val closestProximity = readyDescriptions.firstOrNull()?.proximity
            when (closestProximity) {
                Proximity.NEAR -> hapticManager.trigger(HapticPattern.PROXIMITY_NEAR)
                Proximity.MED -> hapticManager.trigger(HapticPattern.PROXIMITY_MEDIUM)
                else -> hapticManager.trigger(HapticPattern.DETECTION)
            }
        }
    }

    // Periodically evict expired cooldowns so the map doesn't grow unbounded
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            val now = System.currentTimeMillis()
            labelCooldowns.entries.removeAll { (_, ts) -> now - ts >= labelCooldownMs }
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
