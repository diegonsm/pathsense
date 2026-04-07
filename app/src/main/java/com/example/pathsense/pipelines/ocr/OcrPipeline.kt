package com.example.pathsense.pipelines.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.example.pathsense.core.FrameData
import com.example.pathsense.pipelines.results.OcrResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

class OcrPipeline {
    private val recognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun run(frame: FrameData): OcrResult {
        val processed = preprocessBitmap(frame.bitmap)
        val image = InputImage.fromBitmap(processed, 0)
        val visionText = recognizer.process(image).await()
        processed.recycle()

        val filtered = visionText.textBlocks
            .flatMap { it.lines }
            .filter { line ->
                line.elements.isNotEmpty() &&
                line.elements.sumOf { it.confidence.toDouble() } / line.elements.size >= CONFIDENCE_THRESHOLD
            }
            .joinToString(" ") { it.text }

        return OcrResult(text = filtered.trim(), tsNs = frame.timestampNs)
    }

    private fun preprocessBitmap(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val cm = ColorMatrix(floatArrayOf(
            CONTRAST, 0f, 0f, 0f, BRIGHTNESS,
            0f, CONTRAST, 0f, 0f, BRIGHTNESS,
            0f, 0f, CONTRAST, 0f, BRIGHTNESS,
            0f, 0f, 0f, 1f, 0f
        ))
        canvas.drawBitmap(src, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(cm) })
        return out
    }

    fun close() = recognizer.close()

    companion object {
        private const val CONFIDENCE_THRESHOLD = 0.7f
        private const val CONTRAST = 1.3f
        private const val BRIGHTNESS = -30f
    }
}
