package com.obpiub.myapplication.presentation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun rememberVoiceStart(viewModel: VoiceViewModel?, enabled: Boolean): () -> Unit {
    val context = LocalContext.current
    val currentEnabled by rememberUpdatedState(enabled)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            if (currentEnabled && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) viewModel?.start()
        } else viewModel?.permissionDenied()
    }
    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel?.cancelRecording()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            viewModel?.cancelRecording()
        }
    }
    return {
        if (currentEnabled) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                viewModel?.start()
            } else permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

@Composable
fun VoiceControls(state: VoiceUiState, viewModel: VoiceViewModel?) {
    if (state.busy) {
        Text(when (state.phase) {
            VoicePhase.Recording -> "Говорите — пауза завершит ввод"
            VoicePhase.Recognizing -> "Распознаём речь…"
            VoicePhase.Formatting -> "GigaChat формулирует задачу…"
            VoicePhase.Saving -> "Сохраняем результат…"
            VoicePhase.Idle -> ""
        })
        if (state.phase == VoicePhase.Recording) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel?.finish() }) { Text("Завершить запись") }
                TextButton(onClick = { viewModel?.cancelRecording() }) { Text("Отмена") }
            }
        } else {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.phase == VoicePhase.Recognizing) {
                TextButton(onClick = { viewModel?.cancelRecording() }) { Text("Отмена") }
            }
        }
    }
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}
