package com.englishtutor.app.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.englishtutor.app.EnglishTutorApp
import com.englishtutor.app.audio.TutorVoice
import com.englishtutor.app.audio.VoiceOption
import com.englishtutor.app.data.SettingsRepository
import com.englishtutor.app.data.TutorSettings
import com.englishtutor.app.data.VoiceEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository,
    /** Voces en inglés instaladas en el celular. */
    val voices: StateFlow<List<VoiceOption>>,
    private val androidVoice: TutorVoice,
    private val orpheusVoice: TutorVoice,
    private val spanishVoice: TutorVoice,
) : ViewModel() {
    val settings: StateFlow<TutorSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TutorSettings())

    private val _previewError = MutableStateFlow<String?>(null)
    val previewError: StateFlow<String?> = _previewError.asStateFlow()
    private var previewJob: Job? = null

    fun update(transform: (TutorSettings) -> TutorSettings) {
        viewModelScope.launch { repository.update(transform) }
    }

    /** Hace hablar a la voz elegida (y a la de español, si las correcciones habladas están activas). */
    fun preview() {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            _previewError.value = null
            val s = settings.value
            try {
                val english = if (s.voiceEngine == VoiceEngine.ORPHEUS) orpheusVoice else androidVoice
                english.speak(PREVIEW_EN, s)
                if (s.speakCorrections) spanishVoice.speak(PREVIEW_ES, s)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Tutor", "La prueba de voz falló", e)
                _previewError.value = "No se pudo probar la voz: ${e.message}"
            }
        }
    }

    override fun onCleared() {
        androidVoice.stop()
        orpheusVoice.stop()
    }

    companion object {
        const val PREVIEW_EN = "Hi! I'm your English tutor. How was your day?"
        const val PREVIEW_ES = "Así sonarán las explicaciones de tus errores."

        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EnglishTutorApp
                SettingsViewModel(app.settingsRepository, app.speaker.voices, app.speaker, app.orpheusVoice, app.spanishVoice)
            }
        }
    }
}
