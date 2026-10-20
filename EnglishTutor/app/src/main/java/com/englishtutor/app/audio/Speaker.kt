package com.englishtutor.app.audio

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

/** Envoltorio de TextToSpeech: speak() suspende hasta que termina de hablar. */
class Speaker(context: Context) : TextToSpeech.OnInitListener {

    @Volatile private var ready = false
    @Volatile private var onDone: (() -> Unit)? = null
    private val cacheDir = context.cacheDir
    private var player: MediaPlayer? = null // reproduce el audio de Orpheus

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

    /** Reproduce un .wav (voz Orpheus) y suspende hasta que termina. */
    suspend fun playWav(bytes: ByteArray) {
        val file = File(cacheDir, "reply.wav").apply { writeBytes(bytes) }
        val mp = MediaPlayer().apply { setDataSource(file.absolutePath); prepare() }
        player = mp
        try {
            suspendCancellableCoroutine { cont ->
                onDone = { if (cont.isActive) cont.resume(Unit) }
                mp.setOnCompletionListener { finish() }
                mp.setOnErrorListener { _, _, _ -> finish(); true }
                mp.start()
            }
        } finally {
            player = null
            mp.release()
        }
    }

    fun stop() {
        tts.stop()
        player?.stop()
        finish()
    }

    fun shutdown() = tts.shutdown()

    private fun finish() {
        onDone?.invoke()
        onDone = null
    }
}
