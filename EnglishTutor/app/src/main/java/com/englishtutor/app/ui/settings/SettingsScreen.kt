package com.englishtutor.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishtutor.app.BuildConfig
import com.englishtutor.app.data.Level
import com.englishtutor.app.data.Scenario
import com.englishtutor.app.data.TutorSettings
import com.englishtutor.app.data.VoiceEngine
import com.englishtutor.app.ui.components.ScreenScaffold
import java.util.Locale

@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    SettingsContent(settings, vm::update)
}

@Composable
fun SettingsContent(settings: TutorSettings, update: ((TutorSettings) -> TutorSettings) -> Unit) {
    ScreenScaffold(title = "Ajustes") { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Section("Modelo del tutor (Groq)")
            TutorSettings.LLM_MODELS.forEach { model ->
                Option(model, settings.llmModel == model) { update { it.copy(llmModel = model) } }
            }

            Section("Tu nivel")
            Level.entries.forEach { level ->
                Option(level.label, settings.level == level) { update { it.copy(level = level) } }
            }

            Section("Escenario por defecto")
            Scenario.entries.forEach { scenario ->
                Option("${scenario.emoji} ${scenario.title}", settings.defaultScenario == scenario) {
                    update { it.copy(defaultScenario = scenario) }
                }
            }

            Section("Meta diaria: ${settings.dailyGoalMinutes} min")
            Slider(
                value = settings.dailyGoalMinutes.toFloat(),
                onValueChange = { v -> update { it.copy(dailyGoalMinutes = v.toInt()) } },
                valueRange = 15f..120f,
                steps = 6, // 15, 30, 45, 60, 75, 90, 105, 120
            )

            Section("Voz del tutor")
            VoiceEngine.entries.forEach { engine ->
                Option(engine.label, settings.voiceEngine == engine) { update { it.copy(voiceEngine = engine) } }
            }
            if (settings.voiceEngine == VoiceEngine.ANDROID) {
                Text("Velocidad: ${String.format(Locale.US, "%.1f", settings.ttsRate)}x", Modifier.padding(top = 8.dp))
                Slider(
                    value = settings.ttsRate,
                    onValueChange = { v -> update { it.copy(ttsRate = (v * 10).toInt() / 10f) } },
                    valueRange = 0.5f..1.5f,
                    steps = 9,
                )
            } else {
                Text(
                    "Orpheus es una voz más natural, pero de pago: USD 22 por millón de caracteres " +
                        "(aprox. USD 0,20 por hora de práctica). Está en \"preview\" en Groq. " +
                        "Si falla, la app usa automáticamente la voz de Android.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                TutorSettings.ORPHEUS_VOICES.forEach { voice ->
                    Option(voice.replaceFirstChar { it.uppercase() }, settings.orpheusVoice == voice) {
                        update { it.copy(orpheusVoice = voice) }
                    }
                }
            }

            Section("API key")
            Text(
                if (BuildConfig.GROQ_API_KEY.isBlank()) "❌ Falta GROQ_API_KEY en local.properties"
                else "✅ Configurada (termina en …${BuildConfig.GROQ_API_KEY.takeLast(4)})",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = 16.dp, bottom = 8.dp))
    Text(title, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun Option(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.padding(start = 8.dp, top = 12.dp, bottom = 12.dp))
    }
}
