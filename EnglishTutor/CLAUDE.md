# Proyecto: English Tutor – Asistente de voz personal para practicar inglés

## Objetivo
App Android **personal** (solo para mí) para practicar inglés hablando con un tutor de IA **al menos 1 hora al día**.
El tutor debe conversar de forma **natural** por voz y, al mismo tiempo, **explicar mis errores en español** sin cortar la conversación.

- Usuario: hispanohablante (Chile), nivel intermedio.
- No es una app comercial: no se publica en Play Store, no necesita login ni backend propio.
- Prioridad: que funcione y sea práctica, no la perfección.

## Stack
| Pieza | Tecnología |
|---|---|
| Lenguaje / UI | Kotlin + Jetpack Compose (Material 3) |
| Arquitectura | MVVM simple: UI (Compose) → ViewModel → Repository → API / Room |
| HTTP | Retrofit + OkHttp + kotlinx.serialization |
| Voz → texto (STT) | Groq Whisper `whisper-large-v3-turbo` |
| LLM (tutor) | Groq `llama-3.3-70b-versatile` (alternativa: `openai/gpt-oss-120b`) |
| Texto → voz (TTS) | `android.speech.tts.TextToSpeech` (voz en inglés, nativa y gratis) |
| Grabación | `MediaRecorder` en modo push-to-talk (mantener presionado para hablar) |
| Persistencia | Room (SQLite) + DataStore para ajustes |
| Inyección de dependencias | Manual o Hilt (preferir lo más simple) |
| Min SDK | 26 |

## API de Groq
- Base URL: `https://api.groq.com/openai/v1` (compatible con OpenAI)
- Transcripción: `POST /audio/transcriptions` (multipart: `file`, `model=whisper-large-v3-turbo`, `language=en`)
- Chat: `POST /chat/completions` con `response_format: {"type": "json_object"}`
- Uso el **plan gratis** → hay límites por minuto. Manejar HTTP 429 con reintento y mostrar un mensaje claro, sin que la app se caiga.

### Seguridad de la API key
- La key va en `local.properties` como `GROQ_API_KEY=...` y se expone con `BuildConfig.GROQ_API_KEY`.
- `local.properties` **nunca** se sube a Git (verificar `.gitignore`).

## Flujo de una conversación
1. Mantengo presionado el botón de micrófono → se graba el audio (`.m4a`).
2. Al soltarlo → el audio se envía a Whisper → obtengo el texto de lo que dije.
3. Ese texto se agrega al historial y se envía al LLM junto con el system prompt.
4. El LLM responde en JSON (ver contrato abajo).
5. `reply` se lee en voz alta con TTS y se muestra como burbuja de chat.
6. `corrections` aparecen como **tarjetas** bajo mi mensaje (expandibles).
7. `new_vocab` se guarda en Room.

Estados de la UI: `Idle`, `Recording`, `Transcribing`, `Thinking`, `Speaking`, `Error`.

## Contrato JSON del tutor
```json
{
  "reply": "Respuesta natural en inglés que continúa la conversación (se lee en voz alta)",
  "corrections": [
    {
      "original": "lo que dije",
      "better": "cómo lo diría un nativo",
      "explanation": "explicación breve en español"
    }
  ],
  "new_vocab": [
    { "term": "expresión útil", "meaning": "significado en español", "example": "ejemplo en inglés" }
  ]
}
```
- Si no hay errores, `corrections` es `[]`.
- Si el JSON viene mal formado, usar el texto crudo como `reply` y no mostrar tarjetas (sin crashear).

## System prompt del tutor
```
You are my personal English tutor. My native language is Spanish (Chile).
Talk to me naturally, like a friendly native speaker, and keep the conversation going with questions.
Adapt to my level (intermediate). Use common, real-life expressions.
Correct only important mistakes (grammar, wrong words, unnatural phrasing), max 3 per turn.
Explanations must be short and in Spanish.
Keep "reply" short (1-3 sentences) so it feels like a real conversation.
Always answer ONLY with valid JSON in this format:
{"reply": "...", "corrections": [{"original": "...", "better": "...", "explanation": "..."}], "new_vocab": [{"term": "...", "meaning": "...", "example": "..."}]}
```
- Enviar solo los últimos ~20 mensajes como historial para no gastar tokens.

## Modelo de datos (Room)
- `Session(id, startedAt, endedAt, scenario)`
- `Message(id, sessionId, role[user|assistant], text, createdAt)`
- `Correction(id, messageId, original, better, explanation)`
- `Vocab(id, term, meaning, example, createdAt, timesReviewed)`

## Pantallas
1. **Conversación** (principal): chat + botón grande push-to-talk + indicador de estado + tarjetas de corrección.
2. **Historial**: lista de sesiones anteriores.
3. **Vocabulario / Errores**: palabras nuevas y errores más repetidos.
4. **Ajustes**: modelo LLM, velocidad del TTS, nivel, escenario por defecto.

## Fases
- **Fase 1 (MVP):** grabar → Whisper → LLM → TTS, con chat en pantalla. Sin Room todavía.
- **Fase 2:** tarjetas de corrección + guardar sesiones, correcciones y vocabulario en Room.
- **Fase 3:** escenarios de roleplay (entrevista de trabajo, restaurante, soporte técnico, charla libre) y un repaso de mis errores frecuentes al inicio de cada sesión.
- **Fase 4 (opcional):** voz más natural con Groq Orpheus TTS (es de pago) y contador del tiempo practicado por día.

## Cómo quiero que trabajes
- Avanzar **por fases y en pasos pequeños**; al terminar cada paso, decirme cómo probarlo en el emulador o en el celular.
- Explicarme brevemente en español qué hace cada parte nueva (estoy estudiando Ingeniería en Informática).
- Código limpio y simple, sin sobreingeniería.
- Pedir los permisos necesarios en tiempo de ejecución (`RECORD_AUDIO`) y declarar `INTERNET`.
- No inventar endpoints ni modelos: si hay dudas, revisar la documentación de Groq.
