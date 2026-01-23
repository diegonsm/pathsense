package com.example.pathsense.camera

import android.graphics.*
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.pathsense.core.FrameData
import com.example.pathsense.core.FrameHub
import java.io.ByteArrayOutputStream

class CameraStreamer(private val hub: FrameHub) {

    private var lastFrameMs = 0L

    fun buildImageAnalysis(): ImageAnalysis =
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also { analysis ->
                analysis.setAnalyzer({ it.run() }) { imageProxy ->
                    analyze(imageProxy)
                }
            }

    private fun analyze(imageProxy: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastFrameMs < 300) { // ~3 fps
            imageProxy.close()
            return
        }
        lastFrameMs = now

        try {
            val bmp = imageProxy.toBitmap() ?: return
            hub.publish(
                FrameData(
                    bitmap = bmp,
                    rotationDegrees = imageProxy.imageInfo.rotationDegrees,
                    timestampNs = imageProxy.imageInfo.timestamp
                )
            )
        } finally {
            imageProxy.close()
        }
    }

    @androidx.camera.core.ExperimentalGetImage
    private fun ImageProxy.toBitmap(): Bitmap? {
        val image = image ?: return null
        if (image.format != ImageFormat.YUV_420_888) return null

        val y = image.planes[0].buffer
        val u = image.planes[1].buffer
        val v = image.planes[2].buffer

        val nv21 = ByteArray(y.remaining() + u.remaining() + v.remaining())
        y.get(nv21, 0, y.remaining())
        v.get(nv21, y.remaining(), v.remaining())
        u.get(nv21, y.remaining() + v.remaining(), u.remaining())

        val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuv.compressToJpeg(Rect(0, 0, width, height), 70, out)
        val bytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}
