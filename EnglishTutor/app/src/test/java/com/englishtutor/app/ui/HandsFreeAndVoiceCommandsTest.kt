package com.englishtutor.app.ui

import com.englishtutor.app.FakeGroqApi
import com.englishtutor.app.FakeRecorder
import com.englishtutor.app.FakeVoice
import com.englishtutor.app.data.Role
import com.englishtutor.app.data.TutorRepository
import com.englishtutor.app.data.TutorSettings
import com.englishtutor.app.data.local.AppDatabase
import com.englishtutor.app.httpError
import com.englishtutor.app.inMemoryDatabase
import com.englishtutor.app.tutorJson
import com.englishtutor.app.ui.conversation.ConversationViewModel
import com.englishtutor.app.ui.conversation.TutorStatus
import com.englishtutor.app.voice.CorrectionSpeech
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Modo manos libres, comandos de voz y correcciones habladas.
 * Con manos libres activo el bucle de escucha no termina nunca, así que se avanza el reloj virtual
 * con advanceTimeBy (en vez de advanceUntilIdle) y se apaga al final de cada prueba.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HandsFreeAndVoiceCommandsTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var api: FakeGroqApi
    private lateinit var repository: TutorRepository
    private val recorder = FakeRecorder()
    private val spokenLog = mutableListOf<String>()
    private val androidVoice = FakeVoice(log = spokenLog, tag = "en:")
    private val spanishVoice = FakeVoice(log = spokenLog, tag = "es:")
    private val settings = MutableStateFlow(TutorSettings(speakCorrections = false))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = inMemoryDatabase()
        api = FakeGroqApi()
        repository = TutorRepository(api, db.tutorDao())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private val createdVms = mutableListOf<ConversationViewModel>()

    /**
     * runTest que siempre apaga manos libres al final: si una prueba falla a la mitad,
     * el bucle de escucha no queda vivo y la prueba falla en vez de quedarse colgada.
     */
    private fun handsFreeTest(body: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try {
            body()
        } finally {
            createdVms.forEach { it.setHandsFree(false) }
            runCurrent()
        }
    }

    private fun createVm() = ConversationViewModel(
        repository = repository,
        settingsFlow = settings,
        recorder = recorder,
        androidVoice = androidVoice,
        orpheusVoice = FakeVoice(),
        spanishVoice = spanishVoice,
        updateSettings = { transform -> settings.value = transform(settings.value) },
        buildApiKey = "gsk_local",
        todayStart = 0L,
    ).also { createdVms += it }

    private fun TestScope.advance(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    /** Un turno normal con el botón (push-to-talk). */
    private fun TestScope.pushToTalk(vm: ConversationViewModel, said: String) {
        api.transcription = said
        vm.onPressMic()
        vm.onReleaseMic()
        advanceUntilIdle()
    }

    private val replyWithCorrection = tutorJson(
        reply = "Sounds fun! Did you swim?",
        corrections = """[{"original":"I go","better":"I went","explanation":"Pasado simple"}]""",
    )

    // ---- Manos libres ----

    @Test
    fun `manos libres escucha, detecta que terminaste, responde y vuelve a escuchar`() = handsFreeTest {
        val vm = createVm()
        advanceUntilIdle()
        recorder.scriptSpeech(speechMs = 1000)

        vm.setHandsFree(true)
        advance(500)
        assertEquals(TutorStatus.Recording, vm.state.value.status) // detectó tu voz
        advance(5_000) // terminas de hablar, 1.5 s de silencio, responde y habla

        assertEquals(listOf(Role.USER, Role.ASSISTANT), vm.state.value.messages.map { it.role })
        assertEquals(1, androidVoice.spoken.size)
        assertEquals(TutorStatus.Listening, vm.state.value.status) // ya está escuchando otra vez
        assertEquals(2, recorder.started)

        vm.setHandsFree(false)
        assertEquals(TutorStatus.Idle, vm.state.value.status)
        runCurrent() // la limpieza (finally) de la escucha cancelada corre en el siguiente ciclo
        assertEquals(recorder.started, recorder.stopped) // soltó el micrófono
    }

    @Test
    fun `si nadie habla no se envia nada y la grabacion se reinicia`() = handsFreeTest {
        val vm = createVm()
        vm.setHandsFree(true)
        advance(65_000)
        assertTrue(api.chatRequests.isEmpty())
        assertTrue(recorder.started >= 3) // se reinicia cada 30 s para que el archivo no crezca
        vm.setHandsFree(false)
    }

    @Test
    fun `un error en manos libres lo pausa para no repetirlo en bucle`() = handsFreeTest {
        api.chatError = httpError(401)
        val vm = createVm()
        recorder.scriptSpeech()
        vm.setHandsFree(true)
        advance(5_000)
        assertFalse(vm.state.value.handsFree)
        assertEquals(TutorStatus.Error, vm.state.value.status)
        assertEquals(1, api.chatRequests.size)
    }

    @Test
    fun `si el microfono esta ocupado se avisa y se apaga manos libres`() = handsFreeTest {
        recorder.startError = IllegalStateException("busy")
        val vm = createVm()
        vm.setHandsFree(true)
        advance(100)
        assertFalse(vm.state.value.handsFree)
        assertTrue(vm.state.value.error!!.contains("micrófono"))
    }

    @Test
    fun `si activas manos libres mientras el tutor abre, escucha al terminar`() = handsFreeTest {
        val vm = createVm()
        advanceUntilIdle()
        vm.startConversation()
        vm.setHandsFree(true) // el tutor aún está "pensando"
        advance(1_000)
        assertEquals(Role.ASSISTANT, vm.state.value.messages.single().role)
        assertEquals(TutorStatus.Listening, vm.state.value.status)
        vm.setHandsFree(false)
    }

    @Test
    fun `con manos libres el boton de mantener presionado no graba`() = handsFreeTest {
        val vm = createVm()
        vm.setHandsFree(true)
        advance(100)
        val started = recorder.started
        vm.onPressMic()
        assertEquals(started, recorder.started)
        vm.setHandsFree(false)
    }

    // ---- Comandos de voz ----

    @Test
    fun `repeat repite lo ultimo sin preguntarle al tutor`() = handsFreeTest {
        api.chatReplies += tutorJson(reply = "How was your weekend?")
        val vm = createVm()
        advanceUntilIdle()
        pushToTalk(vm, "Hello!")
        pushToTalk(vm, "Repeat, please.")

        assertEquals(listOf("How was your weekend?", "How was your weekend?"), androidVoice.spoken)
        assertEquals(1, api.chatRequests.size)
        assertEquals(2, vm.state.value.messages.size) // el comando no aparece en el chat
        assertTrue(vm.state.value.notice!!.contains("repetir"))
        assertEquals(TutorStatus.Idle, vm.state.value.status)
    }

    @Test
    fun `mas lento baja la velocidad, la guarda y repite`() = handsFreeTest {
        val vm = createVm()
        advanceUntilIdle()
        pushToTalk(vm, "Hello!")
        pushToTalk(vm, "Más lento")
        assertEquals(0.8f, settings.value.ttsRate)
        assertEquals(0.8f, androidVoice.rates.last())

        pushToTalk(vm, "Faster")
        assertEquals(1.0f, settings.value.ttsRate)
    }

    @Test
    fun `la velocidad no baja de 0_5x`() = handsFreeTest {
        settings.value = settings.value.copy(ttsRate = 0.5f)
        val vm = createVm()
        advanceUntilIdle()
        pushToTalk(vm, "Slow down")
        assertEquals(0.5f, settings.value.ttsRate)
    }

    @Test
    fun `pausa por voz apaga manos libres`() = handsFreeTest {
        api.transcription = "Pause."
        val vm = createVm()
        recorder.scriptSpeech()
        vm.setHandsFree(true)
        advance(5_000)
        assertFalse(vm.state.value.handsFree)
        assertEquals(TutorStatus.Idle, vm.state.value.status)
        assertTrue(api.chatRequests.isEmpty())
        val started = recorder.started
        advance(10_000)
        assertEquals(started, recorder.started) // ya no vuelve a escuchar
    }

    @Test
    fun `nueva sesion por voz limpia el chat`() = handsFreeTest {
        val vm = createVm()
        advanceUntilIdle()
        pushToTalk(vm, "Hello!")
        pushToTalk(vm, "Nueva sesión")
        assertTrue(vm.state.value.messages.isEmpty())
        assertFalse(vm.state.value.sessionStarted)
    }

    @Test
    fun `explicame lee en espanol las correcciones del ultimo mensaje`() = handsFreeTest {
        api.chatReplies += replyWithCorrection
        val vm = createVm()
        advanceUntilIdle()
        pushToTalk(vm, "Yesterday I go to the beach")
        spokenLog.clear()

        pushToTalk(vm, "Explain in Spanish")
        assertEquals(listOf("es:Corrección. Mejor di:", "en:I went", "es:Pasado simple"), spokenLog)
    }

    @Test
    fun `explicame sin errores lo dice en espanol`() = handsFreeTest {
        val vm = createVm()
        advanceUntilIdle()
        pushToTalk(vm, "Hello!")
        pushToTalk(vm, "Explícame")
        assertEquals(listOf(CorrectionSpeech.NO_CORRECTIONS), spanishVoice.spoken)
    }

    // ---- Correcciones habladas ----

    @Test
    fun `las correcciones se dicen antes de la respuesta`() = handsFreeTest {
        settings.value = settings.value.copy(speakCorrections = true)
        api.chatReplies += replyWithCorrection
        val vm = createVm()
        advanceUntilIdle()
        pushToTalk(vm, "Yesterday I go to the beach")
        assertEquals(
            listOf("es:Corrección. Mejor di:", "en:I went", "es:Pasado simple", "en:Sounds fun! Did you swim?"),
            spokenLog,
        )
    }

    @Test
    fun `si se desactivan, solo se dice la respuesta`() = handsFreeTest {
        api.chatReplies += replyWithCorrection
        val vm = createVm()
        advanceUntilIdle()
        pushToTalk(vm, "Yesterday I go to the beach")
        assertEquals(listOf("en:Sounds fun! Did you swim?"), spokenLog)
    }

    @Test
    fun `cuando el tutor abre la conversacion no hay correcciones que decir`() = handsFreeTest {
        settings.value = settings.value.copy(speakCorrections = true)
        api.chatReplies += replyWithCorrection // aunque el LLM invente una corrección
        val vm = createVm()
        advanceUntilIdle()
        vm.startConversation()
        advanceUntilIdle()
        assertTrue(spanishVoice.spoken.isEmpty())
    }
}
