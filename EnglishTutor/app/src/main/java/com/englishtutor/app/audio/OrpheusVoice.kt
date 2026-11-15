package com.englishtutor.app.audio

import com.englishtutor.app.data.TextChunker
import com.englishtutor.app.data.TutorRepository
import com.englishtutor.app.data.TutorSettings
import java.io.File

/**
 * Voz más natural con Groq Orpheus (de pago, Fase 4).
 * Orpheus acepta 200 caracteres por petición: dividimos el texto y reproducimos cada parte.
 * Si falla, lanza la excepción para que el ViewModel use la voz de Android.
 */
class OrpheusVoice(
    private val repository: TutorRepository,
    private val player: AudioPlayer,
    private val cacheDir: File,
) : TutorVoice {

    override suspend fun speak(text: String, settings: TutorSettings) {
        // La velocidad (ttsRate) no aplica: Orpheus no permite cambiarla
        TextChunker.chunk(text).forEachIndexed { i, part ->
            val wav = repository.synthesize(part, settings.orpheusVoice, File(cacheDir, "orpheus_$i.wav"))
            player.play(wav)
        }
    }

    override fun stop() = player.stop()
}
