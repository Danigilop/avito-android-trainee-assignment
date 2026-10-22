package com.obpiub.myapplication.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun SearchAndSort(
    text: String,
    order: SortOrder,
    onTextChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSort: (SortOrder) -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    Column {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Поиск по названию") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(); keyboard?.hide() }),
            trailingIcon = {
                TextButton(onClick = { onSearch(); keyboard?.hide() }) { Text("Найти") }
            },
        )
        Box {
            TextButton(onClick = { menuOpen = true }) { Text("Сортировка: ${order.label}") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                SortOrder.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = { onSort(option); menuOpen = false },
                    )
                }
            }
        }
    }
}

@Composable
fun ErrorMessage(message: String, onRetry: () -> Unit, actionText: String = "Повторить") {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(message, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry) { Text(actionText) }
    }
}
