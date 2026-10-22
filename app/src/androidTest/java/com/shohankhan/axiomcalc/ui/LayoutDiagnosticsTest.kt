package com.shohankhan.axiomcalc.ui

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shohankhan.axiomcalc.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TEMPORARY DIAGNOSTIC - remove once the layout question it answers is settled.
 *
 * Twenty-five instrumented UI tests failed with two different symptoms on the
 * same screen: some reported that a keypad key with a given content description
 * could not be found at all, and one reported that a node WAS found but was not
 * displayed. Those are contradictory unless the layout is putting the keypad
 * somewhere the tests do not expect - clipped, off-screen, or zero-sized.
 *
 * Guessing at that from source would be a guess, and the runner logs and build
 * artifacts are unreachable from the authoring sandbox, so the only channel back
 * is the pull-request comment. This test therefore fails on purpose and puts the
 * answer in its failure message: the window size, the density, and every node in
 * the unmerged semantics tree with its bounds and displayed state.
 *
 * It is named with a `diagnostic` prefix because the CI report gives those a
 * larger share of the comment budget. It asserts nothing about the product and
 * must not be left in the suite: a permanently failing test teaches whoever
 * reads the report to ignore failures.
 */
@RunWith(AndroidJUnit4::class)
class LayoutDiagnosticsTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test fun diagnosticDumpTheStandardScreenLayout() {
        compose.waitForIdle()

        val activity = compose.activity
        val metrics = activity.resources.displayMetrics
        val configuration = activity.resources.configuration

        val nodes = compose.onAllNodes(SemanticsMatcher("any node") { true }, useUnmergedTree = true)
            .fetchSemanticsNodes()

        val dump = StringBuilder()
        dump.append("DIAGNOSTIC screen=").append(metrics.widthPixels).append('x')
            .append(metrics.heightPixels).append("px")
            .append(" density=").append(metrics.density)
            .append(" densityDpi=").append(metrics.densityDpi)
            .append(" fontScale=").append(configuration.fontScale)
            .append(" screenLayout=").append(configuration.screenLayout)
            .append(" smallestScreenWidthDp=").append(configuration.smallestScreenWidthDp)
            .append(" orientation=").append(configuration.orientation)
            .append(" nodes=").append(nodes.size)
            .append(" || ")

        // The unmerged tree, in order. Bounds are read defensively: a node that
        // was never laid out throws, and that fact is itself the interesting
        // part of the answer.
        nodes.take(120).forEach { node ->
            val bounds = runCatching { node.boundsInRoot.toString() }
                .getOrElse { "UNLAID(${it.javaClass.simpleName})" }
            dump.append('[').append(node.id).append(']')
                .append(" displayed=").append(node.isDisplayed())
                .append(" bounds=").append(bounds)
                .append(" cfg=").append(node.config.toString().replace('\n', ' '))
                .append(" | ")
        }

        throw AssertionError(dump.toString())
    }
}
