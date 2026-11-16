package com.englishtutor.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceCommandsTest {

    @Test
    fun `reconoce comandos en ingles con puntuacion y mayusculas`() {
        assertEquals(VoiceCommand.REPEAT, VoiceCommands.parse("Repeat."))
        assertEquals(VoiceCommand.REPEAT, VoiceCommands.parse("Say that again?"))
        assertEquals(VoiceCommand.SLOWER, VoiceCommands.parse("Slow down, please!"))
        assertEquals(VoiceCommand.FASTER, VoiceCommands.parse("Faster"))
        assertEquals(VoiceCommand.NEW_SESSION, VoiceCommands.parse("Start over."))
        assertEquals(VoiceCommand.PAUSE, VoiceCommands.parse("Stop listening"))
        assertEquals(VoiceCommand.EXPLAIN, VoiceCommands.parse("Explain in Spanish."))
    }

    @Test
    fun `reconoce comandos en espanol con y sin tildes`() {
        assertEquals(VoiceCommand.REPEAT, VoiceCommands.parse("¡Repítelo!"))
        assertEquals(VoiceCommand.SLOWER, VoiceCommands.parse("Más lento, por favor"))
        assertEquals(VoiceCommand.FASTER, VoiceCommands.parse("mas rapido"))
        assertEquals(VoiceCommand.NEW_SESSION, VoiceCommands.parse("Nueva sesión"))
        assertEquals(VoiceCommand.PAUSE, VoiceCommands.parse("Pausa"))
        assertEquals(VoiceCommand.EXPLAIN, VoiceCommands.parse("Explícame en español"))
    }

    @Test
    fun `acepta prefijos como tutor u ok`() {
        assertEquals(VoiceCommand.REPEAT, VoiceCommands.parse("Hey tutor, repeat"))
        assertEquals(VoiceCommand.PAUSE, VoiceCommands.parse("OK, pause."))
    }

    @Test
    fun `las frases normales nunca son comandos`() {
        assertNull(VoiceCommands.parse("I want to repeat the course next year"))
        assertNull(VoiceCommands.parse("Can you speak slower when you explain grammar?"))
        assertNull(VoiceCommands.parse("The bus didn't stop at my station yesterday"))
        assertNull(VoiceCommands.parse("Save the word though"))
        assertNull(VoiceCommands.parse(""))
    }
}
