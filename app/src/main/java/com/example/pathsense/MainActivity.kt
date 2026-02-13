package com.example.pathsense

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.view.PreviewView.ScaleType.FIT_CENTER
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.pathsense.camera.CameraStreamer
import com.example.pathsense.core.FrameHub
import com.example.pathsense.pipelines.PipelineCoordinator
import com.example.pathsense.pipelines.detection.cocoLabel
import com.example.pathsense.pipelines.results.DetectionResult

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppScreen() }
    }
}

@Composable
fun AppScreen() {
    val context = LocalContext.current
    val hub = remember { FrameHub() }
    val scope = rememberCoroutineScope()
    val coordinator = remember {
        PipelineCoordinator(context.applicationContext, hub, scope)
    }

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }

    // start pipelines once permission is granted
    LaunchedEffect(granted) {
        if (granted) coordinator.start()
    }

    DisposableEffect(Unit) {
        onDispose { coordinator.stop() }
    }

    val ocr by coordinator.ocrState.collectAsState(initial = null)
    val det by coordinator.detState.collectAsState(initial = null)
    val depth by coordinator.depthState.collectAsState(initial = null)

    Column(Modifier.fillMaxSize()) {
        if (!granted) {
            Text(
                text = "Camera permission required",
                modifier = Modifier.padding(16.dp)
            )
            return@Column
        }

        // CHANGED: Wrap preview in a Box and draw overlay on top
        Box(Modifier.weight(1f)) {
            CameraPreviewStreaming(
                modifier = Modifier.fillMaxSize(),
                hub = hub
            )

            DetectionOverlay(
                modifier = Modifier.fillMaxSize(),
                det = det
            )
        }

        Column(Modifier.padding(12.dp)) {
            Text(
                text = "OCR: ${ocr?.text?.take(120) ?: "..."}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Detections: ${det?.numDetections ?: 0}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Top: ${
                    det?.detections?.take(3)?.joinToString {
                        "${cocoLabel(it.classId)} ${(it.score * 100).toInt()}%"
                    } ?: "..."
                }",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Depth: ${if (depth?.depthViz != null) "viz ready" else "..."}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

// NEW: Draws bounding boxes from DetectionResult on top of the camera preview
@Composable
fun DetectionOverlay(
    modifier: Modifier = Modifier,
    det: DetectionResult?
) {
    Canvas(modifier = modifier) {
        val detections = det?.detections ?: return@Canvas

        for (d in detections) {
            // Assumes left/top/right/bottom are normalized [0,1] in preview coordinates.
            val leftPx = d.left.coerceIn(0f, 1f) * size.width
            val topPx = d.top.coerceIn(0f, 1f) * size.height
            val rightPx = d.right.coerceIn(0f, 1f) * size.width
            val bottomPx = d.bottom.coerceIn(0f, 1f) * size.height

            val w = (rightPx - leftPx).coerceAtLeast(0f)
            val h = (bottomPx - topPx).coerceAtLeast(0f)

            drawRect(
                color = Color.Green,
                topLeft = Offset(leftPx, topPx),
                size = Size(w, h),
                style = Stroke(width = 4f)
            )
        }
    }
}

@Composable
fun CameraPreviewStreaming(
    modifier: Modifier = Modifier,
    hub: FrameHub
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)

            previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
            previewView.scaleType = FIT_CENTER
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val streamer = CameraStreamer(hub)
                val analysis = streamer.buildImageAnalysis()

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                } catch (_: Exception) {
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = modifier
    )
}
