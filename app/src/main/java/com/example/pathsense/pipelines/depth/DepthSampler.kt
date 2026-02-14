package com.example.pathsense.pipelines.depth

import com.example.pathsense.accessibility.NavigationZone
import com.example.pathsense.accessibility.NavigationZoneResult
import com.example.pathsense.pipelines.results.Detection
import com.example.pathsense.pipelines.results.Proximity

/**
 * Samples depth map values at specific regions to determine proximity.
 * Used for enriching object detections with distance and for zone-based navigation.
 */
class DepthSampler {

    companion object {
        // Closeness thresholds for proximity classification
        // Higher closeness value = closer object (255 = very close, 0 = very far)
        const val THRESHOLD_NEAR = 200  // ~1m or less
        const val THRESHOLD_MED = 100   // ~2-3m
        const val THRESHOLD_FAR = 30    // >4m

        // Zone sampling grid (3x3)
        private val ZONE_CONFIGS = listOf(
            ZoneConfig(NavigationZone.TOP_LEFT, 0f, 0f, 0.33f, 0.33f),
            ZoneConfig(NavigationZone.TOP_CENTER, 0.33f, 0f, 0.67f, 0.33f),
            ZoneConfig(NavigationZone.TOP_RIGHT, 0.67f, 0f, 1f, 0.33f),
            ZoneConfig(NavigationZone.MIDDLE_LEFT, 0f, 0.33f, 0.33f, 0.67f),
            ZoneConfig(NavigationZone.MIDDLE_CENTER, 0.33f, 0.33f, 0.67f, 0.67f),
            ZoneConfig(NavigationZone.MIDDLE_RIGHT, 0.67f, 0.33f, 1f, 0.67f),
            ZoneConfig(NavigationZone.BOTTOM_LEFT, 0f, 0.67f, 0.33f, 1f),
            ZoneConfig(NavigationZone.BOTTOM_CENTER, 0.33f, 0.67f, 0.67f, 1f),
            ZoneConfig(NavigationZone.BOTTOM_RIGHT, 0.67f, 0.67f, 1f, 1f)
        )
    }

    private data class ZoneConfig(
        val zone: NavigationZone,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )

    /**
     * Convert closeness value (0-255) to Proximity enum.
     */
    fun closenessToProximity(closeness: Int): Proximity {
        return when {
            closeness >= THRESHOLD_NEAR -> Proximity.NEAR
            closeness >= THRESHOLD_MED -> Proximity.MED
            closeness >= THRESHOLD_FAR -> Proximity.FAR
            else -> Proximity.FAR
        }
    }

    /**
     * Sample depth map at a detection's bounding box.
     * Returns the maximum closeness value within the box (closest point).
     */
    fun sampleDetection(depthMap: DepthAnythingRunner.DepthMap, detection: Detection): Int {
        return sampleRegion(
            depthMap,
            detection.left,
            detection.top,
            detection.right,
            detection.bottom
        )
    }

    /**
     * Enrich a detection with proximity information from the depth map.
     */
    fun enrichDetection(
        depthMap: DepthAnythingRunner.DepthMap,
        detection: Detection
    ): Detection {
        val closeness = sampleDetection(depthMap, detection)
        val proximity = closenessToProximity(closeness)
        return detection.copy(proximity = proximity)
    }

    /**
     * Enrich all detections with proximity information.
     */
    fun enrichDetections(
        depthMap: DepthAnythingRunner.DepthMap,
        detections: List<Detection>
    ): List<Detection> {
        return detections.map { enrichDetection(depthMap, it) }
    }

    /**
     * Sample all 9 navigation zones and return their proximity status.
     */
    fun sampleNavigationZones(depthMap: DepthAnythingRunner.DepthMap): List<NavigationZoneResult> {
        return ZONE_CONFIGS.map { config ->
            val closeness = sampleRegion(
                depthMap,
                config.left,
                config.top,
                config.right,
                config.bottom
            )
            NavigationZoneResult(
                zone = config.zone,
                proximity = closenessToProximity(closeness),
                closenessValue = closeness
            )
        }
    }

    /**
     * Get the closeness value for a specific navigation zone.
     */
    fun sampleZone(depthMap: DepthAnythingRunner.DepthMap, zone: NavigationZone): Int {
        val config = ZONE_CONFIGS.find { it.zone == zone }
            ?: return 0
        return sampleRegion(depthMap, config.left, config.top, config.right, config.bottom)
    }

    /**
     * Check if the center path is clear (no near obstacles in MIDDLE_CENTER).
     */
    fun isCenterPathClear(depthMap: DepthAnythingRunner.DepthMap): Boolean {
        val centerCloseness = sampleZone(depthMap, NavigationZone.MIDDLE_CENTER)
        return centerCloseness < THRESHOLD_NEAR
    }

    /**
     * Sample a region of the depth map (normalized coordinates 0-1).
     * Returns the maximum closeness value in the region (closest point).
     */
    private fun sampleRegion(
        depthMap: DepthAnythingRunner.DepthMap,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ): Int {
        val w = depthMap.width
        val h = depthMap.height

        // Convert normalized coordinates to pixel coordinates
        val x1 = (left * w).toInt().coerceIn(0, w - 1)
        val y1 = (top * h).toInt().coerceIn(0, h - 1)
        val x2 = (right * w).toInt().coerceIn(0, w - 1)
        val y2 = (bottom * h).toInt().coerceIn(0, h - 1)

        // Sample strategy: take maximum closeness (closest point)
        // Use sparse sampling for performance (every 4th pixel)
        var maxCloseness = 0
        val step = 4

        var y = y1
        while (y <= y2) {
            var x = x1
            while (x <= x2) {
                val idx = y * w + x
                if (idx in depthMap.closeness.indices) {
                    val closeness = depthMap.closeness[idx].toInt() and 0xFF
                    if (closeness > maxCloseness) {
                        maxCloseness = closeness
                    }
                }
                x += step
            }
            y += step
        }

        return maxCloseness
    }

    /**
     * Sample a single point in the depth map (normalized coordinates).
     */
    fun samplePoint(depthMap: DepthAnythingRunner.DepthMap, x: Float, y: Float): Int {
        val px = (x * depthMap.width).toInt().coerceIn(0, depthMap.width - 1)
        val py = (y * depthMap.height).toInt().coerceIn(0, depthMap.height - 1)
        val idx = py * depthMap.width + px
        return if (idx in depthMap.closeness.indices) {
            depthMap.closeness[idx].toInt() and 0xFF
        } else {
            0
        }
    }

    /**
     * Get average closeness for a region (for less noisy readings).
     */
    fun sampleRegionAverage(
        depthMap: DepthAnythingRunner.DepthMap,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ): Int {
        val w = depthMap.width
        val h = depthMap.height

        val x1 = (left * w).toInt().coerceIn(0, w - 1)
        val y1 = (top * h).toInt().coerceIn(0, h - 1)
        val x2 = (right * w).toInt().coerceIn(0, h - 1)
        val y2 = (bottom * h).toInt().coerceIn(0, h - 1)

        var sum = 0L
        var count = 0
        val step = 4

        var y = y1
        while (y <= y2) {
            var x = x1
            while (x <= x2) {
                val idx = y * w + x
                if (idx in depthMap.closeness.indices) {
                    sum += depthMap.closeness[idx].toInt() and 0xFF
                    count++
                }
                x += step
            }
            y += step
        }

        return if (count > 0) (sum / count).toInt() else 0
    }
}
