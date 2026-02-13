package com.example.pathsense.pipelines.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.example.pathsense.core.assetToFilePath
import com.example.pathsense.pipelines.results.Detection
import java.nio.ByteBuffer
import kotlin.math.min

class MobileNetSsdRunner(private val context: Context) {

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var inputName: String = "image"

    private val inputW = 300
    private val inputH = 300

    // CHANGED: metadata so we can map boxes back to the original frame correctly
    private data class LetterboxMeta( // CHANGED
        val origW: Int,
        val origH: Int,
        val scale: Float,
        val padX: Float,
        val padY: Float
    )

    fun load() {
        env = OrtEnvironment.getEnvironment()
        val modelPath = assetToFilePath(context, "ssd_mobilenet_v1_12.onnx")
        session = env!!.createSession(modelPath, OrtSession.SessionOptions())
        inputName = session!!.inputNames.first()
    }

    fun run(bitmap: Bitmap): List<Detection> {
        val env = env ?: return emptyList()
        val session = session ?: return emptyList()

        // CHANGED: preprocess returns both input tensor bytes and letterbox mapping metadata
        val (input, meta) = preprocessLetterbox(bitmap) // CHANGED

        val shape = longArrayOf(1, inputH.toLong(), inputW.toLong(), 3)

        val inputTensor = OnnxTensor.createTensor(
            env,
            input,
            shape,
            OnnxJavaType.UINT8
        )

        inputTensor.use {
            session.run(mapOf(inputName to it)).use { out ->
                val boxesAny = (out[0] as OnnxTensor).value
                val classesAny = (out[1] as OnnxTensor).value
                val scoresAny = (out[2] as OnnxTensor).value

                val classes = classesAny as? Array<FloatArray> ?: return emptyList()
                val scores = scoresAny as? Array<FloatArray> ?: return emptyList()
                val boxes = boxesAny as? Array<Array<FloatArray>> ?: return emptyList()

                val cls = classes[0]
                val sc = scores[0]
                val bx = boxes[0] // N x 4, normalized in letterboxed 300x300 space

                val dets = ArrayList<Detection>()
                for (i in sc.indices) {
                    val s = sc[i]
                    if (s < 0.35f) continue

                    val box = bx.getOrNull(i)
                    if (box == null || box.size < 4) continue

                    // SSD format confirmed from your log: [ymin, xmin, ymax, xmax] normalized
                    val yMin = box[0]
                    val xMin = box[1]
                    val yMax = box[2]
                    val xMax = box[3]

                    // CHANGED: map from model's normalized coords (in 300x300) back to original frame
                    val mapped = mapBoxToOriginalNormalized( // CHANGED
                        xMin = xMin, yMin = yMin, xMax = xMax, yMax = yMax,
                        meta = meta
                    ) ?: continue

                    dets.add(
                        Detection(
                            classId = cls.getOrNull(i)?.toInt() ?: 0,
                            score = s,
                            left = mapped.left,
                            top = mapped.top,
                            right = mapped.right,
                            bottom = mapped.bottom
                        )
                    )
                }

                return dets.sortedByDescending { it.score }.take(10)
            }
        }
    }

    // CHANGED: helper type for mapped normalized box coords in original frame space
    private data class NormBox(val left: Float, val top: Float, val right: Float, val bottom: Float) // CHANGED

    // CHANGED: undo letterbox padding/scale so overlay lines up with the real camera frame
    private fun mapBoxToOriginalNormalized(
        xMin: Float, yMin: Float, xMax: Float, yMax: Float,
        meta: LetterboxMeta
    ): NormBox? {
        // Model coords are normalized 0..1 relative to 300x300 input
        val inXMin = xMin * inputW
        val inYMin = yMin * inputH
        val inXMax = xMax * inputW
        val inYMax = yMax * inputH

        // Undo padding first
        val unpadXMin = inXMin - meta.padX
        val unpadYMin = inYMin - meta.padY
        val unpadXMax = inXMax - meta.padX
        val unpadYMax = inYMax - meta.padY

        // Undo scale back to original pixels
        val origXMin = unpadXMin / meta.scale
        val origYMin = unpadYMin / meta.scale
        val origXMax = unpadXMax / meta.scale
        val origYMax = unpadYMax / meta.scale

        // Convert to normalized coords in original bitmap space
        val left = (origXMin / meta.origW).coerceIn(0f, 1f)
        val top = (origYMin / meta.origH).coerceIn(0f, 1f)
        val right = (origXMax / meta.origW).coerceIn(0f, 1f)
        val bottom = (origYMax / meta.origH).coerceIn(0f, 1f)

        // Filter invalid / degenerate boxes (helps prevent weird tall lines)
        if (right <= left || bottom <= top) return null

        return NormBox(left, top, right, bottom)
    }

    // CHANGED: replaces squashing resize with letterboxing (keeps aspect ratio)
    private fun preprocessLetterbox(bitmap: Bitmap): Pair<ByteBuffer, LetterboxMeta> { // CHANGED
        val origW = bitmap.width
        val origH = bitmap.height

        // scale to fit within 300x300 while preserving aspect ratio
        val scale = min(inputW.toFloat() / origW.toFloat(), inputH.toFloat() / origH.toFloat())
        val newW = (origW * scale).toInt()
        val newH = (origH * scale).toInt()

        // padding to center the image in 300x300
        val padX = (inputW - newW) / 2f
        val padY = (inputH - newH) / 2f

        // Create a 300x300 bitmap and draw the scaled image onto it (letterbox)
        val boxed = Bitmap.createBitmap(inputW, inputH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(boxed)
        canvas.drawColor(Color.BLACK) // padding color

        val scaled = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        canvas.drawBitmap(scaled, padX, padY, Paint(Paint.FILTER_BITMAP_FLAG))

        val pixels = IntArray(inputW * inputH)
        boxed.getPixels(pixels, 0, inputW, 0, 0, inputW, inputH)

        val buf = ByteBuffer.allocateDirect(1 * inputW * inputH * 3)
        for (p in pixels) {
            buf.put(((p shr 16) and 0xFF).toByte())
            buf.put(((p shr 8) and 0xFF).toByte())
            buf.put((p and 0xFF).toByte())
        }
        buf.rewind()

        return buf to LetterboxMeta(
            origW = origW,
            origH = origH,
            scale = scale,
            padX = padX,
            padY = padY
        )
    }

    fun close() {
        try { session?.close() } catch (_: Exception) {}
        session = null
        try { env?.close() } catch (_: Exception) {}
        env = null
    }
}
