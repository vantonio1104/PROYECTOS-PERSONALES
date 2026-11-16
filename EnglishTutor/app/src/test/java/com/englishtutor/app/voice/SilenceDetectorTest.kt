package com.englishtutor.app.voice

import com.englishtutor.app.voice.SilenceDetector.Event
import org.junit.Assert.assertEquals
import org.junit.Test

class SilenceDetectorTest {

    private fun detector() = SilenceDetector(threshold = 1000, silenceMs = 1000, maxWaitMs = 5000, maxSpeechMs = 3000, minSpeechMs = 300)

    /** Alimenta una muestra cada 100 ms y devuelve el último evento. */
    private fun SilenceDetector.feed(amplitudes: List<Int>, startT: Long = 100): List<Event> =
        amplitudes.mapIndexed { i, a -> onSample(a, startT + i * 100L) }

    @Test
    fun `espera mientras hay silencio`() {
        assertEquals(List(5) { Event.WAITING }, detector().feed(List(5) { 50 }))
    }

    @Test
    fun `detecta que empiezas a hablar y que terminas tras el silencio`() {
        val events = detector().feed(listOf(50, 2000, 2500, 1800, 2200, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100))
        assertEquals(Event.SPEECH_STARTED, events[1])
        assertEquals(Event.SPEAKING, events[2])
        assertEquals(Event.DONE, events.last { it != Event.SPEAKING })
        assertEquals(14, events.indexOf(Event.DONE)) // 1 s después de la última muestra fuerte (índice 4)
    }

    @Test
    fun `una pausa corta al hablar no corta el turno`() {
        val events = detector().feed(listOf(2000, 2000, 2000, 100, 100, 100, 2000, 2000))
        assertEquals(false, events.contains(Event.DONE))
    }

    @Test
    fun `un ruido corto se ignora y sigue esperando`() {
        val d = detector()
        val events = d.feed(listOf(3000) + List(10) { 50 })
        assertEquals(Event.NOISE_IGNORED, events[10])
        assertEquals(Event.WAITING, d.onSample(50, 1200))
    }

    @Test
    fun `si nadie habla avisa para reiniciar la grabacion`() {
        assertEquals(Event.NO_SPEECH, detector().onSample(0, 5000))
    }

    @Test
    fun `un turno muy largo se corta en el maximo`() {
        val d = detector()
        d.onSample(2000, 0)
        assertEquals(Event.DONE, d.onSample(2000, 3000))
    }
}
