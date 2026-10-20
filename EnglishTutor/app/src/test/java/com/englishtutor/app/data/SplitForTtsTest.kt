package com.englishtutor.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitForTtsTest {
    @Test
    fun shortTextIsOneChunk() {
        assertEquals(listOf("Hi! How are you?"), TutorRepository.splitForTts("Hi! How are you?"))
    }

    @Test
    fun longTextSplitsAtSentencesWithinLimit() {
        val text = "First sentence here. Second sentence is longer. Third one."
        val chunks = TutorRepository.splitForTts(text, max = 30)
        assertEquals(listOf("First sentence here.", "Second sentence is longer.", "Third one."), chunks)
    }

    @Test
    fun hugeSentenceIsCutAtSpacesAndKeepsOrder() {
        val text = "Ok. " + "word ".repeat(100).trim() + ". End."
        val chunks = TutorRepository.splitForTts(text, max = 50)
        assertTrue(chunks.all { it.length <= 50 })
        assertEquals(text.replace(" ", ""), chunks.joinToString("").replace(" ", ""))
        assertEquals("Ok.", chunks.first())
    }
}
