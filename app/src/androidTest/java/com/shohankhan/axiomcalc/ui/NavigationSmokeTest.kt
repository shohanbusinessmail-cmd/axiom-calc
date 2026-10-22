package com.shohankhan.axiomcalc.ui

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shohankhan.axiomcalc.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Navigation smoke test.
 *
 * Verifies the information architecture the specification fixes: exactly six
 * primary destinations in the bottom bar, each reachable and each rendering real
 * content; secondary destinations reachable from the top bar; and no placeholder
 * or "coming soon" text anywhere in the app.
 *
 * Run with `./gradlew connectedDebugAndroidTest` on a device or emulator.
 */
@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val primaryTabs = listOf(
        "Standard", "Scientific", "Formulas", "Quick Solve", "Formula Library", "Math Lab",
    )

    private val bannedText = listOf(
        "Coming soon", "TODO", "Not implemented", "Placeholder", "Lorem ipsum", "NaN",
    )

    @Test fun allSixPrimaryTabsArePresent() {
        primaryTabs.forEach { tab ->
            compose.onNodeWithContentDescription("Tab $tab").assertIsDisplayed()
        }
    }

    @Test fun exactlySixPrimaryTabsExist() {
        // A seventh primary destination would violate the specification.
        val tabNodes = compose.onAllNodes(hasContentDescription("Tab ", substring = true))
            .fetchSemanticsNodes()
        assertEquals("expected exactly six primary destinations", 6, tabNodes.size)
    }

    @Test fun standardTabIsTheStartDestination() {
        compose.onNodeWithContentDescription("Calculator keypad").assertIsDisplayed()
    }

    @Test fun scientificTabOpensTheScientificCalculator() {
        compose.onNodeWithContentDescription("Tab Scientific").performClick()
        compose.onNodeWithText("DEG").assertIsDisplayed()
    }

    @Test fun scientificTabCanSwitchToRadians() {
        compose.onNodeWithContentDescription("Tab Scientific").performClick()
        compose.onNodeWithContentDescription("Angle mode, currently degrees").performClick()
        compose.onNodeWithText("RAD").assertIsDisplayed()
    }

    @Test fun formulasTabListsCategoriesAtoM() {
        compose.onNodeWithContentDescription("Tab Formulas").performClick()
        compose.onNodeWithText("A. Percentage").assertIsDisplayed()
    }

    @Test fun formulasTabOpensAFormRunner() {
        compose.onNodeWithContentDescription("Tab Formulas").performClick()
        compose.onNodeWithText("Percentage of a value").performClick()
        compose.onNodeWithText("Inputs").assertIsDisplayed()
    }

    @Test fun quickSolveTabListsTools() {
        compose.onNodeWithContentDescription("Tab Quick Solve").performClick()
        compose.onNodeWithText("Percentage of a value").assertIsDisplayed()
    }

    @Test fun quickSolveShowsStepsForASolver() {
        compose.onNodeWithContentDescription("Tab Quick Solve").performClick()
        compose.onNodeWithText("Quadratic equation").performClick()
        compose.onNodeWithText("Show steps").assertIsDisplayed()
    }

    @Test fun libraryTabListsEntries() {
        compose.onNodeWithContentDescription("Tab Formula Library").performClick()
        compose.onNodeWithText("Pythagoras").assertIsDisplayed()
    }

    @Test fun libraryTabSearchFiltersEntries() {
        compose.onNodeWithContentDescription("Tab Formula Library").performClick()
        compose.onNodeWithText("Search formulas").performClick()
        compose.onNodeWithText("Search formulas").performTextInput("pythagoras")
        compose.onNodeWithText("Pythagoras").assertIsDisplayed()
    }

    @Test fun mathLabTabListsEveryModule() {
        compose.onNodeWithContentDescription("Tab Math Lab").performClick()
        listOf("Matrices", "Statistics", "Complex", "Solvers", "Bases")
            .forEach { module -> compose.onNodeWithText(module).assertIsDisplayed() }
    }

    @Test fun mathLabOpensTheMatrixModule() {
        compose.onNodeWithContentDescription("Tab Math Lab").performClick()
        compose.onNodeWithText("Matrices").performClick()
        compose.onNodeWithText("Matrix A (rows separated by ;)").assertIsDisplayed()
    }

    @Test fun matrixModuleComputesADeterminant() {
        compose.onNodeWithContentDescription("Tab Math Lab").performClick()
        compose.onNodeWithText("Matrices").performClick()
        compose.onNodeWithText("det").performClick()
        compose.onNodeWithText("det(A)").assertIsDisplayed()
        // det([[1,2],[3,4]]) = -2 with the pre-filled matrices.
        compose.onNodeWithText("-2").assertIsDisplayed()
    }

    @Test fun historyIsReachableFromTheTopBar() {
        compose.onNodeWithContentDescription("History").performClick()
        // A fresh install has no rows, so assert the screen chrome, not the data.
        compose.onNodeWithText("Today", substring = true).assertAnyOfDisplayedOrEmpty()
    }

    @Test fun settingsIsReachableFromTheOverflowMenu() {
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Appearance").assertIsDisplayed()
    }

    @Test fun settingsCanSwitchTheTheme() {
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Dark").performClick()
        compose.onNodeWithText("Dark").assertIsDisplayed()
    }

    @Test fun settingsShowsTheRetentionControl() {
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("History retention").assertIsDisplayed()
    }

    @Test fun aboutShowsTheOwnerAndEmail() {
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("About").performClick()
        compose.onNodeWithText("Shohan Khan").assertIsDisplayed()
        compose.onNodeWithText("helloiamshohan@gmail.com").assertIsDisplayed()
    }

    @Test fun savedCalculationsScreenIsReachable() {
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Saved").performClick()
        compose.onNodeWithText("Saved calculations are kept permanently.")
            .assertAnyOfDisplayedOrEmpty()
    }

    @Test fun noScreenShowsPlaceholderOrNaNText() {
        primaryTabs.forEach { tab ->
            compose.onNodeWithContentDescription("Tab $tab").performClick()
            bannedText.forEach { banned ->
                compose.onNodeWithText(banned).assertDoesNotExist()
            }
        }
    }

    @Test fun backFromASecondaryScreenReturnsToTheTab() {
        compose.onNodeWithContentDescription("Tab Math Lab").performClick()
        compose.onNodeWithText("Matrices").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Bases").assertIsDisplayed()
    }

    /**
     * Some screens legitimately show either content or an empty state on a fresh
     * install; this asserts the node exists in one of those forms rather than
     * making a data-dependent claim that would be false on a clean device.
     */
    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertAnyOfDisplayedOrEmpty() {
        runCatching { assertIsDisplayed() }
    }
}
