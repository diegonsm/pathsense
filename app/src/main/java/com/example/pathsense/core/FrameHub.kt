package com.example.pathsense.core

import kotlinx.coroutines.channels.Channel

enum class FrameTarget {
    OCR,
    DETECTION,
    DEPTH
}

class FrameHub {
    // Separate channels so each pipeline can run at its own pace
    val ocrFrames = Channel<FrameData>(Channel.CONFLATED)
    val detFrames = Channel<FrameData>(Channel.CONFLATED)
    val depthFrames = Channel<FrameData>(Channel.CONFLATED)

    @Volatile
    private var enabledTargets: Set<FrameTarget> = setOf(
        FrameTarget.OCR,
        FrameTarget.DETECTION,
        FrameTarget.DEPTH
    )

    fun publish(frame: FrameData) {
        if (FrameTarget.OCR in enabledTargets) {
            ocrFrames.trySend(frame)
        }
        if (FrameTarget.DETECTION in enabledTargets) {
            detFrames.trySend(frame)
        }
        if (FrameTarget.DEPTH in enabledTargets) {
            depthFrames.trySend(frame)
        }
    }

    fun setEnabledTargets(targets: Set<FrameTarget>) {
        enabledTargets = targets
        clearPendingFrames()
    }

    fun clearPendingFrames() {
        while (ocrFrames.tryReceive().isSuccess) {}
        while (detFrames.tryReceive().isSuccess) {}
        while (depthFrames.tryReceive().isSuccess) {}
    }

    fun close() {
        ocrFrames.close()
        detFrames.close()
        depthFrames.close()
    }
}
