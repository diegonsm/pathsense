package com.example.pathsense.core

import kotlinx.coroutines.channels.Channel

class FrameHub {
    // Separate channels so each pipeline can run at its own pace
    val ocrFrames = Channel<FrameData>(Channel.CONFLATED)
    val detFrames = Channel<FrameData>(Channel.CONFLATED)
    val depthFrames = Channel<FrameData>(Channel.CONFLATED)

    fun publish(frame: FrameData) {
        ocrFrames.trySend(frame)
        detFrames.trySend(frame)
        depthFrames.trySend(frame)
    }

    fun close() {
        ocrFrames.close()
        detFrames.close()
        depthFrames.close()
    }
}
