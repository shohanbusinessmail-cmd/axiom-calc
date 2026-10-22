package com.shohankhan.axiomcalc.feature.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.shohankhan.axiomcalc.R
import com.shohankhan.axiomcalc.core.ui.EmptyState
import com.shohankhan.axiomcalc.domain.formula.FormResult
import com.shohankhan.axiomcalc.domain.model.CalcOrigin
import com.shohankhan.axiomcalc.domain.model.HistoryEntry
import com.shohankhan.axiomcalc.infra.di.AppContainer
import com.shohankhan.axiomcalc.infra.di.axiomViewModel
import kotlinx.coroutines.launch

/**
 * Hosts the shared runner for one Formula Calculator form or Quick Solve tool.
 *
 * The id comes from the navigation argument and is resolved against the real
 * registries; an unknown id renders an explicit "not found" state rather than a
 * blank screen or an invented tool.
 */
@Composable
fun ToolRunnerScreen(
    toolId: String,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val spec = remember(toolId) { ToolSpec.resolve(toolId) }

    Box(modifier.fillMaxSize()) {
        val resolved = spec
        if (resolved == null) {
            EmptyState(
                iconRes = R.drawable.ic_search,
                title = stringResource(R.string.tool_not_found_title),
                body = stringResource(R.string.tool_not_found_body),
            )
        } else {
            val viewModel: ToolRunnerViewModel = axiomViewModel { container, _ ->
                ToolRunnerViewModel(resolved) { toolSpec, values, result ->
                    recordToolResult(container, toolSpec, values, result)
                }
            }
            ToolRunnerPanel(
                viewModel = viewModel,
                onCopyResult = { text ->
                    copyToClipboard(context, text)
                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.action_copy) + "d") }
                },
            )
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * Writes one completed form/tool result into global history.
 *
 * Only a successful computation with a real value is recorded; a failure never
 * produces a row. The stored `resultValue` is the normalized value, separate
 * from `resultDisplay`, so reuse never parses formatted text.
 */
private fun recordToolResult(
    container: AppContainer,
    spec: ToolSpec,
    values: Map<String, String>,
    result: FormResult,
) {
    val primary = result.lines.firstOrNull() ?: return
    val expression = spec.title + ": " +
        spec.inputs.joinToString(", ") { input -> "${input.label}=${values[input.id].orEmpty()}" }
    container.applicationScope.launch {
        runCatching {
            container.historyRepository.add(
                HistoryEntry(
                    id = 0,
                    expression = expression,
                    resultValue = primary.value,
                    resultDisplay = primary.value,
                    origin = if (spec is ToolSpec.Form) CalcOrigin.FORMULA else CalcOrigin.QUICK_SOLVE,
                    timestampMillis = System.currentTimeMillis(),
                ),
            )
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    runCatching {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        manager.setPrimaryClip(ClipData.newPlainText("Axiom Calc result", text))
    }
}
