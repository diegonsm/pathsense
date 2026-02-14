package com.example.pathsense.pipelines

import android.content.Context
import android.graphics.Bitmap
import com.example.pathsense.core.FrameHub
import com.example.pathsense.pipelines.depth.DepthAnythingRunner
import com.example.pathsense.pipelines.detection.MobileNetSsdRunner
import com.example.pathsense.pipelines.ocr.OcrPipeline
import com.example.pathsense.pipelines.results.DetectionResult
import com.example.pathsense.pipelines.results.DepthResult
import com.example.pathsense.pipelines.results.OcrResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Coordinates all ML pipelines (OCR, object detection, depth estimation).
 * Exposes reactive state flows for UI consumption.
 */
class PipelineCoordinator(
    context: Context,
    private val hub: FrameHub,
    private val scope: CoroutineScope
) {
    private val appContext = context.applicationContext

    private val ocr = OcrPipeline()
    private val det = MobileNetSsdRunner(appContext)
    private val depth = DepthAnythingRunner(appContext)

    // Pipeline result states
    private val _ocrState = MutableStateFlow<OcrResult?>(null)
    val ocrState: StateFlow<OcrResult?> = _ocrState.asStateFlow()

    private val _detState = MutableStateFlow<DetectionResult?>(null)
    val detState: StateFlow<DetectionResult?> = _detState.asStateFlow()

    private val _depthState = MutableStateFlow<DepthResult?>(null)
    val depthState: StateFlow<DepthResult?> = _depthState.asStateFlow()

    // Raw depth map for sampling (used by accessibility features)
    private val _depthMapState = MutableStateFlow<DepthAnythingRunner.DepthMap?>(null)
    val depthMapState: StateFlow<DepthAnythingRunner.DepthMap?> = _depthMapState.asStateFlow()

    private var started = false

    // Depth is DISABLED by default due to potential native crashes on some devices
    // The app works fully with Explore (detection) and Text (OCR) modes without depth
    private var depthEnabled = false

    fun start() {
        if (started) return
        started = true

        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    det.load()
                    android.util.Log.i("PipelineCoordinator", "Detection model loaded successfully")
                } catch (e: Throwable) {
                    android.util.Log.e("PipelineCoordinator", "Failed to load detection model", e)
                }

                // Depth model loading is disabled by default
                // Uncomment the block below to enable depth (may crash on some devices)
                /*
                try {
                    depth.load()
                    depthEnabled = true
                    android.util.Log.i("PipelineCoordinator", "Depth model loaded successfully")
                } catch (e: Throwable) {
                    android.util.Log.e("PipelineCoordinator", "Failed to load depth model", e)
                    depthEnabled = false
                }
                */
            }

            // OCR pipeline
            launch(Dispatchers.Default) {
                for (frame in hub.ocrFrames) {
                    try {
                        _ocrState.value = ocr.run(frame)
                    } catch (_: Throwable) {}
                }
            }

            // Object detection pipeline
            launch(Dispatchers.Default) {
                for (frame in hub.detFrames) {
                    try {
                        val dets = det.run(frame.bitmap)
                        _detState.value = DetectionResult(frame.timestampNs, dets)
                    } catch (_: Throwable) {}
                }
            }

            // Depth estimation pipeline - DISABLED by default
            // Only runs if depthEnabled is manually set to true above
            if (depthEnabled) {
                launch(Dispatchers.Default) {
                    var lastDepthMs = 0L

                    for (frame in hub.depthFrames) {
                        val nowMs = android.os.SystemClock.elapsedRealtime()
                        if (nowMs - lastDepthMs < 1000) continue // ~1 FPS
                        lastDepthMs = nowMs

                        try {
                            val map = depth.run(frame.bitmap)
                            _depthMapState.value = map

                            val viz = map?.let { depth.toGrayscaleBitmap(it) }
                            val scaledViz = viz?.let {
                                Bitmap.createScaledBitmap(it, frame.bitmap.width, frame.bitmap.height, true)
                            }
                            _depthState.value = DepthResult(tsNs = frame.timestampNs, depthViz = scaledViz)
                        } catch (e: Throwable) {
                            android.util.Log.e("PipelineCoordinator", "Depth inference failed", e)
                            depthEnabled = false
                            break
                        }
                    }
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
