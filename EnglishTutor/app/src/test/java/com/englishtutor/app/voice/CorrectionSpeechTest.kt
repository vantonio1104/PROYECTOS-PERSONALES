package com.englishtutor.app.voice

import com.englishtutor.app.data.Correction
import org.junit.Assert.assertEquals
import org.junit.Test

class CorrectionSpeechTest {

    @Test
    fun `cada correccion se dice en espanol con la frase correcta en ingles`() {
        val segments = CorrectionSpeech.segments(listOf(
            Correction("I go", "I went", "Usa el pasado simple."),
            Correction("He don't", "He doesn't", ""),
        ))
        assertEquals(
            listOf(
                SpeechSegment("Corrección. Mejor di:", spanish = true),
                SpeechSegment("I went", spanish = false),
                SpeechSegment("Usa el pasado simple.", spanish = true),
                SpeechSegment("Otra corrección. Mejor di:", spanish = true),
                SpeechSegment("He doesn't", spanish = false),
            ),
            segments,
        )
    }

    @Test
    fun `sin correcciones no se dice nada`() {
        assertEquals(emptyList<SpeechSegment>(), CorrectionSpeech.segments(emptyList()))
    }
}
