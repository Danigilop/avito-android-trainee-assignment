package com.obpiub.myapplication.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(state: NotesUiState, viewModel: NotesViewModel, onOpenNote: (Long) -> Unit) {
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Заметки") },
                windowInsets = WindowInsets(0, 0, 0, 0),
                actions = {
                    TextButton(onClick = viewModel::toggleDeleteMode, enabled = !state.isDeleting) {
                        Text(if (state.deleteMode) "Готово" else "Удаление")
                    }
                },
            )
        },
        floatingActionButton = {
            if (!state.deleteMode) {
                FloatingActionButton(onClick = { onOpenNote(0) }) { Text("Создать") }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchAndSort(state.searchText, state.sortOrder,
                viewModel::changeSearch, viewModel::search, viewModel::sort)
            if (state.deleteMode) Text("Режим удаления: открытие заметок отключено")
            state.error?.let { ErrorMessage(it, viewModel::reload, "Обновить список") }
            if (state.isLoading || state.isDeleting) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!state.isLoading && state.error == null && state.notes.isEmpty()) {
                    item {
                        Text(if (state.query.isEmpty()) "Заметок пока нет" else "Ничего не найдено")
                    }
                }
                items(state.notes, key = { it.id }) { note ->
                    Card(
                        onClick = { onOpenNote(note.id) },
                        enabled = !state.deleteMode,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(
                                modifier = Modifier.size(56.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small,
                            ) {
                                NoteImage(note.imagePath, Modifier.fillMaxSize())
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(note.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium)
                                Text(dateFormat.format(Date(note.createdAt)),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            if (state.deleteMode) {
                                TextButton(
                                    onClick = { viewModel.delete(note.id) },
                                    enabled = !state.isDeleting,
                                ) { Text("Удалить") }
                            }
                        }
                    }
                }
            }
        }
    }
}
