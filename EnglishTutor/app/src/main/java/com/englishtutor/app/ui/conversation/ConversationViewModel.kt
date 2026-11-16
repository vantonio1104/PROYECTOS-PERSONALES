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
import com.englishtutor.app.data.Correction
import com.englishtutor.app.data.Role
import com.englishtutor.app.data.Scenario
import com.englishtutor.app.data.TutorException
import com.englishtutor.app.data.TutorPrompt
import com.englishtutor.app.data.TutorRepository
import com.englishtutor.app.data.TutorSettings
import com.englishtutor.app.data.VoiceEngine
import com.englishtutor.app.data.local.FrequentError
import com.englishtutor.app.voice.CorrectionSpeech
import com.englishtutor.app.voice.SilenceDetector
import com.englishtutor.app.voice.VoiceCommand
import com.englishtutor.app.voice.VoiceCommands
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** Listening = manos libres esperando que hables; Recording = te está grabando. */
enum class TutorStatus { Idle, Listening, Recording, Transcribing, Thinking, Speaking, Error }

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
    /** No hay key ni en Ajustes ni en local.properties. */
    val apiKeyMissing: Boolean = false,
    /** Modo manos libres: la app escucha sola, sin mantener presionado el botón. */
    val handsFree: Boolean = false,
    /** Volumen del micrófono (0..1) para la animación del botón en manos libres. */
    val micLevel: Float = 0f,
) {
    val busy get() = status == TutorStatus.Transcribing || status == TutorStatus.Thinking
}

class ConversationViewModel(
    private val repository: TutorRepository,
    settingsFlow: Flow<TutorSettings>,
    private val recorder: VoiceRecorder,
    private val androidVoice: TutorVoice,
    private val orpheusVoice: TutorVoice,
    private val spanishVoice: TutorVoice,
    private val updateSettings: suspend ((TutorSettings) -> TutorSettings) -> Unit = {},
    private val buildApiKey: String = BuildConfig.GROQ_API_KEY,
    todayStart: Long = startOfToday(),
) : ViewModel() {

    private val _state = MutableStateFlow(ConversationUiState())
    val state: StateFlow<ConversationUiState> = _state.asStateFlow()

    private var settings = TutorSettings()
    private var settingsLoaded = false
    private var turnJob: Job? = null      // un turno con el botón (o el tutor abriendo)
    private var handsFreeJob: Job? = null // el bucle de escucha del modo manos libres
    private var nextId = 0L
    private var sessionId: Long? = null // se crea con el primer mensaje (sin sesiones vacías)

    init {
        viewModelScope.launch {
            settingsFlow.collect { s ->
                settings = s
                _state.update {
                    it.copy(
                        goalMinutes = s.dailyGoalMinutes,
                        apiKeyMissing = s.effectiveApiKey(buildApiKey).isBlank(),
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
        if (s.sessionStarted || s.busy || s.status == TutorStatus.Recording || s.status == TutorStatus.Listening) return
        turnJob = viewModelScope.launch {
            guarded { respond(ensureSession(), userMessageId = null) }
            resumeHandsFreeIfNeeded()
        }
    }

    /** El usuario presionó el botón del micrófono (modo normal, push-to-talk). */
    fun onPressMic() {
        val s = _state.value
        if (s.handsFree || s.busy || s.status == TutorStatus.Recording) return
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
        if (_state.value.handsFree || _state.value.status != TutorStatus.Recording) return
        val audio = recorder.stop()
        if (audio == null) {
            showError("Mantén presionado el botón mientras hablas.")
            return
        }
        turnJob = viewModelScope.launch {
            guarded { runUserTurn(audio, handsFree = false) }
            resumeHandsFreeIfNeeded()
        }
    }

    /** Activa o desactiva el modo manos libres. */
    fun setHandsFree(on: Boolean) {
        if (on == _state.value.handsFree) return
        _state.update { it.copy(handsFree = on, micLevel = 0f) }
        if (on) {
            // Si el tutor está pensando o hablando, empieza a escuchar cuando termine
            val st = _state.value.status
            if (st == TutorStatus.Idle || st == TutorStatus.Error) startListening()
        } else {
            stopListening()
        }
    }

    /** Termina la sesión actual y deja todo listo para una nueva. */
    fun newSession() {
        val handsFree = _state.value.handsFree
        handsFreeJob?.cancel()
        handsFreeJob = null
        if (_state.value.status == TutorStatus.Recording || _state.value.status == TutorStatus.Listening) recorder.stop()
        stopSpeaking()
        resetSession()
        if (handsFree) startListening()
    }

    fun dismissError() = setStatus(TutorStatus.Idle)
    fun dismissNotice() = _state.update { it.copy(notice = null) }

    // ---- Modo manos libres ----

    private fun startListening() {
        if (handsFreeJob?.isActive == true) return
        handsFreeJob = viewModelScope.launch {
            while (_state.value.handsFree) {
                val audio = try {
                    listenOnce() ?: continue
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "No se pudo escuchar", e)
                    _state.update { it.copy(handsFree = false, micLevel = 0f) }
                    showError("No se pudo usar el micrófono.")
                    break
                }
                if (!guarded { runUserTurn(audio, handsFree = true) }) {
                    // Si algo falla (sin internet, límite de Groq...) pausamos para no repetir el error en bucle
                    _state.update { it.copy(handsFree = false, micLevel = 0f) }
                    break
                }
            }
        }
    }

    private fun stopListening() {
        val job = handsFreeJob ?: return
        handsFreeJob = null
        job.cancel()
        stopSpeaking()
        if (_state.value.status != TutorStatus.Error) setStatus(TutorStatus.Idle)
    }

    private fun resumeHandsFreeIfNeeded() {
        if (_state.value.handsFree && _state.value.status == TutorStatus.Idle) startListening()
    }

    /** Graba hasta que hables y luego te quedes en silencio. Devuelve null si nadie habló (se reintenta). */
    private suspend fun listenOnce(): File? {
        val detector = SilenceDetector(settings.micSensitivity.threshold, settings.silenceMs.toLong())
        recorder.start()
        setStatus(TutorStatus.Listening)
        var finished = false
        var t = 0L
        try {
            while (true) {
                delay(SAMPLE_MS)
                t += SAMPLE_MS
                val amplitude = recorder.amplitude()
                _state.update { it.copy(micLevel = (amplitude / 32767f).coerceIn(0f, 1f)) }
                when (detector.onSample(amplitude, t)) {
                    SilenceDetector.Event.SPEECH_STARTED -> setStatus(TutorStatus.Recording)
                    SilenceDetector.Event.NOISE_IGNORED -> setStatus(TutorStatus.Listening)
                    SilenceDetector.Event.DONE -> {
                        finished = true
                        _state.update { it.copy(micLevel = 0f) }
                        return recorder.stop()
                    }
                    SilenceDetector.Event.NO_SPEECH -> {
                        finished = true
                        recorder.stop() // descartamos el archivo para que no crezca
                        return null
                    }
                    else -> Unit
                }
            }
        } finally {
            if (!finished) recorder.stop() // cancelado: soltamos el micrófono
        }
    }

    // ---- Flujo de un turno ----

    private suspend fun runUserTurn(audio: File, handsFree: Boolean) {
        // 1) Voz → texto
        setStatus(TutorStatus.Transcribing)
        val userText = repository.transcribe(audio)
        if (userText.isBlank()) {
            if (handsFree) setStatus(TutorStatus.Idle) // en manos libres simplemente seguimos escuchando
            else showError("No se escuchó nada. Intenta de nuevo.")
            return
        }
        // ¿Es un comando de voz ("repeat", "más lento"...)? Entonces no va al tutor.
        VoiceCommands.parse(userText)?.let { command ->
            handleCommand(command)
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
        // Primero las correcciones (en español), al final la respuesta: así lo último que oyes es su pregunta
        if (userMessageId != null && settings.speakCorrections) speakCorrections(reply.corrections)
        speak(reply.reply)
        setStatus(TutorStatus.Idle)
    }

    // ---- Comandos de voz ----

    private suspend fun handleCommand(command: VoiceCommand) {
        _state.update { it.copy(notice = "🎙️ Comando: ${command.label}") }
        when (command) {
            VoiceCommand.REPEAT -> repeatLast()
            VoiceCommand.SLOWER -> changeRate(-RATE_STEP)
            VoiceCommand.FASTER -> changeRate(RATE_STEP)
            VoiceCommand.NEW_SESSION -> resetSession()
            VoiceCommand.PAUSE -> _state.update {
                it.copy(handsFree = false, micLevel = 0f, notice = "⏸️ Manos libres en pausa. Actívalo de nuevo cuando quieras.")
            }
            VoiceCommand.EXPLAIN -> explainLast()
        }
        setStatus(TutorStatus.Idle)
    }

    private suspend fun repeatLast() {
        val last = _state.value.messages.lastOrNull { it.role == Role.ASSISTANT } ?: return
        setStatus(TutorStatus.Speaking)
        speak(last.text)
    }

    private suspend fun changeRate(delta: Float) {
        val rate = ((settings.ttsRate + delta) * 10).roundToInt().coerceIn(5, 15) / 10f
        settings = settings.copy(ttsRate = rate)
        updateSettings { it.copy(ttsRate = rate) }
        _state.update { it.copy(notice = "${if (delta < 0) "🐢" else "🐇"} Velocidad de la voz: ${rate}x") }
        repeatLast()
    }

    /** Lee en español las correcciones de tu último mensaje. */
    private suspend fun explainLast() {
        val corrections = _state.value.messages.lastOrNull { it.role == Role.USER }?.corrections.orEmpty()
        setStatus(TutorStatus.Speaking)
        if (corrections.isEmpty()) spanishVoice.speak(CorrectionSpeech.NO_CORRECTIONS, settings)
        else speakCorrections(corrections)
    }

    // ---- Voz ----

    private suspend fun speakCorrections(corrections: List<Correction>) {
        CorrectionSpeech.segments(corrections).forEach { segment ->
            if (segment.spanish) spanishVoice.speak(segment.text, settings) else speak(segment.text)
        }
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

    private fun stopSpeaking() {
        turnJob?.cancel()
        androidVoice.stop()
        orpheusVoice.stop()
        spanishVoice.stop()
    }

    // ---- Sesión ----

    private suspend fun ensureSession(): Long =
        sessionId ?: repository.startSession(_state.value.scenario).also { id ->
            sessionId = id
            _state.update { it.copy(sessionStarted = true) }
        }

    private fun resetSession() {
        sessionId = null
        _state.update {
            it.copy(
                messages = emptyList(), status = TutorStatus.Idle, error = null, notice = null,
                sessionStarted = false, scenario = settings.defaultScenario,
            )
        }
        loadReview()
    }

    /** Ejecuta un turno mostrando errores claros sin que la app se caiga. Devuelve false si hubo error. */
    private suspend fun guarded(block: suspend () -> Unit): Boolean {
        try {
            block()
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: TutorException) {
            showError(e.message ?: "Error desconocido")
        } catch (e: Exception) {
            Log.e(TAG, "Error inesperado en el turno", e)
            showError("Algo salió mal: ${e.message}")
        }
        return false
    }

    /** Al iniciar una sesión: errores frecuentes y palabras difíciles a practicar. */
    private fun loadReview() {
        viewModelScope.launch {
            val review = runCatching { repository.frequentErrors(REVIEW_SIZE) }.getOrDefault(emptyList())
            val myWords = runCatching { repository.myWordsToPractice(MY_WORDS_SIZE) }.getOrDefault(emptyList()).map { it.term }
            _state.update { if (it.sessionStarted) it else it.copy(review = review, myWords = myWords) }
        }
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
        spanishVoice.stop()
        recorder.stop()
    }

    companion object {
        private const val TAG = "Tutor"
        const val REVIEW_SIZE = 3
        const val MY_WORDS_SIZE = 5
        const val SAMPLE_MS = 100L
        private const val RATE_STEP = 0.2f

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
                    spanishVoice = app.spanishVoice,
                    updateSettings = app.settingsRepository::update,
                )
            }
        }
    }
}
