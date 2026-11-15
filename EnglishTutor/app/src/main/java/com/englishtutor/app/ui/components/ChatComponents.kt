package com.englishtutor.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.englishtutor.app.data.Correction

/** Burbuja de chat: tuya a la derecha, del tutor a la izquierda. */
@Composable
fun MessageBubble(text: String, isUser: Boolean) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** Mensaje del usuario + sus tarjetas de corrección debajo. */
@Composable
fun UserMessageWithCorrections(text: String, corrections: List<Correction>, initiallyExpanded: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MessageBubble(text, isUser = true)
        corrections.forEach { CorrectionCard(it, initiallyExpanded) }
    }
}

/**
 * Tarjeta de corrección (Fase 2): cerrada muestra "original → mejor";
 * al tocarla se expande con la explicación en español.
 */
@Composable
fun CorrectionCard(correction: Correction, initiallyExpanded: Boolean = false) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Card(
            onClick = { expanded = !expanded },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            modifier = Modifier.widthIn(max = 300.dp).animateContentSize(),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = buildAnnotatedString {
                            append("✏️ ")
                            withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(correction.original) }
                            append("  →  ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(correction.better) }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(
                        if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) "Ocultar explicación" else "Ver explicación",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                AnimatedVisibility(expanded) {
                    Text(
                        correction.explanation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
