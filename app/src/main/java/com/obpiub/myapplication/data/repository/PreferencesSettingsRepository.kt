package com.obpiub.myapplication.data.repository

import android.content.SharedPreferences
import com.obpiub.myapplication.domain.model.AccentColor
import com.obpiub.myapplication.domain.model.AppSettings
import com.obpiub.myapplication.domain.model.ThemeMode
import com.obpiub.myapplication.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

class PreferencesSettingsRepository(private val preferences: SharedPreferences) : SettingsRepository {
    private val writeMutex = Mutex()

    override fun observeSettings() = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(read()) }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(read())
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    private fun read() = AppSettings(
        theme = ThemeMode.entries.find { it.name == preferences.getString("theme", null) } ?: ThemeMode.System,
        accent = AccentColor.entries.find { it.name == preferences.getString("accent", null) } ?: AccentColor.Purple,
    )

    override suspend fun setTheme(theme: ThemeMode) = write { putString("theme", theme.name) }
    override suspend fun setAccent(accent: AccentColor) = write { putString("accent", accent.name) }
    override suspend fun reset() = write { remove("theme"); remove("accent") }

    private suspend fun write(change: SharedPreferences.Editor.() -> Unit) {
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                if (!preferences.edit().apply(change).commit()) {
                    throw IOException("Не удалось записать настройки")
                }
            }
        }
    }
}