package com.example.pathsense.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Visual indicator showing when TTS is speaking.
 * Displays an animated speaker icon with pulsing effect.
 *
 * @param isSpeaking Whether TTS is currently speaking
 * @param modifier Modifier for the component
 * @param highContrast Whether to use high contrast colors
 */
@Composable
fun SpeakingIndicator(
    isSpeaking: Boolean,
    modifier: Modifier = Modifier,
    highContrast: Boolean = false
) {
    if (!isSpeaking) return

    val infiniteTransition = rememberInfiniteTransition(label = "speaking")

    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val backgroundColor = if (highContrast) Color.Yellow else MaterialTheme.colorScheme.primary
    val contentColor = if (highContrast) Color.Black else MaterialTheme.colorScheme.onPrimary

    Surface(
        modifier = modifier
            .scale(scale)
            .alpha(alpha)
            .semantics {
                contentDescription = "Speaking"
            },
        shape = CircleShape,
        color = backgroundColor
    ) {
        Icon(
            imageVector = Icons.Default.VolumeUp,
            contentDescription = null,
            modifier = Modifier
                .padding(8.dp)
                .size(24.dp),
            tint = contentColor
        )
    }
}

/**
 * Feedback chip showing current status or last announcement.
 *
 * @param text The feedback text to display
 * @param isSpeaking Whether this text is currently being spoken
 * @param modifier Modifier for the component
 * @param highContrast Whether to use high contrast colors
 */
@Composable
fun FeedbackChip(
    text: String,
    isSpeaking: Boolean,
    modifier: Modifier = Modifier,
    highContrast: Boolean = false
) {
    val backgroundColor = if (highContrast) {
        if (isSpeaking) Color.Yellow else Color.Black
    } else {
        if (isSpeaking) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    }

    val contentColor = if (highContrast) {
        if (isSpeaking) Color.Black else Color.White
    } else {
        if (isSpeaking) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    }

    Surface(
        modifier = modifier.semantics {
            contentDescription = if (isSpeaking) {
                "Currently announcing: $text"
            } else {
                "Last announcement: $text"
            }
        },
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSpeaking) {
                SpeakingDots(highContrast = highContrast)
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                maxLines = 2
            )
        }
    }
}

/**
 * Animated dots indicating active speech.
 */
@Composable
private fun SpeakingDots(
    highContrast: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")

    val dotColor = if (highContrast) Color.Black else MaterialTheme.colorScheme.primary

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val delay = index * 150

            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 600,
                        delayMillis = delay,
                        easing = LinearEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$index"
            )

            Box(
                modifier = Modifier
                    .size(6.dp)
                    .alpha(alpha)
                    .background(dotColor, CircleShape)
            )
        }
    }
}

/**
 * Mode indicator showing current operating mode.
 *
 * @param modeName The name of the current mode
 * @param modifier Modifier for the component
 * @param highContrast Whether to use high contrast colors
 */
@Composable
fun ModeIndicator(
    modeName: String,
    modifier: Modifier = Modifier,
    highContrast: Boolean = false
) {
    val backgroundColor = if (highContrast) Color.Black else MaterialTheme.colorScheme.surface
    val contentColor = if (highContrast) Color.Yellow else MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier.semantics {
            contentDescription = "$modeName mode active"
        },
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor.copy(alpha = 0.9f)
    ) {
        Text(
            text = modeName,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/**
 * Detection count indicator.
 *
 * @param count Number of objects detected
 * @param modifier Modifier for the component
 * @param highContrast Whether to use high contrast colors
 */
@Composable
fun DetectionCountIndicator(
    count: Int,
    modifier: Modifier = Modifier,
    highContrast: Boolean = false
) {
    val backgroundColor = if (highContrast) Color.Black else MaterialTheme.colorScheme.surface
    val contentColor = if (highContrast) Color.Yellow else MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = modifier.semantics {
            contentDescription = "$count objects detected"
        },
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor.copy(alpha = 0.9f)
    ) {
        Text(
            text = "$count detected",
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
