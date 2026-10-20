# English Tutor — Fase 2

Fase 2: tarjetas de corrección bajo tus mensajes + todo se guarda en Room (`tutor.db`).

Flujo: **mantener presionado 🎙️ → Whisper (Groq) → LLM (Groq) → TextToSpeech**, con el chat en pantalla.

## Estructura
```
app/src/main/java/com/englishtutor/app/
├── EnglishTutorApp.kt          # "DI manual": crea repository, recorder y speaker
├── MainActivity.kt             # Arranca Compose
├── data/
│   ├── TutorModels.kt          # TutorReply / Correction / Vocab (contrato JSON) + ChatMessage
│   ├── TutorRepository.kt      # transcribe(), chat(), saveTurn(), system prompt, reintento 429, JSON tolerante
│   ├── local/TutorDatabase.kt  # Room: tablas sessions/messages/corrections/vocab + DAO
│   └── remote/                 # Retrofit: GroqApi, GroqClient (auth + logging), DTOs
├── audio/
│   ├── AudioRecorder.kt        # MediaRecorder → cache/turn.m4a (AAC 16 kHz mono)
│   └── Speaker.kt              # TextToSpeech en inglés; speak() espera a que termine
└── ui/
    ├── ConversationViewModel.kt # Estados Idle/Recording/Transcribing/Thinking/Speaking/Error
    ├── ConversationScreen.kt    # Chat + botón push-to-talk + permiso RECORD_AUDIO
    └── theme/Theme.kt
```

## Configuración
1. Abre la carpeta en Android Studio (deja que haga *Gradle Sync*).
2. Crea tu key en https://console.groq.com/keys
3. En `local.properties` (raíz del proyecto) agrega: `GROQ_API_KEY=gsk_...`
   (ver `local.properties.example`). Este archivo está en `.gitignore`.
4. *Build → Rebuild Project* (para que se regenere `BuildConfig.GROQ_API_KEY`).

## Cómo probar cada parte
Filtra Logcat por el tag **`Tutor`** y por **`okhttp`**.

| Paso | Qué probar | Qué deberías ver |
|---|---|---|
| 1. Esqueleto | Ejecutar la app (▶) | Pantalla "English Tutor" + botón azul. Si falta la key: banner rojo "Falta GROQ_API_KEY". |
| 2. Permiso | Presionar el botón la 1ª vez | Diálogo de permiso de micrófono. Acéptalo. |
| 3. Grabación | Mantener presionado | Botón rojo y más grande, "Grabando…". Un toque rápido (<0,5 s) muestra "Mantén presionado…". |
| 4. Whisper | Decir "Hello, how are you?" y soltar | "Transcribiendo…", luego tu burbuja. Logcat: `Whisper: "Hello, how are you?"` y `POST …/audio/transcriptions 200`. |
| 5. LLM | (sigue solo) | "El tutor está pensando…", luego burbuja del tutor. Logcat: `LLM raw: {"reply": …}`. |
| 6. TTS | (sigue solo) | Se escucha la respuesta en inglés, "Hablando…". Presionar el botón mientras habla lo interrumpe. |
| 7. Errores | Modo avión y hablar | Banner "Sin conexión…" sin que la app se caiga. Key falsa → "API key inválida". |
| 8. Correcciones | Decir algo con error ("Yesterday I go to the beach") | Bajo tu burbuja aparece una tarjeta "✏️ Yesterday I went to the beach". Tócala: se ve lo que dijiste (tachado) y la explicación en español. |
| 9. Room | Hablar 2-3 turnos y abrir *View → Tool Windows → App Inspection → Database Inspector* | Base `tutor.db` con filas en `sessions`, `messages`, `corrections` y `vocab`. Ciérrala desde recientes y ábrela: se crea una sesión nueva y los datos anteriores siguen ahí. |

### Emulador
- Micrófono: en el emulador, `⋮ → Microphone → "Virtual microphone uses host audio input"` (se desactiva cada vez que reinicias el emulador).
- Voz: si no se escucha, revisa *Ajustes → Sistema → Idioma → Salida de texto a voz* y descarga la voz en inglés (EE.UU.).

### Celular
Activa *Opciones de desarrollador → Depuración USB*, conéctalo y elígelo en Android Studio. Es la forma más fiable de probar el micrófono.
