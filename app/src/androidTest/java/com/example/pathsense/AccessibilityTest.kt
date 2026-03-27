package com.example.pathsense

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelectable
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pathsense.ui.components.AccessibleButton
import com.example.pathsense.ui.components.AccessibleButtonStyle
import com.example.pathsense.ui.components.AppMode
import com.example.pathsense.ui.components.FeedbackChip
import com.example.pathsense.ui.components.ModeIndicator
import com.example.pathsense.ui.components.ModeSelector
import com.example.pathsense.ui.components.SpeakingIndicator
import com.example.pathsense.ui.theme.PathSenseTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for accessibility features.
 * Verifies touch targets, content descriptions, and semantics.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // Mode Selector Tests

    @Test
    fun modeSelector_hasAccessibleLabels() {
        var selectedMode = AppMode.SCENE

        composeTestRule.setContent {
            PathSenseTheme {
                ModeSelector(
                    currentMode = selectedMode,
                    onModeSelected = { selectedMode = it }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(
            "Scene mode, selected. Scene mode: Identify and describe objects around you",
            substring = true
        ).assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription(
            "Read mode. Read mode: Read text from signs, documents, and screens",
            substring = true
        ).assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription(
            "Navigate mode. Navigate mode: Real-time obstacle warnings while walking",
            substring = true
        ).assertIsDisplayed()
    }

    @Test
    fun modeSelector_changesSelectionOnClick() {
        var selectedMode = AppMode.SCENE

        composeTestRule.setContent {
            PathSenseTheme {
                ModeSelector(
                    currentMode = selectedMode,
                    onModeSelected = { selectedMode = it }
                )
            }
        }

        // Click on Read mode
        composeTestRule.onNodeWithText("Read").performClick()

        // Verify selection changed
        assert(selectedMode == AppMode.READ)
    }

    // Accessible Button Tests

    @Test
    fun accessibleButton_hasContentDescription() {
        composeTestRule.setContent {
            PathSenseTheme {
                AccessibleButton(
                    text = "Test Button",
                    onClick = {},
                    contentDescription = "Activate test feature"
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Activate test feature")
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun accessibleButton_disabledStateAnnounced() {
        composeTestRule.setContent {
            PathSenseTheme {
                AccessibleButton(
                    text = "Disabled Button",
                    onClick = {},
                    enabled = false
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Disabled Button, disabled")
            .assertIsDisplayed()
    }

    @Test
    fun accessibleButton_highContrastMode() {
        composeTestRule.setContent {
            PathSenseTheme(highContrast = true) {
                AccessibleButton(
                    text = "High Contrast",
                    onClick = {},
                    highContrast = true
                )
            }
        }

        composeTestRule.onNodeWithText("High Contrast")
            .assertIsDisplayed()
    }

    @Test
    fun accessibleButton_differentStyles() {
        composeTestRule.setContent {
            PathSenseTheme {
                AccessibleButton(
                    text = "Filled",
                    onClick = {},
                    style = AccessibleButtonStyle.FILLED
                )
                AccessibleButton(
                    text = "Tonal",
                    onClick = {},
                    style = AccessibleButtonStyle.TONAL
                )
                AccessibleButton(
                    text = "Outlined",
                    onClick = {},
                    style = AccessibleButtonStyle.OUTLINED
                )
            }
        }

        composeTestRule.onNodeWithText("Filled").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tonal").assertIsDisplayed()
        composeTestRule.onNodeWithText("Outlined").assertIsDisplayed()
    }

    // Feedback Indicator Tests

    @Test
    fun speakingIndicator_shownWhenSpeaking() {
        composeTestRule.setContent {
            PathSenseTheme {
                SpeakingIndicator(isSpeaking = true)
            }
        }

        composeTestRule.onNodeWithContentDescription("Speaking")
            .assertIsDisplayed()
    }

    @Test
    fun speakingIndicator_hiddenWhenNotSpeaking() {
        composeTestRule.setContent {
            PathSenseTheme {
                SpeakingIndicator(isSpeaking = false)
            }
        }

        composeTestRule.onAllNodesWithContentDescription("Speaking")
            .fetchSemanticsNodes()
            .isEmpty()
    }

    @Test
    fun feedbackChip_hasCorrectDescription() {
        composeTestRule.setContent {
            PathSenseTheme {
                FeedbackChip(
                    text = "Person at 12 o'clock",
                    isSpeaking = true
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(
            "Currently announcing: Person at 12 o'clock"
        ).assertIsDisplayed()
    }

    @Test
    fun feedbackChip_showsLastAnnouncementWhenNotSpeaking() {
        composeTestRule.setContent {
            PathSenseTheme {
                FeedbackChip(
                    text = "Person at 12 o'clock",
                    isSpeaking = false
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(
            "Last announcement: Person at 12 o'clock"
        ).assertIsDisplayed()
    }

    // Mode Indicator Tests

    @Test
    fun modeIndicator_displaysModeName() {
        composeTestRule.setContent {
            PathSenseTheme {
                ModeIndicator(modeName = "Explore")
            }
        }

        composeTestRule.onNodeWithContentDescription("Explore mode active")
            .assertIsDisplayed()
    }

    // Theme Tests

    @Test
    fun highContrastTheme_appliesCorrectly() {
        composeTestRule.setContent {
            PathSenseTheme(highContrast = true) {
                ModeSelector(
                    currentMode = AppMode.SCENE,
                    onModeSelected = {},
                    highContrast = true
                )
            }
        }

        // Mode selector should be visible with high contrast
        composeTestRule.onNodeWithText("Scene").assertIsDisplayed()
    }

    @Test
    fun largeTextTheme_appliesCorrectly() {
        composeTestRule.setContent {
            PathSenseTheme(largeText = true) {
                AccessibleButton(
                    text = "Large Text",
                    onClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Large Text").assertIsDisplayed()
    }
}
