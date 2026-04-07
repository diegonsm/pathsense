package com.example.pathsense.ui.components

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
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
    showPreview: Boolean = true,
    detections: List<Detection> = emptyList(),
    showBoundingBoxes: Boolean = true,
    depthVisualization: Bitmap? = null,
    showDepthVisualization: Boolean = false,
    depthOverlayAlpha: Float = 0.3f,
    labelProvider: (Int) -> String = { "Object" },
    sourceFrameWidth: Int? = null,
    sourceFrameHeight: Int? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "Camera preview"
            }
    ) {
        // Camera preview — only rendered here when the caller hasn't already
        // placed the PreviewView in a stable parent higher up the hierarchy.
        if (showPreview) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
        }

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
                sourceFrameWidth = sourceFrameWidth,
                sourceFrameHeight = sourceFrameHeight,
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
    modifier: Modifier = Modifier,
    sourceFrameWidth: Int? = null,
    sourceFrameHeight: Int? = null
) {
    val lastLogMs = remember { mutableStateOf(0L) }
    val labelPaint = remember {
        Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.WHITE
            textSize = 34f
            style = Paint.Style.FILL
        }
    }

    Canvas(
        modifier = modifier.semantics {
            contentDescription = "${detections.size} objects detected"
        }
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        var frameWidth = (sourceFrameWidth ?: 0).takeIf { it > 0 } ?: canvasWidth.toInt()
        var frameHeight = (sourceFrameHeight ?: 0).takeIf { it > 0 } ?: canvasHeight.toInt()

        val viewAspect = if (canvasHeight > 0f) canvasWidth / canvasHeight else 0f
        val frameAspect = if (frameHeight > 0) frameWidth.toFloat() / frameHeight.toFloat() else 0f
        val swappedAspect = if (frameWidth > 0) frameHeight.toFloat() / frameWidth.toFloat() else 0f
        val shouldSwap = kotlin.math.abs(frameAspect - viewAspect) > kotlin.math.abs(swappedAspect - viewAspect)
        if (shouldSwap) {
            val tmp = frameWidth
            frameWidth = frameHeight
            frameHeight = tmp
        }

        val fit = fitCenterTransform(
            viewW = canvasWidth,
            viewH = canvasHeight,
            srcW = frameWidth.toFloat(),
            srcH = frameHeight.toFloat()
        )

        detections.forEachIndexed { index, detection ->
            val boxColor = confidenceColor(detection.score)

            // Convert normalized coords (source image) to preview coords (fit-center)
            val left = fit.offsetX + detection.left * fit.scale * frameWidth
            val top = fit.offsetY + detection.top * fit.scale * frameHeight
            val right = fit.offsetX + detection.right * fit.scale * frameWidth
            val bottom = fit.offsetY + detection.bottom * fit.scale * frameHeight

            if (DEBUG_OVERLAY && index == 0) {
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastLogMs.value > 1000L) {
                    lastLogMs.value = now
                    Log.d(
                        TAG,
                        "src=${frameWidth}x${frameHeight} view=${canvasWidth}x${canvasHeight} " +
                            "swap=$shouldSwap s=${"%.4f".format(fit.scale)} " +
                            "ox=${"%.2f".format(fit.offsetX)} oy=${"%.2f".format(fit.offsetY)} " +
                            "boxN=[${"%.3f".format(detection.left)},${"%.3f".format(detection.top)} " +
                            "${"%.3f".format(detection.right)},${"%.3f".format(detection.bottom)}] " +
                            "boxP=[${"%.1f".format(left)},${"%.1f".format(top)} " +
                            "${"%.1f".format(right)},${"%.1f".format(bottom)}]"
                    )
                }
            }

            // Validate coordinates
            if (left < right && top < bottom) {
                // Draw bounding box
                drawRect(
                    color = boxColor,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    style = Stroke(width = 4f)
                )

                val label = "${labelProvider(detection.classId)} ${"%.2f".format(detection.score)}"
                drawLabel(
                    label = label,
                    anchorLeft = left,
                    anchorTop = top,
                    boxColor = boxColor,
                    labelPaint = labelPaint
                )
            }
        }
    }
}


private data class FitTransform(val scale: Float, val offsetX: Float, val offsetY: Float)

private fun fitCenterTransform(viewW: Float, viewH: Float, srcW: Float, srcH: Float): FitTransform {
    if (srcW <= 0f || srcH <= 0f) return FitTransform(1f, 0f, 0f)
    val scale = minOf(viewW / srcW, viewH / srcH)
    val offsetX = (viewW - srcW * scale) / 2f
    val offsetY = (viewH - srcH * scale) / 2f
    return FitTransform(scale, offsetX, offsetY)
}

private const val CONFIDENCE_HIGH = 0.75f
private const val CONFIDENCE_MED = 0.55f
private const val DEBUG_OVERLAY = true
private const val TAG = "DetectionOverlay"

private fun confidenceColor(score: Float): Color {
    return when {
        score >= CONFIDENCE_HIGH -> Color(0xFF4CAF50) // green
        score >= CONFIDENCE_MED -> Color(0xFFFF9800) // orange
        else -> Color(0xFFFFEB3B) // yellow
    }
}

private fun DrawScope.drawLabel(
    label: String,
    anchorLeft: Float,
    anchorTop: Float,
    boxColor: Color,
    labelPaint: Paint
) {
    val padding = 8f
    val textWidth = labelPaint.measureText(label)
    val textHeight = labelPaint.fontMetrics.run { bottom - top }

    val preferredTop = anchorTop - textHeight - padding * 2f
    val labelTop = if (preferredTop >= 0f) preferredTop else anchorTop
    val labelLeft = anchorLeft.coerceAtLeast(0f)

    val backgroundRect = RectF(
        labelLeft,
        labelTop,
        labelLeft + textWidth + padding * 2f,
        labelTop + textHeight + padding * 2f
    )

    drawRoundRect(
        color = Color.Black.copy(alpha = 0.65f),
        topLeft = Offset(backgroundRect.left, backgroundRect.top),
        size = Size(backgroundRect.width(), backgroundRect.height()),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
    )

    drawRect(
        color = boxColor,
        topLeft = Offset(backgroundRect.left, backgroundRect.top),
        size = Size(backgroundRect.width(), backgroundRect.height()),
        style = Stroke(width = 2f)
    )

    drawContext.canvas.nativeCanvas.drawText(
        label,
        backgroundRect.left + padding,
        backgroundRect.top + padding - labelPaint.fontMetrics.top,
        labelPaint
    )
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
 * Shows a color-coded grid where:
 *   Red    = obstacle very close in this zone
 *   Orange = obstacle at medium distance
 *   Yellow = obstacle far ahead
 *   Green  = zone is clear
 *
 * @param zones List of zone displays (size must equal gridColumns × gridRows)
 * @param gridColumns Number of columns in the grid (default 5)
 * @param modifier Modifier for the component
 */
@Composable
fun NavigationZoneOverlay(
    zones: List<ZoneDisplay>,
    modifier: Modifier = Modifier,
    gridColumns: Int = 5
) {
    if (zones.isEmpty()) return
    val gridRows = (zones.size + gridColumns - 1) / gridColumns

    Canvas(
        modifier = modifier.semantics {
            val nearCount = zones.count { it.closeness >= 200 }
            contentDescription = if (nearCount > 0) "$nearCount zones with close obstacles"
                                  else "Path appears clear"
        }
    ) {
        val cellW = size.width / gridColumns
        val cellH = size.height / gridRows

        zones.forEachIndexed { index, zone ->
            val col = index % gridColumns
            val row = index / gridColumns
            val left = col * cellW
            val top = row * cellH

            val fillColor = zoneDepthColor(zone.closeness)
            val isDanger = zone.closeness >= 100  // MED or NEAR

            // Filled background
            drawRect(
                color = fillColor.copy(alpha = 0.30f),
                topLeft = Offset(left, top),
                size = Size(cellW, cellH)
            )

            // Border — thicker and brighter on danger zones
            drawRect(
                color = fillColor.copy(alpha = if (isDanger) 0.85f else 0.40f),
                topLeft = Offset(left, top),
                size = Size(cellW, cellH),
                style = Stroke(width = if (isDanger) 2.5f else 1f)
            )
        }
    }
}

/**
 * Maps a raw closeness value (0-255) to a traffic-light color.
 * Red = very close obstacle, Green = clear zone.
 */
private fun zoneDepthColor(closeness: Int): Color = when {
    closeness >= 200 -> Color(0xFFE53935)  // Red   — NEAR  (~≤1m)
    closeness >= 100 -> Color(0xFFFF6F00)  // Orange — MED  (~2-3m)
    closeness >= 30  -> Color(0xFFFDD835)  // Yellow — FAR  (~4m+)
    else             -> Color(0xFF43A047)  // Green  — clear
}

/**
 * Display data for a navigation zone.
 */
data class ZoneDisplay(
    val proximity: Proximity,
    val closeness: Int = 0
)
