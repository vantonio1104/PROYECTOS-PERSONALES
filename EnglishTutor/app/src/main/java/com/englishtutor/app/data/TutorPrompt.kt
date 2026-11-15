package com.englishtutor.app.data

import com.englishtutor.app.data.local.FrequentError

/** Arma el system prompt del tutor (base de CLAUDE.md + nivel, escenario y repaso de errores). */
object TutorPrompt {

    /** Mensaje "invisible" que se envía cuando el tutor debe abrir la conversación. */
    const val KICKOFF = "(Start the conversation now with a short greeting or question that fits the scenario.)"

    fun build(
        level: Level = Level.INTERMEDIATE,
        scenario: Scenario = Scenario.FREE_TALK,
        review: List<FrequentError> = emptyList(),
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
        appendLine()
        appendLine("Always answer ONLY with valid JSON in this format:")
        append("""{"reply": "...", "corrections": [{"original": "...", "better": "...", "explanation": "..."}], "new_vocab": [{"term": "...", "meaning": "...", "example": "..."}]}""")
    }
}
