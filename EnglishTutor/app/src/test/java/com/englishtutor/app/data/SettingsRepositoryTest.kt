package com.englishtutor.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** DataStore funciona en la JVM: se prueba con un archivo temporal. */
class SettingsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val testScope = TestScope(UnconfinedTestDispatcher())
    private val dataStore = PreferenceDataStoreFactory.create(scope = testScope.backgroundScope) {
        tmp.newFile("settings.preferences_pb")
    }
    private val repository = SettingsRepository(dataStore)

    @Test
    fun `sin datos guardados usa los valores por defecto`() = testScope.runTest {
        assertEquals(TutorSettings(), repository.settings.first())
        assertEquals("llama-3.3-70b-versatile", TutorSettings().llmModel)
        assertEquals(Level.INTERMEDIATE, TutorSettings().level)
        assertEquals(60, TutorSettings().dailyGoalMinutes)
        assertEquals(VoiceEngine.ANDROID, TutorSettings().voiceEngine)
    }

    @Test
    fun `update guarda y se puede leer de nuevo`() = testScope.runTest {
        repository.update {
            it.copy(
                llmModel = "openai/gpt-oss-120b", ttsRate = 0.8f, level = Level.ADVANCED,
                defaultScenario = Scenario.TECH_SUPPORT, dailyGoalMinutes = 90,
                voiceEngine = VoiceEngine.ORPHEUS, orpheusVoice = "troy",
            )
        }
        val s = repository.settings.first()
        assertEquals("openai/gpt-oss-120b", s.llmModel)
        assertEquals(0.8f, s.ttsRate)
        assertEquals(Level.ADVANCED, s.level)
        assertEquals(Scenario.TECH_SUPPORT, s.defaultScenario)
        assertEquals(90, s.dailyGoalMinutes)
        assertEquals(VoiceEngine.ORPHEUS, s.voiceEngine)
        assertEquals("troy", s.orpheusVoice)
    }

    @Test
    fun `valores invalidos guardados vuelven al valor por defecto`() = testScope.runTest {
        dataStore.edit {
            it[stringPreferencesKey("llm_model")] = "modelo-que-ya-no-existe"
            it[stringPreferencesKey("orpheus_voice")] = "nadie"
            it[stringPreferencesKey("level")] = "experto"
        }
        val s = repository.settings.first()
        assertEquals(TutorSettings().llmModel, s.llmModel)
        assertEquals(TutorSettings().orpheusVoice, s.orpheusVoice)
        assertEquals(Level.INTERMEDIATE, s.level)
    }
}
