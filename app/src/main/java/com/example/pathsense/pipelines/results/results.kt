package com.example.pathsense.pipelines.results

import android.graphics.Bitmap

data class OcrResult(val text: String, val tsNs: Long)

data class Detection(
    val classId: Int,
    val score: Float
)

data class DetectionResult(
    val tsNs: Long,
    val detections: List<Detection>
) {
    val numDetections: Int get() = detections.size
}

data class DepthResult(
    val tsNs: Long,
    val depthViz: Bitmap?
)
