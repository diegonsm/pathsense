package com.example.pathsense.pipelines

import android.content.Context
import android.graphics.Bitmap
import com.example.pathsense.core.FrameHub
import com.example.pathsense.core.FrameTarget
import com.example.pathsense.pipelines.depth.DepthAnythingRunner
import com.example.pathsense.pipelines.detection.MobileNetSsdRunner
import com.example.pathsense.pipelines.ocr.OcrPipeline
import com.example.pathsense.pipelines.results.DetectionResult
import com.example.pathsense.pipelines.results.DepthResult
import com.example.pathsense.pipelines.results.OcrResult
import com.example.pathsense.ui.components.AppMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

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

    @Volatile
    private var currentMode: AppMode = AppMode.SCENE
    private val modeToken = AtomicInteger(0)
    private val pipelineJobs = mutableListOf<Job>()

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

                try {
                    depth.load()
                    depthEnabled = true
                    android.util.Log.i("PipelineCoordinator", "Depth model loaded successfully")
                } catch (e: Throwable) {
                    android.util.Log.e("PipelineCoordinator", "Failed to load depth model", e)
                    depthEnabled = false
                }
            }

            refreshTargets()

            // OCR pipeline
            val ocrJob = launch(Dispatchers.Default) {
                for (frame in hub.ocrFrames) {
                    if (!shouldRunOcr()) continue
                    val tokenBefore = modeToken.get()
                    try {
                        val result = ocr.run(frame)
                        if (tokenBefore != modeToken.get()) continue
                        _ocrState.value = result
                    } catch (_: Throwable) {}
                }
            }
            pipelineJobs.add(ocrJob)

            // Object detection pipeline
            val detJob = launch(Dispatchers.Default) {
                for (frame in hub.detFrames) {
                    if (!shouldRunDetection()) continue
                    val tokenBefore = modeToken.get()
                    try {
                        val dets = det.run(frame.bitmap)
                        if (tokenBefore != modeToken.get()) continue
                        _detState.value = DetectionResult(
                            tsNs = frame.timestampNs,
                            detections = dets,
                            frameWidth = frame.bitmap.width,
                            frameHeight = frame.bitmap.height
                        )
                    } catch (_: Throwable) {}
                }
            }
            pipelineJobs.add(detJob)

            // Depth estimation pipeline — runs if model loaded successfully
            if (depthEnabled) {
                val depthJob = launch(Dispatchers.Default) {
                    var lastDepthMs = 0L

                    for (frame in hub.depthFrames) {
                        if (!shouldRunDepth()) continue
                        val tokenBefore = modeToken.get()
                        val nowMs = android.os.SystemClock.elapsedRealtime()
                        if (nowMs - lastDepthMs < 1000) continue // ~1 FPS
                        lastDepthMs = nowMs

                        try {
                            val map = depth.run(frame.bitmap)
                            if (tokenBefore != modeToken.get()) continue
                            _depthMapState.value = map

                            val viz = map?.let { depth.toGrayscaleBitmap(it) }
                            val scaledViz = viz?.let {
                                Bitmap.createScaledBitmap(it, frame.bitmap.width, frame.bitmap.height, true)
                            }
                            _depthState.value = DepthResult(tsNs = frame.timestampNs, depthViz = scaledViz)
                        } catch (e: Throwable) {
                            android.util.Log.e("PipelineCoordinator", "Depth inference failed", e)
                            depthEnabled = false
                            _depthState.value = null
                            _depthMapState.value = null
                            refreshTargets()
                            break
                        }
                    }
                }
                pipelineJobs.add(depthJob)
            }
        }
    }

    fun setMode(mode: AppMode, force: Boolean = false) {
        if (!force && mode == currentMode) return
        currentMode = mode
        modeToken.incrementAndGet()

        refreshTargets()

        // Clear stale pipeline results when switching modes
        if (!modeNeedsOcr(mode)) {
            _ocrState.value = null
        }
        if (!modeNeedsDetection(mode)) {
            _detState.value = null
        }
        if (!modeNeedsDepth(mode)) {
            _depthState.value = null
            _depthMapState.value = null
        }
    }

    fun stop() {
        pipelineJobs.forEach { it.cancel() }
        pipelineJobs.clear()
        hub.close()
        ocr.close()
        det.close()
        depth.close()
    }

    private fun shouldRunOcr(): Boolean = modeNeedsOcr(currentMode)

    private fun shouldRunDetection(): Boolean = modeNeedsDetection(currentMode)

    private fun shouldRunDepth(): Boolean = modeNeedsDepth(currentMode)

    private fun modeNeedsOcr(mode: AppMode): Boolean =
        mode == AppMode.READ || mode == AppMode.ALL

    private fun modeNeedsDetection(mode: AppMode): Boolean =
        mode == AppMode.SCENE || mode == AppMode.NAVIGATE || mode == AppMode.ALL

    private fun modeNeedsDepth(mode: AppMode): Boolean =
        (mode == AppMode.SCENE || mode == AppMode.NAVIGATE || mode == AppMode.ALL) && depthEnabled

    private fun enabledTargetsFor(mode: AppMode): Set<FrameTarget> {
        val targets = mutableSetOf<FrameTarget>()
        if (modeNeedsOcr(mode)) targets.add(FrameTarget.OCR)
        if (modeNeedsDetection(mode)) targets.add(FrameTarget.DETECTION)
        if (modeNeedsDepth(mode)) targets.add(FrameTarget.DEPTH)
        return targets
    }

    private fun refreshTargets() {
        hub.setEnabledTargets(enabledTargetsFor(currentMode))
    }
}
