package com.example.pathsense.pipelines.detection

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.example.pathsense.core.assetToFilePath
import com.example.pathsense.pipelines.results.Detection
import java.nio.ByteBuffer

class MobileNetSsdRunner(private val context: Context) {

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var inputName: String = "image"

    private val inputW = 300
    private val inputH = 300

    fun load() {
        env = OrtEnvironment.getEnvironment()
        val modelPath = assetToFilePath(context, "ssd_mobilenet_v1_12.onnx")
        session = env!!.createSession(modelPath, OrtSession.SessionOptions())
        inputName = session!!.inputNames.first()
    }

    fun run(bitmap: Bitmap): List<Detection> {
        val env = env ?: return emptyList()
        val session = session ?: return emptyList()

        val input = preprocess(bitmap)
        val shape = longArrayOf(1, inputH.toLong(), inputW.toLong(), 3)

        val inputTensor = OnnxTensor.createTensor(
            env,
            input,
            shape,
            OnnxJavaType.UINT8
        )

        inputTensor.use {
            session.run(mapOf(inputName to it)).use { out ->
                val classes = (out[1] as OnnxTensor).value as Array<FloatArray>
                val scores = (out[2] as OnnxTensor).value as Array<FloatArray>

                val cls = classes[0]
                val sc = scores[0]

                val dets = ArrayList<Detection>()
                for (i in sc.indices) {
                    val s = sc[i]
                    if (s >= 0.35f) {
                        dets.add(
                            Detection(
                                classId = cls.getOrNull(i)?.toInt() ?: 0,
                                score = s
                            )
                        )
                    }
                }
                return dets.sortedByDescending { it.score }.take(10)
            }
        }
    }

    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, inputW, inputH, true)
        val pixels = IntArray(inputW * inputH)
        resized.getPixels(pixels, 0, inputW, 0, 0, inputW, inputH)

        val buf = ByteBuffer.allocateDirect(1 * inputW * inputH * 3)
        for (p in pixels) {
            buf.put(((p shr 16) and 0xFF).toByte())
            buf.put(((p shr 8) and 0xFF).toByte())
            buf.put((p and 0xFF).toByte())
        }
        buf.rewind()
        return buf
    }

    fun close() {
        try { session?.close() } catch (_: Exception) {}
        session = null
        try { env?.close() } catch (_: Exception) {}
        env = null
    }
}
