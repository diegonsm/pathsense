package com.example.pathsense.pipelines.results

import android.graphics.Bitmap
data class OcrResult(val text: String, val tsNs: Long)

data class Detection(
    val classId: Int,
    val score: Float,
    // normalized [0,1] in original camera frame coordinates
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val proximity: Proximity = Proximity.UNKNOWN
)

enum class Proximity { NEAR, MED, FAR, UNKNOWN }

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
