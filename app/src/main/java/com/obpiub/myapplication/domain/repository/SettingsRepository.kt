package com.obpiub.myapplication.domain.repository

import com.obpiub.myapplication.domain.model.AccentColor
import com.obpiub.myapplication.domain.model.AppSettings
import com.obpiub.myapplication.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeSettings(): Flow<AppSettings>
    suspend fun setTheme(theme: ThemeMode)
    suspend fun setAccent(accent: AccentColor)
    suspend fun reset()
}