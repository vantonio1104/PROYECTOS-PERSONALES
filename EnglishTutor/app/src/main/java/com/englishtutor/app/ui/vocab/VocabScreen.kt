package com.englishtutor.app.ui.vocab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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

/** Acciones de la pantalla (agrupadas para no pasar 7 lambdas sueltas). */
interface VocabActions {
    fun addWord(term: String)
    fun clearAddState()
    fun markReviewed(id: Long)
    fun setMine(id: Long, mine: Boolean)
    fun delete(id: Long)
}

@Composable
fun VocabScreen(vm: VocabViewModel) {
    val myWords by vm.myWords.collectAsStateWithLifecycle()
    val tutorWords by vm.tutorWords.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()
    val addState by vm.addState.collectAsStateWithLifecycle()
    VocabContent(myWords, tutorWords, errors, addState, object : VocabActions {
        override fun addWord(term: String) = vm.addWord(term)
        override fun clearAddState() = vm.clearAddState()
        override fun markReviewed(id: Long) { vm.markReviewed(id) }
        override fun setMine(id: Long, mine: Boolean) { vm.setMine(id, mine) }
        override fun delete(id: Long) { vm.delete(id) }
    })
}

@Composable
fun VocabContent(
    myWords: List<VocabEntity>,
    tutorWords: List<VocabEntity>,
    errors: List<FrequentError>,
    addState: AddWordState,
    actions: VocabActions,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showAdd by rememberSaveable { mutableStateOf(false) }

    ScreenScaffold(
        title = "Vocabulario",
        actions = {
            IconButton(onClick = { actions.clearAddState(); showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Agregar palabra")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("⭐ Mis palabras (${myWords.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Del tutor (${tutorWords.size})") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Errores frecuentes") })
            }
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                when (tab) {
                    0 -> {
                        if (myWords.isEmpty()) item {
                            EmptyText(
                                "Aquí van las palabras que te cuestan.\n\n" +
                                    "• Mientras conversas, di \"guarda la palabra though\" (o \"save the word though\").\n" +
                                    "• O toca + (arriba) y escríbela.\n" +
                                    "• O marca con ☆ una palabra de la pestaña \"Del tutor\".\n\n" +
                                    "El tutor usará estas palabras en tus próximas conversaciones."
                            )
                        }
                        items(myWords, key = { it.id }) { WordCard(it, actions) }
                    }
                    1 -> {
                        if (tutorWords.isEmpty()) item { EmptyText("Las palabras nuevas que te enseñe el tutor aparecerán aquí.") }
                        items(tutorWords, key = { it.id }) { WordCard(it, actions) }
                    }
                    else -> {
                        if (errors.isEmpty()) item { EmptyText("Aquí verás tus errores, ordenados por cuántas veces los cometiste.") }
                        items(errors, key = { it.original + "→" + it.better }) { ErrorCard(it) }
                    }
                }
            }
        }
    }

    if (showAdd) AddWordDialog(addState, actions, onDismiss = { showAdd = false; actions.clearAddState() })
}

@Composable
private fun AddWordDialog(state: AddWordState, actions: VocabActions, onDismiss: () -> Unit) {
    var term by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar a Mis palabras") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = term,
                    onValueChange = { term = it },
                    label = { Text("Palabra o expresión en inglés") },
                    singleLine = true,
                    enabled = !state.loading,
                )
                when {
                    state.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.padding(end = 8.dp).size(20.dp), strokeWidth = 2.dp)
                        Text("Buscando el significado…")
                    }
                    state.error != null -> Text(state.error, color = MaterialTheme.colorScheme.error)
                    state.lastAdded != null -> Text("⭐ \"${state.lastAdded}\" guardada. Puedes agregar otra.")
                    else -> Text("El tutor buscará el significado en español y un ejemplo.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { actions.addWord(term); term = "" }, enabled = term.isNotBlank() && !state.loading) {
                Text("Guardar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
private fun EmptyText(text: String) = Text(text, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)

@Composable
private fun WordCard(item: VocabEntity, actions: VocabActions) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.term, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                if (item.meaning.isNotBlank()) Text(item.meaning, style = MaterialTheme.typography.bodyMedium)
                if (item.example.isNotBlank()) {
                    Text("\"${item.example}\"", fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodySmall)
                }
                Text("Repasada ${item.timesReviewed} ${if (item.timesReviewed == 1) "vez" else "veces"}", style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = { actions.setMine(item.id, !item.isMine) }) {
                Icon(
                    if (item.isMine) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = if (item.isMine) "Quitar de Mis palabras" else "Agregar a Mis palabras",
                    tint = if (item.isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { actions.markReviewed(item.id) }) { Icon(Icons.Filled.Check, contentDescription = "Marcar como repasada") }
            IconButton(onClick = { actions.delete(item.id) }) { Icon(Icons.Filled.Delete, contentDescription = "Borrar palabra") }
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
