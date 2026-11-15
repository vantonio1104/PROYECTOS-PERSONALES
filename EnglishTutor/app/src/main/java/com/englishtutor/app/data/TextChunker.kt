package com.englishtutor.app.data

/**
 * Orpheus acepta máximo 200 caracteres por petición: dividimos la respuesta
 * en trozos que terminan en una oración (o en un espacio si una oración es muy larga).
 */
object TextChunker {
    fun chunk(text: String, max: Int = 200): List<String> {
        val sentences = text.trim().split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
        val chunks = mutableListOf<String>()
        var current = ""
        for (sentence in sentences.flatMap { splitLong(it, max) }) {
            current = when {
                current.isEmpty() -> sentence
                current.length + 1 + sentence.length <= max -> "$current $sentence"
                else -> { chunks += current; sentence }
            }
        }
        if (current.isNotEmpty()) chunks += current
        return chunks
    }

    /** Corta una oración más larga que `max` en los espacios. */
    private fun splitLong(sentence: String, max: Int): List<String> {
        if (sentence.length <= max) return listOf(sentence)
        val parts = mutableListOf<String>()
        var rest = sentence
        while (rest.length > max) {
            val cut = rest.lastIndexOf(' ', max).takeIf { it > 0 } ?: max
            parts += rest.substring(0, cut).trim()
            rest = rest.substring(cut).trim()
        }
        if (rest.isNotEmpty()) parts += rest
        return parts
    }
}
