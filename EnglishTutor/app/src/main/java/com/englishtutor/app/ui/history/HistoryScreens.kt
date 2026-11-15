package com.englishtutor.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishtutor.app.data.Correction
import com.englishtutor.app.data.Scenario
import com.englishtutor.app.data.local.SessionSummary
import com.englishtutor.app.ui.components.MessageBubble
import com.englishtutor.app.ui.components.ScreenScaffold
import com.englishtutor.app.ui.components.UserMessageWithCorrections
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("EEE d MMM, HH:mm", Locale.forLanguageTag("es-CL"))

fun formatDate(millis: Long): String = dateFormat.format(Date(millis))

/** Duración en minutos (redondeada hacia arriba si hubo al menos un turno). */
fun durationMinutes(startedAt: Long, endedAt: Long?): Int =
    if (endedAt == null || endedAt <= startedAt) 0 else ((endedAt - startedAt + 59_999) / 60_000).toInt()

@Composable
fun HistoryScreen(vm: HistoryViewModel, onOpenSession: (Long) -> Unit) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    ScreenScaffold(title = "Historial") { padding ->
        val list = sessions
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), Alignment.Center) {
                Text("Aún no tienes sesiones. ¡Ve a Conversar y habla con tu tutor!")
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(list, key = { it.session.id }) { SessionCard(it) { onOpenSession(it.session.id) } }
            }
        }
    }
}

@Composable
private fun SessionCard(summary: SessionSummary, onClick: () -> Unit) {
    val s = summary.session
    val scenario = Scenario.fromId(s.scenario)
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("${scenario.emoji} ${scenario.title}", fontWeight = FontWeight.Bold)
            Text(formatDate(s.startedAt), style = MaterialTheme.typography.bodyMedium)
            Text(
                "${durationMinutes(s.startedAt, s.endedAt)} min · ${summary.messageCount} mensajes",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun SessionDetailScreen(vm: SessionDetailViewModel, onBack: () -> Unit) {
    val session by vm.session.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val title = session?.let { "${Scenario.fromId(it.scenario).emoji} ${formatDate(it.startedAt)}" } ?: "Sesión"

    ScreenScaffold(
        title = title,
        onBack = onBack,
        actions = {
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Borrar sesión") }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages, key = { it.message.id }) { m ->
                if (m.message.role == "user") {
                    UserMessageWithCorrections(
                        text = m.message.text,
                        corrections = m.corrections.map { Correction(it.original, it.better, it.explanation) },
                    )
                } else {
                    MessageBubble(m.message.text, isUser = false)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Borrar esta sesión?") },
            text = { Text("Se borran sus mensajes y correcciones. El vocabulario guardado se mantiene.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}
