package com.englishtutor.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextChunkerTest {

    @Test
    fun `texto corto queda en un solo trozo`() {
        assertEquals(listOf("Hi! How are you?"), TextChunker.chunk("Hi! How are you?"))
    }

    @Test
    fun `texto vacio no genera trozos`() {
        assertEquals(emptyList<String>(), TextChunker.chunk("   "))
    }

    @Test
    fun `texto largo se divide en oraciones sin pasar 200 caracteres`() {
        val sentence = "This is a sentence that has around sixty characters in it." // 58
        val text = List(6) { sentence }.joinToString(" ")
        val chunks = TextChunker.chunk(text)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 200 })
        assertTrue(chunks.all { it.endsWith(".") }) // corta al final de una oración
        assertEquals(text, chunks.joinToString(" ")) // no se pierde nada
    }

    @Test
    fun `una oracion enorme se corta en los espacios`() {
        val text = List(80) { "word" }.joinToString(" ") // 399 caracteres, sin puntos
        val chunks = TextChunker.chunk(text)
        assertTrue(chunks.all { it.length <= 200 })
        assertEquals(text, chunks.joinToString(" "))
    }
}
