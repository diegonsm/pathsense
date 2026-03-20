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

sealed interface DepthStatus {
    data object Loading : DepthStatus
    data object Ready : DepthStatus
    data class Failed(val reason: String) : DepthStatus
}

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

    private val _depthStatus = MutableStateFlow<DepthStatus>(DepthStatus.Loading)
    val depthStatus: StateFlow<DepthStatus> = _depthStatus.asStateFlow()

    private var started = false

    // Depth is enabled by default, but we keep it fail-safe:
    // if the model fails to load or inference fails, we disable depth and expose status to the UI.
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

                try {
                    depth.load()
                    depthEnabled = true
                    _depthStatus.value = DepthStatus.Ready
                    android.util.Log.i("PipelineCoordinator", "Depth model loaded successfully")
                } catch (e: Throwable) {
                    android.util.Log.e("PipelineCoordinator", "Failed to load depth model", e)
                    depthEnabled = false
                    _depthStatus.value = DepthStatus.Failed(
                        reason = "Depth model load failed: ${e.message ?: e::class.java.simpleName}"
                    )
                }
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
            // Only runs if depthEnabled was set to true after model load
            if (depthEnabled) {
                launch(Dispatchers.Default) {
                    var lastDepthMs = 0L
                    var consecutiveNullMaps = 0
                    val maxConsecutiveNullMaps = 5

                    for (frame in hub.depthFrames) {
                        val nowMs = android.os.SystemClock.elapsedRealtime()
                        if (nowMs - lastDepthMs < 1000) continue // ~1 FPS
                        lastDepthMs = nowMs

                        try {
                            val map = depth.run(frame.bitmap)
                            if (map == null) {
                                consecutiveNullMaps++
                                if (consecutiveNullMaps >= maxConsecutiveNullMaps) {
                                    depthEnabled = false
                                    _depthStatus.value = DepthStatus.Failed(
                                        reason = "Depth output could not be parsed (no usable depth maps for a while)."
                                    )
                                    break
                                }
                                continue
                            }

                            consecutiveNullMaps = 0
                            _depthMapState.value = map

                            val viz = depth.toGrayscaleBitmap(map)
                            val scaledViz = viz?.let {
                                Bitmap.createScaledBitmap(it, frame.bitmap.width, frame.bitmap.height, true)
                            }
                            _depthState.value = DepthResult(tsNs = frame.timestampNs, depthViz = scaledViz)
                        } catch (e: Throwable) {
                            android.util.Log.e("PipelineCoordinator", "Depth inference failed", e)
                            depthEnabled = false
                            _depthStatus.value = DepthStatus.Failed(
                                reason = "Depth inference failed: ${e.message ?: e::class.java.simpleName}"
                            )
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
        depthEnabled = false
        _depthStatus.value = DepthStatus.Loading
    }
}
