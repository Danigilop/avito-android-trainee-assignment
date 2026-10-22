package com.obpiub.myapplication.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File

@Composable
fun NoteImagePicker(state: NoteEditorUiState, viewModel: NoteEditorViewModel) {
    val context = LocalContext.current
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    val enabled = state.canEdit && !state.isSaving && !state.isImportingImage
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importImage(uri.toString())
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val path = cameraPath
        cameraPath = null
        if (success && path != null) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(path))
            viewModel.importImage(uri.toString()) { File(path).delete() }
        } else if (path != null) {
            File(path).delete()
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = enabled, onClick = {
            try { files.launch(arrayOf("image/*")) }
            catch (_: Exception) { viewModel.imageError("Выбор файлов недоступен") }
        }) { Text("Из файла") }
        OutlinedButton(enabled = enabled, onClick = {
            try {
                val directory = File(context.cacheDir, "camera").apply { mkdirs() }
                val photo = File.createTempFile("photo_", ".jpg", directory)
                cameraPath = photo.absolutePath
                camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", photo))
            } catch (_: Exception) {
                cameraPath?.let { File(it).delete() }
                cameraPath = null
                viewModel.imageError("Не удалось открыть камеру. Проверьте доступ к камере в её настройках или выберите файл")
            }
        }) { Text("Камера") }
    }
    if (state.isImportingImage) LinearProgressIndicator(Modifier.fillMaxWidth())
    state.imageError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (state.imagePath != null) {
        NoteImage(state.imagePath, Modifier.fillMaxWidth().height(220.dp))
        TextButton(enabled = enabled, onClick = viewModel::removeImage) { Text("Удалить изображение") }
    }
}
