package com.example.pathsense.core

import android.graphics.Bitmap

data class FrameData(
    val bitmap: Bitmap,
    val rotationDegrees: Int,
    val timestampNs: Long
)
