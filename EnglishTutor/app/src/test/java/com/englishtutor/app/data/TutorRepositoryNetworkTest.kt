package com.englishtutor.app.data

import com.englishtutor.app.data.local.AppDatabase
import com.englishtutor.app.data.remote.GroqClient
import com.englishtutor.app.inMemoryDatabase
import com.englishtutor.app.tutorJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Prueba TutorRepository contra un servidor HTTP falso que imita a Groq. */
@RunWith(RobolectricTestRunner::class)
class TutorRepositoryNetworkTest {

    private lateinit var server: MockWebServer
    private lateinit var db: AppDatabase
    private lateinit var repository: TutorRepository
    private var currentKey = "test-key"

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        db = inMemoryDatabase()
        repository = TutorRepository(GroqClient.create(server.url("/openai/v1/").toString(), apiKey = { currentKey }), db.tutorDao())
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
    }

    private fun chatResponse(content: String) = MockResponse().setBody(
        """{"choices":[{"index":0,"message":{"role":"assistant","content":${JsonPrimitive(content)}}}],"usage":{}}"""
    )

    private fun lastRequestJson(): JsonObject = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject

    private fun history(n: Int) = List(n) { i -> ChatMessage(i.toLong(), if (i % 2 == 0) Role.USER else Role.ASSISTANT, "msg $i") }

    @Test
    fun `chat envia el formato correcto y entiende el JSON del tutor`() = runTest {
        server.enqueue(chatResponse(tutorJson(
            reply = "Oh nice! Did you swim?",
            corrections = """[{"original":"I go","better":"I went","explanation":"Pasado simple"}]""",
            vocab = """[{"term":"to swim","meaning":"nadar","example":"I love to swim."}]""",
        )))

        val reply = repository.chat(history(1), systemPrompt = "SYSTEM", model = "openai/gpt-oss-120b")

        assertEquals("Oh nice! Did you swim?", reply.reply)
        assertEquals(Correction("I go", "I went", "Pasado simple"), reply.corrections.single())
        assertEquals("to swim", reply.new_vocab.single().term)

        val request = server.takeRequest()
        assertEquals("/openai/v1/chat/completions", request.path)
        assertEquals("Bearer test-key", request.getHeader("Authorization"))
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("openai/gpt-oss-120b", body["model"]!!.jsonPrimitive.content)
        assertEquals("json_object", body["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        val messages = body["messages"]!!.jsonArray
        assertEquals("system", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("SYSTEM", messages[0].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("user", messages[1].jsonObject["role"]!!.jsonPrimitive.content)
    }

    @Test
    fun `entiende saved_words cuando pides guardar una palabra`() = runTest {
        server.enqueue(chatResponse("""{"reply":"Saved! Anyway...","corrections":[],"new_vocab":[],"saved_words":[{"term":"though","meaning":"aunque","example":"Nice, though."}]}"""))
        val reply = repository.chat(history(1))
        assertEquals(Vocab("though", "aunque", "Nice, though."), reply.saved_words.single())
    }

    @Test
    fun `defineWord obtiene significado y ejemplo`() = runTest {
        server.enqueue(chatResponse("""{"term":"though","meaning":"aunque, sin embargo","example":"It was hard, though."}"""))
        val word = repository.defineWord("thogh", model = "openai/gpt-oss-120b")
        assertEquals("though", word.term)
        assertEquals("aunque, sin embargo", word.meaning)
        val body = lastRequestJson()
        assertEquals("openai/gpt-oss-120b", body["model"]!!.jsonPrimitive.content)
        assertTrue(body["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonPrimitive.content.contains("\"thogh\""))
    }

    @Test
    fun `defineWord con respuesta invalida muestra un error claro`() = runTest {
        server.enqueue(chatResponse("no idea"))
        assertTrue(expectTutorException { repository.defineWord("xyz") }.message!!.contains("significado"))
    }

    @Test
    fun `usa la API key vigente en cada peticion`() = runTest {
        server.enqueue(chatResponse(tutorJson()))
        server.enqueue(chatResponse(tutorJson()))
        repository.chat(history(1))
        currentKey = "gsk_nueva"   // por ejemplo, la cambiaste en Ajustes
        repository.chat(history(1))
        assertEquals("Bearer test-key", server.takeRequest().getHeader("Authorization"))
        assertEquals("Bearer gsk_nueva", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `con historial vacio el tutor abre la conversacion`() = runTest {
        server.enqueue(chatResponse(tutorJson(reply = "Hi! Table for one?")))
        repository.chat(emptyList())
        val messages = lastRequestJson()["messages"]!!.jsonArray
        assertEquals(2, messages.size)
        assertEquals(TutorPrompt.KICKOFF, messages[1].jsonObject["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun `solo envia los ultimos 20 mensajes`() = runTest {
        server.enqueue(chatResponse(tutorJson()))
        repository.chat(history(30))
        val messages = lastRequestJson()["messages"]!!.jsonArray
        assertEquals(1 + TutorRepository.MAX_HISTORY, messages.size)
        assertEquals("msg 10", messages[1].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("msg 29", messages.last().jsonObject["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun `JSON mal formado se usa como texto sin tarjetas`() = runTest {
        server.enqueue(chatResponse("Sorry, I can't format that. How was your day?"))
        val reply = repository.chat(history(1))
        assertEquals("Sorry, I can't format that. How was your day?", reply.reply)
        assertTrue(reply.corrections.isEmpty())
    }

    @Test
    fun `JSON sin reply usa el contenido crudo`() = runTest {
        server.enqueue(chatResponse("""{"reply": "", "corrections": []}"""))
        assertEquals("""{"reply": "", "corrections": []}""", repository.chat(history(1)).reply)
    }

    @Test
    fun `campos faltantes o extra no rompen el parseo`() = runTest {
        server.enqueue(chatResponse("""{"reply": "Cool!", "mood": "happy"}"""))
        val reply = repository.chat(history(1))
        assertEquals("Cool!", reply.reply)
        assertTrue(reply.corrections.isEmpty() && reply.new_vocab.isEmpty())
    }

    @Test
    fun `ante 429 reintenta y luego funciona`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("retry-after", "1"))
        server.enqueue(chatResponse(tutorJson(reply = "Second try worked")))
        assertEquals("Second try worked", repository.chat(history(1)).reply)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `si el 429 persiste muestra un mensaje claro`() = runTest {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(429)) }
        val e = expectTutorException { repository.chat(history(1)) }
        assertTrue(e.message!!.contains("límite"))
        assertEquals(3, server.requestCount) // 1 intento + 2 reintentos
    }

    @Test
    fun `401 indica que la API key es invalida`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        assertTrue(expectTutorException { repository.chat(history(1)) }.message!!.contains("API key"))
    }

    @Test
    fun `500 indica problema de Groq`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue(expectTutorException { repository.chat(history(1)) }.message!!.contains("503"))
    }

    @Test
    fun `sin conexion muestra un mensaje claro`() = runTest {
        server.shutdown()
        assertTrue(expectTutorException { repository.chat(history(1)) }.message!!.contains("Sin conexión"))
    }

    @Test
    fun `transcribe envia el audio con el modelo whisper e idioma ingles`() = runTest {
        server.enqueue(MockResponse().setBody("""{"text":"  Hello there  ","x_groq":{}}"""))
        val audio = File.createTempFile("turn", ".m4a").apply { writeText("fake audio") }

        assertEquals("Hello there", repository.transcribe(audio))

        val request = server.takeRequest()
        assertEquals("/openai/v1/audio/transcriptions", request.path)
        assertTrue(request.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("name=\"file\"; filename=\"${audio.name}\""))
        assertTrue(body.contains("whisper-large-v3-turbo"))
        assertTrue(body.contains("name=\"language\""))
        assertTrue(body.contains("fake audio"))
    }

    @Test
    fun `synthesize pide Orpheus en WAV y guarda el audio`() = runTest {
        server.enqueue(MockResponse().setBody("RIFF....WAVE"))
        val out = File.createTempFile("orpheus", ".wav")

        repository.synthesize("Hello!", "troy", out)

        assertEquals("RIFF....WAVE", out.readText())
        val request = server.takeRequest()
        assertEquals("/openai/v1/audio/speech", request.path)
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("canopylabs/orpheus-v1-english", body["model"]!!.jsonPrimitive.content)
        assertEquals("troy", body["voice"]!!.jsonPrimitive.content)
        assertEquals("wav", body["response_format"]!!.jsonPrimitive.content)
        assertEquals("Hello!", body["input"]!!.jsonPrimitive.content)
    }

    private suspend fun expectTutorException(block: suspend () -> Unit): TutorException {
        try {
            block()
        } catch (e: TutorException) {
            return e
        }
        fail("Se esperaba TutorException")
        error("unreachable")
    }
}
