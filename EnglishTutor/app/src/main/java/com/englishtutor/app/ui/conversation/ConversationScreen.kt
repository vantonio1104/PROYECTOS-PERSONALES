package com.englishtutor.app.ui.conversation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishtutor.app.R
import com.englishtutor.app.data.Role as ChatRole
import com.englishtutor.app.data.Scenario
import com.englishtutor.app.data.local.FrequentError
import com.englishtutor.app.ui.components.MessageBubble
import com.englishtutor.app.ui.components.ScreenScaffold
import com.englishtutor.app.ui.components.UserMessageWithCorrections

@Composable
fun ConversationScreen(vm: ConversationViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    ConversationContent(
        state = state,
        onSelectScenario = vm::selectScenario,
        onStartConversation = vm::startConversation,
        onNewSession = vm::newSession,
        onPressMic = vm::onPressMic,
        onReleaseMic = vm::onReleaseMic,
        onDismissError = vm::dismissError,
        onDismissNotice = vm::dismissNotice,
    )
}

/** Pantalla sin ViewModel (recibe estado + acciones): así se puede probar y previsualizar. */
@Composable
fun ConversationContent(
    state: ConversationUiState,
    onSelectScenario: (Scenario) -> Unit,
    onStartConversation: () -> Unit,
    onNewSession: () -> Unit,
    onPressMic: () -> Unit,
    onReleaseMic: () -> Unit,
    onDismissError: () -> Unit,
    onDismissNotice: () -> Unit,
) {
    val listState = rememberLazyListState()
    // Baja automáticamente al último mensaje
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.corrections?.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    ScreenScaffold(
        title = "${state.scenario.emoji} ${state.scenario.title}",
        actions = {
            if (state.sessionStarted) {
                IconButton(onClick = onNewSession) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Nueva sesión")
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            DailyProgress(state.todayMinutes, state.goalMinutes)

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!state.sessionStarted && state.messages.isEmpty()) {
                    item {
                        StartPanel(
                            selected = state.scenario,
                            review = state.review,
                            myWords = state.myWords,
                            enabled = !state.busy,
                            onSelectScenario = onSelectScenario,
                            onStart = onStartConversation,
                        )
                    }
                } else if (state.review.isNotEmpty() || state.myWords.isNotEmpty()) {
                    item { ReviewCard(state.review, state.myWords) }
                }
                items(state.messages, key = { it.id }) { msg ->
                    if (msg.role == ChatRole.USER) UserMessageWithCorrections(msg.text, msg.corrections)
                    else MessageBubble(msg.text, isUser = false)
                }
            }

            if (state.apiKeyMissing) {
                Banner("🔑 Falta tu API key de Groq: agrégala en Ajustes.", MaterialTheme.colorScheme.errorContainer, onDismiss = null)
            }
            state.notice?.let { Banner(it, MaterialTheme.colorScheme.secondaryContainer, onDismissNotice) }
            state.error?.let { Banner(it, MaterialTheme.colorScheme.errorContainer, onDismissError) }

            StatusLabel(state.status)
            PushToTalkButton(status = state.status, onPress = onPressMic, onRelease = onReleaseMic)
        }
    }
}

/** Fase 4: minutos practicados hoy vs. la meta diaria. */
@Composable
private fun DailyProgress(minutes: Int, goal: Int) {
    val progress = if (goal > 0) (minutes.toFloat() / goal).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            if (minutes >= goal) "🎉 Hoy: $minutes / $goal min — ¡meta cumplida!" else "Hoy: $minutes / $goal min",
            style = MaterialTheme.typography.labelMedium,
        )
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
    }
}

/** Antes de empezar: elegir escenario, ver el repaso y dejar que el tutor abra. */
@Composable
private fun StartPanel(
    selected: Scenario,
    review: List<FrequentError>,
    myWords: List<String>,
    enabled: Boolean,
    onSelectScenario: (Scenario) -> Unit,
    onStart: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("¿Qué quieres practicar hoy?", style = MaterialTheme.typography.titleMedium)
        Scenario.entries.forEach { scenario ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .selectable(selected = scenario == selected, role = Role.RadioButton) { onSelectScenario(scenario) }
                    .padding(4.dp),
            ) {
                RadioButton(selected = scenario == selected, onClick = null)
                Text("${scenario.emoji}  ${scenario.title}", Modifier.padding(start = 8.dp))
            }
        }
        if (review.isNotEmpty() || myWords.isNotEmpty()) ReviewCard(review, myWords)
        Text(
            "💡 En cualquier momento di \"guarda la palabra …\" o \"save the word …\" y quedará en Mis palabras.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(onClick = onStart, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text("Que el tutor empiece")
        }
        Text(
            "…o mantén presionado el micrófono y habla tú primero.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

/** Fase 3: errores frecuentes y palabras difíciles que el tutor te hará practicar en esta sesión. */
@Composable
fun ReviewCard(review: List<FrequentError>, myWords: List<String> = emptyList()) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text("📌 Hoy repasamos", fontWeight = FontWeight.Bold)
            review.forEach {
                Text("• ${it.better}  (no \"${it.original}\")", style = MaterialTheme.typography.bodyMedium)
            }
            if (myWords.isNotEmpty()) {
                Text("⭐ Palabras para usar: ${myWords.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun StatusLabel(status: TutorStatus) {
    val text = when (status) {
        TutorStatus.Idle -> "Mantén presionado para hablar"
        TutorStatus.Recording -> "🎙️ Grabando… suelta para enviar"
        TutorStatus.Transcribing -> "Transcribiendo…"
        TutorStatus.Thinking -> "El tutor está pensando…"
        TutorStatus.Speaking -> "🔊 Hablando… (presiona para interrumpir)"
        TutorStatus.Error -> "Intenta de nuevo"
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (status == TutorStatus.Transcribing || status == TutorStatus.Thinking) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun Banner(message: String, color: Color, onDismiss: (() -> Unit)?) {
    Surface(
        color = color,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, Modifier.weight(1f).padding(vertical = 12.dp))
            if (onDismiss != null) TextButton(onClick = onDismiss) { Text("OK") }
        }
    }
}

/** Botón grande: mantener presionado = grabar, soltar = enviar. */
@Composable
private fun PushToTalkButton(status: TutorStatus, onPress: () -> Unit, onRelease: () -> Unit) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val busy = status == TutorStatus.Transcribing || status == TutorStatus.Thinking

    // rememberUpdatedState: pointerInput(Unit) no se reinicia, así siempre usa los valores actuales
    val currentOnPress by rememberUpdatedState(onPress)
    val currentOnRelease by rememberUpdatedState(onRelease)
    val currentBusy by rememberUpdatedState(busy)

    val color = when {
        status == TutorStatus.Recording -> MaterialTheme.colorScheme.error
        busy -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.primary
    }

    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(if (status == TutorStatus.Recording) 104.dp else 88.dp)
                .clip(CircleShape)
                .background(color)
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        if (currentBusy) return@detectTapGestures
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            return@detectTapGestures
                        }
                        currentOnPress()
                        tryAwaitRelease() // espera hasta que levantes el dedo
                        currentOnRelease()
                    })
                },
        ) {
            Icon(
                painterResource(R.drawable.ic_mic),
                contentDescription = "Mantén presionado para hablar",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}
