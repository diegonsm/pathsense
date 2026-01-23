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

    fun run(bitmap: Bitmap): Bitmap? {
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

                val depthBmp = depthToGrayscale(depth2d)

                return Bitmap.createScaledBitmap(depthBmp, bitmap.width, bitmap.height, true)
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

    private fun depthToGrayscale(depth: Array<FloatArray>): Bitmap {
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

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val norm = ((depth[y][x] - mn) / range * 255f).toInt().coerceIn(0, 255)
                val inv = 255 - norm
                bmp.setPixel(x, y, Color.rgb(inv, inv, inv))
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
