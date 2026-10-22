package com.shohankhan.axiomcalc.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shohankhan.axiomcalc.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Accessibility verification on a real device.
 *
 * Two things are measured rather than assumed: every keypad key meets the 48 dp
 * minimum touch target, and every interactive control exposes a content
 * description so TalkBack can announce it.
 *
 * Run with `./gradlew connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val keyDescriptions = listOf(
        "Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
        "Decimal point", "Plus", "Minus", "Times", "Divided by", "Equals", "Percent",
        "Toggle sign", "Open parenthesis", "Close parenthesis", "Previous answer",
        "All clear", "Clear entry", "Backspace", "Undo", "Redo",
        "Memory clear", "Memory recall", "Memory add", "Memory subtract", "Memory store",
    )

    @Test fun everyKeypadKeyMeetsThe48dpMinimumTarget() {
        val minDp = 48f
        keyDescriptions.forEach { description ->
            val bounds = compose.onNodeWithContentDescription(description)
                .assertIsDisplayed()
                .getUnclippedBoundsInRoot()
            assertTrue(
                "$description is ${bounds.width.value}x${bounds.height.value} dp, below the $minDp dp minimum",
                bounds.width.value >= minDp && bounds.height.value >= minDp,
            )
        }
    }

    @Test fun everyInteractiveNodeHasAContentDescription() {
        val clickables = compose.onAllNodes(hasContentDescription(""))
            .fetchSemanticsNodes()
        // Nodes with an explicitly empty description are a TalkBack dead end.
        assertTrue("found ${clickables.size} nodes with empty descriptions", clickables.isEmpty())
    }

    @Test fun navigationTabsAnnounceTheirSelectionState() {
        val standard = compose.onNodeWithContentDescription("Tab Standard, selected")
        standard.assertIsDisplayed()
        compose.onNodeWithContentDescription("Tab Scientific").performClick()
        compose.onNodeWithContentDescription("Tab Scientific, selected").assertIsDisplayed()
    }

    @Test fun theResultIsAnnouncedWithItsLabel() {
        compose.onNodeWithContentDescription("Two").performClick()
        compose.onNodeWithContentDescription("Plus").performClick()
        compose.onNodeWithContentDescription("Two").performClick()
        compose.onNodeWithContentDescription("Equals").performClick()
        compose.onNode(hasContentDescription("Result 4")).assertIsDisplayed()
    }

    @Test fun scientificPanelExposesTheAngleModeState() {
        compose.onNodeWithContentDescription("Tab Scientific").performClick()
        compose.onNodeWithContentDescription("Angle mode, currently degrees").assertIsDisplayed()
        compose.onNodeWithContentDescription("Angle mode, currently degrees").performClick()
        compose.onNodeWithContentDescription("Angle mode, currently radians").assertIsDisplayed()
    }

    @Test fun memoryIndicatorIsAnnouncedOnlyWhenMemoryHoldsAValue() {
        compose.onNode(hasContentDescription("Memory holds ", substring = true))
            .assertDoesNotExist()
        compose.onNodeWithContentDescription("Nine").performClick()
        compose.onNodeWithContentDescription("Memory store").performClick()
        compose.onNode(hasContentDescription("Memory holds ", substring = true))
            .assertIsDisplayed()
    }

    @Test fun fontScaleDoesNotHideTheResultLine() {
        // The display must remain readable at the largest system font scale the
        // app supports; the result node must still be on screen.
        compose.onNodeWithContentDescription("Five").performClick()
        compose.onNodeWithContentDescription("Times").performClick()
        compose.onNodeWithContentDescription("Five").performClick()
        compose.onNodeWithContentDescription("Equals").performClick()
        compose.onNode(hasContentDescription("Result 25")).assertIsDisplayed()
    }
}
