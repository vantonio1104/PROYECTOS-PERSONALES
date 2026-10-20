package com.englishtutor.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.englishtutor.app.R
import com.englishtutor.app.data.ChatMessage
import com.englishtutor.app.data.Correction
import com.englishtutor.app.data.Role
import com.englishtutor.app.data.Scenario

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(vm: ConversationViewModel = viewModel(factory = ConversationViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Baja automáticamente al último mensaje
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.messages.isEmpty()) "English Tutor" else state.scenario.label) },
                actions = {
                    // Meta de CLAUDE.md: 1 hora al día
                    Text("Hoy ${state.todayMinutes}/60 min", style = MaterialTheme.typography.labelLarge)
                    if (state.messages.isNotEmpty()) TextButton(onClick = vm::newConversation) { Text("Nueva") }
                },
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.messages.isEmpty()) {
                    item {
                        SessionStart(state.scenario, state.review, onSelect = vm::selectScenario)
                    }
                }
                items(state.messages, key = { it.id }) { MessageBubble(it) }
            }

            state.error?.let { ErrorBanner(it, onDismiss = vm::dismissError) }

            StatusLabel(state.status)
            PushToTalkButton(
                status = state.status,
                onPress = vm::onPressMic,
                onRelease = vm::onReleaseMic,
            )
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val isUser = msg.role == Role.USER
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(msg.text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyLarge)
        }
        msg.corrections.forEach { CorrectionCard(it) }
    }
}

/** Tarjeta bajo tu mensaje: cerrada muestra cómo decirlo mejor; al tocarla, lo que dijiste y la explicación. */
@Composable
private fun CorrectionCard(c: Correction) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .padding(top = 4.dp)
            .widthIn(max = 300.dp)
            .clickable(onClickLabel = if (expanded) "Ocultar explicación" else "Ver explicación") {
                expanded = !expanded
            },
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text("✏️ ${c.better}", style = MaterialTheme.typography.bodyMedium)
            if (expanded) {
                Text(
                    c.original,
                    style = MaterialTheme.typography.bodySmall,
                    textDecoration = TextDecoration.LineThrough,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(c.explanation, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** Inicio de sesión: elegir escenario + repaso de errores frecuentes. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SessionStart(selected: Scenario, review: List<Correction>, onSelect: (Scenario) -> Unit) {
    Column(Modifier.padding(12.dp)) {
        Text("¿Qué quieres practicar?", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Scenario.entries.forEach { s ->
                FilterChip(selected = s == selected, onClick = { onSelect(s) }, label = { Text(s.label) })
            }
        }
        if (review.isNotEmpty()) {
            Text(
                "Repaso: tus errores frecuentes (toca para ver la explicación)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            review.forEach { CorrectionCard(it) }
        }
        Text(
            "Mantén presionado el micrófono y saluda a tu tutor en inglés 👋",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 16.dp),
        )
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
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onDismiss) { Text("OK") }
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

    Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(if (status == TutorStatus.Recording) 110.dp else 96.dp)
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
    Spacer(Modifier.height(8.dp))
}
