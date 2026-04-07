package com.example.pathsense.ui.screens

import androidx.camera.view.PreviewView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

// Navigation-relevant obstacles only — skip cups, TVs, food, etc.
private val NAVIGATION_OBSTACLES = setOf(
    "person", "car", "truck", "bus", "motorcycle", "bicycle",
    "chair", "bench", "couch", "dining table", "bed",
    "dog", "cat", "suitcase", "backpack", "potted plant",
    "fire hydrant", "stop sign", "traffic light"
)

private const val MIN_CONFIDENCE = 0.45f
private const val NEAR_REANNOUNCE_MS = 2_000L
private const val COOLDOWN_MS = 6_000L

// 5×5 visual grid for smoother depth map display
private const val VISUAL_GRID_COLS = 5
private const val VISUAL_GRID_ROWS = 5

/**
 * Navigate mode: real-time navigation guidance for visually impaired users.
 *
 * Outputs actionable spoken instructions ("Move slightly left", "Stop! person ahead")
 * based on object detection + optional depth estimation. Detection always runs;
 * depth enriches proximity and drives the zone grid overlay.
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

    // Display state
    var enrichedDetections by remember { mutableStateOf<List<Detection>>(emptyList()) }
    var navigationAnalysis by remember { mutableStateOf<NavigationAnalysis?>(null) }
    var zoneDisplays by remember { mutableStateOf<List<ZoneDisplay>>(emptyList()) }

    // Zone overlay toggle (in-screen control)
    var showZoneOverlay by remember { mutableStateOf(true) }

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

    // ── Effect B: Depth zone analysis ────────────────────────────────────────
    LaunchedEffect(depthMap) {
        val depth = depthMap ?: run {
            zoneDisplays = emptyList()
            navigationAnalysis = null
            return@LaunchedEffect
        }

        // 5×5 visual grid — average sampling for noise-free display
        val cellW = 1f / VISUAL_GRID_COLS
        val cellH = 1f / VISUAL_GRID_ROWS
        zoneDisplays = (0 until VISUAL_GRID_ROWS).flatMap { row ->
            (0 until VISUAL_GRID_COLS).map { col ->
                val closeness = depthSampler.sampleRegionAverage(
                    depth,
                    col * cellW, row * cellH,
                    (col + 1) * cellW, (row + 1) * cellH
                )
                ZoneDisplay(depthSampler.closenessToProximity(closeness), closeness)
            }
        }

        // 3×3 guidance zones — still use average for accurate proximity classification
        val zoneResults = depthSampler.sampleNavigationZones(depth)
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

    // ── Effect D: Force re-announcement when danger persists ─────────────────
    LaunchedEffect(Unit) {
        while (true) {
            delay(NEAR_REANNOUNCE_MS)
            if (lastInstructionPriority == AnnouncementPriority.IMMEDIATE) {
                lastInstructionMs = 0L
            }
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Box(modifier = modifier.fillMaxSize()) {

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

        // 5×5 zone grid — shown when depth is running AND toggle is on
        if (showZoneOverlay && zoneDisplays.isNotEmpty()) {
            NavigationZoneOverlay(
                zones = zoneDisplays,
                gridColumns = VISUAL_GRID_COLS,
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

        // Zone overlay toggle button (bottom left)
        ZoneToggleButton(
            isOn = showZoneOverlay,
            enabled = zoneDisplays.isNotEmpty(), // only show when depth is active
            onClick = { showZoneOverlay = !showZoneOverlay },
            highContrast = highContrast,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        )

        MuteTtsButton(
            isMuted = isMuted,
            onToggle = { audioManager.toggleMute() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            highContrast = highContrast
        )

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

// ── Zone toggle button ────────────────────────────────────────────────────────

@Composable
private fun ZoneToggleButton(
    isOn: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    highContrast: Boolean,
    modifier: Modifier = Modifier
) {
    if (!enabled) return

    val activeColor = if (highContrast) Color.Yellow else MaterialTheme.colorScheme.primary
    val inactiveColor = if (highContrast) Color.Black else MaterialTheme.colorScheme.surface
    val iconColor = when {
        highContrast && isOn -> Color.Black
        highContrast -> Color.Yellow
        isOn -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = modifier
            .size(40.dp)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = if (isOn) "Hide zone grid" else "Show zone grid"
            },
        shape = CircleShape,
        color = (if (isOn) activeColor else inactiveColor).copy(alpha = 0.9f)
    ) {
        Icon(
            imageVector = Icons.Default.GridView,
            contentDescription = null,
            modifier = Modifier
                .padding(8.dp)
                .size(24.dp),
            tint = iconColor
        )
    }
}

// ── Guidance engine ───────────────────────────────────────────────────────────

/**
 * Builds one actionable navigation instruction from detections + optional depth analysis.
 */
private fun buildGuidanceInstruction(
    detections: List<Detection>,
    navigationAnalysis: NavigationAnalysis?,
    spatialDescriber: SpatialDescriber
): Pair<String, AnnouncementPriority> {

    val obstacles = detections
        .filter { cocoLabel(it.classId) in NAVIGATION_OBSTACLES && it.score >= MIN_CONFIDENCE }
        .sortedWith(compareBy { proximityOrder(it.proximity) })

    if (obstacles.isEmpty()) {
        return if (navigationAnalysis == null || navigationAnalysis.clearPath) {
            "Path is clear" to AnnouncementPriority.NORMAL
        } else {
            buildDepthOnlyInstruction(navigationAnalysis)
        }
    }

    // Check for obstacles on multiple sides — warn user not to move into another hazard
    val leftBlocked = obstacles.any { (it.left + it.right) / 2f < 0.40f && it.proximity != Proximity.FAR }
    val rightBlocked = obstacles.any { (it.left + it.right) / 2f > 0.60f && it.proximity != Proximity.FAR }
    val centerBlocked = obstacles.any {
        val cx = (it.left + it.right) / 2f
        cx in 0.33f..0.67f && it.proximity != Proximity.FAR
    }
    val nearestProximity = obstacles.first().proximity

    if (leftBlocked && rightBlocked && centerBlocked) {
        return when (nearestProximity) {
            Proximity.NEAR -> "Stop! obstacles all around you" to AnnouncementPriority.IMMEDIATE
            Proximity.MED -> "Slow down, obstacles on all sides" to AnnouncementPriority.HIGH
            else -> "Multiple obstacles around you" to AnnouncementPriority.NORMAL
        }
    }

    if (leftBlocked && rightBlocked) {
        return when (nearestProximity) {
            Proximity.NEAR -> "Stop! obstacles left and right" to AnnouncementPriority.IMMEDIATE
            else -> "Caution, obstacles on both sides" to AnnouncementPriority.HIGH
        }
    }

    val primary = obstacles.first()
    val label = cocoLabel(primary.classId)
    val centerX = (primary.left + primary.right) / 2f
    val clock = spatialDescriber.getClockPosition(centerX)
    val proximity = primary.proximity

    return buildInstruction(label, clock, proximity)
}

/**
 * Generates actionable instruction text from an object's clock position and proximity.
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

    // ── Left (9-10 o'clock) ───────────────────────────────────────────────────
    clock == ClockPosition.TEN_OCLOCK && proximity == Proximity.NEAR ->
        "$label on your left, move right" to AnnouncementPriority.HIGH

    clock == ClockPosition.TEN_OCLOCK ->
        "$label at ${clock.description}" to AnnouncementPriority.NORMAL

    clock == ClockPosition.NINE_OCLOCK && proximity == Proximity.NEAR ->
        "$label on your far left" to AnnouncementPriority.HIGH

    clock == ClockPosition.NINE_OCLOCK ->
        "$label at ${clock.description}" to AnnouncementPriority.NORMAL

    // ── Right (2-3 o'clock) ───────────────────────────────────────────────────
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
 * Fallback guidance when depth sees an obstacle but detection found no named object.
 */
private fun buildDepthOnlyInstruction(
    analysis: NavigationAnalysis
): Pair<String, AnnouncementPriority> {
    val obstacle = analysis.primaryObstacle
        ?: return "Path is clear" to AnnouncementPriority.NORMAL
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

private fun proximityOrder(proximity: Proximity): Int = when (proximity) {
    Proximity.NEAR -> 0
    Proximity.MED -> 1
    Proximity.FAR -> 2
    Proximity.UNKNOWN -> 3
}
