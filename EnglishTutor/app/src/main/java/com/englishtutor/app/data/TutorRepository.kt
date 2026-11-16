package com.englishtutor.app.data

import android.util.Log
import com.englishtutor.app.data.local.CorrectionEntity
import com.englishtutor.app.data.local.FrequentError
import com.englishtutor.app.data.local.MessageEntity
import com.englishtutor.app.data.local.MessageWithCorrections
import com.englishtutor.app.data.local.SessionEntity
import com.englishtutor.app.data.local.SessionSummary
import com.englishtutor.app.data.local.TutorDao
import com.englishtutor.app.data.local.VocabEntity
import com.englishtutor.app.data.remote.ApiMessage
import com.englishtutor.app.data.remote.ChatRequest
import com.englishtutor.app.data.remote.GroqApi
import com.englishtutor.app.data.remote.GroqClient
import com.englishtutor.app.data.remote.SpeechRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.IOException

/** Error con un mensaje listo para mostrar al usuario (en español). */
class TutorException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Único punto de acceso a los datos: Groq (red) y Room (base de datos local). */
class TutorRepository(
    private val api: GroqApi,
    private val dao: TutorDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    // ---- Persistencia (Room) ----

    suspend fun startSession(scenario: Scenario = Scenario.FREE_TALK): Long =
        dao.insertSession(SessionEntity(startedAt = clock(), scenario = scenario.id))

    /** Guarda tu mensaje y devuelve su id (para ligarle después las correcciones). */
    suspend fun saveUserMessage(sessionId: Long, text: String): Long {
        val now = clock()
        val id = dao.insertMessage(MessageEntity(sessionId = sessionId, role = "user", text = text, createdAt = now))
        dao.updateSessionEnd(sessionId, now)
        return id
    }

    /**
     * Guarda la respuesta del tutor, las correcciones de tu mensaje y el vocabulario nuevo.
     * `userMessageId` es null cuando el tutor abre la conversación (no hay mensaje tuyo que corregir).
     */
    suspend fun saveTutorTurn(sessionId: Long, userMessageId: Long?, reply: TutorReply) {
        val now = clock()
        dao.insertMessage(MessageEntity(sessionId = sessionId, role = "assistant", text = reply.reply, createdAt = now))
        if (userMessageId != null) {
            dao.insertCorrections(reply.corrections.map {
                CorrectionEntity(messageId = userMessageId, original = it.original, better = it.better, explanation = it.explanation)
            })
        }
        dao.insertVocab(reply.new_vocab.filter { it.term.isNotBlank() }.map {
            VocabEntity(term = it.term.trim(), meaning = it.meaning.trim(), example = it.example.trim(), createdAt = now)
        })
        reply.saved_words.forEach { saveMyWord(it) }
        dao.updateSessionEnd(sessionId, now) // la sesión "termina" en tu último turno
    }

    /**
     * Guarda una palabra en "Mis palabras". Si ya existía (por ejemplo, la enseñó el tutor),
     * solo la marca como tuya y actualiza su significado.
     */
    suspend fun saveMyWord(word: Vocab) {
        val term = word.term.trim()
        if (term.isEmpty()) return
        val meaning = word.meaning.trim()
        val example = word.example.trim()
        val id = dao.insertVocabIfNew(VocabEntity(term = term, meaning = meaning, example = example, createdAt = clock(), isMine = true))
        if (id == -1L) dao.markMine(term, meaning, example)
    }

    fun observeSessions(): Flow<List<SessionSummary>> = dao.observeSessionSummaries()
    fun observeSession(sessionId: Long): Flow<SessionEntity?> = dao.observeSession(sessionId)
    fun observeMessages(sessionId: Long): Flow<List<MessageWithCorrections>> = dao.observeMessagesWithCorrections(sessionId)
    suspend fun deleteSession(sessionId: Long) = dao.deleteSession(sessionId)

    fun observeVocab(): Flow<List<VocabEntity>> = dao.observeVocab()
    suspend fun markVocabReviewed(id: Long) = dao.markVocabReviewed(id)
    suspend fun deleteVocab(id: Long) = dao.deleteVocab(id)
    suspend fun setMine(id: Long, mine: Boolean) = dao.setMine(id, mine)

    /** Tus palabras difíciles, las menos repasadas primero. */
    suspend fun myWordsToPractice(limit: Int): List<VocabEntity> = dao.observeMyWordsToPractice(limit).first()

    fun observeFrequentErrors(limit: Int = 50): Flow<List<FrequentError>> = dao.observeFrequentErrors(limit)
    suspend fun frequentErrors(limit: Int): List<FrequentError> = dao.observeFrequentErrors(limit).first()

    /** Minutos practicados desde `since` (por ejemplo, desde las 00:00 de hoy). */
    fun observePracticeMillisSince(since: Long): Flow<Long> = dao.observePracticeMillisSince(since)

    // ---- Groq ----

    /** Audio (.m4a) → texto en inglés usando Whisper. */
    suspend fun transcribe(audio: File): String = withRetry {
        val filePart = MultipartBody.Part.createFormData(
            "file", audio.name, audio.asRequestBody("audio/mp4".toMediaType())
        )
        val text = api.transcribe(
            file = filePart,
            model = WHISPER_MODEL.toRequestBody(TEXT),
            language = "en".toRequestBody(TEXT),
        ).text.trim()
        Log.d(TAG, "Whisper: \"$text\"")
        text
    }

    /**
     * Historial → respuesta del tutor. Solo enviamos los últimos ~20 mensajes.
     * Si el historial está vacío, el tutor abre la conversación (mensaje KICKOFF invisible).
     */
    suspend fun chat(
        history: List<ChatMessage>,
        systemPrompt: String = TutorPrompt.build(),
        model: String = TutorSettings.LLM_MODELS.first(),
    ): TutorReply = withRetry {
        val messages = buildList {
            add(ApiMessage("system", systemPrompt))
            if (history.isEmpty()) add(ApiMessage("user", TutorPrompt.KICKOFF))
            history.takeLast(MAX_HISTORY).forEach { msg ->
                val role = if (msg.role == Role.USER) "user" else "assistant"
                add(ApiMessage(role, msg.text))
            }
        }
        val content = api.chat(ChatRequest(model = model, messages = messages))
            .choices.firstOrNull()?.message?.content.orEmpty()
        Log.d(TAG, "LLM raw: $content")
        parseReply(content)
    }

    /** Busca con el LLM el significado en español de una palabra que agregas a mano. */
    suspend fun defineWord(term: String, model: String = TutorSettings.LLM_MODELS.first()): Vocab = withRetry {
        val request = ChatRequest(model = model, messages = listOf(ApiMessage("user", TutorPrompt.define(term.trim()))))
        val content = api.chat(request).choices.firstOrNull()?.message?.content.orEmpty()
        Log.d(TAG, "Definición: $content")
        val parsed = runCatching { GroqClient.json.decodeFromString(Vocab.serializer(), content) }.getOrNull()
        if (parsed == null || parsed.term.isBlank() || parsed.meaning.isBlank()) {
            throw TutorException("No pude obtener el significado de \"$term\". Intenta de nuevo.")
        }
        parsed
    }

    /** Texto (máx. 200 caracteres) → archivo WAV con la voz de Orpheus. */
    suspend fun synthesize(text: String, voice: String, outFile: File): File = withRetry {
        val body = api.speech(SpeechRequest(model = ORPHEUS_MODEL, input = text, voice = voice))
        body.byteStream().use { input -> outFile.outputStream().use { input.copyTo(it) } }
        outFile
    }

    /** Si el JSON viene mal formado, usamos el texto crudo como reply (sin crashear). */
    internal fun parseReply(content: String): TutorReply =
        try {
            val parsed = GroqClient.json.decodeFromString(TutorReply.serializer(), content)
            if (parsed.reply.isBlank()) TutorReply(reply = content.trim()) else parsed
        } catch (e: Exception) {
            Log.w(TAG, "JSON inválido, uso texto crudo", e)
            TutorReply(reply = content.trim())
        }

    /** Reintenta ante HTTP 429 (límite del plan gratis) y traduce errores a mensajes claros. */
    private suspend fun <T> withRetry(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: HttpException) {
                if (e.code() == 429 && attempt < MAX_RETRIES) {
                    val waitSec = e.response()?.headers()?.get("retry-after")?.toDoubleOrNull() ?: 2.0
                    attempt++
                    Log.w(TAG, "429: reintento $attempt en ${waitSec}s")
                    delay((waitSec.coerceIn(1.0, 10.0) * 1000).toLong())
                    continue
                }
                throw TutorException(httpMessage(e.code()), e)
            } catch (e: IOException) {
                throw TutorException("Sin conexión a internet o Groq no respondió.", e)
            }
        }
    }

    private fun httpMessage(code: Int) = when (code) {
        401 -> "API key inválida o vacía. Revísala en Ajustes."
        429 -> "Llegaste al límite por minuto del plan gratis. Espera unos segundos y vuelve a intentar."
        400 -> "Groq rechazó la petición (400). Quizás el audio era muy corto."
        in 500..599 -> "Groq tiene problemas ahora mismo ($code). Intenta de nuevo."
        else -> "Error de Groq (HTTP $code)."
    }

    companion object {
        const val TAG = "Tutor"
        const val WHISPER_MODEL = "whisper-large-v3-turbo"
        const val ORPHEUS_MODEL = "canopylabs/orpheus-v1-english"
        const val MAX_HISTORY = 20
        private const val MAX_RETRIES = 2
        private val TEXT = "text/plain".toMediaType()
    }
}
