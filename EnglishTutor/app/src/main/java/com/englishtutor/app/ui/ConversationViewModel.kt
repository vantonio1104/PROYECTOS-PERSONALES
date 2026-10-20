package com.englishtutor.app.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.englishtutor.app.BuildConfig
import com.englishtutor.app.EnglishTutorApp
import com.englishtutor.app.audio.AudioRecorder
import com.englishtutor.app.audio.Speaker
import com.englishtutor.app.data.ChatMessage
import com.englishtutor.app.data.Correction
import com.englishtutor.app.data.Role
import com.englishtutor.app.data.Scenario
import com.englishtutor.app.data.TutorException
import com.englishtutor.app.data.TutorRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TutorStatus { Idle, Recording, Transcribing, Thinking, Speaking, Error }

data class ConversationUiState(
    val messages: List<ChatMessage> = emptyList(),
    val status: TutorStatus = TutorStatus.Idle,
    val error: String? = null,
    val scenario: Scenario = Scenario.FREE,
    val review: List<Correction> = emptyList(), // errores frecuentes para repasar al empezar
)

class ConversationViewModel(
    private val repository: TutorRepository,
    private val recorder: AudioRecorder,
    private val speaker: Speaker,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ConversationUiState(
            error = if (BuildConfig.GROQ_API_KEY.isBlank())
                "Falta GROQ_API_KEY en local.properties" else null
        )
    )
    val state: StateFlow<ConversationUiState> = _state.asStateFlow()

    private var turnJob: Job? = null
    private var nextId = 0L

    init {
        loadReview()
    }

    /** Solo se puede elegir antes de empezar a hablar (el escenario es de toda la sesión). */
    fun selectScenario(scenario: Scenario) {
        if (_state.value.messages.isEmpty()) _state.update { it.copy(scenario = scenario) }
    }

    /** Termina la conversación actual: vuelve a la elección de escenario y al repaso. */
    fun newConversation() {
        turnJob?.cancel()
        speaker.stop()
        recorder.stop()
        repository.startNewSession()
        _state.update { it.copy(messages = emptyList(), status = TutorStatus.Idle, error = null) }
        loadReview()
    }

    private fun loadReview() {
        viewModelScope.launch {
            val review = repository.frequentErrors()
            _state.update { it.copy(review = review) }
        }
    }

    /** El usuario presionó el botón del micrófono. */
    fun onPressMic() {
        val status = _state.value.status
        if (status == TutorStatus.Transcribing || status == TutorStatus.Thinking) return
        // Si el tutor está hablando, lo interrumpimos para que puedas responder.
        turnJob?.cancel()
        speaker.stop()
        try {
            recorder.start()
            setStatus(TutorStatus.Recording)
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo iniciar la grabación", e)
            showError("No se pudo usar el micrófono.")
        }
    }

    /** El usuario soltó el botón: enviamos el turno completo. */
    fun onReleaseMic() {
        if (_state.value.status != TutorStatus.Recording) return
        val audio = recorder.stop()
        if (audio == null) {
            showError("Mantén presionado el botón mientras hablas.")
            return
        }
        turnJob = viewModelScope.launch { runTurn(audio) }
    }

    private suspend fun runTurn(audio: java.io.File) {
        try {
            // 1) Voz → texto
            setStatus(TutorStatus.Transcribing)
            val userText = repository.transcribe(audio)
            if (userText.isBlank()) {
                showError("No se escuchó nada. Intenta de nuevo.")
                return
            }
            addMessage(ChatMessage(nextId++, Role.USER, userText))

            // 2) Texto → tutor (LLM)
            setStatus(TutorStatus.Thinking)
            val reply = repository.chat(_state.value.messages, _state.value.scenario)

            // Las correcciones van en el mensaje del usuario (se ven como tarjetas)
            _state.update { s ->
                val msgs = s.messages.toMutableList()
                val last = msgs.lastIndex
                msgs[last] = msgs[last].copy(corrections = reply.corrections)
                s.copy(messages = msgs + ChatMessage(nextId++, Role.ASSISTANT, reply.reply))
            }

            // Guardar antes de hablar: si interrumpes al tutor, el turno ya quedó guardado.
            repository.saveTurn(userText, reply, _state.value.scenario)

            // 3) Texto → voz
            setStatus(TutorStatus.Speaking)
            speaker.speak(reply.reply)
            setStatus(TutorStatus.Idle)
        } catch (e: TutorException) {
            showError(e.message ?: "Error desconocido")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error inesperado en el turno", e)
            showError("Algo salió mal: ${e.message}")
        }
    }

    fun dismissError() = setStatus(TutorStatus.Idle)

    private fun addMessage(msg: ChatMessage) =
        _state.update { it.copy(messages = it.messages + msg) }

    private fun setStatus(status: TutorStatus) =
        _state.update { it.copy(status = status, error = if (status == TutorStatus.Error) it.error else null) }

    private fun showError(message: String) =
        _state.update { it.copy(status = TutorStatus.Error, error = message) }

    override fun onCleared() {
        speaker.stop()
        recorder.stop()
    }

    companion object {
        private const val TAG = "Tutor"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EnglishTutorApp
                ConversationViewModel(app.repository, app.recorder, app.speaker)
            }
        }
    }
}
