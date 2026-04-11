package com.example.pathsense.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.PriorityQueue

/**
 * Unit tests for Announcement ordering.
 *
 * AudioFeedbackManager uses a PriorityQueue<Announcement> to decide what to say
 * next. The ordering contract is:
 *   1. Higher priority first (IMMEDIATE > HIGH > NORMAL > LOW)
 *   2. Within the same priority, earlier timestamp first (FIFO)
 *
 * A regression here would silently starve critical alerts or reorder speech
 * in ways blind users would experience as random — hence the explicit tests.
 */
class AnnouncementTest {

    @Test
    fun `higher priority compares as less than lower priority`() {
        val high = Announcement("alert", AnnouncementPriority.HIGH, timestamp = 100L)
        val low = Announcement("info", AnnouncementPriority.LOW, timestamp = 100L)

        // PriorityQueue polls the smallest element; higher priority must be "smaller"
        assertTrue(high < low)
    }

    @Test
    fun `IMMEDIATE beats HIGH`() {
        val immediate = Announcement("danger", AnnouncementPriority.IMMEDIATE, timestamp = 200L)
        val high = Announcement("notice", AnnouncementPriority.HIGH, timestamp = 100L)

        // Even though HIGH is older, IMMEDIATE must still come first
        assertTrue(immediate < high)
    }

    @Test
    fun `same priority orders by timestamp FIFO`() {
        val earlier = Announcement("first", AnnouncementPriority.NORMAL, timestamp = 100L)
        val later = Announcement("second", AnnouncementPriority.NORMAL, timestamp = 200L)

        assertTrue(earlier < later)
    }

    @Test
    fun `priority queue polls highest priority first`() {
        val queue = PriorityQueue<Announcement>()
        queue.add(Announcement("low", AnnouncementPriority.LOW, timestamp = 100L))
        queue.add(Announcement("normal", AnnouncementPriority.NORMAL, timestamp = 101L))
        queue.add(Announcement("immediate", AnnouncementPriority.IMMEDIATE, timestamp = 200L))
        queue.add(Announcement("high", AnnouncementPriority.HIGH, timestamp = 102L))

        assertEquals("immediate", queue.poll()!!.text)
        assertEquals("high", queue.poll()!!.text)
        assertEquals("normal", queue.poll()!!.text)
        assertEquals("low", queue.poll()!!.text)
    }

    @Test
    fun `priority queue preserves FIFO within same priority`() {
        val queue = PriorityQueue<Announcement>()
        queue.add(Announcement("second", AnnouncementPriority.NORMAL, timestamp = 200L))
        queue.add(Announcement("first", AnnouncementPriority.NORMAL, timestamp = 100L))
        queue.add(Announcement("third", AnnouncementPriority.NORMAL, timestamp = 300L))

        assertEquals("first", queue.poll()!!.text)
        assertEquals("second", queue.poll()!!.text)
        assertEquals("third", queue.poll()!!.text)
    }

    @Test
    fun `priority values are strictly ordered`() {
        // Guard against someone re-numbering the enum
        assertTrue(AnnouncementPriority.LOW.value < AnnouncementPriority.NORMAL.value)
        assertTrue(AnnouncementPriority.NORMAL.value < AnnouncementPriority.HIGH.value)
        assertTrue(AnnouncementPriority.HIGH.value < AnnouncementPriority.IMMEDIATE.value)
    }

    @Test
    fun `announcements have unique ids by default`() {
        val a = Announcement("text", AnnouncementPriority.NORMAL)
        val b = Announcement("text", AnnouncementPriority.NORMAL)

        // UUIDs collide only if the generator is broken
        assertTrue("Announcements should have unique IDs", a.id != b.id)
    }
}
