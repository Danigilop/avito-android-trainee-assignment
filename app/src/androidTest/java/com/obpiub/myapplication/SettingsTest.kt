package com.obpiub.myapplication

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.obpiub.myapplication.data.repository.PreferencesSettingsRepository
import com.obpiub.myapplication.domain.model.*
import com.obpiub.myapplication.domain.repository.BalanceRepository
import com.obpiub.myapplication.presentation.*
import com.obpiub.myapplication.presentation.theme.MyApplicationTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SettingsTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences("settings-instrumentation-test", Context.MODE_PRIVATE)
    private val repository = PreferencesSettingsRepository(preferences)
    private val store = ViewModelStore()

    @After
    fun tearDown() {
        compose.runOnUiThread { store.clear() }
        preferences.edit().clear().commit()
    }

    @Test
    fun choicesPersistAcrossRepositoryRecreationAndResetRestoresDefaults() = runBlocking {
        repository.reset()
        repository.setTheme(ThemeMode.Dark)
        repository.setAccent(AccentColor.Blue)
        val reopened = PreferencesSettingsRepository(
            context.getSharedPreferences("settings-instrumentation-test", Context.MODE_PRIVATE),
        )
        assertEquals(AppSettings(ThemeMode.Dark, AccentColor.Blue), reopened.observeSettings().first())
        reopened.reset()
        assertEquals(AppSettings(), repository.observeSettings().first())
    }

    @Test
    fun changingThemeAndAccentUpdatesMaterialThemeImmediately() {
        runBlocking { repository.reset() }
        lateinit var model: SettingsViewModel
        var primary = Color.Unspecified
        var background = Color.Unspecified
        compose.runOnUiThread {
            model = SettingsViewModel(repository, object : BalanceRepository {
                override suspend fun getBalance() = listOf(TokenBalance("GigaChat", 12))
            })
            store.put("settings", model)
        }
        compose.setContent {
            val state by model.state.collectAsState()
            MyApplicationTheme(state.settings) {
                val colors = MaterialTheme.colorScheme
                SideEffect { primary = colors.primary; background = colors.background }
                SettingsScreen(state, model)
            }
        }
        compose.waitUntil(5_000) { !model.state.value.isLoading }
        compose.onNodeWithText("Тёмная").performScrollTo().performClick()
        compose.waitUntil(5_000) { model.state.value.settings.theme == ThemeMode.Dark && !model.state.value.isSaving }
        compose.runOnIdle { assertTrue(background.luminance() < 0.5f) }
        val purple = primary
        compose.onNodeWithText("Зелёный").performScrollTo().performClick()
        compose.waitUntil(5_000) { model.state.value.settings.accent == AccentColor.Green && !model.state.value.isSaving }
        compose.onNodeWithText("Зелёный").assertIsSelected()
        compose.runOnIdle { assertNotEquals(purple, primary) }
        compose.onNodeWithText("Сбросить настройки").performScrollTo().performClick()
        compose.waitUntil(5_000) { model.state.value.settings == AppSettings() }
        compose.onNodeWithText("GigaChat: 12 токенов").performScrollTo().assertIsDisplayed()
    }
}
