package com.obpiub.myapplication.presentation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(state: TasksUiState, viewModel: TasksViewModel,
    voiceViewModel: VoiceViewModel? = null, voiceState: VoiceUiState = VoiceUiState()) {
    val startVoice = rememberVoiceStart(voiceViewModel, !state.isSaving && !state.isCreating && !voiceState.busy)
    var creationMenu by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(state.isCreating) {
        if (state.isCreating) listState.scrollToItem(0)
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Задачи") }, windowInsets = WindowInsets(0, 0, 0, 0)) },
        floatingActionButton = {
            if (!state.isCreating && !voiceState.busy) {
                Box {
                    FloatingActionButton(onClick = { creationMenu = true }) { Text("Создать") }
                    DropdownMenu(expanded = creationMenu, onDismissRequest = { creationMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Текстовый ввод") },
                            onClick = { creationMenu = false; viewModel.startCreating() },
                            enabled = !state.isSaving,
                        )
                        DropdownMenuItem(
                            text = { Text("Голосовой ввод") },
                            enabled = !state.isSaving && voiceViewModel != null,
                            onClick = { creationMenu = false; startVoice() },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchAndSort(state.searchText, state.sortOrder,
                viewModel::changeSearch, viewModel::search, viewModel::sort)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TaskFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { viewModel.filter(filter) },
                        label = { Text(filter.label) },
                    )
                }
            }
            state.error?.let { ErrorMessage(it, viewModel::reload, "Обновить список") }
            VoiceControls(voiceState, voiceViewModel)
            if (state.isLoading || state.isSaving) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.isCreating) {
                    item(key = "draft") { TaskDraft(state, viewModel) }
                }
                if (!state.isLoading && state.error == null && state.tasks.isEmpty()) {
                    item(key = "empty") {
                        Text(if (state.query.isEmpty() && state.filter == TaskFilter.All)
                            "Задач пока нет" else "Нет задач по выбранным условиям")
                    }
                }
                items(state.tasks, key = { it.id }) { task ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = task.isCompleted,
                                onCheckedChange = { viewModel.setCompleted(task.id, it) },
                                enabled = !state.isSaving,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = task.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                                )
                                Text(
                                    if (task.isCompleted) "Выполнена" else "Активна",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskDraft(state: TasksUiState, viewModel: TasksViewModel) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    Card {
        Column(modifier = Modifier.padding(12.dp)) {
            OutlinedTextField(
                value = state.draft,
                onValueChange = viewModel::changeDraft,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                label = { Text("Название задачи") },
                enabled = !state.isSaving,
                singleLine = true,
                isError = state.draftError != null,
                supportingText = { state.draftError?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.saveTask() }),
            )
            Row {
                TextButton(onClick = viewModel::saveTask, enabled = !state.isSaving) { Text("Сохранить задачу") }
                TextButton(onClick = viewModel::cancelCreating, enabled = !state.isSaving) { Text("Отмена") }
            }
        }
    }
}
