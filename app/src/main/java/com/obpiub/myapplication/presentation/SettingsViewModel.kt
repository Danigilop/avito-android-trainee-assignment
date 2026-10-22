package com.obpiub.myapplication.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obpiub.myapplication.domain.model.*
import com.obpiub.myapplication.domain.repository.BalanceException
import com.obpiub.myapplication.domain.repository.BalanceRepository
import com.obpiub.myapplication.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface BalanceState {
    data object Idle : BalanceState
    data object Loading : BalanceState
    data class Success(val items: List<TokenBalance>) : BalanceState
    data class Error(val message: String) : BalanceState
}

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isLoading: Boolean = true,
    val hasLoadedSettings: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val balance: BalanceState = BalanceState.Idle,
)

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val balanceRepository: BalanceRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state = _state.asStateFlow()
    private var observation: Job? = null

    init { reloadSettings() }

    fun reloadSettings() {
        observation?.cancel()
        observation = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                repository.observeSettings().collect { settings ->
                    _state.update { it.copy(settings = settings, isLoading = false, hasLoadedSettings = true) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.update { it.copy(isLoading = false, hasLoadedSettings = true, error = "Не удалось прочитать настройки") }
            }
        }
    }

    fun setTheme(theme: ThemeMode) = save { repository.setTheme(theme) }
    fun setAccent(accent: AccentColor) = save { repository.setAccent(accent) }
    fun reset() = save { repository.reset() }

    private fun save(action: suspend () -> Unit) {
        if (state.value.isSaving || state.value.isLoading) return
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.update { it.copy(error = "Не удалось сохранить настройки. Повторите выбор") }
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    fun refreshBalance() {
        if (state.value.balance is BalanceState.Loading) return
        _state.update { it.copy(balance = BalanceState.Loading) }
        viewModelScope.launch {
            try {

                val balance = balanceRepository.getBalance().sortedWith(
                    compareBy<TokenBalance> { it.model.lowercase(java.util.Locale.ROOT) }
                        .thenBy { it.model },
                )
                _state.update { it.copy(balance = BalanceState.Success(balance)) }
            } catch (error: CancellationException) {
                _state.update { it.copy(balance = BalanceState.Idle) }
                throw error
            } catch (error: Exception) {
                val message = if (error is BalanceException) error.message.orEmpty()
                    else "Не удалось получить баланс. Попробуйте ещё раз."
                _state.update { it.copy(balance = BalanceState.Error(message)) }
            }
        }
    }
}
