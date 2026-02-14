package com.example.pathsense.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Accessible button styles.
 */
enum class AccessibleButtonStyle {
    FILLED,
    TONAL,
    OUTLINED,
    TEXT
}

/**
 * Minimum touch target size for accessibility (48dp per WCAG guidelines).
 */
private val MinTouchTarget = 48.dp

/**
 * An accessible button with minimum 48dp touch target and proper content descriptions.
 *
 * @param text The button text
 * @param onClick Click handler
 * @param modifier Modifier for the component
 * @param contentDescription Content description for screen readers (defaults to text)
 * @param icon Optional leading icon
 * @param style Button style (filled, tonal, outlined, text)
 * @param enabled Whether the button is enabled
 * @param highContrast Whether to use high contrast colors
 */
@Composable
fun AccessibleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = text,
    icon: ImageVector? = null,
    style: AccessibleButtonStyle = AccessibleButtonStyle.FILLED,
    enabled: Boolean = true,
    highContrast: Boolean = false
) {
    val buttonModifier = modifier
        .defaultMinSize(minWidth = MinTouchTarget, minHeight = MinTouchTarget)
        .semantics {
            this.contentDescription = if (enabled) {
                contentDescription
            } else {
                "$contentDescription, disabled"
            }
        }

    val colors = if (highContrast) {
        getHighContrastColors(style)
    } else {
        null
    }

    val content: @Composable () -> Unit = {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = text,
            modifier = if (icon != null) Modifier else Modifier
        )
    }

    when (style) {
        AccessibleButtonStyle.FILLED -> {
            Button(
                onClick = onClick,
                modifier = buttonModifier,
                enabled = enabled,
                colors = colors ?: ButtonDefaults.buttonColors(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                content()
            }
        }
        AccessibleButtonStyle.TONAL -> {
            FilledTonalButton(
                onClick = onClick,
                modifier = buttonModifier,
                enabled = enabled,
                colors = colors ?: ButtonDefaults.filledTonalButtonColors(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                content()
            }
        }
        AccessibleButtonStyle.OUTLINED -> {
            OutlinedButton(
                onClick = onClick,
                modifier = buttonModifier,
                enabled = enabled,
                colors = colors ?: ButtonDefaults.outlinedButtonColors(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                content()
            }
        }
        AccessibleButtonStyle.TEXT -> {
            TextButton(
                onClick = onClick,
                modifier = buttonModifier,
                enabled = enabled,
                colors = colors ?: ButtonDefaults.textButtonColors(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun getHighContrastColors(style: AccessibleButtonStyle): ButtonColors {
    return when (style) {
        AccessibleButtonStyle.FILLED -> ButtonDefaults.buttonColors(
            containerColor = Color.Yellow,
            contentColor = Color.Black
        )
        AccessibleButtonStyle.TONAL -> ButtonDefaults.filledTonalButtonColors(
            containerColor = Color.White,
            contentColor = Color.Black
        )
        AccessibleButtonStyle.OUTLINED -> ButtonDefaults.outlinedButtonColors(
            contentColor = Color.Yellow
        )
        AccessibleButtonStyle.TEXT -> ButtonDefaults.textButtonColors(
            contentColor = Color.Yellow
        )
    }
}

/**
 * Large accessible button for primary actions.
 * Has increased minimum size (56dp) for easier touch targeting.
 */
@Composable
fun LargeAccessibleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = text,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    highContrast: Boolean = false
) {
    AccessibleButton(
        text = text,
        onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = 120.dp, minHeight = 56.dp),
        contentDescription = contentDescription,
        icon = icon,
        style = AccessibleButtonStyle.FILLED,
        enabled = enabled,
        highContrast = highContrast
    )
}
