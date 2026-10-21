package com.shohankhan.axiomcalc.feature.calculator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shohankhan.axiomcalc.R
import com.shohankhan.axiomcalc.core.design.AxiomDimens
import com.shohankhan.axiomcalc.infra.di.axiomViewModel
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

/** Builds either calculator ViewModel from the manual container. */
@Composable
fun rememberStandardViewModel(): CalculatorViewModel = axiomViewModel { container, context ->
    StandardCalculatorViewModel(context, container.settings, container.historyRepository, container.savedRepository)
}

@Composable
fun rememberScientificViewModel(): CalculatorViewModel = axiomViewModel { container, context ->
    ScientificCalculatorViewModel(context, container.settings, container.historyRepository, container.savedRepository)
}

/**
 * Standard Calculator screen.
 *
 * Layout is driven by available size rather than by device type: in landscape
 * (or on any wide window) the display sits beside the keypad; in portrait it
 * sits above it. Nothing scrolls — the keypad is always fully reachable.
 */
@Composable
fun StandardCalculatorScreen(
    viewModel: CalculatorViewModel = rememberStandardViewModel(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    var showSaveDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CalculatorEvent.Haptic -> haptics.performHapticFeedback(
                    if (event.strong) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove,
                )
                is CalculatorEvent.Message -> snackbarHostState.showSnackbar(event.text)
                CalculatorEvent.RequestSaveName -> showSaveDialog = true
            }
        }
    }

    if (showSaveDialog) {
        SaveCalculationDialog(
            expression = state.expression,
            result = state.result,
            onDismiss = { showSaveDialog = false },
            onConfirm = { name ->
                viewModel.saveCalculation(name)
                showSaveDialog = false
            },
        )
    }

    Box(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val landscape = maxWidth > maxHeight
            if (landscape) {
                Row(Modifier.fillMaxSize().padding(horizontal = AxiomDimens.ScreenPadding)) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        CalculatorDisplay(state, viewModel)
                    }
                    Column(Modifier.weight(1f)) {
                        StandardKeypad(state, viewModel, compact = maxHeight < 420.dp)
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal = AxiomDimens.ScreenPadding)) {
                    CalculatorDisplay(state, viewModel)
                    Spacer(Modifier.height(AxiomDimens.ListGap))
                    StandardKeypad(state, viewModel, compact = maxHeight < 620.dp)
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

/** Display + status + quick actions, shared by both calculators. */
@Composable
fun CalculatorDisplay(state: CalculatorUiState, viewModel: CalculatorViewModel) {
    Column {
        if (state.memory != null) {
            Text(
                text = stringResource(R.string.memory_value_format, state.memory),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .semantics { stateDescription = "Memory holds ${state.memory}" },
            )
        }
        com.shohankhan.axiomcalc.core.ui.DisplayPanel(
            expression = state.expression,
            result = state.result,
            preview = state.preview,
            error = state.error,
            onLongPressResult = { viewModel.onCopyExpressionAndResult() },
        )
        Spacer(Modifier.height(AxiomDimens.ListGap))
        CalculatorActionRow(viewModel)
    }
}

/** Undo / redo / copy / paste / save strip above the keypad. */
@Composable
fun CalculatorActionRow(viewModel: CalculatorViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AxiomDimens.KeyGap),
    ) {
        KeyButton(
            key = CalcKey.UNDO,
            onKey = { viewModel.onKey(it) },
            modifier = Modifier.weight(1f),
            compact = true,
            enabled = state.canUndo,
        )
        KeyButton(
            key = CalcKey.REDO,
            onKey = { viewModel.onKey(it) },
            modifier = Modifier.weight(1f),
            compact = true,
            enabled = state.canRedo,
        )
        KeyButton(
            key = CalcKey.COPY,
            onKey = { viewModel.onKey(it) },
            modifier = Modifier.weight(1f),
            compact = true,
            enabled = state.expression.isNotBlank(),
        )
        KeyButton(
            key = CalcKey.PASTE,
            onKey = { viewModel.onKey(it) },
            modifier = Modifier.weight(1f),
            compact = true,
        )
        KeyButton(
            key = CalcKey.SAVE,
            onKey = { viewModel.onKey(it) },
            modifier = Modifier.weight(1f),
            compact = true,
        )
    }
}

/** Memory strip + main grid + footer with parentheses, Ans and equals. */
@Composable
fun StandardKeypad(
    state: CalculatorUiState,
    viewModel: CalculatorViewModel,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Hardware keyboard support: the keypad is focusable and takes key
            // focus so a physical or on-screen keyboard can drive the calculator.
            .focusRequester(focusRequester)
            .focusable()
            .semantics { contentDescription = "Calculator keypad" }
            .onPreviewKeyEvent { event -> viewModel.consumeKeyEvent(event) },
        verticalArrangement = Arrangement.spacedBy(AxiomDimens.KeyGap),
    ) {
        LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
        KeypadGrid(
            rows = listOf(KeypadLayouts.memoryRow),
            onKey = { viewModel.onKey(it) },
            compact = true,
            // Recall/clear/accumulate only make sense once memory holds a value;
            // store is always available because it reads the live entry.
            enabledKeys = if (state.memory != null) {
                KeypadLayouts.memoryRow.toSet()
            } else {
                setOf(CalcKey.MS)
            },
        )
        KeypadGrid(
            rows = KeypadLayouts.standardRows,
            onKey = { viewModel.onKey(it) },
            compact = compact,
        )
        WeightedKeyRow(
            keys = KeypadLayouts.standardFooterRow,
            onKey = { viewModel.onKey(it) },
            spans = listOf(1f, 1f, 1f, 1f, 1f, 2.4f),
            compact = compact,
        )
    }
}

/**
 * Translates a hardware key event into a calculator key.
 *
 * Only KeyDown is consumed; repeats are handled by the platform. Returns false
 * for unmapped keys so the system can still handle navigation and IME keys.
 */
fun CalculatorViewModel.consumeKeyEvent(event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val codePoint = event.utf16CodePoint
    val char = if (codePoint in 0x20..0x7E) codePoint.toChar() else null
    if (char != null && onHardwareKey(char)) return true
    return when (event.key) {
        Key.Enter, Key.NumPadEnter -> { onEquals(); true }
        Key.Backspace -> { onBackspace(); true }
        Key.Delete -> { onClearEntry(); true }
        Key.Escape -> { onAllClear(); true }
        else -> false
    }
}

/** Name-entry dialog used when saving a calculation. */
@Composable
fun SaveCalculationDialog(
    expression: String,
    result: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.save_calculation_title)) },
        text = {
            Column {
                Text(
                    text = "$expression = $result",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.save_calculation_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.save_calculation_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
