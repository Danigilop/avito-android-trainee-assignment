package com.obpiub.myapplication.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    state: NoteEditorUiState,
    viewModel: NoteEditorViewModel,
    voiceViewModel: VoiceViewModel? = null,
    voiceState: VoiceUiState = VoiceUiState(),
    onBack: () -> Unit,
) {
    val startVoice = rememberVoiceStart(voiceViewModel,
        state.canEdit && !state.isSaving && !state.isImportingImage && !voiceState.busy)
    LaunchedEffect(state.saved) { if (state.saved) onBack() }
    BackHandler {
        if (voiceState.busy) voiceViewModel?.cancelRecording()
        else viewModel.discard(onBack)
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Заметка") },
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    TextButton(onClick = { viewModel.discard(onBack) }, enabled = !state.isSaving && !state.isImportingImage && !voiceState.busy) { Text("Назад") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding()
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.isLoading || state.isSaving) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.takeIf { !voiceState.busy }?.let { error ->
                ErrorMessage(error, if (state.canEdit) viewModel::save else viewModel::load)
            }
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::changeTitle,
                label = { Text("Заголовок") },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canEdit && !state.isSaving && !state.isImportingImage,
                singleLine = true,
                isError = state.titleError != null,
                supportingText = { state.titleError?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = state.text,
                onValueChange = viewModel::changeText,
                label = { Text("Текст заметки") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 6,
                enabled = state.canEdit && !state.isSaving && !state.isImportingImage,
            )
            NoteImagePicker(state.copy(canEdit = state.canEdit && !voiceState.busy), viewModel)
            if (voiceViewModel != null) {
                OutlinedButton(onClick = startVoice,
                    enabled = state.canEdit && !state.isSaving && !state.isImportingImage && !voiceState.busy) {
                    Text("Голосовой ввод")
                }
                VoiceControls(voiceState, voiceViewModel)
            }
            Button(onClick = viewModel::save, enabled = state.canEdit && !state.isSaving && !state.isImportingImage && !voiceState.busy) {
                Text("Сохранить")
            }
        }
    }
}
