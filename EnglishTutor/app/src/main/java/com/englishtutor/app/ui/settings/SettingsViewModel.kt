package com.englishtutor.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.englishtutor.app.EnglishTutorApp
import com.englishtutor.app.data.SettingsRepository
import com.englishtutor.app.data.TutorSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    val settings: StateFlow<TutorSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TutorSettings())

    fun update(transform: (TutorSettings) -> TutorSettings) {
        viewModelScope.launch { repository.update(transform) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { SettingsViewModel((this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EnglishTutorApp).settingsRepository) }
        }
    }
}
