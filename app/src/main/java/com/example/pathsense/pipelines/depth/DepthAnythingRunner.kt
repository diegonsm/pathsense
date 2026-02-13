package com.example.pathsense.pipelines.depth

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.example.pathsense.core.assetToFilePath
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

class DepthAnythingRunner(private val context: Context) {

    // NEW: lightweight depth representation you can sample later for Near/Med/Far, etc.
    data class DepthMap( // CHANGED/NEW
        val width: Int,
        val height: Int,
        val closeness: ByteArray // 0..255, where 255 = "closer" (inverted normalized depth) // CHANGED/NEW
    )

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var inputName: String = "image"

    private val inputSize = 518
    private val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val std = floatArrayOf(0.229f, 0.224f, 0.225f)

    fun load() {
        env = OrtEnvironment.getEnvironment()
        val modelPath = assetToFilePath(context, "depth_anything_v2_small.onnx")
        session = env!!.createSession(modelPath, OrtSession.SessionOptions())
        inputName = session!!.inputNames.first()
    }

    // CHANGED: return DepthMap instead of Bitmap so detection can sample depth efficiently
    fun run(bitmap: Bitmap): DepthMap? { // CHANGED
        val env = env ?: return null
        val session = session ?: return null

        val input = preprocess(bitmap)
        val shape = longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong())

        val inputTensor = OnnxTensor.createTensor(env, input, shape)

        inputTensor.use {
            session.run(mapOf(inputName to it)).use { out ->
                val value = (out[0] as OnnxTensor).value
                val depth2d: Array<FloatArray>? = when (value) {
                    is Array<*> -> {
                        val a0 = value as Array<*>
                        val first = a0.firstOrNull()
                        when (first) {
                            is Array<*> -> {
                                val first2 = (first as Array<*>).firstOrNull()
                                when (first2) {
                                    is FloatArray -> first as Array<FloatArray> // [H][W]
                                    is Array<*> -> {
                                        val inner = first2 as Array<*>
                                        if (inner.firstOrNull() is FloatArray) {
                                            inner as Array<FloatArray> // [H][W]
                                        } else null
                                    }
                                    else -> null
                                }
                            }
                            is FloatArray -> null
                            else -> null
                        }
                    }
                    else -> null
                }

                if (depth2d == null) return null

                // CHANGED: convert raw depth -> compact closeness map (0..255) for fast ROI sampling
                return depthToClosenessMap(depth2d) // CHANGED
            }
        }
    }

    private fun preprocess(src: Bitmap): FloatBuffer {
        val resized = Bitmap.createScaledBitmap(src, inputSize, inputSize, true)
        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        val fb = FloatBuffer.allocate(1 * 3 * inputSize * inputSize)

        // CHW layout
        for (c in 0 until 3) {
            val mc = mean[c]
            val sc = std[c]
            for (i in pixels.indices) {
                val p = pixels[i]
                val v = when (c) {
                    0 -> ((p shr 16) and 0xFF) / 255.0f
                    1 -> ((p shr 8) and 0xFF) / 255.0f
                    else -> (p and 0xFF) / 255.0f
                }
                fb.put((v - mc) / sc)
            }
        }

        fb.rewind()
        return fb
    }

    // NEW: convert raw depth (arbitrary scale) to a 0..255 "closeness" map
    // - normalize depth across the frame
    // - invert so "closer" becomes higher intensity (255 = closer) to match your previous grayscale viz
    private fun depthToClosenessMap(depth: Array<FloatArray>): DepthMap { // CHANGED/NEW
        val h = depth.size
        val w = depth[0].size

        var mn = Float.POSITIVE_INFINITY
        var mx = Float.NEGATIVE_INFINITY
        for (y in 0 until h) {
            for (x in 0 until w) {
                val v = depth[y][x]
                mn = min(mn, v)
                mx = max(mx, v)
            }
        }
        val range = (mx - mn).takeIf { it > 1e-6f } ?: 1f

        val closeness = ByteArray(w * h) // CHANGED/NEW
        var idx = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val norm = ((depth[y][x] - mn) / range * 255f).toInt().coerceIn(0, 255)
                val inv = 255 - norm // closer => higher
                closeness[idx++] = inv.toByte()
            }
        }

        return DepthMap(width = w, height = h, closeness = closeness) // CHANGED/NEW
    }

    // OPTIONAL helper: if you still want a visualization Bitmap for debug/UI overlay later
    fun toGrayscaleBitmap(map: DepthMap): Bitmap { // CHANGED/NEW
        val bmp = Bitmap.createBitmap(map.width, map.height, Bitmap.Config.ARGB_8888)
        var idx = 0
        for (y in 0 until map.height) {
            for (x in 0 until map.width) {
                val v = map.closeness[idx++].toInt() and 0xFF
                bmp.setPixel(x, y, Color.rgb(v, v, v))
            }
        }
        return bmp
    }

    fun close() {
        try { session?.close() } catch (_: Exception) {}
        session = null
        try { env?.close() } catch (_: Exception) {}
        env = null
    }
}
