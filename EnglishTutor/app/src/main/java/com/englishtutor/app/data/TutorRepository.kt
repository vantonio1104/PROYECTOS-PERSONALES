package com.englishtutor.app.data

import android.database.SQLException
import android.util.Log
import com.englishtutor.app.data.local.SessionEntity
import com.englishtutor.app.data.local.TutorDao
import com.englishtutor.app.data.remote.ApiMessage
import com.englishtutor.app.data.remote.ChatRequest
import com.englishtutor.app.data.remote.GroqApi
import com.englishtutor.app.data.remote.GroqClient
import com.englishtutor.app.data.remote.SpeechRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId

/** Error con un mensaje listo para mostrar al usuario (en español). */
class TutorException(message: String, cause: Throwable? = null) : Exception(message, cause)

class TutorRepository(
    private val api: GroqApi,
    private val dao: TutorDao,
    private val llmModel: String = DEFAULT_LLM_MODEL,
) {

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

    /** Historial → respuesta del tutor. Solo enviamos los últimos ~20 mensajes. */
    suspend fun chat(history: List<ChatMessage>, scenario: Scenario): TutorReply = withRetry {
        val messages = buildList {
            add(ApiMessage("system", "$SYSTEM_PROMPT\n\nScenario: ${scenario.prompt} Stay in this role, but keep correcting my mistakes."))
            history.takeLast(MAX_HISTORY).forEach { msg ->
                val role = if (msg.role == Role.USER) "user" else "assistant"
                add(ApiMessage(role, msg.text))
            }
        }
        val content = api.chat(ChatRequest(model = llmModel, messages = messages))
            .choices.firstOrNull()?.message?.content.orEmpty()
        Log.d(TAG, "LLM raw: $content")
        parseReply(content)
    }

    /** Texto → audios .wav con Orpheus (máx. 200 caracteres por petición, así que va por trozos). */
    suspend fun speech(text: String, voice: String): List<ByteArray> =
        splitForTts(text).map { chunk ->
            withRetry {
                // bytes() lee la red: va en IO, no en el hilo principal
                withContext(Dispatchers.IO) {
                    api.speech(SpeechRequest(TTS_MODEL, voice, chunk)).use { it.bytes() }
                }
            }
        }

    // La sesión se crea con el primer turno (sin sesiones vacías); "Nueva" la reinicia.
    private var sessionId: Long? = null

    fun startNewSession() {
        sessionId = null
    }

    /** Guarda el turno en Room. Si falla, solo se registra: la conversación sigue. */
    suspend fun saveTurn(userText: String, reply: TutorReply, scenario: Scenario) {
        try {
            val now = System.currentTimeMillis()
            val id = sessionId ?: dao.insertSession(
                SessionEntity(startedAt = now, endedAt = now, scenario = scenario.name.lowercase())
            ).also { sessionId = it }
            dao.saveTurn(id, userText, reply)
        } catch (e: SQLException) {
            Log.e(TAG, "No se pudo guardar el turno en Room", e)
        }
    }

    /** Para el repaso al inicio de cada sesión. */
    suspend fun frequentErrors(): List<Correction> =
        try {
            dao.frequentErrors(5)
        } catch (e: SQLException) {
            Log.e(TAG, "No se pudo leer el repaso", e)
            emptyList()
        }

    /** Minutos practicados hoy (suma de las sesiones que empezaron hoy). */
    suspend fun practicedTodayMinutes(): Long =
        try {
            val since = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            dao.practicedSince(since) / 60_000
        } catch (e: SQLException) {
            Log.e(TAG, "No se pudo leer el tiempo de hoy", e)
            0
        }

    /** Si el JSON viene mal formado, usamos el texto crudo como reply (sin crashear). */
    private fun parseReply(content: String): TutorReply =
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
        401 -> "API key inválida. Revisa GROQ_API_KEY en local.properties."
        429 -> "Llegaste al límite por minuto del plan gratis. Espera unos segundos y vuelve a intentar."
        400 -> "Groq rechazó la petición (400). Quizás el audio era muy corto."
        in 500..599 -> "Groq tiene problemas ahora mismo ($code). Intenta de nuevo."
        else -> "Error de Groq (HTTP $code)."
    }

    companion object {
        const val TAG = "Tutor"
        const val WHISPER_MODEL = "whisper-large-v3-turbo"
        const val DEFAULT_LLM_MODEL = "llama-3.3-70b-versatile"
        const val TTS_MODEL = "canopylabs/orpheus-v1-english"
        private const val TTS_MAX_CHARS = 200

        /** Junta oraciones en trozos de hasta [max] caracteres; una oración más larga se corta en un espacio. */
        fun splitForTts(text: String, max: Int = TTS_MAX_CHARS): List<String> {
            val chunks = mutableListOf<String>()
            var current = ""
            for (sentence in text.trim().split(Regex("(?<=[.!?])\\s+"))) {
                var s = sentence
                while (s.length > max) {
                    val cut = s.lastIndexOf(' ', max).takeIf { it > 0 } ?: max
                    if (current.isNotEmpty()) chunks += current.also { current = "" }
                    chunks += s.substring(0, cut)
                    s = s.substring(cut).trim()
                }
                current = when {
                    current.isEmpty() -> s
                    current.length + 1 + s.length <= max -> "$current $s"
                    else -> s.also { chunks += current }
                }
            }
            if (current.isNotEmpty()) chunks += current
            return chunks
        }
        private const val MAX_HISTORY = 20
        private const val MAX_RETRIES = 2
        private val TEXT = "text/plain".toMediaType()

        val SYSTEM_PROMPT = """
            You are my personal English tutor. My native language is Spanish (Chile).
            Talk to me naturally, like a friendly native speaker, and keep the conversation going with questions.
            Adapt to my level (intermediate). Use common, real-life expressions.
            Correct only important mistakes (grammar, wrong words, unnatural phrasing), max 3 per turn.
            Explanations must be short and in Spanish.
            Keep "reply" short (1-3 sentences) so it feels like a real conversation.
            Always answer ONLY with valid JSON in this format:
            {"reply": "...", "corrections": [{"original": "...", "better": "...", "explanation": "..."}], "new_vocab": [{"term": "...", "meaning": "...", "example": "..."}]}
        """.trimIndent()
    }
}
