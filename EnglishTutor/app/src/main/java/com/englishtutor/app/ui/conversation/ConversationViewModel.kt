package com.englishtutor.app.ui.conversation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.englishtutor.app.BuildConfig
import com.englishtutor.app.EnglishTutorApp
import com.englishtutor.app.audio.TutorVoice
import com.englishtutor.app.audio.VoiceRecorder
import com.englishtutor.app.data.ChatMessage
import com.englishtutor.app.data.Role
import com.englishtutor.app.data.Scenario
import com.englishtutor.app.data.TutorException
import com.englishtutor.app.data.TutorPrompt
import com.englishtutor.app.data.TutorRepository
import com.englishtutor.app.data.TutorSettings
import com.englishtutor.app.data.VoiceEngine
import com.englishtutor.app.data.local.FrequentError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

enum class TutorStatus { Idle, Recording, Transcribing, Thinking, Speaking, Error }

data class ConversationUiState(
    val messages: List<ChatMessage> = emptyList(),
    val status: TutorStatus = TutorStatus.Idle,
    val error: String? = null,
    /** Aviso que no detiene la conversación (por ejemplo, "Orpheus falló, uso la voz de Android"). */
    val notice: String? = null,
    val scenario: Scenario = Scenario.FREE_TALK,
    val sessionStarted: Boolean = false,
    /** Errores frecuentes que se repasan en esta sesión (Fase 3). */
    val review: List<FrequentError> = emptyList(),
    /** Tus palabras difíciles ("Mis palabras") que el tutor usará en esta sesión. */
    val myWords: List<String> = emptyList(),
    val todayMinutes: Int = 0,
    val goalMinutes: Int = 60,
) {
    val busy get() = status == TutorStatus.Transcribing || status == TutorStatus.Thinking
}

class ConversationViewModel(
    private val repository: TutorRepository,
    settingsFlow: Flow<TutorSettings>,
    private val recorder: VoiceRecorder,
    private val androidVoice: TutorVoice,
    private val orpheusVoice: TutorVoice,
    apiKeyMissing: Boolean = BuildConfig.GROQ_API_KEY.isBlank(),
    todayStart: Long = startOfToday(),
) : ViewModel() {

    private val _state = MutableStateFlow(
        ConversationUiState(error = if (apiKeyMissing) "Falta GROQ_API_KEY en local.properties" else null)
    )
    val state: StateFlow<ConversationUiState> = _state.asStateFlow()

    private var settings = TutorSettings()
    private var settingsLoaded = false
    private var turnJob: Job? = null
    private var nextId = 0L
    private var sessionId: Long? = null // se crea con el primer mensaje (sin sesiones vacías)

    init {
        viewModelScope.launch {
            settingsFlow.collect { s ->
                settings = s
                _state.update {
                    it.copy(
                        goalMinutes = s.dailyGoalMinutes,
                        // El escenario por defecto solo se aplica al abrir la app (no pisa tu elección)
                        scenario = if (!settingsLoaded && !it.sessionStarted) s.defaultScenario else it.scenario,
                    )
                }
                settingsLoaded = true
            }
        }
        viewModelScope.launch {
            repository.observePracticeMillisSince(todayStart).collect { ms ->
                _state.update { it.copy(todayMinutes = (ms / 60_000).toInt()) }
            }
        }
        loadReview()
    }

    // ---- Acciones de la UI ----

    fun selectScenario(scenario: Scenario) {
        if (!_state.value.sessionStarted) _state.update { it.copy(scenario = scenario) }
    }

    /** El tutor abre la conversación según el escenario elegido. */
    fun startConversation() {
        val s = _state.value
        if (s.sessionStarted || s.busy || s.status == TutorStatus.Recording) return
        turnJob = viewModelScope.launch {
            guarded {
                val session = ensureSession()
                respond(session, userMessageId = null)
            }
        }
    }

    /** El usuario presionó el botón del micrófono. */
    fun onPressMic() {
        val s = _state.value
        if (s.busy || s.status == TutorStatus.Recording) return
        // Si el tutor está hablando, lo interrumpimos para que puedas responder.
        stopSpeaking()
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
        turnJob = viewModelScope.launch { guarded { runUserTurn(audio) } }
    }

    /** Termina la sesión actual y deja todo listo para una nueva. */
    fun newSession() {
        stopSpeaking()
        if (_state.value.status == TutorStatus.Recording) recorder.stop()
        sessionId = null
        _state.update {
            it.copy(
                messages = emptyList(), status = TutorStatus.Idle, error = null, notice = null,
                sessionStarted = false, scenario = settings.defaultScenario,
            )
        }
        loadReview()
    }

    fun dismissError() = setStatus(TutorStatus.Idle)
    fun dismissNotice() = _state.update { it.copy(notice = null) }

    // ---- Flujo de un turno ----

    private suspend fun runUserTurn(audio: File) {
        // 1) Voz → texto
        setStatus(TutorStatus.Transcribing)
        val userText = repository.transcribe(audio)
        if (userText.isBlank()) {
            showError("No se escuchó nada. Intenta de nuevo.")
            return
        }
        addMessage(ChatMessage(nextId++, Role.USER, userText))
        val session = ensureSession()
        val userMessageId = repository.saveUserMessage(session, userText)
        respond(session, userMessageId)
    }

    /** 2) Texto → tutor (LLM) y 3) texto → voz. userMessageId = null cuando el tutor abre. */
    private suspend fun respond(session: Long, userMessageId: Long?) {
        setStatus(TutorStatus.Thinking)
        val s = _state.value
        val reply = repository.chat(
            history = s.messages,
            systemPrompt = TutorPrompt.build(settings.level, s.scenario, s.review, s.myWords),
            model = settings.llmModel,
        )
        _state.update { st ->
            val msgs = st.messages.toMutableList()
            // Las correcciones se muestran como tarjetas bajo tu último mensaje
            if (userMessageId != null && msgs.isNotEmpty()) {
                msgs[msgs.lastIndex] = msgs.last().copy(corrections = reply.corrections)
            }
            st.copy(messages = msgs + ChatMessage(nextId++, Role.ASSISTANT, reply.reply))
        }
        repository.saveTutorTurn(session, userMessageId, reply)
        val saved = reply.saved_words.filter { it.term.isNotBlank() }
        if (saved.isNotEmpty()) {
            val words = saved.joinToString(", ") { if (it.meaning.isBlank()) it.term else "${it.term} (${it.meaning})" }
            _state.update { it.copy(notice = "⭐ Guardado en Mis palabras: $words") }
        }

        setStatus(TutorStatus.Speaking)
        speak(reply.reply)
        setStatus(TutorStatus.Idle)
    }

    /** Usa Orpheus si está activado; si falla, sigue con la voz de Android. */
    private suspend fun speak(text: String) {
        if (settings.voiceEngine == VoiceEngine.ORPHEUS) {
            try {
                orpheusVoice.speak(text, settings)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Orpheus falló, uso la voz de Android", e)
                _state.update { it.copy(notice = "Orpheus no respondió (${e.message}). Usando la voz de Android.") }
            }
        }
        androidVoice.speak(text, settings)
    }

    private suspend fun ensureSession(): Long =
        sessionId ?: repository.startSession(_state.value.scenario).also { id ->
            sessionId = id
            _state.update { it.copy(sessionStarted = true) }
        }

    /** Ejecuta un turno mostrando errores claros sin que la app se caiga. */
    private suspend fun guarded(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: TutorException) {
            showError(e.message ?: "Error desconocido")
        } catch (e: Exception) {
            Log.e(TAG, "Error inesperado en el turno", e)
            showError("Algo salió mal: ${e.message}")
        }
    }

    /** Al iniciar una sesión: errores frecuentes y palabras difíciles a practicar. */
    private fun loadReview() {
        viewModelScope.launch {
            val review = runCatching { repository.frequentErrors(REVIEW_SIZE) }.getOrDefault(emptyList())
            val myWords = runCatching { repository.myWordsToPractice(MY_WORDS_SIZE) }.getOrDefault(emptyList()).map { it.term }
            _state.update { if (it.sessionStarted) it else it.copy(review = review, myWords = myWords) }
        }
    }

    private fun stopSpeaking() {
        turnJob?.cancel()
        androidVoice.stop()
        orpheusVoice.stop()
    }

    private fun addMessage(msg: ChatMessage) =
        _state.update { it.copy(messages = it.messages + msg) }

    private fun setStatus(status: TutorStatus) =
        _state.update { it.copy(status = status, error = if (status == TutorStatus.Error) it.error else null) }

    private fun showError(message: String) =
        _state.update { it.copy(status = TutorStatus.Error, error = message) }

    override fun onCleared() {
        androidVoice.stop()
        orpheusVoice.stop()
        recorder.stop()
    }

    companion object {
        private const val TAG = "Tutor"
        const val REVIEW_SIZE = 3
        const val MY_WORDS_SIZE = 5

        fun startOfToday(): Long =
            LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EnglishTutorApp
                ConversationViewModel(
                    repository = app.repository,
                    settingsFlow = app.settingsRepository.settings,
                    recorder = app.recorder,
                    androidVoice = app.speaker,
                    orpheusVoice = app.orpheusVoice,
                )
            }
        }
    }
}
