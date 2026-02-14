package com.example.pathsense.accessibility

import com.example.pathsense.pipelines.results.Detection
import com.example.pathsense.pipelines.results.Proximity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SpatialDescriber clock position mapping and proximity descriptions.
 */
class SpatialDescriberTest {

    private lateinit var describer: SpatialDescriber

    @Before
    fun setup() {
        describer = SpatialDescriber()
    }

    // Clock Position Tests

    @Test
    fun `far left position returns 9 o'clock`() {
        val position = describer.getClockPosition(0.0f)
        assertEquals(ClockPosition.NINE_OCLOCK, position)
    }

    @Test
    fun `left position returns 10 o'clock`() {
        val position = describer.getClockPosition(0.25f)
        assertEquals(ClockPosition.TEN_OCLOCK, position)
    }

    @Test
    fun `slightly left position returns 11 o'clock`() {
        val position = describer.getClockPosition(0.40f)
        assertEquals(ClockPosition.ELEVEN_OCLOCK, position)
    }

    @Test
    fun `center position returns 12 o'clock`() {
        val position = describer.getClockPosition(0.50f)
        assertEquals(ClockPosition.TWELVE_OCLOCK, position)
    }

    @Test
    fun `slightly right position returns 1 o'clock`() {
        val position = describer.getClockPosition(0.60f)
        assertEquals(ClockPosition.ONE_OCLOCK, position)
    }

    @Test
    fun `right position returns 2 o'clock`() {
        val position = describer.getClockPosition(0.75f)
        assertEquals(ClockPosition.TWO_OCLOCK, position)
    }

    @Test
    fun `far right position returns 3 o'clock`() {
        val position = describer.getClockPosition(1.0f)
        assertEquals(ClockPosition.THREE_OCLOCK, position)
    }

    @Test
    fun `boundary at 0_17 returns 10 o'clock`() {
        val position = describer.getClockPosition(0.17f)
        assertEquals(ClockPosition.TEN_OCLOCK, position)
    }

    @Test
    fun `boundary at 0_83 returns 3 o'clock`() {
        val position = describer.getClockPosition(0.83f)
        assertEquals(ClockPosition.THREE_OCLOCK, position)
    }

    // Detection Description Tests

    @Test
    fun `detection at center creates correct spatial description`() {
        val detection = Detection(
            classId = 1,
            score = 0.9f,
            left = 0.4f,
            top = 0.3f,
            right = 0.6f,
            bottom = 0.7f,
            proximity = Proximity.NEAR
        )

        val description = describer.describeDetection(detection, "person")

        assertEquals(ClockPosition.TWELVE_OCLOCK, description.clockPosition)
        assertEquals(Proximity.NEAR, description.proximity)
        assertEquals("person", description.label)
        assertEquals(0.9f, description.confidence, 0.001f)
    }

    @Test
    fun `detection at left creates correct spatial description`() {
        val detection = Detection(
            classId = 1,
            score = 0.8f,
            left = 0.0f,
            top = 0.3f,
            right = 0.2f,
            bottom = 0.7f,
            proximity = Proximity.FAR
        )

        val description = describer.describeDetection(detection, "car")

        assertEquals(ClockPosition.NINE_OCLOCK, description.clockPosition)
        assertEquals(Proximity.FAR, description.proximity)
    }

    // Spoken Text Tests

    @Test
    fun `near proximity generates 'very close' text`() {
        val description = SpatialDescription(
            clockPosition = ClockPosition.TWELVE_OCLOCK,
            proximity = Proximity.NEAR,
            label = "person",
            confidence = 0.9f
        )

        val spoken = description.toSpokenText()

        assertEquals("person at 12 o'clock, very close", spoken)
    }

    @Test
    fun `medium proximity generates 'nearby' text`() {
        val description = SpatialDescription(
            clockPosition = ClockPosition.THREE_OCLOCK,
            proximity = Proximity.MED,
            label = "chair",
            confidence = 0.8f
        )

        val spoken = description.toSpokenText()

        assertEquals("chair at 3 o'clock, nearby", spoken)
    }

    @Test
    fun `far proximity generates 'in the distance' text`() {
        val description = SpatialDescription(
            clockPosition = ClockPosition.NINE_OCLOCK,
            proximity = Proximity.FAR,
            label = "car",
            confidence = 0.7f
        )

        val spoken = description.toSpokenText()

        assertEquals("car at 9 o'clock, in the distance", spoken)
    }

    @Test
    fun `unknown proximity generates no distance text`() {
        val description = SpatialDescription(
            clockPosition = ClockPosition.ELEVEN_OCLOCK,
            proximity = Proximity.UNKNOWN,
            label = "bottle",
            confidence = 0.6f
        )

        val spoken = description.toSpokenText()

        assertEquals("bottle at 11 o'clock", spoken)
    }

    // Multiple Detections Tests

    @Test
    fun `detections are sorted by proximity then confidence`() {
        val detections = listOf(
            Detection(1, 0.9f, 0.1f, 0.1f, 0.2f, 0.2f, Proximity.FAR),
            Detection(2, 0.8f, 0.4f, 0.4f, 0.6f, 0.6f, Proximity.NEAR),
            Detection(3, 0.95f, 0.7f, 0.7f, 0.9f, 0.9f, Proximity.MED)
        )

        val descriptions = describer.describeDetections(detections) { "object$it" }

        // NEAR should come first
        assertEquals(Proximity.NEAR, descriptions[0].proximity)
        // MED should come second
        assertEquals(Proximity.MED, descriptions[1].proximity)
        // FAR should come last
        assertEquals(Proximity.FAR, descriptions[2].proximity)
    }

    @Test
    fun `summary announcement limits to max items`() {
        val descriptions = listOf(
            SpatialDescription(ClockPosition.TWELVE_OCLOCK, Proximity.NEAR, "person", 0.9f),
            SpatialDescription(ClockPosition.THREE_OCLOCK, Proximity.MED, "car", 0.8f),
            SpatialDescription(ClockPosition.NINE_OCLOCK, Proximity.FAR, "chair", 0.7f),
            SpatialDescription(ClockPosition.ONE_OCLOCK, Proximity.FAR, "table", 0.6f)
        )

        val summary = describer.generateSummaryAnnouncement(descriptions, maxItems = 2)

        // Should only include first 2 items
        assertTrue(summary.contains("person"))
        assertTrue(summary.contains("car"))
        assertTrue(!summary.contains("chair"))
        assertTrue(!summary.contains("table"))
    }

    @Test
    fun `empty detections returns 'no objects detected'`() {
        val summary = describer.generateSummaryAnnouncement(emptyList())

        assertEquals("No objects detected", summary)
    }

    // Navigation Zone Tests

    @Test
    fun `top left coordinates return TOP_LEFT zone`() {
        val zone = describer.getNavigationZone(0.1f, 0.1f)
        assertEquals(NavigationZone.TOP_LEFT, zone)
    }

    @Test
    fun `center coordinates return MIDDLE_CENTER zone`() {
        val zone = describer.getNavigationZone(0.5f, 0.5f)
        assertEquals(NavigationZone.MIDDLE_CENTER, zone)
    }

    @Test
    fun `bottom right coordinates return BOTTOM_RIGHT zone`() {
        val zone = describer.getNavigationZone(0.9f, 0.9f)
        assertEquals(NavigationZone.BOTTOM_RIGHT, zone)
    }

    // Navigation Analysis Tests

    @Test
    fun `clear center path returns clearPath true`() {
        val zones = listOf(
            NavigationZoneResult(NavigationZone.TOP_LEFT, Proximity.FAR, 50),
            NavigationZoneResult(NavigationZone.TOP_CENTER, Proximity.FAR, 40),
            NavigationZoneResult(NavigationZone.TOP_RIGHT, Proximity.FAR, 60),
            NavigationZoneResult(NavigationZone.MIDDLE_LEFT, Proximity.MED, 120),
            NavigationZoneResult(NavigationZone.MIDDLE_CENTER, Proximity.FAR, 30),  // Center is clear
            NavigationZoneResult(NavigationZone.MIDDLE_RIGHT, Proximity.MED, 110),
            NavigationZoneResult(NavigationZone.BOTTOM_LEFT, Proximity.FAR, 20),
            NavigationZoneResult(NavigationZone.BOTTOM_CENTER, Proximity.FAR, 25),
            NavigationZoneResult(NavigationZone.BOTTOM_RIGHT, Proximity.FAR, 35)
        )

        val analysis = describer.analyzeNavigationZones(zones)

        assertTrue(analysis.clearPath)
        assertEquals("Clear path ahead", analysis.toSpokenText())
    }

    @Test
    fun `obstacle in center returns clearPath false`() {
        val zones = listOf(
            NavigationZoneResult(NavigationZone.TOP_LEFT, Proximity.FAR, 50),
            NavigationZoneResult(NavigationZone.TOP_CENTER, Proximity.FAR, 40),
            NavigationZoneResult(NavigationZone.TOP_RIGHT, Proximity.FAR, 60),
            NavigationZoneResult(NavigationZone.MIDDLE_LEFT, Proximity.FAR, 70),
            NavigationZoneResult(NavigationZone.MIDDLE_CENTER, Proximity.NEAR, 220),  // Obstacle!
            NavigationZoneResult(NavigationZone.MIDDLE_RIGHT, Proximity.FAR, 80),
            NavigationZoneResult(NavigationZone.BOTTOM_LEFT, Proximity.FAR, 20),
            NavigationZoneResult(NavigationZone.BOTTOM_CENTER, Proximity.FAR, 25),
            NavigationZoneResult(NavigationZone.BOTTOM_RIGHT, Proximity.FAR, 35)
        )

        val analysis = describer.analyzeNavigationZones(zones)

        assertTrue(!analysis.clearPath)
        assertEquals(NavigationZone.MIDDLE_CENTER, analysis.primaryObstacle?.zone)
        assertTrue(analysis.toSpokenText().contains("very close"))
    }
}
