package com.shohankhan.axiomcalc.feature.formulas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shohankhan.axiomcalc.R
import com.shohankhan.axiomcalc.core.design.AxiomDimens
import com.shohankhan.axiomcalc.core.ui.EmptyState
import com.shohankhan.axiomcalc.domain.formula.FormulaCatalog
import com.shohankhan.axiomcalc.domain.formula.FormulaCategory
import com.shohankhan.axiomcalc.domain.formula.FormulaForm

/**
 * Formula Calculator (primary destination 3).
 *
 * A pure selection screen: it lists the 13 categories A–M and the forms inside
 * them, all from [FormulaCatalog]. Opening a form navigates to the shared tool
 * runner, so there is exactly one computation path for every form.
 */
@Composable
fun FormulasScreen(
    onOpenForm: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialCategoryId: String? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf(initialCategoryId ?: "") }

    val category = FormulaCategory.entries.firstOrNull { it.id == selectedCategory }
    val forms: List<FormulaForm> = when {
        query.isNotBlank() -> FormulaCatalog.search(query)
        category != null -> FormulaCatalog.byCategory(category)
        else -> FormulaCatalog.all
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = AxiomDimens.ScreenPadding)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.formulas_search_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(AxiomDimens.ListGap))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = category == null && query.isBlank(),
                    onClick = { selectedCategory = "" },
                    label = { Text(stringResource(R.string.formulas_category_all)) },
                )
            }
            items(FormulaCategory.entries.toList()) { entry ->
                FilterChip(
                    selected = selectedCategory == entry.id && query.isBlank(),
                    onClick = { selectedCategory = entry.id },
                    label = { Text("${entry.id.uppercase()}. ${entry.title}") },
                    modifier = Modifier.semantics {
                        contentDescription = "Category ${entry.title}"
                    },
                )
            }
        }
        Spacer(Modifier.height(AxiomDimens.ListGap))

        if (forms.isEmpty()) {
            EmptyState(
                iconRes = R.drawable.ic_nav_formulas,
                title = stringResource(R.string.formulas_empty),
                body = stringResource(R.string.formulas_empty_body),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(AxiomDimens.ListGap),
            ) {
                items(forms, key = { it.id }) { form ->
                    FormCard(form = form, onClick = { onOpenForm(form.id) })
                }
            }
        }
    }
}

@Composable
private fun FormCard(form: FormulaForm, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(AxiomDimens.Radius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(AxiomDimens.CardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = form.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = form.category.id.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = form.formulaText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
