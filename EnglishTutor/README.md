# English Tutor — Fase 1 (MVP)

Flujo: **mantener presionado 🎙️ → Whisper (Groq) → LLM (Groq) → TextToSpeech**, con el chat en pantalla.

## Estructura
```
app/src/main/java/com/englishtutor/app/
├── EnglishTutorApp.kt          # "DI manual": crea repository, recorder y speaker
├── MainActivity.kt             # Arranca Compose
├── data/
│   ├── TutorModels.kt          # TutorReply / Correction / Vocab (contrato JSON) + ChatMessage
│   ├── TutorRepository.kt      # transcribe(), chat(), system prompt, reintento 429, JSON tolerante
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
| 8. Correcciones | Decir algo con error ("Yesterday I go to the beach") | Logcat `LLM raw` trae `corrections` con explicación en español (las tarjetas se ven en Fase 2). |

### Emulador
- Micrófono: en el emulador, `⋮ → Microphone → "Virtual microphone uses host audio input"` (se desactiva cada vez que reinicias el emulador).
- Voz: si no se escucha, revisa *Ajustes → Sistema → Idioma → Salida de texto a voz* y descarga la voz en inglés (EE.UU.).

### Celular
Activa *Opciones de desarrollador → Depuración USB*, conéctalo y elígelo en Android Studio. Es la forma más fiable de probar el micrófono.

---

# Fase 2 — Room y correcciones

## Progreso
| Paso | Estado |
|---|---|
| Fase 1 compila (`gradlew assembleDebug`, 0 errores) | ✅ |
| 2.1 Guardar sesiones, mensajes, correcciones y vocabulario en Room | ✅ compila — falta probar en el celular |
| 2.2 Tarjetas de corrección expandibles | ⏳ siguiente |
| 2.3 Pantallas Historial y Vocabulario | ⏳ |
| 2.4 Ajustes (DataStore) | ⏳ |

## Paso 2.1: qué se agregó
- `data/local/Entities.kt`: las 4 tablas (`sessions`, `messages`, `corrections`, `vocab`). Las claves foráneas con `CASCADE` borran mensajes y correcciones si se borra su sesión.
- `data/local/TutorDao.kt`: las consultas. Room genera el SQL real al compilar (con KSP) y avisa si una consulta está mal escrita.
- `data/local/AppDatabase.kt`: crea el archivo `english_tutor.db`.
- `TutorRepository`: `startSession()`, `saveUserMessage()` y `saveTutorTurn()`.
- La sesión se crea con tu **primer mensaje** (abrir y cerrar la app no deja sesiones vacías). `endedAt` se actualiza en cada turno.

## Cómo probar el paso 2.1
1. Ejecuta la app en el celular y conversa 2 o 3 turnos (di algo con un error a propósito, por ejemplo "Yesterday I go to the beach").
2. En Android Studio: **View → Tool Windows → App Inspection → Database Inspector**, con la app abierta.
3. Abre `english_tutor.db` y revisa:
   - `sessions`: 1 fila.
   - `messages`: tus mensajes (`user`) y los del tutor (`assistant`).
   - `corrections`: la corrección, con `messageId` = id de tu mensaje.
   - `vocab`: palabras nuevas, si el tutor sugirió alguna.
