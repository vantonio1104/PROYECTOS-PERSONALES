package com.englishtutor.app.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.englishtutor.app.data.TutorSettings
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/** Voz gratis de Android (TextToSpeech). speak() suspende hasta que termina de hablar. */
class Speaker(context: Context) : TextToSpeech.OnInitListener, TutorVoice {

    @Volatile private var ready = false
    @Volatile private var onDone: (() -> Unit)? = null

    private val tts = TextToSpeech(context.applicationContext, this)

    /** Lo llama Android cuando el motor de voz terminó de iniciar. */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.US)
            ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            if (!ready) Log.w("Tutor", "Voz en inglés no disponible en este dispositivo")
        } else {
            Log.w("Tutor", "No se pudo iniciar TextToSpeech ($status)")
        }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = finish()
            override fun onStop(utteranceId: String?, interrupted: Boolean) = finish()
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finish()
        })
    }

    override suspend fun speak(text: String, settings: TutorSettings) {
        if (!ready || text.isBlank()) return
        tts.setSpeechRate(settings.ttsRate)
        suspendCancellableCoroutine { cont ->
            onDone = { if (cont.isActive) cont.resume(Unit) }
            cont.invokeOnCancellation { tts.stop() }
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reply-${System.nanoTime()}")
        }
    }

    override fun stop() {
        tts.stop()
        finish()
    }

    fun shutdown() = tts.shutdown()

    private fun finish() {
        onDone?.invoke()
        onDone = null
    }
}
