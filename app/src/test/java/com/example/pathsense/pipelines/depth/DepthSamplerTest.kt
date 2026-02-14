package com.example.pathsense.pipelines.depth

import com.example.pathsense.accessibility.NavigationZone
import com.example.pathsense.pipelines.results.Detection
import com.example.pathsense.pipelines.results.Proximity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for DepthSampler proximity calculation from depth values.
 */
class DepthSamplerTest {

    private lateinit var sampler: DepthSampler

    @Before
    fun setup() {
        sampler = DepthSampler()
    }

    // Proximity Classification Tests

    @Test
    fun `closeness above 200 returns NEAR`() {
        val proximity = sampler.closenessToProximity(220)
        assertEquals(Proximity.NEAR, proximity)
    }

    @Test
    fun `closeness at 200 returns NEAR`() {
        val proximity = sampler.closenessToProximity(200)
        assertEquals(Proximity.NEAR, proximity)
    }

    @Test
    fun `closeness at 199 returns MED`() {
        val proximity = sampler.closenessToProximity(199)
        assertEquals(Proximity.MED, proximity)
    }

    @Test
    fun `closeness at 100 returns MED`() {
        val proximity = sampler.closenessToProximity(100)
        assertEquals(Proximity.MED, proximity)
    }

    @Test
    fun `closeness at 99 returns FAR`() {
        val proximity = sampler.closenessToProximity(99)
        assertEquals(Proximity.FAR, proximity)
    }

    @Test
    fun `closeness at 30 returns FAR`() {
        val proximity = sampler.closenessToProximity(30)
        assertEquals(Proximity.FAR, proximity)
    }

    @Test
    fun `closeness at 0 returns FAR`() {
        val proximity = sampler.closenessToProximity(0)
        assertEquals(Proximity.FAR, proximity)
    }

    @Test
    fun `closeness at max 255 returns NEAR`() {
        val proximity = sampler.closenessToProximity(255)
        assertEquals(Proximity.NEAR, proximity)
    }

    // Depth Map Sampling Tests

    @Test
    fun `sample point returns correct value from depth map`() {
        val depthMap = createTestDepthMap(10, 10) { x, y -> (x * 25).toByte() }

        val value = sampler.samplePoint(depthMap, 0.5f, 0.5f)

        // At x=0.5 in a 10-wide map, we're at pixel 5
        // Value should be around 5 * 25 = 125
        assertTrue(value in 100..150)
    }

    @Test
    fun `sample detection returns max closeness in bounding box`() {
        // Create depth map where center is closer (higher values)
        val depthMap = createTestDepthMap(100, 100) { x, y ->
            val centerDist = kotlin.math.sqrt(
                ((x - 50) * (x - 50) + (y - 50) * (y - 50)).toDouble()
            )
            // Closer to center = higher closeness
            (255 - centerDist.toInt().coerceIn(0, 255)).toByte()
        }

        // Detection at center
        val detection = Detection(
            classId = 1,
            score = 0.9f,
            left = 0.4f,
            top = 0.4f,
            right = 0.6f,
            bottom = 0.6f
        )

        val closeness = sampler.sampleDetection(depthMap, detection)

        // Center should have high closeness (close to 255)
        assertTrue("Expected high closeness at center, got $closeness", closeness > 200)
    }

    @Test
    fun `enrich detection adds correct proximity`() {
        // Create depth map with high values (close objects)
        val depthMap = createTestDepthMap(100, 100) { _, _ -> 220.toByte() }

        val detection = Detection(
            classId = 1,
            score = 0.9f,
            left = 0.2f,
            top = 0.2f,
            right = 0.4f,
            bottom = 0.4f,
            proximity = Proximity.UNKNOWN
        )

        val enriched = sampler.enrichDetection(depthMap, detection)

        assertEquals(Proximity.NEAR, enriched.proximity)
        // Other fields should be unchanged
        assertEquals(detection.classId, enriched.classId)
        assertEquals(detection.score, enriched.score, 0.001f)
    }

    @Test
    fun `enrich multiple detections processes all correctly`() {
        val depthMap = createTestDepthMap(100, 100) { x, _ ->
            // Left side is far (low values), right side is close (high values)
            (x * 2.5f).toInt().coerceIn(0, 255).toByte()
        }

        val detections = listOf(
            Detection(1, 0.9f, 0.0f, 0.3f, 0.2f, 0.7f),  // Left side - should be FAR
            Detection(2, 0.8f, 0.8f, 0.3f, 1.0f, 0.7f)   // Right side - should be NEAR
        )

        val enriched = sampler.enrichDetections(depthMap, detections)

        assertEquals(2, enriched.size)
        assertEquals(Proximity.FAR, enriched[0].proximity)
        assertEquals(Proximity.NEAR, enriched[1].proximity)
    }

    // Navigation Zone Tests

    @Test
    fun `sample navigation zones returns 9 zones`() {
        val depthMap = createTestDepthMap(90, 90) { _, _ -> 100.toByte() }

        val zones = sampler.sampleNavigationZones(depthMap)

        assertEquals(9, zones.size)
    }

    @Test
    fun `navigation zones have correct zone assignments`() {
        val depthMap = createTestDepthMap(90, 90) { _, _ -> 100.toByte() }

        val zones = sampler.sampleNavigationZones(depthMap)

        val zoneNames = zones.map { it.zone }
        assertTrue(zoneNames.contains(NavigationZone.TOP_LEFT))
        assertTrue(zoneNames.contains(NavigationZone.TOP_CENTER))
        assertTrue(zoneNames.contains(NavigationZone.TOP_RIGHT))
        assertTrue(zoneNames.contains(NavigationZone.MIDDLE_LEFT))
        assertTrue(zoneNames.contains(NavigationZone.MIDDLE_CENTER))
        assertTrue(zoneNames.contains(NavigationZone.MIDDLE_RIGHT))
        assertTrue(zoneNames.contains(NavigationZone.BOTTOM_LEFT))
        assertTrue(zoneNames.contains(NavigationZone.BOTTOM_CENTER))
        assertTrue(zoneNames.contains(NavigationZone.BOTTOM_RIGHT))
    }

    @Test
    fun `center zone with low closeness is clear`() {
        // Create depth map with low closeness values (far away)
        val depthMap = createTestDepthMap(100, 100) { _, _ -> 50.toByte() }

        val isClear = sampler.isCenterPathClear(depthMap)

        assertTrue(isClear)
    }

    @Test
    fun `center zone with high closeness is not clear`() {
        // Create depth map with high closeness in center
        val depthMap = createTestDepthMap(100, 100) { x, y ->
            if (x in 33..66 && y in 33..66) {
                220.toByte()  // Close in center
            } else {
                50.toByte()   // Far elsewhere
            }
        }

        val isClear = sampler.isCenterPathClear(depthMap)

        assertTrue(!isClear)
    }

    @Test
    fun `sample specific zone returns correct value`() {
        // Create depth map where top-left is far, center is close
        val depthMap = createTestDepthMap(90, 90) { x, y ->
            when {
                x < 30 && y < 30 -> 30.toByte()   // Top-left: far
                x in 30..60 && y in 30..60 -> 220.toByte()  // Center: near
                else -> 100.toByte()
            }
        }

        val topLeftCloseness = sampler.sampleZone(depthMap, NavigationZone.TOP_LEFT)
        val centerCloseness = sampler.sampleZone(depthMap, NavigationZone.MIDDLE_CENTER)

        assertTrue("Top-left should be far", topLeftCloseness < 100)
        assertTrue("Center should be near", centerCloseness > 150)
    }

    // Edge Cases

    @Test
    fun `detection at edge of frame is handled correctly`() {
        val depthMap = createTestDepthMap(100, 100) { _, _ -> 150.toByte() }

        val edgeDetection = Detection(
            classId = 1,
            score = 0.9f,
            left = 0.9f,
            top = 0.9f,
            right = 1.0f,
            bottom = 1.0f
        )

        // Should not throw exception
        val closeness = sampler.sampleDetection(depthMap, edgeDetection)
        assertTrue(closeness > 0)
    }

    @Test
    fun `very small detection is handled correctly`() {
        val depthMap = createTestDepthMap(100, 100) { _, _ -> 150.toByte() }

        val tinyDetection = Detection(
            classId = 1,
            score = 0.9f,
            left = 0.5f,
            top = 0.5f,
            right = 0.51f,
            bottom = 0.51f
        )

        // Should not throw exception
        val closeness = sampler.sampleDetection(depthMap, tinyDetection)
        assertTrue(closeness >= 0)
    }

    // Helper function to create test depth maps
    private fun createTestDepthMap(
        width: Int,
        height: Int,
        generator: (Int, Int) -> Byte
    ): DepthAnythingRunner.DepthMap {
        val closeness = ByteArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                closeness[y * width + x] = generator(x, y)
            }
        }
        return DepthAnythingRunner.DepthMap(width, height, closeness)
    }
}
