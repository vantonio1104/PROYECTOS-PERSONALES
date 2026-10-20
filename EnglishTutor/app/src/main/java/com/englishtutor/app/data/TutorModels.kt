package com.englishtutor.app.data

import kotlinx.serialization.Serializable

/** Respuesta del tutor según el contrato JSON de CLAUDE.md. */
@Serializable
data class TutorReply(
    val reply: String,
    val corrections: List<Correction> = emptyList(),
    val new_vocab: List<Vocab> = emptyList(),
)

@Serializable
data class Correction(val original: String, val better: String, val explanation: String)

@Serializable
data class Vocab(val term: String, val meaning: String, val example: String = "")

enum class Role { USER, ASSISTANT }

/** Un mensaje del chat en pantalla (en memoria; lo que se guarda en Room está en data/local). */
data class ChatMessage(
    val id: Long,
    val role: Role,
    val text: String,
    val corrections: List<Correction> = emptyList(),
)
