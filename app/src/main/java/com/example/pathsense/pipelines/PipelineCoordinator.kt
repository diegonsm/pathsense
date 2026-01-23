package com.example.pathsense.pipelines

import android.content.Context
import com.example.pathsense.core.FrameHub
import com.example.pathsense.pipelines.depth.DepthAnythingRunner
import com.example.pathsense.pipelines.detection.MobileNetSsdRunner
import com.example.pathsense.pipelines.ocr.OcrPipeline
import com.example.pathsense.pipelines.results.DetectionResult
import com.example.pathsense.pipelines.results.DepthResult
import com.example.pathsense.pipelines.results.OcrResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

class PipelineCoordinator(
    context: Context,
    private val hub: FrameHub,
    private val scope: CoroutineScope
) {
    private val appContext = context.applicationContext

    private val ocr = OcrPipeline()
    private val det = MobileNetSsdRunner(appContext)
    private val depth = DepthAnythingRunner(appContext)

    val ocrState = MutableStateFlow<OcrResult?>(null)
    val detState = MutableStateFlow<DetectionResult?>(null)
    val depthState = MutableStateFlow<DepthResult?>(null)

    private var started = false

    fun start() {
        if (started) return
        started = true

        scope.launch {
            withContext(Dispatchers.IO) {
                det.load()
                try {
                    depth.load()
                } catch (e: Exception) {
                    depthState.value = null
                }
            }

            launch(Dispatchers.Default) {
                for (frame in hub.ocrFrames) {
                    try {
                        ocrState.value = ocr.run(frame)
                    } catch (_: Exception) {}
                }
            }

            launch(Dispatchers.Default) {
                for (frame in hub.detFrames) {
                    try {
                        val dets = det.run(frame.bitmap)
                        detState.value = DetectionResult(frame.timestampNs, dets)
                    } catch (_: Exception) {}
                }
            }

            launch(Dispatchers.Default) {
                var lastDepthMs = 0L

                for (frame in hub.depthFrames) {
                    val nowMs = android.os.SystemClock.elapsedRealtime()
                    if (nowMs - lastDepthMs < 1000) continue // ~1 FPS
                    lastDepthMs = nowMs

                    try {
                        val viz = depth.run(frame.bitmap)
                        depthState.value = DepthResult(tsNs = frame.timestampNs, depthViz = viz)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun stop() {
        hub.close()
        ocr.close()
        det.close()
        depth.close()
    }
}
