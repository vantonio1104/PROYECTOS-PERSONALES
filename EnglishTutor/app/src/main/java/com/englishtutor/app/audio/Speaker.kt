package com.englishtutor.app.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/** Envoltorio de TextToSpeech: speak() suspende hasta que termina de hablar. */
class Speaker(context: Context) : TextToSpeech.OnInitListener {

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

    suspend fun speak(text: String) {
        if (!ready || text.isBlank()) return
        suspendCancellableCoroutine { cont ->
            onDone = { if (cont.isActive) cont.resume(Unit) }
            cont.invokeOnCancellation { tts.stop() }
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reply-${System.nanoTime()}")
        }
    }

    fun stop() {
        tts.stop()
        finish()
    }

    fun shutdown() = tts.shutdown()

    private fun finish() {
        onDone?.invoke()
        onDone = null
    }
}
