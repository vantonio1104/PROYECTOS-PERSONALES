package com.englishtutor.app.voice

import java.text.Normalizer

/** Comandos que puedes decir en vez de conversar. Se detectan antes de enviar el texto al tutor. */
enum class VoiceCommand(val label: String) {
    REPEAT("repetir"),
    SLOWER("más lento"),
    FASTER("más rápido"),
    NEW_SESSION("nueva sesión"),
    PAUSE("pausa"),
    EXPLAIN("explicar en español"),
}

object VoiceCommands {

    /** Frases exactas (sin tildes ni signos). Whisper está en inglés, así que también puede "traducir" el comando. */
    private val phrases: Map<String, VoiceCommand> = buildMap {
        fun add(cmd: VoiceCommand, vararg list: String) = list.forEach { put(it, cmd) }
        add(VoiceCommand.REPEAT, "repeat", "repeat that", "repeat it", "say that again", "say it again", "again",
            "one more time", "pardon", "come again", "what did you say", "repite", "repitelo", "repite eso", "otra vez")
        add(VoiceCommand.SLOWER, "slower", "slow down", "speak slower", "speak more slowly", "more slowly", "slowly",
            "mas lento", "habla mas lento", "mas despacio", "habla mas despacio")
        add(VoiceCommand.FASTER, "faster", "speak faster", "speed up", "mas rapido", "habla mas rapido")
        add(VoiceCommand.NEW_SESSION, "new session", "start over", "new conversation", "start a new session",
            "nueva sesion", "nueva conversacion", "empezar de nuevo")
        add(VoiceCommand.PAUSE, "pause", "stop", "stop listening", "pausa", "pausar", "para", "detente")
        add(VoiceCommand.EXPLAIN, "explain", "explain that", "explain it", "explain in spanish", "explain my mistakes",
            "in spanish", "explicame", "explicame en espanol", "explica", "en espanol", "explicalo")
    }

    private val prefixes = listOf("hey tutor ", "ok tutor ", "okay tutor ", "tutor ", "please ", "por favor ", "ok ", "okay ")
    private val suffixes = listOf(" please", " por favor")

    /** Devuelve el comando si TODO lo que dijiste es un comando (así una frase normal nunca se confunde). */
    fun parse(text: String): VoiceCommand? {
        var s = normalize(text)
        if (s.isEmpty() || s.count { it == ' ' } > 5) return null
        prefixes.firstOrNull { s.startsWith(it) }?.let { s = s.removePrefix(it) }
        suffixes.firstOrNull { s.endsWith(it) }?.let { s = s.removeSuffix(it) }
        return phrases[s.trim()]
    }

    /** minúsculas, sin tildes, sin signos de puntuación y con un solo espacio entre palabras. */
    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .replace(Regex("[^a-z0-9ñ ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
