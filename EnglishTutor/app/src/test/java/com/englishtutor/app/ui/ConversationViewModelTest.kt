package com.englishtutor.app.ui

import com.englishtutor.app.FakeGroqApi
import com.englishtutor.app.FakeRecorder
import com.englishtutor.app.FakeVoice
import com.englishtutor.app.data.Role
import com.englishtutor.app.data.Scenario
import com.englishtutor.app.data.TutorPrompt
import com.englishtutor.app.data.TutorReply
import com.englishtutor.app.data.Correction
import com.englishtutor.app.data.TutorRepository
import com.englishtutor.app.data.TutorSettings
import com.englishtutor.app.data.VoiceEngine
import com.englishtutor.app.data.local.AppDatabase
import com.englishtutor.app.httpError
import com.englishtutor.app.inMemoryDatabase
import com.englishtutor.app.tutorJson
import com.englishtutor.app.ui.conversation.ConversationViewModel
import com.englishtutor.app.ui.conversation.TutorStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Flujo completo de conversación con micrófono, Groq y voces falsas, y Room en memoria. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ConversationViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var api: FakeGroqApi
    private lateinit var repository: TutorRepository
    private val recorder = FakeRecorder()
    private val androidVoice = FakeVoice()
    private val settings = MutableStateFlow(TutorSettings())

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

    private fun createVm(orpheus: FakeVoice = FakeVoice(), buildApiKey: String = "gsk_local") =
        ConversationViewModel(repository, settings, recorder, androidVoice, orpheus, buildApiKey, todayStart = 0L)

    @Test
    fun `turno completo - graba, transcribe, responde, corrige, guarda y habla`() = runTest(dispatcher) {
        api.chatReplies += tutorJson(
            reply = "Sounds fun! Did you swim?",
            corrections = """[{"original":"I go","better":"I went","explanation":"Pasado simple"}]""",
            vocab = """[{"term":"to swim","meaning":"nadar","example":"I swim."}]""",
        )
        val vm = createVm()
        advanceUntilIdle()

        vm.onPressMic()
        assertEquals(TutorStatus.Recording, vm.state.value.status)
        vm.onReleaseMic()
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(TutorStatus.Idle, s.status)
        assertNull(s.error)
        assertTrue(s.sessionStarted)
        assertEquals(listOf(Role.USER, Role.ASSISTANT), s.messages.map { it.role })
        assertEquals("Yesterday I go to the beach", s.messages[0].text)
        assertEquals("I went", s.messages[0].corrections.single().better)
        assertEquals(listOf("Sounds fun! Did you swim?"), androidVoice.spoken)

        // Se guardó en Room
        val session = repository.observeSessions().first().single()
        assertEquals(2, session.messageCount)
        assertEquals("to swim", repository.observeVocab().first().single().term)
    }

    @Test
    fun `el tutor puede abrir la conversacion con el escenario elegido`() = runTest(dispatcher) {
        api.chatReplies += tutorJson(reply = "Welcome! Can I get you something to drink?")
        val vm = createVm()
        advanceUntilIdle()

        vm.selectScenario(Scenario.RESTAURANT)
        vm.startConversation()
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(Role.ASSISTANT, s.messages.single().role)
        assertEquals(listOf("Welcome! Can I get you something to drink?"), androidVoice.spoken)
        val request = api.chatRequests.single()
        assertTrue(request.messages[0].content.contains("you are a waiter"))
        assertEquals(TutorPrompt.KICKOFF, request.messages[1].content)
        assertEquals("restaurant", repository.observeSessions().first().single().session.scenario)

        // Con la sesión iniciada ya no se puede cambiar el escenario ni volver a "empezar"
        vm.selectScenario(Scenario.JOB_INTERVIEW)
        vm.startConversation()
        advanceUntilIdle()
        assertEquals(Scenario.RESTAURANT, vm.state.value.scenario)
        assertEquals(1, api.chatRequests.size)
    }

    @Test
    fun `usa el modelo y nivel de los ajustes`() = runTest(dispatcher) {
        settings.value = TutorSettings(llmModel = "openai/gpt-oss-120b", level = com.englishtutor.app.data.Level.BASIC)
        val vm = createVm()
        advanceUntilIdle()
        vm.onPressMic(); vm.onReleaseMic()
        advanceUntilIdle()
        val request = api.chatRequests.single()
        assertEquals("openai/gpt-oss-120b", request.model)
        assertTrue(request.messages[0].content.contains("beginner (A2)"))
    }

    @Test
    fun `el escenario por defecto viene de los ajustes`() = runTest(dispatcher) {
        settings.value = TutorSettings(defaultScenario = Scenario.JOB_INTERVIEW)
        val vm = createVm()
        advanceUntilIdle()
        assertEquals(Scenario.JOB_INTERVIEW, vm.state.value.scenario)
    }

    @Test
    fun `una grabacion demasiado corta muestra un aviso`() = runTest(dispatcher) {
        recorder.returnNull = true
        val vm = createVm()
        vm.onPressMic(); vm.onReleaseMic()
        advanceUntilIdle()
        assertEquals(TutorStatus.Error, vm.state.value.status)
        assertTrue(vm.state.value.error!!.contains("Mantén presionado"))
        assertTrue(api.chatRequests.isEmpty())
    }

    @Test
    fun `si Whisper no entiende nada no se crea sesion`() = runTest(dispatcher) {
        api.transcription = "   "
        val vm = createVm()
        vm.onPressMic(); vm.onReleaseMic()
        advanceUntilIdle()
        assertTrue(vm.state.value.error!!.contains("No se escuchó"))
        assertFalse(vm.state.value.sessionStarted)
        assertTrue(repository.observeSessions().first().isEmpty())
    }

    @Test
    fun `un error de Groq se muestra sin cerrar la app y se puede seguir`() = runTest(dispatcher) {
        api.chatError = httpError(401)
        val vm = createVm()
        vm.onPressMic(); vm.onReleaseMic()
        advanceUntilIdle()
        assertEquals(TutorStatus.Error, vm.state.value.status)
        assertTrue(vm.state.value.error!!.contains("API key"))

        vm.dismissError()
        assertEquals(TutorStatus.Idle, vm.state.value.status)
        assertNull(vm.state.value.error)

        api.chatError = null
        vm.onPressMic(); vm.onReleaseMic()
        advanceUntilIdle()
        assertEquals(TutorStatus.Idle, vm.state.value.status)
        assertEquals(Role.ASSISTANT, vm.state.value.messages.last().role)
    }

    @Test
    fun `sin API key se avisa y el aviso desaparece al guardarla en Ajustes`() = runTest(dispatcher) {
        val vm = createVm(buildApiKey = "")
        advanceUntilIdle()
        assertTrue(vm.state.value.apiKeyMissing)

        settings.value = TutorSettings(apiKey = "gsk_desde_ajustes")
        advanceUntilIdle()
        assertFalse(vm.state.value.apiKeyMissing)
    }

    @Test
    fun `con la key de local properties no hay aviso`() = runTest(dispatcher) {
        val vm = createVm(buildApiKey = "gsk_local")
        advanceUntilIdle()
        assertFalse(vm.state.value.apiKeyMissing)
    }

    @Test
    fun `con Orpheus activado usa esa voz`() = runTest(dispatcher) {
        settings.value = TutorSettings(voiceEngine = VoiceEngine.ORPHEUS)
        val orpheus = FakeVoice()
        val vm = createVm(orpheus)
        advanceUntilIdle()
        vm.onPressMic(); vm.onReleaseMic()
        advanceUntilIdle()
        assertEquals(1, orpheus.spoken.size)
        assertTrue(androidVoice.spoken.isEmpty())
    }

    @Test
    fun `si Orpheus falla habla la voz de Android y avisa`() = runTest(dispatcher) {
        settings.value = TutorSettings(voiceEngine = VoiceEngine.ORPHEUS)
        val vm = createVm(orpheus = FakeVoice(error = IllegalStateException("terms not accepted")))
        advanceUntilIdle()
        vm.onPressMic(); vm.onReleaseMic()
        advanceUntilIdle()
        assertEquals(1, androidVoice.spoken.size)
        assertEquals(TutorStatus.Idle, vm.state.value.status)
        assertTrue(vm.state.value.notice!!.contains("terms not accepted"))
        vm.dismissNotice()
        assertNull(vm.state.value.notice)
    }

    @Test
    fun `nueva sesion limpia el chat y la siguiente conversacion es otra sesion`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        vm.onPressMic(); vm.onReleaseMic()
        advanceUntilIdle()

        vm.newSession()
        advanceUntilIdle()
        assertTrue(vm.state.value.messages.isEmpty())
        assertFalse(vm.state.value.sessionStarted)

        vm.onPressMic(); vm.onReleaseMic()
        advanceUntilIdle()
        assertEquals(2, repository.observeSessions().first().size)
        // La segunda sesión no arrastra el historial de la primera
        assertEquals(2, api.chatRequests.last().messages.size) // system + tu mensaje
    }

    @Test
    fun `al empezar repasa los errores frecuentes y los incluye en el prompt`() = runTest(dispatcher) {
        val old = repository.startSession()
        val msg = repository.saveUserMessage(old, "I go yesterday")
        repository.saveTutorTurn(old, msg, TutorReply("ok", corrections = listOf(Correction("I go yesterday", "I went yesterday", "pasado"))))

        val vm = createVm()
        advanceUntilIdle()
        assertEquals("I went yesterday", vm.state.value.review.single().better)

        vm.startConversation()
        advanceUntilIdle()
        assertTrue(api.chatRequests.single().messages[0].content.contains("I said \"I go yesterday\" instead of \"I went yesterday\""))
    }

    @Test
    fun `el contador diario suma los minutos practicados`() = runTest(dispatcher) {
        val clockRepo = TutorRepository(api, db.tutorDao(), clock = { 10 * 60_000L })
        val session = clockRepo.startSession()
        db.tutorDao().updateSessionEnd(session, 25 * 60_000L) // 15 minutos
        settings.value = TutorSettings(dailyGoalMinutes = 30)

        val vm = createVm()
        advanceUntilIdle()
        assertEquals(15, vm.state.value.todayMinutes)
        assertEquals(30, vm.state.value.goalMinutes)
    }

    @Test
    fun `decir guarda la palabra la deja en Mis palabras y avisa`() = runTest(dispatcher) {
        api.transcription = "Save the word though"
        api.chatReplies += """{"reply":"Done! Anyway, how was work?","corrections":[],"new_vocab":[],"saved_words":[{"term":"though","meaning":"aunque","example":"Nice, though."}]}"""
        val vm = createVm()
        advanceUntilIdle()
        vm.onPressMic(); vm.onReleaseMic()
        advanceUntilIdle()

        assertTrue(vm.state.value.notice!!.contains("though (aunque)"))
        val word = repository.observeVocab().first().single()
        assertTrue(word.isMine)
        assertEquals("aunque", word.meaning)
    }

    @Test
    fun `mis palabras dificiles se cargan al empezar y van en el prompt`() = runTest(dispatcher) {
        repository.saveMyWord(com.englishtutor.app.data.Vocab("though", "aunque"))
        val vm = createVm()
        advanceUntilIdle()
        assertEquals(listOf("though"), vm.state.value.myWords)

        vm.startConversation()
        advanceUntilIdle()
        assertTrue(api.chatRequests.single().messages[0].content.contains("Words I find difficult: though."))
    }

    @Test
    fun `presionar mientras habla interrumpe al tutor`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        val stopsBefore = androidVoice.stops
        vm.onPressMic()
        assertTrue(androidVoice.stops > stopsBefore)
        assertEquals(1, recorder.started)
    }
}
