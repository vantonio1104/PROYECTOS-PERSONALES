package com.englishtutor.app.data

import androidx.datastore.core.DataStore
import com.englishtutor.app.BuildConfig
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class VoiceEngine(val id: String, val label: String) {
    ANDROID("android", "Android (gratis)"),
    ORPHEUS("orpheus", "Groq Orpheus (de pago)");

    companion object {
        fun fromId(id: String?): VoiceEngine = entries.firstOrNull { it.id == id } ?: ANDROID
    }
}

/** Todos los ajustes de la app, con sus valores por defecto. */
data class TutorSettings(
    val llmModel: String = LLM_MODELS.first(),
    val ttsRate: Float = 1.0f,
    val level: Level = Level.INTERMEDIATE,
    val defaultScenario: Scenario = Scenario.FREE_TALK,
    val dailyGoalMinutes: Int = 60,
    val voiceEngine: VoiceEngine = VoiceEngine.ANDROID,
    val orpheusVoice: String = ORPHEUS_VOICES.first(),
    /** Key de Groq ingresada en la app. Vacía = se usa GROQ_API_KEY de local.properties. */
    val apiKey: String = "",
) {
    /** La key que realmente se usa: la de Ajustes o, si está vacía, la de local.properties. */
    fun effectiveApiKey(buildKey: String = BuildConfig.GROQ_API_KEY): String = apiKey.ifBlank { buildKey }

    // Para no mostrar la key si alguna vez se imprime el objeto en Logcat
    override fun toString() = "TutorSettings(llmModel=$llmModel, level=$level, scenario=$defaultScenario, voice=$voiceEngine, apiKey=${if (apiKey.isBlank()) "-" else "***"})"

    companion object {
        /** Modelos de chat de Groq verificados en console.groq.com/docs/models. */
        val LLM_MODELS = listOf("llama-3.3-70b-versatile", "openai/gpt-oss-120b")

        /** Voces en inglés de canopylabs/orpheus-v1-english. */
        val ORPHEUS_VOICES = listOf("diana", "hannah", "autumn", "austin", "daniel", "troy")
    }
}

/** Guarda los ajustes en DataStore (un archivo de preferencias, persistente y asíncrono). */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<TutorSettings> = dataStore.data.map { it.toSettings() }

    suspend fun update(transform: (TutorSettings) -> TutorSettings) {
        dataStore.edit { p ->
            val new = transform(p.toSettings())
            p[LLM_MODEL] = new.llmModel
            p[TTS_RATE] = new.ttsRate
            p[LEVEL] = new.level.id
            p[SCENARIO] = new.defaultScenario.id
            p[DAILY_GOAL] = new.dailyGoalMinutes
            p[VOICE_ENGINE] = new.voiceEngine.id
            p[ORPHEUS_VOICE] = new.orpheusVoice
            p[API_KEY] = new.apiKey.trim()
        }
    }

    /** Lee las preferencias; si falta un valor (o es inválido) usa el de por defecto. */
    private fun Preferences.toSettings(): TutorSettings {
        val defaults = TutorSettings()
        return TutorSettings(
            llmModel = this[LLM_MODEL]?.takeIf { it in TutorSettings.LLM_MODELS } ?: defaults.llmModel,
            ttsRate = this[TTS_RATE] ?: defaults.ttsRate,
            level = Level.fromId(this[LEVEL]),
            defaultScenario = Scenario.fromId(this[SCENARIO]),
            dailyGoalMinutes = this[DAILY_GOAL] ?: defaults.dailyGoalMinutes,
            voiceEngine = VoiceEngine.fromId(this[VOICE_ENGINE]),
            orpheusVoice = this[ORPHEUS_VOICE]?.takeIf { it in TutorSettings.ORPHEUS_VOICES } ?: defaults.orpheusVoice,
            apiKey = this[API_KEY] ?: "",
        )
    }

    private companion object {
        val LLM_MODEL = stringPreferencesKey("llm_model")
        val TTS_RATE = floatPreferencesKey("tts_rate")
        val LEVEL = stringPreferencesKey("level")
        val SCENARIO = stringPreferencesKey("default_scenario")
        val DAILY_GOAL = intPreferencesKey("daily_goal_minutes")
        val VOICE_ENGINE = stringPreferencesKey("voice_engine")
        val ORPHEUS_VOICE = stringPreferencesKey("orpheus_voice")
        val API_KEY = stringPreferencesKey("groq_api_key")
    }
}
