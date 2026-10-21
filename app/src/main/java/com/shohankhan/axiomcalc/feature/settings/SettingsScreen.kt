package com.shohankhan.axiomcalc.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.shohankhan.axiomcalc.R
import com.shohankhan.axiomcalc.core.design.AxiomDimens
import com.shohankhan.axiomcalc.core.ui.SectionCard
import com.shohankhan.axiomcalc.domain.model.AppSettings
import com.shohankhan.axiomcalc.domain.model.RetentionPolicy
import com.shohankhan.axiomcalc.domain.model.ThemeMode
import com.shohankhan.axiomcalc.domain.repository.HistoryRepository
import com.shohankhan.axiomcalc.domain.repository.SettingsRepository
import com.shohankhan.axiomcalc.infra.di.axiomViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Settings ViewModel: reads and writes through the repository contract only. */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _rowCount = kotlinx.coroutines.flow.MutableStateFlow(0)
    val historyCount: StateFlow<Int> = _rowCount

    init {
        refreshCount()
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            settingsRepository.update(transform)
        }
    }

    fun refreshCount() {
        viewModelScope.launch {
            _rowCount.value = runCatching { historyRepository.count() }.getOrDefault(0)
        }
    }

    /**
     * Applies retention immediately and reports the number of rows removed, so
     * the user can see the effect rather than trusting a background job.
     */
    fun runRetentionNow(onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val deleted = runCatching {
                historyRepository.deleteExpired(System.currentTimeMillis(), settings.value.retention)
            }.getOrDefault(-1)
            refreshCount()
            onResult(deleted)
        }
    }
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = axiomViewModel { container, _ ->
        SettingsViewModel(container.settings, container.historyRepository)
    },
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val historyCount by viewModel.historyCount.collectAsStateWithLifecycle()
    var retentionNotice by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AxiomDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(title = stringResource(R.string.settings_appearance)) {
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.theme == mode,
                        onClick = { viewModel.update { it.copy(theme = mode) } },
                        label = {
                            Text(
                                stringResource(
                                    when (mode) {
                                        ThemeMode.SYSTEM -> R.string.settings_theme_system
                                        ThemeMode.LIGHT -> R.string.settings_theme_light
                                        ThemeMode.DARK -> R.string.settings_theme_dark
                                    },
                                ),
                            )
                        },
                    )
                }
            }
        }

        SectionCard(title = stringResource(R.string.settings_feedback)) {
            SwitchRow(
                title = stringResource(R.string.settings_haptics),
                summary = stringResource(R.string.settings_haptics_summary),
                checked = settings.hapticsEnabled,
                onCheckedChange = { value -> viewModel.update { it.copy(hapticsEnabled = value) } },
            )
        }

        SectionCard(title = stringResource(R.string.settings_numbers)) {
            Text(stringResource(R.string.settings_angle_mode), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("DEGREES" to R.string.angle_degrees, "RADIANS" to R.string.angle_radians).forEach { (name, label) ->
                    FilterChip(
                        selected = settings.angleModeName == name,
                        onClick = { viewModel.update { it.copy(angleModeName = name) } },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            SwitchRow(
                title = stringResource(R.string.settings_auto_precision),
                summary = stringResource(R.string.settings_auto_precision_summary),
                checked = settings.autoPrecision,
                onCheckedChange = { value -> viewModel.update { it.copy(autoPrecision = value) } },
            )
            if (!settings.autoPrecision) {
                Text(
                    text = stringResource(R.string.settings_decimal_precision) + ": " + settings.decimalPlaces,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Slider(
                    value = settings.decimalPlaces.toFloat(),
                    onValueChange = { value ->
                        viewModel.update { it.copy(decimalPlaces = value.toInt()) }
                    },
                    valueRange = 0f..16f,
                    steps = 15,
                    modifier = Modifier.semantics {
                        contentDescription = "Decimal places, currently ${settings.decimalPlaces}"
                    },
                )
            }
            SwitchRow(
                title = stringResource(R.string.settings_grouping),
                summary = stringResource(R.string.settings_grouping_summary),
                checked = settings.grouping,
                onCheckedChange = { value -> viewModel.update { it.copy(grouping = value) } },
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.settings_number_format), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "DECIMAL" to R.string.settings_format_decimal,
                    "SCIENTIFIC" to R.string.settings_format_scientific,
                    "ENGINEERING" to R.string.settings_format_engineering,
                ).forEach { (name, label) ->
                    FilterChip(
                        selected = settings.notationName == name,
                        onClick = { viewModel.update { it.copy(notationName = name) } },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
        }

        SectionCard(title = stringResource(R.string.settings_data)) {
            Text(stringResource(R.string.settings_retention), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(R.string.settings_retention_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RetentionPolicy.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { policy ->
                            FilterChip(
                                selected = settings.retention == policy,
                                onClick = {
                                    viewModel.update { it.copy(retention = policy) }
                                    viewModel.runRetentionNow { }
                                },
                                label = { Text(policy.label) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_storage_summary, historyCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { viewModel.refreshCount() }) {
                    Text(stringResource(R.string.settings_refresh_count))
                }
                TextButton(onClick = {
                    viewModel.runRetentionNow { deleted ->
                        retentionNotice = if (deleted < 0) {
                            "Retention could not be applied right now."
                        } else {
                            "Removed $deleted expired entries."
                        }
                    }
                }) { Text(stringResource(R.string.settings_apply_retention)) }
            }
            retentionNotice?.let { notice ->
                Text(
                    text = notice,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        SectionCard(title = stringResource(R.string.settings_privacy)) {
            Text(
                text = stringResource(R.string.settings_offline_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier
                .size(AxiomDimens.MinTouchTarget)
                .semantics { contentDescription = title },
        )
    }
}
