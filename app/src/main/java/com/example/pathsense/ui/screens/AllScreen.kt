package com.example.pathsense.ui.screens

import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pathsense.accessibility.AnnouncementPriority
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
 * All mode: object detection, OCR, and depth running concurrently.
 *
 * Announcement priority:
 *   1. NEAR obstacle (depth-confirmed) → IMMEDIATE
 *   2. Person / vehicle detected       → HIGH
 *   3. Stable OCR text (>10 chars)     → NORMAL
 *   4. Other objects (with cooldown)   → NORMAL
 */
@Composable
fun AllScreen(
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
    val detResult by coordinator.detState.collectAsState(initial = null)
    val depthMap by coordinator.depthMapState.collectAsState(initial = null)
    val ocrResult by coordinator.ocrState.collectAsState(initial = null)
    val isSpeaking by audioManager.isSpeaking.collectAsState()

    val labelCooldowns = remember { mutableStateMapOf<String, Long>() }
    val labelCooldownMs = 7_000L

    var enrichedDetections by remember { mutableStateOf<List<Detection>>(emptyList()) }
    var lastOcrText by remember { mutableStateOf("") }
    var lastAnnouncement by remember { mutableStateOf("") }

    // Enrich detections with depth proximity
    LaunchedEffect(detResult, depthMap) {
        val detections = detResult?.detections ?: emptyList()
        val depth = depthMap
        enrichedDetections = if (depth != null && detections.isNotEmpty()) {
            depthSampler.enrichDetections(depth, detections)
        } else {
            detections
        }
    }

    // Detection + depth announcements
    LaunchedEffect(enrichedDetections) {
        if (enrichedDetections.isEmpty()) return@LaunchedEffect

        val now = System.currentTimeMillis()

        // NEAR obstacles get immediate priority regardless of cooldown
        val nearObjects = enrichedDetections.filter { it.proximity == Proximity.NEAR }
        if (nearObjects.isNotEmpty()) {
            val names = nearObjects.take(2).joinToString(" and ") { cocoLabel(it.classId) }
            val announcement = "$names very close"
            if (announcement != lastAnnouncement) {
                lastAnnouncement = announcement
                audioManager.announce(announcement, AnnouncementPriority.IMMEDIATE, bypassDebounce = true)
                hapticManager.trigger(HapticPattern.ALERT)
                nearObjects.forEach { labelCooldowns[cocoLabel(it.classId)] = now }
            }
            return@LaunchedEffect
        }

        // Non-NEAR objects: respect cooldowns, use priority sorting
        val descriptions = spatialDescriber.describeDetections(
            detections = enrichedDetections,
            labelProvider = ::cocoLabel
        )
        val ready = descriptions.filter { desc ->
            now - (labelCooldowns[desc.label] ?: 0L) >= labelCooldownMs
        }
        if (ready.isEmpty()) return@LaunchedEffect

        val announcement = spatialDescriber.generateSummaryAnnouncement(ready)
        if (announcement != lastAnnouncement) {
            lastAnnouncement = announcement
            ready.forEach { labelCooldowns[it.label] = now }
            audioManager.announce(announcement, AnnouncementPriority.NORMAL)
            hapticManager.trigger(HapticPattern.DETECTION)
        }
    }

    // OCR announcements — only when text is stable and meaningfully long
    LaunchedEffect(ocrResult) {
        val text = ocrResult?.text?.trim() ?: return@LaunchedEffect
        if (text.length > 10 && text != lastOcrText) {
            lastOcrText = text
            // Only read text if no NEAR obstacle is active
            val hasNearObstacle = enrichedDetections.any { it.proximity == Proximity.NEAR }
            if (!hasNearObstacle) {
                audioManager.announce(text, AnnouncementPriority.NORMAL)
                hapticManager.trigger(HapticPattern.SUCCESS)
            }
        }
    }

    // Periodic cooldown cleanup
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            val now = System.currentTimeMillis()
            labelCooldowns.entries.removeAll { (_, ts) -> now - ts >= labelCooldownMs }
            audioManager.cleanupDebounceCache()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        CameraViewWithOverlay(
            previewView = previewView,
            detections = enrichedDetections,
            showBoundingBoxes = showBoundingBoxes,
            labelProvider = ::cocoLabel,
            modifier = Modifier.fillMaxSize()
        )

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
