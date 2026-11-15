package com.englishtutor.app.data

/** Escenarios de roleplay (Fase 3). `id` es lo que se guarda en Room y en Ajustes. */
enum class Scenario(
    val id: String,
    val title: String,
    val emoji: String,
    /** Instrucción en inglés que se agrega al system prompt. */
    val roleInstructions: String,
) {
    FREE_TALK(
        "free_talk", "Charla libre", "💬",
        "We are having a casual chat. Ask me about my day, my interests and my plans.",
    ),
    JOB_INTERVIEW(
        "job_interview", "Entrevista de trabajo", "💼",
        "Roleplay: you are a friendly interviewer at a tech company and I am a candidate for a software developer position. " +
            "Ask typical interview questions one at a time (about me, experience, strengths, a project, teamwork, salary expectations).",
    ),
    RESTAURANT(
        "restaurant", "Restaurante", "🍽️",
        "Roleplay: you are a waiter at a restaurant in the United States and I am a customer. " +
            "Greet me, offer the menu, take my order, suggest dishes and drinks, and later bring the check.",
    ),
    TECH_SUPPORT(
        "tech_support", "Soporte técnico", "🛠️",
        "Roleplay: I work in tech support and you are a customer whose laptop or internet connection has a problem. " +
            "Describe the problem little by little and react to my questions and instructions, so I practice explaining technical steps.",
    );

    companion object {
        fun fromId(id: String?): Scenario = entries.firstOrNull { it.id == id } ?: FREE_TALK
    }
}

/** Nivel de inglés que el tutor debe asumir. */
enum class Level(val id: String, val label: String, val promptName: String) {
    BASIC("basic", "Básico", "beginner (A2)"),
    INTERMEDIATE("intermediate", "Intermedio", "intermediate (B1-B2)"),
    ADVANCED("advanced", "Avanzado", "advanced (C1)");

    companion object {
        fun fromId(id: String?): Level = entries.firstOrNull { it.id == id } ?: INTERMEDIATE
    }
}
