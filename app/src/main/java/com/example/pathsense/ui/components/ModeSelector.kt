package com.example.pathsense.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Operating modes for the PathSense app.
 */
enum class AppMode(
    val label: String,
    val icon: ImageVector,
    val contentDescription: String
) {
    SCENE(
        label = "Scene",
        icon = Icons.Default.Visibility,
        contentDescription = "Scene mode: Identify and describe objects around you"
    ),
    READ(
        label = "Read",
        icon = Icons.Default.MenuBook,
        contentDescription = "Read mode: Read text from signs, documents, and screens"
    ),
    NAVIGATE(
        label = "Navigate",
        icon = Icons.Default.DirectionsWalk,
        contentDescription = "Navigate mode: Real-time obstacle warnings while walking"
    ),
    ALL(
        label = "All",
        icon = Icons.Default.Dashboard,
        contentDescription = "All mode: Scene, text, and navigation running together"
    )
}

/**
 * Bottom navigation bar for mode selection.
 * Features large touch targets (48dp+) and proper accessibility semantics.
 *
 * @param currentMode The currently selected mode
 * @param onModeSelected Callback when a mode is selected
 * @param modifier Modifier for the component
 * @param highContrast Whether to use high contrast colors
 */
@Composable
fun ModeSelector(
    currentMode: AppMode,
    onModeSelected: (AppMode) -> Unit,
    modifier: Modifier = Modifier,
    highContrast: Boolean = false
) {
    val backgroundColor = if (highContrast) {
        Color.Black
    } else {
        MaterialTheme.colorScheme.surface
    }

    val selectedColor = if (highContrast) {
        Color.Yellow
    } else {
        MaterialTheme.colorScheme.primary
    }

    val unselectedColor = if (highContrast) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = backgroundColor,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .selectableGroup()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppMode.entries.forEach { mode ->
                val isSelected = mode == currentMode
                val color = if (isSelected) selectedColor else unselectedColor

                ModeButton(
                    mode = mode,
                    isSelected = isSelected,
                    color = color,
                    onClick = { onModeSelected(mode) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ModeButton(
    mode: AppMode,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(72.dp)
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.Tab
            )
            .semantics {
                contentDescription = if (isSelected) {
                    "${mode.label} mode, selected. ${mode.contentDescription}"
                } else {
                    "${mode.label} mode. ${mode.contentDescription}"
                }
                selected = isSelected
            }
            .padding(vertical = 8.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = mode.icon,
            contentDescription = null, // Handled by parent semantics
            modifier = Modifier.size(28.dp),
            tint = color
        )
        Text(
            text = mode.label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
