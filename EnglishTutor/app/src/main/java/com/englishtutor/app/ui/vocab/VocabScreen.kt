package com.englishtutor.app.ui.vocab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishtutor.app.data.local.FrequentError
import com.englishtutor.app.data.local.VocabEntity
import com.englishtutor.app.ui.components.ScreenScaffold

@Composable
fun VocabScreen(vm: VocabViewModel) {
    val vocab by vm.vocab.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    ScreenScaffold(title = "Vocabulario y errores") { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Palabras (${vocab.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Errores frecuentes") })
            }
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (tab == 0) {
                    if (vocab.isEmpty()) item { EmptyText("Las palabras nuevas que te enseñe el tutor aparecerán aquí.") }
                    items(vocab, key = { it.id }) { VocabCard(it, { vm.markReviewed(it.id) }, { vm.delete(it.id) }) }
                } else {
                    if (errors.isEmpty()) item { EmptyText("Aquí verás tus errores, ordenados por cuántas veces los cometiste.") }
                    items(errors, key = { it.original + "→" + it.better }) { ErrorCard(it) }
                }
            }
        }
    }
}

@Composable
private fun EmptyText(text: String) = Text(text, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)

@Composable
private fun VocabCard(item: VocabEntity, onReviewed: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.term, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(item.meaning, style = MaterialTheme.typography.bodyMedium)
                if (item.example.isNotBlank()) {
                    Text("\"${item.example}\"", fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodySmall)
                }
                Text("Repasada ${item.timesReviewed} ${if (item.timesReviewed == 1) "vez" else "veces"}", style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onReviewed) { Icon(Icons.Filled.Check, contentDescription = "Marcar como repasada") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Borrar palabra") }
        }
    }
}

@Composable
private fun ErrorCard(error: FrequentError) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(buildAnnotatedString {
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(error.original) }
                append("  →  ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(error.better) }
            })
            Text(error.explanation, style = MaterialTheme.typography.bodyMedium)
            Text("${error.times} ${if (error.times == 1) "vez" else "veces"}", style = MaterialTheme.typography.labelSmall)
        }
    }
}
