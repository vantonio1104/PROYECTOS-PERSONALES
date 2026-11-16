package com.englishtutor.app.data

import com.englishtutor.app.data.local.FrequentError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorPromptTest {

    @Test
    fun `incluye nivel, escenario y el contrato JSON`() {
        val prompt = TutorPrompt.build(Level.ADVANCED, Scenario.RESTAURANT)
        assertTrue(prompt.contains("advanced (C1)"))
        assertTrue(prompt.contains("you are a waiter"))
        assertTrue(prompt.contains("max 3 per turn"))
        assertTrue(prompt.contains("in Spanish"))
        assertTrue(prompt.trimEnd().endsWith("""[{"term": "...", "meaning": "...", "example": "..."}]}"""))
    }

    @Test
    fun `por defecto es nivel intermedio y charla libre, sin repaso`() {
        val prompt = TutorPrompt.build()
        assertTrue(prompt.contains("intermediate (B1-B2)"))
        assertTrue(prompt.contains(Scenario.FREE_TALK.roleInstructions))
        assertFalse(prompt.contains("mistakes I make often"))
    }

    @Test
    fun `agrega los errores frecuentes a repasar`() {
        val review = listOf(FrequentError("I go yesterday", "I went yesterday", "pasado", 3))
        val prompt = TutorPrompt.build(review = review)
        assertTrue(prompt.contains("mistakes I make often"))
        assertTrue(prompt.contains("I said \"I go yesterday\" instead of \"I went yesterday\""))
        assertTrue(prompt.contains("Do not tell me"))
    }

    @Test
    fun `explica como guardar palabras y pide saved_words en el JSON`() {
        val prompt = TutorPrompt.build()
        assertTrue(prompt.contains("guarda la palabra X"))
        assertTrue(prompt.contains(""""saved_words": [{"term": "...", "meaning": "...", "example": "..."}]"""))
        assertFalse(prompt.contains("Words I find difficult"))
    }

    @Test
    fun `incluye mis palabras dificiles para practicarlas`() {
        val prompt = TutorPrompt.build(myWords = listOf("though", "thorough"))
        assertTrue(prompt.contains("Words I find difficult: though, thorough."))
    }

    @Test
    fun `el prompt de diccionario pide significado en espanol`() {
        val prompt = TutorPrompt.define("though")
        assertTrue(prompt.contains("\"though\""))
        assertTrue(prompt.contains("español"))
    }

    @Test
    fun `ids desconocidos vuelven al valor por defecto`() {
        assertEquals(Scenario.FREE_TALK, Scenario.fromId("no-existe"))
        assertEquals(Scenario.JOB_INTERVIEW, Scenario.fromId("job_interview"))
        assertEquals(Level.INTERMEDIATE, Level.fromId(null))
        assertEquals(VoiceEngine.ANDROID, VoiceEngine.fromId("x"))
    }
}
