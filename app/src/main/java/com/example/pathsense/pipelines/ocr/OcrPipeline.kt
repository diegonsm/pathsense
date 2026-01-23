package com.example.pathsense.pipelines.ocr

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
        val image = InputImage.fromBitmap(frame.bitmap, 0)
        val text = recognizer.process(image).await().text
        return OcrResult(text = text, tsNs = frame.timestampNs)
    }

    fun close() = recognizer.close()
}
