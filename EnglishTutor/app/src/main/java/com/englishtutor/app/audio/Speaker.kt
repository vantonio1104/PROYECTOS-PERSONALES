package com.englishtutor.app.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.englishtutor.app.data.TutorSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/** Una voz en inglés instalada en el celular, para elegirla en Ajustes. */
data class VoiceOption(val name: String, val label: String)

/**
 * Voz gratis de Android (TextToSpeech). speak() suspende hasta que termina de hablar.
 * Habla en inglés con la voz elegida en Ajustes, y en español para explicar las correcciones.
 */
class Speaker(context: Context) : TextToSpeech.OnInitListener, TutorVoice {

    @Volatile private var ready = false
    @Volatile private var onDone: (() -> Unit)? = null
    private var spanishLocale: Locale? = null

    private val _voices = MutableStateFlow<List<VoiceOption>>(emptyList())
    /** Voces en inglés instaladas (se llena cuando el motor de voz termina de iniciar). */
    val voices: StateFlow<List<VoiceOption>> = _voices.asStateFlow()

    private val tts = TextToSpeech(context.applicationContext, this)

    /** Lo llama Android cuando el motor de voz terminó de iniciar. */
    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.w("Tutor", "No se pudo iniciar TextToSpeech ($status)")
            return
        }
        ready = isAvailable(tts.setLanguage(Locale.US))
        if (!ready) Log.w("Tutor", "Voz en inglés no disponible en este dispositivo")
        // Español de Chile si existe; si no, otro español
        spanishLocale = listOf(Locale.forLanguageTag("es-CL"), Locale.forLanguageTag("es-US"), Locale.forLanguageTag("es-ES"))
            .firstOrNull { isAvailable(tts.isLanguageAvailable(it)) }
        _voices.value = englishVoices()
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
        val chosen = settings.androidVoiceName.takeIf { it.isNotBlank() }?.let { name -> allVoices().firstOrNull { it.name == name } }
        if (chosen != null) tts.voice = chosen else tts.language = Locale.US
        speakNow(text, settings.ttsRate)
    }

    /** Habla en español (para las explicaciones). Si el celular no tiene voz en español, no dice nada. */
    suspend fun speakSpanish(text: String, rate: Float) {
        val locale = spanishLocale ?: return
        if (text.isBlank()) return
        tts.language = locale
        speakNow(text, rate)
    }

    override fun stop() {
        tts.stop()
        finish()
    }

    fun shutdown() = tts.shutdown()

    private suspend fun speakNow(text: String, rate: Float) {
        tts.setSpeechRate(rate)
        suspendCancellableCoroutine { cont ->
            onDone = { if (cont.isActive) cont.resume(Unit) }
            cont.invokeOnCancellation { tts.stop() }
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utt-${System.nanoTime()}")
        }
    }

    private fun finish() {
        onDone?.invoke()
        onDone = null
    }

    private fun isAvailable(result: Int) =
        result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED

    // Algunos motores de voz lanzan excepciones o devuelven null aquí
    private fun allVoices(): List<Voice> = runCatching { tts.voices?.toList() }.getOrNull().orEmpty()

    /** Voces en inglés instaladas, agrupadas por país y numeradas: "Estados Unidos · voz 2 (sin internet)". */
    private fun englishVoices(): List<VoiceOption> {
        val spanish = Locale.forLanguageTag("es")
        val countryOrder = listOf("US", "GB", "AU", "CA", "IE", "IN")
        return allVoices()
            .filter { it.locale.language == "en" && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty() }
            .sortedWith(compareBy({ countryOrder.indexOf(it.locale.country).let { i -> if (i < 0) 99 else i } }, { it.name }))
            .groupBy { it.locale.country }
            .flatMap { (_, group) ->
                group.mapIndexed { i, v ->
                    val country = v.locale.getDisplayCountry(spanish).ifBlank { v.locale.toLanguageTag() }
                    val net = if (v.isNetworkConnectionRequired) " (requiere internet)" else ""
                    VoiceOption(v.name, "$country · voz ${i + 1}$net")
                }
            }
    }
}

/** Adaptador: la voz en español del Speaker como TutorVoice (para usarla igual que las otras en el ViewModel). */
class SpanishVoice(private val speaker: Speaker) : TutorVoice {
    override suspend fun speak(text: String, settings: TutorSettings) = speaker.speakSpanish(text, settings.ttsRate)
    override fun stop() = speaker.stop()
}
