package com.englishtutor.app.audio

import com.englishtutor.app.data.TutorSettings
import java.io.File

/** Algo que graba tu voz. Interfaz para poder usar un grabador falso en los tests. */
interface VoiceRecorder {
    fun start()
    /** Volumen máximo (0..32767) desde la última vez que se preguntó. Sirve para detectar silencio. */
    fun amplitude(): Int
    /** Devuelve el audio grabado, o null si fue demasiado corto o falló. */
    fun stop(): File?
}

/** Algo que lee en voz alta. speak() suspende hasta terminar de hablar. Cada voz usa los ajustes que le sirven. */
interface TutorVoice {
    suspend fun speak(text: String, settings: TutorSettings)
    fun stop()
}
