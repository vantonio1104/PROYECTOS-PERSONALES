package com.englishtutor.app.voice

import com.englishtutor.app.data.Correction

/** Un trozo de audio: en español (explicaciones) o en inglés (la frase correcta, para oír la pronunciación). */
data class SpeechSegment(val text: String, val spanish: Boolean)

/** Arma lo que se dice en voz alta para cada corrección: "Corrección:" + frase correcta en inglés + explicación. */
object CorrectionSpeech {
    fun segments(corrections: List<Correction>): List<SpeechSegment> = buildList {
        corrections.forEachIndexed { i, c ->
            add(SpeechSegment(if (i == 0) "Corrección. Mejor di:" else "Otra corrección. Mejor di:", spanish = true))
            add(SpeechSegment(c.better, spanish = false))
            if (c.explanation.isNotBlank()) add(SpeechSegment(c.explanation, spanish = true))
        }
    }

    const val NO_CORRECTIONS = "En tu último mensaje no hubo errores. ¡Bien hecho!"
}
