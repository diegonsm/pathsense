package com.example.pathsense.pipelines.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the sparse COCO label map.
 *
 * ssd_mobilenet_v1_12.onnx emits original COCO category IDs, which are 1-indexed
 * and contain gaps (IDs 12, 26, 29, 30, 45, 66, 68, 69, 71, 83 do not exist).
 * These tests guard against regressions where the map gets flattened back to a
 * contiguous 0-indexed list, which would mislabel every class.
 */
class CocoLabelsTest {

    // Sentinel entries — if these shift, every detection label will be wrong
    @Test
    fun `person is class 1`() {
        assertEquals("person", COCO_LABELS[1])
    }

    @Test
    fun `chair is class 62`() {
        assertEquals("chair", COCO_LABELS[62])
    }

    @Test
    fun `toothbrush is class 90`() {
        assertEquals("toothbrush", COCO_LABELS[90])
    }

    @Test
    fun `vehicle labels occupy expected IDs`() {
        assertEquals("bicycle", COCO_LABELS[2])
        assertEquals("car", COCO_LABELS[3])
        assertEquals("motorcycle", COCO_LABELS[4])
        assertEquals("bus", COCO_LABELS[6])
        assertEquals("truck", COCO_LABELS[8])
    }

    // Gap preservation — the dataset skips these IDs on purpose
    @Test
    fun `class id 12 is absent`() {
        assertFalse("COCO id 12 must remain a gap", COCO_LABELS.containsKey(12))
    }

    @Test
    fun `all known COCO gaps are preserved`() {
        val knownGaps = listOf(12, 26, 29, 30, 45, 66, 68, 69, 71, 83)
        knownGaps.forEach { id ->
            assertFalse("COCO id $id should be a gap but was ${COCO_LABELS[id]}",
                COCO_LABELS.containsKey(id))
        }
    }

    @Test
    fun `class id 0 is not mapped`() {
        // COCO is 1-indexed — class 0 would indicate an off-by-one regression
        assertFalse(COCO_LABELS.containsKey(0))
    }

    @Test
    fun `map contains exactly 80 classes`() {
        // COCO has 80 object categories; the sparse IDs go up to 90 but with 10 gaps
        assertEquals(80, COCO_LABELS.size)
    }

    @Test
    fun `no label is blank`() {
        COCO_LABELS.forEach { (id, label) ->
            assertTrue("Label for id $id is blank", label.isNotBlank())
        }
    }

    // cocoLabel() helper

    @Test
    fun `cocoLabel returns label for known class`() {
        assertEquals("person", cocoLabel(1))
        assertEquals("chair", cocoLabel(62))
    }

    @Test
    fun `cocoLabel returns fallback for unknown class`() {
        assertEquals("object", cocoLabel(999))
        assertEquals("object", cocoLabel(-1))
    }

    @Test
    fun `cocoLabel returns fallback for gap IDs`() {
        // A gap ID should never land in the map; the fallback must catch it
        assertEquals("object", cocoLabel(12))
        assertEquals("object", cocoLabel(26))
    }

    // Tier-0 safety-critical labels (SpatialDescriber prioritizes these)
    @Test
    fun `safety critical labels are present and spelled correctly`() {
        val tier0 = listOf("person", "car", "truck", "bus", "motorcycle", "bicycle")
        tier0.forEach { label ->
            assertNotNull(
                "Tier-0 safety-critical label '$label' missing from COCO map",
                COCO_LABELS.values.firstOrNull { it == label }
            )
        }
    }
}
