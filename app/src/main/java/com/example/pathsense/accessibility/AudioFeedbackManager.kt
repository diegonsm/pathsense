package com.example.pathsense.accessibility

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.PriorityQueue
import java.util.UUID

/**
 * Priority levels for TTS announcements.
 * Higher priority announcements interrupt lower priority ones.
 */
enum class AnnouncementPriority(val value: Int) {
    LOW(0),      // Background information
    NORMAL(1),   // Regular announcements
    HIGH(2),     // Important information
    IMMEDIATE(3) // Critical alerts - interrupts everything
}

/**
 * Represents a queued TTS announcement.
 */
data class Announcement(
    val text: String,
    val priority: AnnouncementPriority,
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis()
) : Comparable<Announcement> {
    override fun compareTo(other: Announcement): Int {
        // Higher priority first, then earlier timestamp
        val priorityCompare = other.priority.value.compareTo(this.priority.value)
        return if (priorityCompare != 0) priorityCompare else timestamp.compareTo(other.timestamp)
    }
}

/**
 * Manages Text-to-Speech audio feedback with priority queue, debouncing, and rate control.
 * Provides accessible audio announcements for visually impaired users.
 */
class AudioFeedbackManager(
    private val context: Context,
    private val preferences: AccessibilityPreferences
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val announcementQueue = PriorityQueue<Announcement>()
    private val recentAnnouncements = mutableMapOf<String, Long>()

    // Debounce time in milliseconds - same text won't be repeated within this window
    private val debounceTimeMs = 3000L

    // Current speech settings
    private var speechRate = AccessibilityPreferences.DEFAULT_SPEECH_RATE
    private var speechPitch = AccessibilityPreferences.DEFAULT_SPEECH_PITCH

    // Callback for announcement completion
    private var onCompletionCallback: ((String) -> Unit)? = null

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { engine ->
                val result = engine.setLanguage(Locale.US)
                if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    isInitialized = true
                    engine.setSpeechRate(speechRate)
                    engine.setPitch(speechPitch)
                    setupUtteranceListener()
                    processQueue()
                }
            }
        }
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
                utteranceId?.let { onCompletionCallback?.invoke(it) }
                processQueue()
            }

            @Deprecated("Deprecated in API")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                processQueue()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _isSpeaking.value = false
                processQueue()
            }
        })
    }

    /**
     * Announce text with the specified priority.
     * Debouncing prevents the same text from being repeated within 3 seconds.
     *
     * @param text The text to announce
     * @param priority The priority level (default: NORMAL)
     * @param bypassDebounce If true, ignores debouncing for this announcement
     */
    fun announce(
        text: String,
        priority: AnnouncementPriority = AnnouncementPriority.NORMAL,
        bypassDebounce: Boolean = false
    ) {
        if (text.isBlank()) return

        // Check debouncing
        if (!bypassDebounce) {
            val lastTime = recentAnnouncements[text]
            val now = System.currentTimeMillis()
            if (lastTime != null && (now - lastTime) < debounceTimeMs) {
                return // Skip - too recent
            }
            recentAnnouncements[text] = now
        }

        val announcement = Announcement(text, priority)

        // IMMEDIATE priority interrupts current speech
        if (priority == AnnouncementPriority.IMMEDIATE) {
            stop()
            speakNow(announcement)
        } else {
            announcementQueue.add(announcement)
            if (!_isSpeaking.value) {
                processQueue()
            }
        }
    }

    /**
     * Announce with HIGH priority - important information that should be heard soon.
     */
    fun announceHigh(text: String) {
        announce(text, AnnouncementPriority.HIGH)
    }

    /**
     * Announce with IMMEDIATE priority - critical alerts that interrupt everything.
     */
    fun announceImmediate(text: String) {
        announce(text, AnnouncementPriority.IMMEDIATE, bypassDebounce = true)
    }

    private fun processQueue() {
        if (!isInitialized || _isSpeaking.value) return

        val nextAnnouncement = announcementQueue.poll() ?: return
        speakNow(nextAnnouncement)
    }

    private fun speakNow(announcement: Announcement) {
        tts?.speak(
            announcement.text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            announcement.id
        )
    }

    /**
     * Update speech rate (0.5x - 2.0x).
     */
    fun setSpeechRate(rate: Float) {
        speechRate = rate.coerceIn(0.5f, 2.0f)
        tts?.setSpeechRate(speechRate)
    }

    /**
     * Update speech pitch (0.5x - 2.0x).
     */
    fun setSpeechPitch(pitch: Float) {
        speechPitch = pitch.coerceIn(0.5f, 2.0f)
        tts?.setPitch(speechPitch)
    }

    /**
     * Set callback for announcement completion.
     */
    fun setOnCompletionListener(callback: (String) -> Unit) {
        onCompletionCallback = callback
    }

    /**
     * Stop current speech and clear the queue.
     */
    fun stop() {
        tts?.stop()
        announcementQueue.clear()
        _isSpeaking.value = false
    }

    /**
     * Clear only the announcement queue, but let current speech finish.
     */
    fun clearQueue() {
        announcementQueue.clear()
    }

    /**
     * Clean up recent announcements map (call periodically to prevent memory growth).
     */
    fun cleanupDebounceCache() {
        val now = System.currentTimeMillis()
        recentAnnouncements.entries.removeIf { (_, timestamp) ->
            (now - timestamp) > debounceTimeMs * 2
        }
    }

    /**
     * Release TTS resources. Call when the manager is no longer needed.
     */
    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
