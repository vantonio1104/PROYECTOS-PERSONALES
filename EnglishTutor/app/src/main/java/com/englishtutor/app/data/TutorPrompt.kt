package com.englishtutor.app.data

import com.englishtutor.app.data.local.FrequentError

/** Arma el system prompt del tutor (base de CLAUDE.md + nivel, escenario, repaso y "Mis palabras"). */
object TutorPrompt {

    /** Mensaje "invisible" que se envía cuando el tutor debe abrir la conversación. */
    const val KICKOFF = "(Start the conversation now with a short greeting or question that fits the scenario.)"

    fun build(
        level: Level = Level.INTERMEDIATE,
        scenario: Scenario = Scenario.FREE_TALK,
        review: List<FrequentError> = emptyList(),
        myWords: List<String> = emptyList(),
    ): String = buildString {
        appendLine("You are my personal English tutor. My native language is Spanish (Chile).")
        appendLine("Talk to me naturally, like a friendly native speaker, and keep the conversation going with questions.")
        appendLine("Adapt to my level (${level.promptName}). Use common, real-life expressions.")
        appendLine("Correct only important mistakes (grammar, wrong words, unnatural phrasing), max 3 per turn.")
        appendLine("Explanations must be short and in Spanish.")
        appendLine("Keep \"reply\" short (1-3 sentences) so it feels like a real conversation.")
        appendLine()
        appendLine("Scenario: ${scenario.roleInstructions} Stay in this role during the whole conversation.")
        if (review.isNotEmpty()) {
            appendLine()
            appendLine("These are mistakes I make often:")
            review.forEach { appendLine("- I said \"${it.original}\" instead of \"${it.better}\"") }
            appendLine("During the first turns, naturally create chances for me to use the correct forms. Do not tell me you are doing this.")
        }
        if (myWords.isNotEmpty()) {
            appendLine()
            appendLine("Words I find difficult: ${myWords.joinToString(", ")}.")
            appendLine("Use some of them naturally in the conversation and invite me to use them too.")
        }
        appendLine()
        appendLine(
            "If I ask you to save a word or expression (for example \"save the word X\" or \"guarda la palabra X\", in English or Spanish), " +
                "put it in \"saved_words\" with its meaning in Spanish and a short example in English, confirm it briefly in \"reply\" " +
                "and then continue the conversation. That request is not a mistake: do not correct it."
        )
        appendLine()
        appendLine("Always answer ONLY with valid JSON in this format:")
        append("""{"reply": "...", "corrections": [{"original": "...", "better": "...", "explanation": "..."}], "new_vocab": [{"term": "...", "meaning": "...", "example": "..."}], "saved_words": [{"term": "...", "meaning": "...", "example": "..."}]}""")
    }

    /** Prompt para buscar el significado de una palabra que agregas a mano en "Mis palabras". */
    fun define(term: String): String = buildString {
        appendLine("You are a dictionary for a Spanish speaker (Chile) learning English.")
        appendLine("Explain the English word or expression \"$term\". If it is misspelled, use the correct spelling in \"term\".")
        appendLine("Answer ONLY with valid JSON in this format:")
        append("""{"term": "...", "meaning": "significado breve en español", "example": "a short example sentence in English"}""")
    }
}
