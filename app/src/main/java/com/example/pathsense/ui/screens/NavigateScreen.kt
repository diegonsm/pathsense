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
import com.example.pathsense.accessibility.AnnouncementPriority
import com.example.pathsense.accessibility.AudioFeedbackManager
import com.example.pathsense.accessibility.ClockPosition
import com.example.pathsense.accessibility.HapticFeedbackManager
import com.example.pathsense.accessibility.HapticPattern
import com.example.pathsense.accessibility.NavigationAnalysis
import com.example.pathsense.accessibility.NavigationZone
import com.example.pathsense.accessibility.SpatialDescriber
import com.example.pathsense.pipelines.PipelineCoordinator
import com.example.pathsense.pipelines.depth.DepthSampler
import com.example.pathsense.pipelines.detection.cocoLabel
import com.example.pathsense.pipelines.results.Detection
import com.example.pathsense.pipelines.results.Proximity
import com.example.pathsense.ui.components.CameraViewWithOverlay
import com.example.pathsense.ui.components.FeedbackChip
import com.example.pathsense.ui.components.ModeIndicator
import com.example.pathsense.ui.components.MuteTtsButton
import com.example.pathsense.ui.components.NavigationZoneOverlay
import com.example.pathsense.ui.components.SpeakingIndicator
import com.example.pathsense.ui.components.ZoneDisplay
import kotlinx.coroutines.delay

// Only objects that genuinely block or endanger a pedestrian's path.
private val NAVIGATION_OBSTACLES = setOf(
    "person", "car", "truck", "bus", "motorcycle", "bicycle",
    "chair", "bench", "couch", "dining table", "bed",
    "dog", "cat", "suitcase", "backpack", "potted plant",
    "fire hydrant", "stop sign", "traffic light"
)

private const val MIN_CONFIDENCE = 0.45f
private const val NEAR_REANNOUNCE_MS = 2_000L
private const val COOLDOWN_MS = 6_000L

/**
 * Navigate mode: real-time navigation guidance for visually impaired users.
 *
 * Outputs actionable spoken instructions ("Move slightly left", "Stop! person ahead")
 * based on object detection + optional depth estimation. Detection always runs;
 * depth enriches proximity information when the model is available.
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
    // Pipeline state
    val depthMap by coordinator.depthMapState.collectAsState(initial = null)
    val depthResult by coordinator.depthState.collectAsState(initial = null)
    val detResult by coordinator.detState.collectAsState(initial = null)
    val isSpeaking by audioManager.isSpeaking.collectAsState()
    val isMuted by audioManager.isMuted.collectAsState()

    // Derived display state
    var enrichedDetections by remember { mutableStateOf<List<Detection>>(emptyList()) }
    var navigationAnalysis by remember { mutableStateOf<NavigationAnalysis?>(null) }
    var zoneDisplays by remember { mutableStateOf<List<ZoneDisplay>>(emptyList()) }

    // Announcement throttle state
    var lastInstruction by remember { mutableStateOf("") }
    var lastInstructionPriority by remember { mutableStateOf(AnnouncementPriority.NORMAL) }
    var lastInstructionMs by remember { mutableStateOf(0L) }

    // ── Effect A: Enrich detections with depth proximity ─────────────────────
    LaunchedEffect(detResult, depthMap) {
        val detections = detResult?.detections ?: emptyList()
        enrichedDetections = if (depthMap != null && detections.isNotEmpty()) {
            depthSampler.enrichDetections(depthMap!!, detections)
        } else {
            detections
        }
    }

    // ── Effect B: Depth zone analysis (state only, no announcements) ──────────
    LaunchedEffect(depthMap) {
        val depth = depthMap ?: run {
            zoneDisplays = emptyList()
            navigationAnalysis = null
            return@LaunchedEffect
        }
        val zoneResults = depthSampler.sampleNavigationZones(depth)
        zoneDisplays = zoneResults.map { ZoneDisplay(it.proximity, it.closenessValue) }
        navigationAnalysis = spatialDescriber.analyzeNavigationZones(zoneResults)
    }

    // ── Effect C: Navigation guidance announcement ────────────────────────────
    LaunchedEffect(enrichedDetections, navigationAnalysis) {
        val now = System.currentTimeMillis()
        val (instruction, priority) = buildGuidanceInstruction(
            enrichedDetections, navigationAnalysis, spatialDescriber
        )

        val isNear = priority == AnnouncementPriority.IMMEDIATE
        val changed = instruction != lastInstruction
        val cooldownExpired = now - lastInstructionMs >= COOLDOWN_MS
        val nearReannounce = isNear && now - lastInstructionMs >= NEAR_REANNOUNCE_MS

        if (changed || cooldownExpired || nearReannounce) {
            lastInstruction = instruction
            lastInstructionPriority = priority
            lastInstructionMs = now
            audioManager.announce(instruction, priority, bypassDebounce = isNear)
            when (priority) {
                AnnouncementPriority.IMMEDIATE -> hapticManager.trigger(HapticPattern.ALERT)
                AnnouncementPriority.HIGH -> hapticManager.trigger(HapticPattern.WARNING)
                else -> hapticManager.trigger(HapticPattern.SUCCESS)
            }
        }
    }

    // ── Effect D: Force re-announcement for persistent NEAR danger ────────────
    LaunchedEffect(Unit) {
        while (true) {
            delay(NEAR_REANNOUNCE_MS)
            if (lastInstructionPriority == AnnouncementPriority.IMMEDIATE) {
                // Reset timestamp so the next detection frame triggers a fresh announcement
                lastInstructionMs = 0L
            }
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Box(modifier = modifier.fillMaxSize()) {

        // Camera preview with bounding boxes and optional depth overlay
        CameraViewWithOverlay(
            previewView = previewView,
            showPreview = false,
            showBoundingBoxes = true,
            detections = enrichedDetections,
            labelProvider = ::cocoLabel,
            sourceFrameWidth = detResult?.frameWidth,
            sourceFrameHeight = detResult?.frameHeight,
            depthVisualization = depthResult?.depthViz,
            showDepthVisualization = showDepthVisualization && depthMap != null,
            depthOverlayAlpha = 0.4f,
            modifier = Modifier.fillMaxSize()
        )

        // Zone proximity grid — only shown when depth model is running
        if (zoneDisplays.isNotEmpty()) {
            NavigationZoneOverlay(
                zones = zoneDisplays,
                modifier = Modifier.fillMaxSize()
            )
        }

        ModeIndicator(
            modeName = "Navigate",
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

        MuteTtsButton(
            isMuted = isMuted,
            onToggle = { audioManager.toggleMute() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            highContrast = highContrast
        )

        // Current guidance instruction shown at the bottom
        FeedbackChip(
            text = lastInstruction.ifEmpty { "Scanning..." },
            isSpeaking = isSpeaking,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            highContrast = highContrast
        )
    }
}

/**
 * Builds a single actionable navigation instruction from the available sensor data.
 * Detection is always used; depth enriches proximity when available.
 *
 * Returns a (instruction string, priority) pair.
 */
private fun buildGuidanceInstruction(
    detections: List<Detection>,
    navigationAnalysis: NavigationAnalysis?,
    spatialDescriber: SpatialDescriber
): Pair<String, AnnouncementPriority> {

    // Filter to navigation-relevant obstacles above confidence threshold,
    // sorted so closest (NEAR) objects come first
    val obstacles = detections
        .filter { cocoLabel(it.classId) in NAVIGATION_OBSTACLES && it.score >= MIN_CONFIDENCE }
        .sortedWith(compareBy { proximityOrder(it.proximity) })

    // If no relevant detections and depth says clear (or unavailable), we're good
    if (obstacles.isEmpty()) {
        return if (navigationAnalysis == null || navigationAnalysis.clearPath) {
            "Path is clear" to AnnouncementPriority.NORMAL
        } else {
            // Depth sees something but detection didn't classify it — use zone description
            buildDepthOnlyInstruction(navigationAnalysis)
        }
    }

    // Use the highest-priority (closest) obstacle as the primary subject
    val primary = obstacles.first()
    val label = cocoLabel(primary.classId)
    val centerX = (primary.left + primary.right) / 2f
    val clock = spatialDescriber.getClockPosition(centerX)

    // If depth is available, use depth-confirmed proximity; otherwise use detection-default
    val proximity = primary.proximity

    return buildInstruction(label, clock, proximity)
}

/**
 * Generates instruction text + priority from a named object's position and distance.
 */
private fun buildInstruction(
    label: String,
    clock: ClockPosition,
    proximity: Proximity
): Pair<String, AnnouncementPriority> = when {

    // ── Directly ahead ────────────────────────────────────────────────────────
    clock == ClockPosition.TWELVE_OCLOCK && proximity == Proximity.NEAR ->
        "Stop! $label directly ahead" to AnnouncementPriority.IMMEDIATE

    clock == ClockPosition.TWELVE_OCLOCK && proximity == Proximity.MED ->
        "$label ahead, slow down" to AnnouncementPriority.HIGH

    clock == ClockPosition.TWELVE_OCLOCK ->
        "$label ahead" to AnnouncementPriority.NORMAL

    // ── Slightly left (11 o'clock) ────────────────────────────────────────────
    clock == ClockPosition.ELEVEN_OCLOCK && proximity == Proximity.NEAR ->
        "Stop! $label ahead on the left" to AnnouncementPriority.IMMEDIATE

    clock == ClockPosition.ELEVEN_OCLOCK && proximity == Proximity.MED ->
        "Move slightly right, $label on the left" to AnnouncementPriority.HIGH

    clock == ClockPosition.ELEVEN_OCLOCK ->
        "$label slightly left" to AnnouncementPriority.NORMAL

    // ── Slightly right (1 o'clock) ────────────────────────────────────────────
    clock == ClockPosition.ONE_OCLOCK && proximity == Proximity.NEAR ->
        "Stop! $label ahead on the right" to AnnouncementPriority.IMMEDIATE

    clock == ClockPosition.ONE_OCLOCK && proximity == Proximity.MED ->
        "Move slightly left, $label on the right" to AnnouncementPriority.HIGH

    clock == ClockPosition.ONE_OCLOCK ->
        "$label slightly right" to AnnouncementPriority.NORMAL

    // ── Far left (9-10 o'clock) ───────────────────────────────────────────────
    clock == ClockPosition.TEN_OCLOCK && proximity == Proximity.NEAR ->
        "$label on your left, move right" to AnnouncementPriority.HIGH

    clock == ClockPosition.TEN_OCLOCK ->
        "$label at ${clock.description}" to AnnouncementPriority.NORMAL

    clock == ClockPosition.NINE_OCLOCK && proximity == Proximity.NEAR ->
        "$label on your far left" to AnnouncementPriority.HIGH

    clock == ClockPosition.NINE_OCLOCK ->
        "$label at ${clock.description}" to AnnouncementPriority.NORMAL

    // ── Far right (2-3 o'clock) ───────────────────────────────────────────────
    clock == ClockPosition.TWO_OCLOCK && proximity == Proximity.NEAR ->
        "$label on your right, move left" to AnnouncementPriority.HIGH

    clock == ClockPosition.TWO_OCLOCK ->
        "$label at ${clock.description}" to AnnouncementPriority.NORMAL

    clock == ClockPosition.THREE_OCLOCK && proximity == Proximity.NEAR ->
        "$label on your far right" to AnnouncementPriority.HIGH

    clock == ClockPosition.THREE_OCLOCK ->
        "$label at ${clock.description}" to AnnouncementPriority.NORMAL

    else -> "$label at ${clock.description}" to AnnouncementPriority.NORMAL
}

/**
 * Fallback when depth sees an obstacle but detection didn't produce a named match.
 * Uses zone description to give directional guidance without a specific label.
 */
private fun buildDepthOnlyInstruction(
    analysis: NavigationAnalysis
): Pair<String, AnnouncementPriority> {
    val obstacle = analysis.primaryObstacle ?: return "Path is clear" to AnnouncementPriority.NORMAL
    return when {
        obstacle.proximity == Proximity.NEAR && obstacle.zone == NavigationZone.MIDDLE_CENTER ->
            "Stop! obstacle directly ahead" to AnnouncementPriority.IMMEDIATE

        obstacle.proximity == Proximity.NEAR ->
            "Obstacle very close, ${obstacle.zone.description}" to AnnouncementPriority.IMMEDIATE

        obstacle.proximity == Proximity.MED ->
            "Obstacle nearby, ${obstacle.zone.description}" to AnnouncementPriority.HIGH

        else ->
            "Obstacle ahead, ${obstacle.zone.description}" to AnnouncementPriority.NORMAL
    }
}

/** Sorts NEAR first, then MED, FAR, UNKNOWN. */
private fun proximityOrder(proximity: Proximity): Int = when (proximity) {
    Proximity.NEAR -> 0
    Proximity.MED -> 1
    Proximity.FAR -> 2
    Proximity.UNKNOWN -> 3
}
