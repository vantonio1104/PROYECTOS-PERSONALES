package com.englishtutor.app

import android.app.Application
import com.englishtutor.app.audio.AudioRecorder
import com.englishtutor.app.audio.Speaker
import com.englishtutor.app.data.TutorRepository
import com.englishtutor.app.data.local.TutorDatabase
import com.englishtutor.app.data.remote.GroqClient

/** Inyección de dependencias manual: un solo lugar donde se crean los objetos. */
class EnglishTutorApp : Application() {
    val repository by lazy { TutorRepository(GroqClient.create(), TutorDatabase.create(this).dao()) }
    val recorder by lazy { AudioRecorder(this) }
    val speaker by lazy { Speaker(this) }
}
