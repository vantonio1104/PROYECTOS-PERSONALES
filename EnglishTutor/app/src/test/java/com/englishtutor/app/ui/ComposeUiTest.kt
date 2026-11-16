package com.englishtutor.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.englishtutor.app.data.ChatMessage
import com.englishtutor.app.data.Correction
import com.englishtutor.app.data.Level
import com.englishtutor.app.data.Role
import com.englishtutor.app.data.Scenario
import com.englishtutor.app.data.TutorSettings
import com.englishtutor.app.data.VoiceEngine
import com.englishtutor.app.data.local.FrequentError
import com.englishtutor.app.ui.components.CorrectionCard
import com.englishtutor.app.ui.conversation.ConversationContent
import com.englishtutor.app.ui.conversation.ConversationUiState
import com.englishtutor.app.ui.conversation.TutorStatus
import com.englishtutor.app.ui.settings.SettingsContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Pruebas de pantalla: se dibujan los composables en Robolectric y se "toca" la UI. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp") // pantalla de un celular común
class ComposeUiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun conversation(
        state: ConversationUiState,
        onSelectScenario: (Scenario) -> Unit = {},
        onStart: () -> Unit = {},
        onNewSession: () -> Unit = {},
        onSetHandsFree: (Boolean) -> Unit = {},
    ) = compose.setContent {
        ConversationContent(state, onSelectScenario, onStart, onNewSession, {}, {}, {}, {}, onSetHandsFree)
    }

    @Test
    fun tarjeta_de_correccion_se_expande_al_tocarla() {
        compose.setContent { CorrectionCard(Correction("I go", "I went", "Usa el pasado simple.")) }
        compose.onNodeWithText("I went", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Usa el pasado simple.").assertDoesNotExist()

        compose.onNodeWithContentDescription("Ver explicación").performClick()
        compose.onNodeWithText("Usa el pasado simple.").assertIsDisplayed()

        compose.onNodeWithContentDescription("Ocultar explicación").performClick()
        compose.onNodeWithText("Usa el pasado simple.").assertDoesNotExist()
    }

    @Test
    fun inicio_muestra_escenarios_repaso_y_progreso() {
        var selected: Scenario? = null
        var started = false
        conversation(
            ConversationUiState(
                review = listOf(FrequentError("I go yesterday", "I went yesterday", "pasado", 2)),
                todayMinutes = 12, goalMinutes = 60,
            ),
            onSelectScenario = { selected = it },
            onStart = { started = true },
        )
        compose.onNodeWithText("Hoy: 12 / 60 min").assertIsDisplayed()
        compose.onNodeWithText("📌 Hoy repasamos").assertIsDisplayed()
        compose.onNodeWithText("I went yesterday", substring = true).assertIsDisplayed()

        compose.onNodeWithText("🍽️  Restaurante").performClick()
        assertEquals(Scenario.RESTAURANT, selected)

        compose.onNodeWithText("Que el tutor empiece").performClick()
        assertEquals(true, started)
    }

    @Test
    fun meta_cumplida_se_celebra() {
        conversation(ConversationUiState(todayMinutes = 75, goalMinutes = 60))
        compose.onNodeWithText("meta cumplida", substring = true).assertIsDisplayed()
    }

    @Test
    fun chat_muestra_mensajes_correcciones_estado_y_errores() {
        var newSession = false
        conversation(
            ConversationUiState(
                sessionStarted = true,
                scenario = Scenario.JOB_INTERVIEW,
                status = TutorStatus.Error,
                error = "API key inválida.",
                messages = listOf(
                    ChatMessage(0, Role.USER, "I have 5 years working", listOf(Correction("I have 5 years working", "I've been working for 5 years", "Present perfect continuous"))),
                    ChatMessage(1, Role.ASSISTANT, "Great, tell me about a project."),
                ),
            ),
            onNewSession = { newSession = true },
        )
        compose.onNodeWithText("💼 Entrevista de trabajo").assertIsDisplayed()
        compose.onNodeWithText("Great, tell me about a project.").assertIsDisplayed()
        compose.onNodeWithText("I've been working for 5 years", substring = true).assertIsDisplayed()
        compose.onNodeWithText("API key inválida.").assertIsDisplayed()
        compose.onNodeWithText("Que el tutor empiece").assertDoesNotExist()

        compose.onNodeWithContentDescription("Nueva sesión").performClick()
        assertEquals(true, newSession)
    }

    @Test
    fun manos_libres_se_activa_y_muestra_que_escucha() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        var handsFree: Boolean? = null
        conversation(ConversationUiState(), onSetHandsFree = { handsFree = it })
        compose.onNodeWithText("🎧 Manos libres").performClick()
        assertEquals(true, handsFree)
    }

    @Test
    fun manos_libres_activo_muestra_estado_y_boton_de_pausa() {
        var handsFree: Boolean? = null
        conversation(
            ConversationUiState(handsFree = true, status = TutorStatus.Listening, sessionStarted = true),
            onSetHandsFree = { handsFree = it },
        )
        compose.onNodeWithText("👂 Te escucho… habla cuando quieras").assertIsDisplayed()
        compose.onNodeWithContentDescription("Pausar manos libres").performClick()
        assertEquals(false, handsFree)
    }

    @Test
    fun ajustes_de_voz_correcciones_y_prueba() {
        var settings = TutorSettings()
        var previews = 0
        compose.setContent {
            SettingsContent(settings, { transform -> settings = transform(settings) }, emptyList(), { previews++ })
        }
        compose.onNodeWithText("▶ Probar voz").performScrollTo().performClick()
        assertEquals(1, previews)

        compose.onNodeWithText("Leer en voz alta", substring = true).performScrollTo().performClick()
        assertEquals(false, settings.speakCorrections)

        compose.onNodeWithText("Alta (hablas bajito)").performScrollTo().performClick()
        assertEquals(com.englishtutor.app.data.MicSensitivity.HIGH, settings.micSensitivity)
    }

    @Test
    fun ajustes_cambian_al_tocar_una_opcion() {
        var settings = TutorSettings()
        compose.setContent { SettingsContent(settings, update = { transform -> settings = transform(settings) }) }

        compose.onNodeWithText("Avanzado").performClick()
        assertEquals(Level.ADVANCED, settings.level)

        compose.onNodeWithText("openai/gpt-oss-120b").performClick()
        assertEquals("openai/gpt-oss-120b", settings.llmModel)

        compose.onNodeWithText(VoiceEngine.ORPHEUS.label).performScrollTo().performClick()
        assertEquals(VoiceEngine.ORPHEUS, settings.voiceEngine)
    }

    @Test
    fun mis_palabras_muestra_la_lista_y_la_estrella_funciona() {
        var starred: Pair<Long, Boolean>? = null
        val actions = object : com.englishtutor.app.ui.vocab.VocabActions {
            override fun addWord(term: String) {}
            override fun clearAddState() {}
            override fun markReviewed(id: Long) {}
            override fun setMine(id: Long, mine: Boolean) { starred = id to mine }
            override fun delete(id: Long) {}
        }
        val word = com.englishtutor.app.data.local.VocabEntity(1, "though", "aunque", "Nice, though.", 0, isMine = true)
        compose.setContent {
            com.englishtutor.app.ui.vocab.VocabContent(listOf(word), emptyList(), emptyList(), com.englishtutor.app.ui.vocab.AddWordState(), actions)
        }
        compose.onNodeWithText("⭐ Mis palabras (1)").assertIsDisplayed()
        compose.onNodeWithText("aunque").assertIsDisplayed()

        compose.onNodeWithContentDescription("Quitar de Mis palabras").performClick()
        assertEquals(1L to false, starred)
        compose.onNodeWithContentDescription("Agregar palabra").assertIsDisplayed()
        // No abrimos el diálogo: en Robolectric un AlertDialog con campo de texto nunca queda "en reposo"
        // (limitación del entorno de pruebas). Agregar palabras se prueba en VocabViewModelTest.
    }
}
