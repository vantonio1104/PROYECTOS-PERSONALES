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

/** Escenarios de práctica: label para la UI (español), prompt para el tutor (inglés). */
enum class Scenario(val label: String, val prompt: String) {
    FREE("Charla libre", "Just have a casual, friendly chat about my day, hobbies or anything I bring up."),
    INTERVIEW(
        "Entrevista de trabajo",
        "Roleplay: you are an interviewer at a tech company and I am a candidate for a junior software developer job. Ask one interview question at a time.",
    ),
    RESTAURANT(
        "Restaurante",
        "Roleplay: you are a waiter at a restaurant and I am a customer. Greet me, take my order and answer questions about the menu.",
    ),
    SUPPORT(
        "Soporte técnico",
        "Roleplay: you are an IT support agent and I am a customer with a computer or internet problem. Help me step by step.",
    ),
}

/** Un mensaje del chat en pantalla (en memoria; lo que se guarda en Room está en data/local). */
data class ChatMessage(
    val id: Long,
    val role: Role,
    val text: String,
    val corrections: List<Correction> = emptyList(),
)
