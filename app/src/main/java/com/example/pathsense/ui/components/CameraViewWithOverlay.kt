package com.example.pathsense.ui.components

import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import com.example.pathsense.pipelines.results.Detection
import com.example.pathsense.pipelines.results.Proximity

/**
 * Camera preview with optional overlays for detections and depth visualization.
 *
 * @param previewView The CameraX PreviewView to display
 * @param modifier Modifier for the component
 * @param detections List of detections to draw as bounding boxes
 * @param showBoundingBoxes Whether to show bounding boxes
 * @param depthVisualization Optional depth map bitmap overlay
 * @param showDepthVisualization Whether to show depth visualization
 * @param depthOverlayAlpha Alpha value for depth overlay (0-1)
 * @param labelProvider Function to get label text for a class ID
 */
@Composable
fun CameraViewWithOverlay(
    previewView: PreviewView,
    modifier: Modifier = Modifier,
    detections: List<Detection> = emptyList(),
    showBoundingBoxes: Boolean = true,
    depthVisualization: Bitmap? = null,
    showDepthVisualization: Boolean = false,
    depthOverlayAlpha: Float = 0.3f,
    labelProvider: (Int) -> String = { "Object" }
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "Camera preview"
            }
    ) {
        // Camera preview
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Depth visualization overlay
        if (showDepthVisualization && depthVisualization != null) {
            Image(
                bitmap = depthVisualization.asImageBitmap(),
                contentDescription = "Depth visualization",
                modifier = Modifier.fillMaxSize(),
                alpha = depthOverlayAlpha,
                contentScale = ContentScale.FillBounds
            )
        }

        // Detection bounding boxes overlay
        if (showBoundingBoxes && detections.isNotEmpty()) {
            DetectionOverlay(
                detections = detections,
                labelProvider = labelProvider,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Draws detection bounding boxes on a canvas overlay.
 */
@Composable
fun DetectionOverlay(
    detections: List<Detection>,
    labelProvider: (Int) -> String,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.semantics {
            contentDescription = "${detections.size} objects detected"
        }
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        detections.forEach { detection ->
            // Get color based on proximity
            val boxColor = getProximityColor(detection.proximity)

            // Convert normalized coordinates to canvas pixels
            val left = detection.left * canvasWidth
            val top = detection.top * canvasHeight
            val right = detection.right * canvasWidth
            val bottom = detection.bottom * canvasHeight

            // Validate coordinates
            if (left < right && top < bottom) {
                // Draw bounding box
                drawRect(
                    color = boxColor,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    style = Stroke(width = 4f)
                )

                // Draw proximity indicator (corner fill)
                val cornerSize = 12f
                drawRect(
                    color = boxColor,
                    topLeft = Offset(left, top),
                    size = Size(cornerSize, cornerSize)
                )
            }
        }
    }
}

/**
 * Get color for bounding box based on proximity.
 */
private fun getProximityColor(proximity: Proximity): Color {
    return when (proximity) {
        Proximity.NEAR -> Color.Red
        Proximity.MED -> Color.Yellow
        Proximity.FAR -> Color.Green
        Proximity.UNKNOWN -> Color(0xFF00FF00) // Default green
    }
}

/**
 * Text region highlight overlay for OCR results.
 */
@Composable
fun TextRegionOverlay(
    regions: List<TextRegion>,
    modifier: Modifier = Modifier,
    highlightColor: Color = Color.Yellow
) {
    Canvas(
        modifier = modifier.semantics {
            contentDescription = "${regions.size} text regions detected"
        }
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        regions.forEach { region ->
            val left = region.left * canvasWidth
            val top = region.top * canvasHeight
            val right = region.right * canvasWidth
            val bottom = region.bottom * canvasHeight

            if (left < right && top < bottom) {
                // Draw highlight rectangle
                drawRect(
                    color = highlightColor.copy(alpha = 0.3f),
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top)
                )
                // Draw border
                drawRect(
                    color = highlightColor,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    style = Stroke(width = 2f)
                )
            }
        }
    }
}

/**
 * Represents a text region in normalized coordinates.
 */
data class TextRegion(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val text: String = ""
)

/**
 * Navigation zone overlay for depth-based navigation.
 * Shows a 3x3 grid with color-coded proximity indicators.
 */
@Composable
fun NavigationZoneOverlay(
    zones: List<ZoneDisplay>,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.semantics {
            val nearCount = zones.count { it.proximity == Proximity.NEAR }
            contentDescription = if (nearCount > 0) {
                "$nearCount obstacle zones nearby"
            } else {
                "Path appears clear"
            }
        }
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val cellWidth = canvasWidth / 3f
        val cellHeight = canvasHeight / 3f

        zones.forEachIndexed { index, zone ->
            val col = index % 3
            val row = index / 3

            val left = col * cellWidth
            val top = row * cellHeight

            val color = getProximityColor(zone.proximity).copy(alpha = 0.4f)

            // Fill zone with proximity color
            drawRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(cellWidth, cellHeight)
            )

            // Draw zone border
            drawRect(
                color = Color.White.copy(alpha = 0.5f),
                topLeft = Offset(left, top),
                size = Size(cellWidth, cellHeight),
                style = Stroke(width = 1f)
            )
        }
    }
}

/**
 * Display data for a navigation zone.
 */
data class ZoneDisplay(
    val proximity: Proximity,
    val closeness: Int = 0
)
