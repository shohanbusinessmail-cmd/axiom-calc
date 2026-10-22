package com.shohankhan.axiomcalc.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyPress
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shohankhan.axiomcalc.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end UI tests for the Standard calculator.
 *
 * These drive the real activity through Compose semantics: assertions are on
 * what the user sees, so a regression in the buffer, the engine or the
 * formatter fails here even though each layer has its own unit tests.
 *
 * Run with `./gradlew connectedDebugAndroidTest` on a device or emulator.
 */
@RunWith(AndroidJUnit4::class)
class StandardCalculatorUiTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    /** Clicks keys through their TalkBack descriptions, never through glyphs. */
    private fun pressKeys(vararg descriptions: String) {
        descriptions.forEach { description ->
            compose.onNodeWithContentDescription(description).performClick()
        }
    }

    @Test fun twoPlusTwoShowsFour() {
        pressKeys("Two", "Plus", "Two", "Equals")
        compose.onNodeWithText("4").assertIsDisplayed()
    }

    @Test fun precedenceIsRespectedOnScreen() {
        pressKeys("Two", "Plus", "Three", "Times", "Four", "Equals")
        compose.onNodeWithText("14").assertIsDisplayed()
    }

    @Test fun percentKeyAfterPlusMeansPercentOfTheRunningTotal() {
        // 200 + 15 % = 230, not 200.15: the documented percentage-key semantics.
        pressKeys("Two", "Zero", "Zero", "Plus", "One", "Five", "Percent", "Equals")
        compose.onNodeWithText("230").assertIsDisplayed()
    }

    @Test fun divisionByZeroShowsATypedMessageAndNoSilentValue() {
        pressKeys("Five", "Divided by", "Zero", "Equals")
        compose.onNodeWithText("Cannot divide by zero.").assertIsDisplayed()
    }

    @Test fun backspaceRemovesOneTokenAtATime() {
        pressKeys("One", "Two", "Three", "Backspace")
        compose.onNodeWithText("12").assertIsDisplayed()
    }

    @Test fun allClearKeepsMemory() {
        pressKeys("Seven", "Memory store", "All clear", "Memory recall")
        compose.onNodeWithText("7").assertIsDisplayed()
    }

    @Test fun undoRestoresTheClearedExpression() {
        pressKeys("Eight", "Nine", "All clear", "Undo")
        compose.onNodeWithText("89").assertIsDisplayed()
    }

    @Test fun equalsResultCanBeReusedAsAns() {
        pressKeys("Six", "Times", "Seven", "Equals")
        compose.onNodeWithText("42").assertIsDisplayed()
        pressKeys("Previous answer", "Plus", "Eight", "Equals")
        compose.onNodeWithText("50").assertIsDisplayed()
    }

    @Test fun hardwareKeyboardDigitsReachTheBuffer() {
        // The keypad requests focus through a FocusRequester and routes events
        // through onPreviewKeyEvent to CalculatorViewModel.consumeKeyEvent, so a
        // physical keyboard must drive it with no on-screen text field.
        //
        // Only stable APIs are used here, deliberately. performKeyPress takes
        // androidx.compose.ui.input.key.KeyEvent, which is a value class over an
        // android.view.KeyEvent, so the platform event is built explicitly.
        // The two alternatives both depend on which Compose version is being
        // compiled: performKeyInput { pressKey(Key.One) } is marked experimental
        // in the pinned BOM, and the opt-in marker is named
        // ExperimentalComposeUiTestApi in some releases and ExperimentalTestApi
        // in others. Neither name resolved here, and a test should not fail to
        // compile over an annotation that exists only to warn about an API this
        // test does not need.
        val keypad = compose.onNodeWithContentDescription("Calculator keypad")
        for (keyCode in listOf(
            AndroidKeyEvent.KEYCODE_1,
            AndroidKeyEvent.KEYCODE_2,
            AndroidKeyEvent.KEYCODE_PLUS,
            AndroidKeyEvent.KEYCODE_3,
            AndroidKeyEvent.KEYCODE_0,
            AndroidKeyEvent.KEYCODE_ENTER,
        )) {
            keypad.performKeyPress(
                KeyEvent(AndroidKeyEvent(AndroidKeyEvent.ACTION_DOWN, keyCode)),
            )
        }
        compose.onNodeWithText("42").assertIsDisplayed()
    }

    @Test fun everyKeypadKeyHasAnAccessibilityDescription() {
        listOf(
            "Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
            "Decimal point", "Plus", "Minus", "Times", "Divided by", "Equals", "Percent",
            "Toggle sign", "Open parenthesis", "Close parenthesis", "Previous answer",
            "All clear", "Clear entry", "Backspace", "Undo", "Redo",
            "Memory clear", "Memory recall", "Memory add", "Memory subtract", "Memory store",
        ).forEach { description ->
            compose.onNode(hasContentDescription(description)).assertIsDisplayed()
        }
    }
}
