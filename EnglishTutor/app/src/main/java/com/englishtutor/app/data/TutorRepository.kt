package com.englishtutor.app.data

import android.database.SQLException
import android.util.Log
import com.englishtutor.app.data.local.SessionEntity
import com.englishtutor.app.data.local.TutorDao
import com.englishtutor.app.data.remote.ApiMessage
import com.englishtutor.app.data.remote.ChatRequest
import com.englishtutor.app.data.remote.GroqApi
import com.englishtutor.app.data.remote.GroqClient
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.IOException

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
    suspend fun chat(history: List<ChatMessage>): TutorReply = withRetry {
        val messages = buildList {
            add(ApiMessage("system", SYSTEM_PROMPT))
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

    // Una sesión por cada vez que abres la app; se crea con el primer turno (sin sesiones vacías).
    private var sessionId: Long? = null

    /** Guarda el turno en Room. Si falla, solo se registra: la conversación sigue. */
    suspend fun saveTurn(userText: String, reply: TutorReply) {
        try {
            val now = System.currentTimeMillis()
            val id = sessionId ?: dao.insertSession(SessionEntity(startedAt = now, endedAt = now)).also { sessionId = it }
            dao.saveTurn(id, userText, reply)
        } catch (e: SQLException) {
            Log.e(TAG, "No se pudo guardar el turno en Room", e)
        }
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
