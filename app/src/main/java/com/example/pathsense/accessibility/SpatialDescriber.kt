package com.example.pathsense.accessibility

import com.example.pathsense.pipelines.results.Detection
import com.example.pathsense.pipelines.results.Proximity

/**
 * Clock position for spatial orientation (inspired by Google Lookout).
 * Uses clock face metaphor where 12 o'clock is straight ahead.
 */
enum class ClockPosition(val hour: Int, val description: String) {
    NINE_OCLOCK(9, "9 o'clock"),      // Far left
    TEN_OCLOCK(10, "10 o'clock"),     // Left
    ELEVEN_OCLOCK(11, "11 o'clock"),  // Slightly left
    TWELVE_OCLOCK(12, "12 o'clock"),  // Straight ahead (center)
    ONE_OCLOCK(1, "1 o'clock"),       // Slightly right
    TWO_OCLOCK(2, "2 o'clock"),       // Right
    THREE_OCLOCK(3, "3 o'clock")      // Far right
}

/**
 * Navigation zone for depth-based obstacle detection.
 * Divides the frame into 9 zones (3x3 grid).
 */
enum class NavigationZone(val description: String) {
    TOP_LEFT("top left"),
    TOP_CENTER("ahead high"),
    TOP_RIGHT("top right"),
    MIDDLE_LEFT("left"),
    MIDDLE_CENTER("directly ahead"),
    MIDDLE_RIGHT("right"),
    BOTTOM_LEFT("bottom left"),
    BOTTOM_CENTER("ground ahead"),
    BOTTOM_RIGHT("bottom right")
}

/**
 * Result of spatial description for a detection.
 */
data class SpatialDescription(
    val clockPosition: ClockPosition,
    val proximity: Proximity,
    val label: String,
    val confidence: Float
) {
    /**
     * Generate natural language description.
     * Example: "Person at 12 o'clock, very close"
     */
    fun toSpokenText(): String {
        val proximityText = when (proximity) {
            Proximity.NEAR -> "very close"
            Proximity.MED -> "nearby"
            Proximity.FAR -> "in the distance"
            Proximity.UNKNOWN -> ""
        }

        return if (proximityText.isNotEmpty()) {
            "$label at ${clockPosition.description}, $proximityText"
        } else {
            "$label at ${clockPosition.description}"
        }
    }
}

/**
 * Result of zone-based navigation analysis.
 */
data class NavigationZoneResult(
    val zone: NavigationZone,
    val proximity: Proximity,
    val closenessValue: Int // 0-255 where 255 is closest
)

/**
 * Full navigation analysis result with all zones.
 */
data class NavigationAnalysis(
    val zones: List<NavigationZoneResult>,
    val clearPath: Boolean,
    val primaryObstacle: NavigationZoneResult?
) {
    /**
     * Generate navigation guidance text.
     */
    fun toSpokenText(): String {
        return when {
            clearPath -> "Clear path ahead"
            primaryObstacle != null -> {
                val proximityText = when (primaryObstacle.proximity) {
                    Proximity.NEAR -> "Obstacle very close"
                    Proximity.MED -> "Obstacle nearby"
                    Proximity.FAR -> "Obstacle ahead"
                    Proximity.UNKNOWN -> "Obstacle detected"
                }
                "$proximityText, ${primaryObstacle.zone.description}"
            }
            else -> "Checking surroundings"
        }
    }
}

/**
 * Converts detection coordinates and depth information to human-readable
 * spatial descriptions using clock orientation and proximity indicators.
 */
class SpatialDescriber {

    /**
     * Convert horizontal position (0.0-1.0) to clock position.
     *
     * Mapping:
     * - 0.0-0.17 = 9 o'clock (far left)
     * - 0.17-0.33 = 10 o'clock
     * - 0.33-0.50 = 11 o'clock
     * - 0.50 = 12 o'clock (center)
     * - 0.50-0.67 = 1 o'clock
     * - 0.67-0.83 = 2 o'clock
     * - 0.83-1.0 = 3 o'clock (far right)
     */
    fun getClockPosition(horizontalCenter: Float): ClockPosition {
        return when {
            horizontalCenter < 0.17f -> ClockPosition.NINE_OCLOCK
            horizontalCenter < 0.33f -> ClockPosition.TEN_OCLOCK
            horizontalCenter < 0.50f -> ClockPosition.ELEVEN_OCLOCK
            horizontalCenter < 0.55f -> ClockPosition.TWELVE_OCLOCK // Slight buffer for center
            horizontalCenter < 0.67f -> ClockPosition.ONE_OCLOCK
            horizontalCenter < 0.83f -> ClockPosition.TWO_OCLOCK
            else -> ClockPosition.THREE_OCLOCK
        }
    }

    /**
     * Describe a detection with spatial orientation.
     */
    fun describeDetection(detection: Detection, label: String): SpatialDescription {
        val centerX = (detection.left + detection.right) / 2f
        val clockPosition = getClockPosition(centerX)

        return SpatialDescription(
            clockPosition = clockPosition,
            proximity = detection.proximity,
            label = label,
            confidence = detection.score
        )
    }

    /**
     * Describe multiple detections, prioritized by proximity and confidence.
     * Returns descriptions sorted by importance (close objects first, then high confidence).
     */
    fun describeDetections(
        detections: List<Detection>,
        labelProvider: (Int) -> String
    ): List<SpatialDescription> {
        return detections
            .map { detection ->
                describeDetection(detection, labelProvider(detection.classId))
            }
            .sortedWith(
                compareBy<SpatialDescription> {
                    // Closer objects have higher priority (lower sort value)
                    when (it.proximity) {
                        Proximity.NEAR -> 0
                        Proximity.MED -> 1
                        Proximity.FAR -> 2
                        Proximity.UNKNOWN -> 3
                    }
                }.thenByDescending { it.confidence }
            )
    }

    /**
     * Get navigation zone from normalized coordinates (0.0-1.0).
     */
    fun getNavigationZone(x: Float, y: Float): NavigationZone {
        val col = when {
            x < 0.33f -> 0
            x < 0.67f -> 1
            else -> 2
        }
        val row = when {
            y < 0.33f -> 0
            y < 0.67f -> 1
            else -> 2
        }

        return when (row * 3 + col) {
            0 -> NavigationZone.TOP_LEFT
            1 -> NavigationZone.TOP_CENTER
            2 -> NavigationZone.TOP_RIGHT
            3 -> NavigationZone.MIDDLE_LEFT
            4 -> NavigationZone.MIDDLE_CENTER
            5 -> NavigationZone.MIDDLE_RIGHT
            6 -> NavigationZone.BOTTOM_LEFT
            7 -> NavigationZone.BOTTOM_CENTER
            8 -> NavigationZone.BOTTOM_RIGHT
            else -> NavigationZone.MIDDLE_CENTER
        }
    }

    /**
     * Analyze navigation zones from depth data.
     * Returns analysis with clear path detection and obstacle warnings.
     */
    fun analyzeNavigationZones(zoneResults: List<NavigationZoneResult>): NavigationAnalysis {
        // Check if center path is clear (MIDDLE_CENTER zone)
        val centerZone = zoneResults.find { it.zone == NavigationZone.MIDDLE_CENTER }
        val clearPath = centerZone?.proximity != Proximity.NEAR

        // Find primary obstacle (closest object in any zone)
        val primaryObstacle = zoneResults
            .filter { it.proximity == Proximity.NEAR }
            .maxByOrNull { it.closenessValue }

        return NavigationAnalysis(
            zones = zoneResults,
            clearPath = clearPath,
            primaryObstacle = primaryObstacle
        )
    }

    /**
     * Generate a summary announcement for multiple detections.
     * Limits to top N items to avoid overwhelming the user.
     */
    fun generateSummaryAnnouncement(
        descriptions: List<SpatialDescription>,
        maxItems: Int = 3
    ): String {
        if (descriptions.isEmpty()) {
            return "No objects detected"
        }

        val topItems = descriptions.take(maxItems)

        return when (topItems.size) {
            1 -> topItems[0].toSpokenText()
            else -> topItems.joinToString(". ") { it.toSpokenText() }
        }
    }
}
