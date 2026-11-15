package com.englishtutor.app.data

import com.englishtutor.app.FakeGroqApi
import com.englishtutor.app.data.local.AppDatabase
import com.englishtutor.app.inMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Prueba lo que se guarda en Room (base de datos en memoria). */
@RunWith(RobolectricTestRunner::class)
class TutorRepositoryPersistenceTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: TutorRepository
    private var now = 1_000_000L

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        repository = TutorRepository(FakeGroqApi(), db.tutorDao(), clock = { now })
    }

    @After
    fun tearDown() = db.close()

    private val replyWithCorrection = TutorReply(
        reply = "Nice! Did you swim?",
        corrections = listOf(Correction("I go", "I went", "Pasado simple")),
        new_vocab = listOf(Vocab("to swim", "nadar", "I love to swim.")),
    )

    @Test
    fun `un turno completo guarda sesion, mensajes, correcciones y vocabulario`() = runTest {
        val session = repository.startSession(Scenario.RESTAURANT)
        val userMsg = repository.saveUserMessage(session, "Yesterday I go to the beach")
        now += 90_000
        repository.saveTutorTurn(session, userMsg, replyWithCorrection)

        val messages = repository.observeMessages(session).first()
        assertEquals(listOf("user", "assistant"), messages.map { it.message.role })
        assertEquals("I went", messages[0].corrections.single().better)
        assertTrue(messages[1].corrections.isEmpty())

        val saved = repository.observeSession(session).first()!!
        assertEquals("restaurant", saved.scenario)
        assertEquals(now, saved.endedAt)

        assertEquals("to swim", repository.observeVocab().first().single().term)
        assertEquals(2, repository.observeSessions().first().single().messageCount)
    }

    @Test
    fun `si el tutor abre la conversacion no se guardan correcciones`() = runTest {
        val session = repository.startSession()
        repository.saveTutorTurn(session, userMessageId = null, reply = replyWithCorrection)
        val messages = repository.observeMessages(session).first()
        assertEquals("assistant", messages.single().message.role)
        assertTrue(repository.frequentErrors(10).isEmpty())
    }

    @Test
    fun `el vocabulario no se duplica aunque cambien mayusculas o espacios`() = runTest {
        val session = repository.startSession()
        repository.saveTutorTurn(session, null, TutorReply("a", new_vocab = listOf(Vocab("Break the ice", "romper el hielo"))))
        repository.saveTutorTurn(session, null, TutorReply("b", new_vocab = listOf(Vocab(" break the ice ", "otra"), Vocab(" ", "vacío"))))
        val vocab = repository.observeVocab().first()
        assertEquals(1, vocab.size)
        assertEquals("romper el hielo", vocab.single().meaning)
    }

    @Test
    fun `marcar como repasada y borrar vocabulario`() = runTest {
        val session = repository.startSession()
        repository.saveTutorTurn(session, null, TutorReply("a", new_vocab = listOf(Vocab("gist", "idea general"))))
        val id = repository.observeVocab().first().single().id
        repository.markVocabReviewed(id)
        repository.markVocabReviewed(id)
        assertEquals(2, repository.observeVocab().first().single().timesReviewed)
        repository.deleteVocab(id)
        assertTrue(repository.observeVocab().first().isEmpty())
    }

    @Test
    fun `errores frecuentes se agrupan sin importar mayusculas y se ordenan por cantidad`() = runTest {
        val session = repository.startSession()
        suspend fun mistake(original: String, better: String) {
            val msg = repository.saveUserMessage(session, original)
            repository.saveTutorTurn(session, msg, TutorReply("ok", corrections = listOf(Correction(original, better, "exp"))))
        }
        mistake("He don't like it", "He doesn't like it")
        mistake("I go yesterday", "I went yesterday")
        mistake("i go yesterday", "I went yesterday")
        mistake("I GO YESTERDAY", "i went yesterday")

        val errors = repository.frequentErrors(10)
        assertEquals(2, errors.size)
        assertEquals(3, errors[0].times)
        assertEquals("I went yesterday", errors[0].better.replaceFirstChar { it.uppercase() })
        assertEquals(1, errors[1].times)
        assertEquals(1, repository.frequentErrors(1).size) // respeta el límite
    }

    @Test
    fun `borrar una sesion borra sus mensajes y correcciones pero no el vocabulario`() = runTest {
        val session = repository.startSession()
        val msg = repository.saveUserMessage(session, "I go")
        repository.saveTutorTurn(session, msg, replyWithCorrection)

        repository.deleteSession(session)

        assertNull(repository.observeSession(session).first())
        assertTrue(repository.observeMessages(session).first().isEmpty())
        assertTrue(repository.frequentErrors(10).isEmpty())
        assertEquals(1, repository.observeVocab().first().size)
    }

    @Test
    fun `minutos practicados suman solo las sesiones desde la hora indicada`() = runTest {
        val startOfDay = 10_000_000L
        now = startOfDay - 3_600_000 // sesión de ayer: no cuenta
        val yesterday = repository.startSession()
        now += 600_000
        repository.saveUserMessage(yesterday, "old")

        now = startOfDay + 1_000 // sesión de hoy de 5 minutos
        val today = repository.startSession()
        now += 300_000
        repository.saveUserMessage(today, "new")

        now += 1_000 // sesión recién creada sin turnos (endedAt null): no cuenta
        repository.startSession()

        assertEquals(300_000L, repository.observePracticeMillisSince(startOfDay).first())
    }

    @Test
    fun `el historial lista las sesiones mas recientes primero`() = runTest {
        val first = repository.startSession(Scenario.FREE_TALK)
        now += 1_000
        val second = repository.startSession(Scenario.JOB_INTERVIEW)
        assertEquals(listOf(second, first), repository.observeSessions().first().map { it.session.id })
    }
}
