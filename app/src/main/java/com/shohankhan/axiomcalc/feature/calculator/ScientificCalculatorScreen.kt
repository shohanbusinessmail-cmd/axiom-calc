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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shohankhan.axiomcalc.R
import com.shohankhan.axiomcalc.core.design.AxiomDimens
import com.shohankhan.axiomcalc.math.AngleMode

/**
 * Scientific Calculator screen.
 *
 * Two density modes are provided as the specification requires: a compact panel
 * that always fits a small portrait phone, and an expanded panel that adds
 * inverse/hyperbolic trigonometry, extra roots and powers, further constants and
 * the combinatorics functions. The expanded state survives process death and
 * configuration change because it is `rememberSaveable`.
 *
 * Complex-number arithmetic (rectangular and polar) is not evaluated inside the
 * real-number expression engine. Rather than pretending otherwise, this screen
 * links to the dedicated complex module in Math Lab, which implements and tests
 * it fully. See MATHEMATICS_SPEC.md §Complex numbers.
 */
@Composable
fun ScientificCalculatorScreen(
    viewModel: CalculatorViewModel = rememberScientificViewModel(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onOpenComplexLab: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    var showSaveDialog by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

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
            val compactKeys = if (landscape) maxHeight < 420.dp else maxHeight < 700.dp

            val panel: @Composable () -> Unit = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(AxiomDimens.KeyGap),
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent { viewModel.consumeKeyEvent(it) },
                ) {
                    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
                    ScientificModeRow(
                        angleMode = state.angleMode,
                        expanded = expanded,
                        onToggleAngle = { viewModel.onKey(CalcKey.DEG_MODE) },
                        onToggleExpanded = { expanded = !expanded },
                        onOpenComplexLab = onOpenComplexLab,
                    )
                    KeypadGrid(
                        rows = listOf(KeypadLayouts.memoryRow),
                        onKey = { viewModel.onKey(it) },
                        compact = true,
                        enabledKeys = if (state.memory != null) {
                            KeypadLayouts.memoryRow.toSet()
                        } else setOf(CalcKey.MS)
                    )
                    KeypadGrid(
                        rows = KeypadLayouts.scientificRows,
                        onKey = { viewModel.onKey(it) },
                        compact = compactKeys,
                        selectedKeys = if (state.angleMode == AngleMode.RADIANS) setOf(CalcKey.DEG_MODE) else emptySet(),
                    )
                    if (expanded) {
                        KeypadGrid(
                            rows = KeypadLayouts.scientificExpandedRows,
                            onKey = { viewModel.onKey(it) },
                            compact = compactKeys,
                        )
                    }
                    KeypadGrid(
                        rows = KeypadLayouts.standardRows,
                        onKey = { viewModel.onKey(it) },
                        compact = compactKeys,
                    )
                    WeightedKeyRow(
                        keys = KeypadLayouts.standardFooterRow,
                        onKey = { viewModel.onKey(it) },
                        spans = listOf(1f, 1f, 1f, 1f, 1f, 2.4f),
                        compact = compactKeys,
                    )
                }
            }

            if (landscape) {
                Row(Modifier.fillMaxSize().padding(horizontal = AxiomDimens.ScreenPadding)) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        CalculatorDisplay(state, viewModel)
                    }
                    Column(Modifier.weight(1.15f)) { panel() }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal = AxiomDimens.ScreenPadding)) {
                    CalculatorDisplay(state, viewModel)
                    Spacer(Modifier.height(AxiomDimens.ListGap))
                    panel()
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

/** DEG/RAD, expand/collapse and the complex-arithmetic entry point. */
@Composable
private fun ScientificModeRow(
    angleMode: AngleMode,
    expanded: Boolean,
    onToggleAngle: () -> Unit,
    onToggleExpanded: () -> Unit,
    onOpenComplexLab: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = angleMode == AngleMode.RADIANS,
            onClick = onToggleAngle,
            label = {
                Text(
                    if (angleMode == AngleMode.DEGREES) {
                        stringResource(R.string.angle_mode_degrees)
                    } else {
                        stringResource(R.string.angle_mode_radians)
                    },
                )
            },
            modifier = Modifier.semantics {
                contentDescription = "Angle mode, currently " +
                    if (angleMode == AngleMode.DEGREES) "degrees" else "radians"
            },
        )
        FilterChip(
            selected = expanded,
            onClick = onToggleExpanded,
            label = {
                Text(
                    if (expanded) {
                        stringResource(R.string.scientific_collapse)
                    } else {
                        stringResource(R.string.scientific_expand)
                    },
                )
            },
        )
        FilterChip(
            selected = false,
            onClick = onOpenComplexLab,
            label = { Text(stringResource(R.string.lab_complex_title)) },
        )
    }
}
