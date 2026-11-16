package com.englishtutor.app

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.englishtutor.app.audio.AudioPlayer
import com.englishtutor.app.audio.AudioRecorder
import com.englishtutor.app.audio.OrpheusVoice
import com.englishtutor.app.audio.SpanishVoice
import com.englishtutor.app.audio.Speaker
import com.englishtutor.app.data.SettingsRepository
import com.englishtutor.app.data.TutorRepository
import com.englishtutor.app.data.local.AppDatabase
import com.englishtutor.app.data.remote.GroqClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Inyección de dependencias manual: un solo lugar donde se crean los objetos. */
class EnglishTutorApp : Application() {
    val database by lazy { AppDatabase.create(this) }
    val repository by lazy {
        // OkHttp llama a esto en un hilo de fondo; DataStore guarda los ajustes en memoria, así que es rápido
        val api = GroqClient.create(apiKey = { runBlocking { settingsRepository.settings.first().effectiveApiKey() } })
        TutorRepository(api, database.tutorDao())
    }
    val settingsRepository by lazy { SettingsRepository(settingsDataStore) }
    val recorder by lazy { AudioRecorder(this) }
    val speaker by lazy { Speaker(this) }
    val spanishVoice by lazy { SpanishVoice(speaker) }
    val orpheusVoice by lazy { OrpheusVoice(repository, AudioPlayer(), cacheDir) }
}
