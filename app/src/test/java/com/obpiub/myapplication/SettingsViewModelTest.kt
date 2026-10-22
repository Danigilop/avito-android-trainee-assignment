package com.obpiub.myapplication

import androidx.lifecycle.ViewModelStore
import com.obpiub.myapplication.domain.model.*
import com.obpiub.myapplication.domain.repository.*
import com.obpiub.myapplication.presentation.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val store = ViewModelStore()
    private val preferences = FakeSettings()
    private val balance = FakeBalance()

    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }
    private fun model() = SettingsViewModel(preferences, balance).also { store.put("settings", it) }

    @Test
    fun themeAndColorAreIndependentAndResetKeepsBalance() = runTest {
        val model = model()
        runCurrent()
        model.setTheme(ThemeMode.Dark)
        runCurrent()
        model.setAccent(AccentColor.Green)
        runCurrent()
        assertEquals(AppSettings(ThemeMode.Dark, AccentColor.Green), model.state.value.settings)
        model.refreshBalance()
        runCurrent()
        model.reset()
        runCurrent()
        assertEquals(AppSettings(), model.state.value.settings)
        assertTrue(model.state.value.balance is BalanceState.Success)
    }

    @Test
    fun balanceHasLoadingErrorAndRetryWithoutDisablingLocalSettings() = runTest {
        val pending = CompletableDeferred<List<TokenBalance>>()
        balance.response = { pending.await() }
        val model = model()
        runCurrent()
        model.refreshBalance()
        model.refreshBalance()
        runCurrent()
        assertEquals(BalanceState.Loading, model.state.value.balance)
        assertEquals(1, balance.calls)
        model.setTheme(ThemeMode.Light)
        runCurrent()
        assertEquals(ThemeMode.Light, model.state.value.settings.theme)
        pending.completeExceptionally(BalanceException("Нет сети"))
        runCurrent()
        assertEquals(BalanceState.Error("Нет сети"), model.state.value.balance)
        balance.response = { listOf(TokenBalance("GigaChat", 0)) }
        model.refreshBalance()
        runCurrent()
        assertEquals(BalanceState.Success(listOf(TokenBalance("GigaChat", 0))), model.state.value.balance)
    }

    @Test
    fun failedSettingsWriteShowsErrorAndAllowsRetry() = runTest {
        val model = model()
        runCurrent()
        preferences.failWrite = true
        model.setTheme(ThemeMode.Dark)
        runCurrent()
        assertNotNull(model.state.value.error)
        assertFalse(model.state.value.isSaving)
        preferences.failWrite = false
        model.setTheme(ThemeMode.Dark)
        runCurrent()
        assertNull(model.state.value.error)
        assertEquals(ThemeMode.Dark, model.state.value.settings.theme)
    }
}

private class FakeSettings : SettingsRepository {
    val values = MutableStateFlow(AppSettings())
    var failWrite = false
    override fun observeSettings() = values
    override suspend fun setTheme(theme: ThemeMode) {
        check(!failWrite)
        values.value = values.value.copy(theme = theme)
    }
    override suspend fun setAccent(accent: AccentColor) { values.value = values.value.copy(accent = accent) }
    override suspend fun reset() { values.value = AppSettings() }
}

private class FakeBalance : BalanceRepository {
    var calls = 0
    var response: suspend () -> List<TokenBalance> = { listOf(TokenBalance("GigaChat", 123)) }
    override suspend fun getBalance(): List<TokenBalance> { calls++; return response() }
}
