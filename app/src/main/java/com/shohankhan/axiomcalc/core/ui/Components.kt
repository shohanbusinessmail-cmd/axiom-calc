package com.shohankhan.axiomcalc.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shohankhan.axiomcalc.core.design.Axiom
import com.shohankhan.axiomcalc.core.design.AxiomDimens
import com.shohankhan.axiomcalc.core.errors.CalcError
import com.shohankhan.axiomcalc.core.ui.userMessage

/** Visual role of a keypad button; drives colour and TalkBack grouping. */
enum class KeyRole { DIGIT, FUNCTION, OPERATOR, EQUALS, MEMORY, NEUTRAL }

/**
 * A keypad button.
 *
 * Height is at least [AxiomDimens.MinTouchTarget] (48 dp) in every role so the
 * accessible target requirement holds even when a compact layout is used, and
 * every button exposes a content description for TalkBack rather than relying
 * on the visible glyph.
 */
@Composable
fun KeypadButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: KeyRole = KeyRole.NEUTRAL,
    description: String = label,
    iconRes: Int? = null,
    iconVector: ImageVector? = null,
    enabled: Boolean = true,
    compact: Boolean = false,
    selected: Boolean = false,
) {
    val extended = Axiom.extended
    val container = when (role) {
        KeyRole.DIGIT -> extended.digitKey
        KeyRole.FUNCTION -> extended.functionKey
        KeyRole.OPERATOR -> extended.operatorKey
        KeyRole.EQUALS -> extended.equalsKey
        KeyRole.MEMORY -> extended.memoryKey
        KeyRole.NEUTRAL -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = when (role) {
        KeyRole.EQUALS -> extended.onEqualsKey
        KeyRole.OPERATOR -> extended.onOperatorKey
        KeyRole.MEMORY -> extended.onMemoryKey
        else -> MaterialTheme.colorScheme.onSurface
    }
    val height = if (compact) AxiomDimens.KeyHeightCompact else AxiomDimens.KeyHeight
    val shape = RoundedCornerShape(AxiomDimens.KeyRadius)
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = AxiomDimens.MinTouchTarget)
            .height(height.coerceAtLeast(AxiomDimens.MinTouchTarget))
            .clip(shape)
            .background(container)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else extended.keyBorder,
                shape = shape,
            ),
        shape = shape,
        color = Color.Transparent,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            when {
                iconVector != null -> Icon(
                    imageVector = iconVector,
                    contentDescription = description,
                    tint = content,
                    modifier = Modifier.size(22.dp),
                )
                iconRes != null -> Icon(
                    painter = painterResource(iconRes),
                    contentDescription = description,
                    tint = content,
                    modifier = Modifier.size(22.dp),
                )
                else -> CompositionLocalProvider(LocalContentColor provides content) {
                    Text(
                        text = label,
                        style = if (role == KeyRole.DIGIT) {
                            MaterialTheme.typography.titleLarge
                        } else {
                            MaterialTheme.typography.labelLarge
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                }
            }
            // TalkBack reads the description once per button, not the glyph.
            if (iconRes != null || iconVector != null) {
                Box(Modifier.matchParentSize().clearAndSetSemantics { contentDescription = description })
            }
        }
    }
}

/**
 * The shared display panel: expression, result and an inline status line that
 * shows a preview, a typed error message, or nothing. It never shows "NaN", a
 * stack trace or a raw exception.
 */
@Composable
fun DisplayPanel(
    expression: String,
    result: String,
    preview: String?,
    error: CalcError?,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = true,
    onLongPressResult: (() -> Unit)? = null,
) {
    val extended = Axiom.extended
    val align = if (alignEnd) TextAlign.End else TextAlign.Start
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AxiomDimens.Radius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(AxiomDimens.CardPadding)) {
            Text(
                text = expression.ifBlank { " " },
                style = MaterialTheme.typography.titleMedium,
                color = extended.previewText,
                textAlign = align,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = result.ifBlank { " " },
                style = MaterialTheme.typography.displayMedium,
                color = extended.displayText,
                textAlign = align,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Result $result" }
                    .then(
                        // Long-press copies the calculation; implemented with
                        // pointerInput so no experimental Foundation API is needed.
                        if (onLongPressResult != null) {
                            Modifier.pointerInput(result) {
                                detectTapGestures(onLongPress = { onLongPressResult() })
                            }
                        } else Modifier
                    ),
            )
            Spacer(Modifier.height(4.dp))
            val status = when {
                error != null -> error.userMessage()
                preview != null -> "= $preview"
                else -> null
            }
            Text(
                text = status ?: " ",
                style = MaterialTheme.typography.bodyMedium,
                color = if (error != null) extended.errorText else extended.previewText,
                textAlign = align,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A labelled numeric/text field used by every form and solver tool. */
@Composable
fun AxiomField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    errorText: String? = null,
    numeric: Boolean = true,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (hint == null) null else ({ Text(hint) }),
        isError = errorText != null,
        supportingText = if (errorText == null) null else ({ Text(errorText) }),
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions.Default,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
    )
}

/** One labelled result row (used by forms, solvers and lab modules). */
@Composable
fun ResultRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.1f),
        )
    }
}

/** A titled card that groups inputs or results. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AxiomDimens.Radius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(AxiomDimens.CardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                trailing?.invoke()
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

/** Empty state with an icon and explanatory text (never a blank screen). */
@Composable
fun EmptyState(
    iconRes: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Inline banner for non-fatal notices (e.g. retention cleanup failures). */
@Composable
fun NoticeBanner(text: String, modifier: Modifier = Modifier, iconRes: Int? = null) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (iconRes != null) {
                Icon(
                    painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontSize = 13.sp,
            )
        }
    }
}
