package com.englishtutor.app.voice

/**
 * Decide, a partir del volumen del micrófono, cuándo empezaste a hablar y cuándo terminaste (modo manos libres).
 * Se le entrega una muestra de volumen cada ~100 ms; `t` son los milisegundos desde que empezó a escuchar.
 * Es lógica pura (sin Android) para poder probarla fácilmente.
 */
class SilenceDetector(
    private val threshold: Int,          // volumen (0..32767) desde el que se considera voz
    private val silenceMs: Long,         // silencio después de hablar para dar el turno por terminado
    private val maxWaitMs: Long = 30_000,   // sin hablar este tiempo: se reinicia la grabación (archivo liviano)
    private val maxSpeechMs: Long = 45_000, // turno más largo permitido
    private val minSpeechMs: Long = 300,    // ruidos más cortos (tos, golpe) se ignoran
) {
    enum class Event { WAITING, SPEECH_STARTED, SPEAKING, NOISE_IGNORED, DONE, NO_SPEECH }

    private var speechStart: Long? = null
    private var lastLoud = 0L

    fun onSample(amplitude: Int, t: Long): Event {
        val loud = amplitude >= threshold
        val start = speechStart
        if (start == null) {
            if (loud) {
                speechStart = t
                lastLoud = t
                return Event.SPEECH_STARTED
            }
            return if (t >= maxWaitMs) Event.NO_SPEECH else Event.WAITING
        }
        if (loud) lastLoud = t
        if (t - start >= maxSpeechMs) return Event.DONE
        if (t - lastLoud >= silenceMs) {
            if (lastLoud - start >= minSpeechMs) return Event.DONE
            speechStart = null // fue solo un ruido corto: seguimos esperando
            return Event.NOISE_IGNORED
        }
        return Event.SPEAKING
    }
}
