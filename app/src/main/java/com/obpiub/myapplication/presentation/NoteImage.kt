package com.obpiub.myapplication.presentation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun NoteImage(path: String?, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(null, path) {
        value = null
        value = withContext(Dispatchers.IO) {
            if (path == null) return@withContext null
            try {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, options)
                options.inSampleSize = 1
                while (options.outWidth / options.inSampleSize > 1200 ||
                    options.outHeight / options.inSampleSize > 1200) options.inSampleSize *= 2
                options.inJustDecodeBounds = false
                val source = BitmapFactory.decodeFile(path, options) ?: return@withContext null
                val orientation = runCatching {
                    ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
                }.getOrDefault(1)
                val matrix = Matrix().apply {
                    when (orientation) {
                        2 -> setScale(-1f, 1f)
                        3 -> setRotate(180f)
                        4 -> setScale(1f, -1f)
                        5 -> { setRotate(90f); postScale(-1f, 1f) }
                        6 -> setRotate(90f)
                        7 -> { setRotate(270f); postScale(-1f, 1f) }
                        8 -> setRotate(270f)
                    }
                }
                Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
                    .also { if (it !== source) source.recycle() }
            } catch (_: Exception) { null }
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        bitmap?.let {
            Image(it.asImageBitmap(), "Изображение заметки", Modifier.matchParentSize(), contentScale = ContentScale.Fit)
        } ?: Text(if (path == null) "Нет фото" else "Фото недоступно")
    }
}
