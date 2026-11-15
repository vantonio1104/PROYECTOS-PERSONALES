package com.englishtutor.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.englishtutor.app.audio.TutorVoice
import com.englishtutor.app.audio.VoiceRecorder
import com.englishtutor.app.data.TutorSettings
import com.englishtutor.app.data.local.AppDatabase
import com.englishtutor.app.data.remote.ApiMessage
import com.englishtutor.app.data.remote.ChatRequest
import com.englishtutor.app.data.remote.ChatResponse
import com.englishtutor.app.data.remote.Choice
import com.englishtutor.app.data.remote.GroqApi
import com.englishtutor.app.data.remote.SpeechRequest
import com.englishtutor.app.data.remote.TranscriptionResponse
import okhttp3.Headers
import okhttp3.MultipartBody
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.io.File
import java.util.concurrent.Executor

/** Base de datos en memoria que ejecuta todo en el mismo hilo (resultados deterministas en tests). */
fun inMemoryDatabase(): AppDatabase {
    val direct = Executor { it.run() }
    return Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .setQueryExecutor(direct)
        .setTransactionExecutor(direct)
        .build()
}

/** JSON de respuesta del tutor, como lo enviaría el LLM. */
fun tutorJson(
    reply: String = "Nice! What did you do there?",
    corrections: String = "[]",
    vocab: String = "[]",
) = """{"reply": "$reply", "corrections": $corrections, "new_vocab": $vocab}"""

/** Crea el error HTTP que lanza Retrofit (por ejemplo 401 o 429). */
fun httpError(code: Int, headers: Headers = Headers.headersOf()): HttpException {
    val raw = okhttp3.Response.Builder()
        .code(code).message("error").protocol(Protocol.HTTP_1_1)
        .request(Request.Builder().url("http://localhost/").build())
        .headers(headers)
        .build()
    return HttpException(Response.error<Any>("{}".toResponseBody(null), raw))
}

/** GroqApi falsa: devuelve respuestas programadas y guarda lo que recibió. */
class FakeGroqApi : GroqApi {
    var transcription = "Yesterday I go to the beach"
    val chatReplies = ArrayDeque<String>()
    var chatError: Exception? = null
    var speechError: Exception? = null
    val chatRequests = mutableListOf<ChatRequest>()
    val speechRequests = mutableListOf<SpeechRequest>()

    override suspend fun transcribe(file: MultipartBody.Part, model: RequestBody, language: RequestBody) =
        TranscriptionResponse(transcription)

    override suspend fun chat(request: ChatRequest): ChatResponse {
        chatRequests += request
        chatError?.let { throw it }
        val content = chatReplies.removeFirstOrNull() ?: tutorJson()
        return ChatResponse(listOf(Choice(ApiMessage("assistant", content))))
    }

    override suspend fun speech(request: SpeechRequest): ResponseBody {
        speechRequests += request
        speechError?.let { throw it }
        return "RIFF-fake-wav".toResponseBody(null)
    }
}

/** Grabador falso: "graba" creando un archivo temporal (o null si simulamos un toque muy corto). */
class FakeRecorder : VoiceRecorder {
    var started = 0
    var returnNull = false
    override fun start() { started++ }
    override fun stop(): File? =
        if (returnNull) null else File.createTempFile("turn", ".m4a").apply { writeText("audio"); deleteOnExit() }
}

/** Voz falsa: guarda lo que "dijo". Puede simular un error (para probar el respaldo de Orpheus). */
class FakeVoice(private val error: Exception? = null) : TutorVoice {
    val spoken = mutableListOf<String>()
    var stops = 0
    override suspend fun speak(text: String, settings: TutorSettings) {
        error?.let { throw it }
        spoken += text
    }
    override fun stop() { stops++ }
}
