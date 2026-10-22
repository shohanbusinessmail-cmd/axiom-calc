package com.shohankhan.axiomcalc.feature.calculator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shohankhan.axiomcalc.core.design.AxiomDimens
import com.shohankhan.axiomcalc.R
import com.shohankhan.axiomcalc.core.ui.KeypadButton

/**
 * Renders a keypad described as rows of [CalcKey].
 *
 * Keys are equal-width via `weight`, so the grid adapts from a narrow phone to a
 * tablet without any breakpoint-specific layout code, and every key keeps a
 * minimum 48 dp height from [CalcKey] itself.
 */
@Composable
fun KeypadGrid(
    rows: List<List<CalcKey>>,
    onKey: (CalcKey) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    selectedKeys: Set<CalcKey> = emptySet(),
    enabledKeys: Set<CalcKey>? = null,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AxiomDimens.KeyGap)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(AxiomDimens.KeyGap)) {
                row.forEach { key ->
                    KeyButton(
                        key = key,
                        onKey = onKey,
                        modifier = Modifier.weight(1f),
                        compact = compact,
                        selected = key in selectedKeys,
                        enabled = enabledKeys == null || key in enabledKeys,
                    )
                }
            }
        }
    }
}

/**
 * A row where the last key may span more than one column (used for the wide
 * equals key). `spans` gives the weight of each key; the default is 1 each.
 */
@Composable
fun WeightedKeyRow(
    keys: List<CalcKey>,
    onKey: (CalcKey) -> Unit,
    modifier: Modifier = Modifier,
    spans: List<Float> = List(keys.size) { 1f },
    compact: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AxiomDimens.KeyGap),
    ) {
        keys.forEachIndexed { index, key ->
            KeyButton(
                key = key,
                onKey = onKey,
                modifier = Modifier.weight(spans.getOrElse(index) { 1f }),
                compact = compact,
            )
        }
    }
}

/**
 * Single keypad button bound to a [CalcKey].
 *
 * The visible glyph comes from the key's label, and the accessibility
 * description is the key's documented description — never the glyph, so
 * TalkBack says "Divided by" rather than the division sign.
 */
@Composable
fun KeyButton(
    key: CalcKey,
    onKey: (CalcKey) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    KeypadButton(
        label = key.label,
        onClick = { onKey(key) },
        modifier = modifier.padding(0.dp),
        role = key.role,
        description = key.description ?: key.label,
        iconRes = key.iconRes(),
        enabled = enabled,
        compact = compact,
        selected = selected,
    )
}

/**
 * Vector icon for the action keys.
 *
 * Every action key that has a hand-authored vector uses it, because the
 * specification forbids emoji and requires real icons; the mathematical keys
 * keep their typographic labels, which are the conventional calculator glyphs
 * and are announced to TalkBack through [CalcKey.description].
 */
fun CalcKey.iconRes(): Int? = when (this) {
    CalcKey.UNDO -> R.drawable.ic_undo
    CalcKey.REDO -> R.drawable.ic_redo
    CalcKey.COPY -> R.drawable.ic_content_copy
    CalcKey.PASTE -> R.drawable.ic_content_paste
    CalcKey.SAVE -> R.drawable.ic_bookmark
    CalcKey.BACK -> R.drawable.ic_backspace
    else -> null
}
