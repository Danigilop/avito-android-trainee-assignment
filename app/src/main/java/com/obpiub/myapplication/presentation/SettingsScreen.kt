package com.obpiub.myapplication.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.obpiub.myapplication.domain.model.AccentColor
import com.obpiub.myapplication.domain.model.ThemeMode
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: SettingsUiState, viewModel: SettingsViewModel) {
    LaunchedEffect(Unit) { viewModel.refreshBalance() }
    val enabled = !state.isLoading && !state.isSaving
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Настройки") }, windowInsets = WindowInsets(0, 0, 0, 0)) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Тема приложения", style = MaterialTheme.typography.titleMedium)
            Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { theme ->
                    SettingOption(
                        label = when (theme) {
                            ThemeMode.System -> "Системная"
                            ThemeMode.Light -> "Светлая"
                            ThemeMode.Dark -> "Тёмная"
                        },
                        selected = state.settings.theme == theme,
                        enabled = enabled,
                        onClick = { viewModel.setTheme(theme) },
                    )
                }
            }
            Text("Основной цвет", style = MaterialTheme.typography.titleMedium)
            Column(Modifier.selectableGroup()) {
                AccentColor.entries.forEach { accent ->
                    SettingOption(
                        label = when (accent) {
                            AccentColor.Purple -> "Фиолетовый"
                            AccentColor.Blue -> "Синий"
                            AccentColor.Green -> "Зелёный"
                        },
                        selected = state.settings.accent == accent,
                        enabled = enabled,
                        onClick = { viewModel.setAccent(accent) },
                    )
                }
            }
            if (state.isLoading || state.isSaving) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { ErrorMessage(it, viewModel::reloadSettings, "Перечитать настройки") }
            OutlinedButton(onClick = viewModel::reset, enabled = enabled) { Text("Сбросить настройки") }
            HorizontalDivider()
            Text("Баланс GigaChat", style = MaterialTheme.typography.titleMedium)
            when (val balance = state.balance) {
                BalanceState.Idle -> Text("Баланс ещё не загружен")
                BalanceState.Loading -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Загрузка баланса…")
                }
                is BalanceState.Success -> {
                    if (balance.items.isEmpty()) Text("API не вернул данных о пакетах токенов")
                    balance.items.forEach {
                        Text("${it.model}: ${NumberFormat.getIntegerInstance().format(it.tokens)} токенов")
                    }
                }
                is BalanceState.Error -> Text(balance.message, color = MaterialTheme.colorScheme.error)
            }
            TextButton(
                onClick = viewModel::refreshBalance,
                enabled = state.balance !is BalanceState.Loading,
            ) { Text("Обновить баланс") }
        }
    }
}

@Composable
private fun SettingOption(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(label)
    }
}