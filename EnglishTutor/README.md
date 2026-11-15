# English Tutor

App Android personal para practicar inglés **hablando** con un tutor de IA (Groq), que corrige tus errores en español sin cortar la conversación. Ver `CLAUDE.md` para el diseño original.

## Estado

| Fase | Contenido | Estado |
|---|---|---|
| 1 | Grabar → Whisper → LLM → voz, chat en pantalla | ✅ compila + pruebas automáticas |
| 2 | Room (sesiones, mensajes, correcciones, vocabulario), tarjetas de corrección, Historial, Vocabulario/Errores, Ajustes | ✅ compila + pruebas automáticas |
| 3 | Escenarios de roleplay y repaso de errores frecuentes al inicio | ✅ compila + pruebas automáticas |
| 4 | Contador de minutos diarios con meta + voz Orpheus opcional (de pago) | ✅ compila + pruebas automáticas |

> ⚠️ Todo fue desarrollado y probado **sin celular** (51 pruebas automáticas en la JVM). Falta la prueba manual en un dispositivo real: ver la lista de abajo.

## Estructura

```
app/src/main/java/com/englishtutor/app/
├── EnglishTutorApp.kt            # DI manual: base de datos, repositorios, micrófono y voces
├── MainActivity.kt
├── audio/
│   ├── Voice.kt                  # Interfaces VoiceRecorder y TutorVoice (permiten fakes en tests)
│   ├── AudioRecorder.kt          # MediaRecorder → cache/turn.m4a
│   ├── Speaker.kt                # Voz gratis de Android (TextToSpeech), con velocidad
│   ├── OrpheusVoice.kt           # Voz Groq Orpheus (divide el texto en trozos de ≤200 caracteres)
│   └── AudioPlayer.kt            # Reproduce los WAV de Orpheus
├── data/
│   ├── TutorRepository.kt        # Groq (transcribir, chat, voz) + Room; reintento 429; errores en español
│   ├── TutorPrompt.kt            # System prompt: nivel + escenario + errores a repasar
│   ├── Scenario.kt               # Escenarios (charla libre, entrevista, restaurante, soporte) y niveles
│   ├── Settings.kt               # TutorSettings + SettingsRepository (DataStore)
│   ├── TextChunker.kt            # Divide texto largo en oraciones (para Orpheus)
│   ├── TutorModels.kt            # Contrato JSON del tutor + ChatMessage
│   ├── local/                    # Room: entidades, DAO, consultas (historial, errores, minutos)
│   └── remote/                   # Retrofit: GroqApi, GroqClient, DTOs
└── ui/
    ├── AppNavigation.kt          # Barra inferior: Conversar · Historial · Vocabulario · Ajustes
    ├── components/               # Burbujas, tarjetas de corrección, ScreenScaffold
    ├── conversation/             # Pantalla principal + ConversationViewModel
    ├── history/                  # Lista de sesiones y detalle (con borrar)
    ├── vocab/                    # Palabras nuevas y errores frecuentes
    ├── settings/                 # Ajustes
    └── theme/

app/src/test/java/com/englishtutor/app/   # Pruebas automáticas (ver abajo)
```

## Configuración

1. Abre la carpeta `EnglishTutor` en Android Studio y espera el *Gradle Sync*.
2. En `local.properties` agrega `GROQ_API_KEY=gsk_...` (ver `local.properties.example`; el archivo está en `.gitignore`).
3. *Build → Rebuild Project* y ejecuta en el celular.

> Si alguna vez instalaste la versión del paso 2.1 (commit `f9558a1`), **desinstala la app una vez** antes de instalar esta: la tabla `vocab` cambió y Room no puede abrir la base de datos antigua con la misma versión.

## Pruebas automáticas (sin celular)

```powershell
cd EnglishTutor
.\gradlew.bat testDebugUnitTest
```
En Android Studio: clic derecho en `app/src/test` → **Run 'Tests in…'**. Reporte HTML: `app/build/reports/tests/testDebugUnitTest/index.html`.

| Archivo | Qué prueba | Tests |
|---|---|---|
| `TutorRepositoryNetworkTest` | Peticiones reales contra un **servidor HTTP falso** (MockWebServer): formato del chat, `json_object`, API key, historial de 20 mensajes, mensaje de inicio, JSON mal formado, reintento 429, errores 401/5xx/sin red, multipart de Whisper, Orpheus WAV | 13 |
| `TutorRepositoryPersistenceTest` | Room en memoria: turno completo, vocabulario sin duplicados, repasadas, errores frecuentes agrupados, borrado en cascada, minutos practicados, orden del historial | 8 |
| `ConversationViewModelTest` | Flujo completo con micrófono/voces falsas: estados, correcciones, guardado, escenario, modelo y nivel de ajustes, grabación corta, transcripción vacía, error y recuperación, Orpheus y su respaldo, nueva sesión, repaso de errores, contador diario | 14 |
| `ComposeUiTest` | Pantallas dibujadas con Robolectric: tarjeta que se expande, inicio (escenarios, repaso, progreso), chat con correcciones y error, ajustes | 5 |
| `SettingsRepositoryTest` | DataStore: valores por defecto, guardar/leer, valores inválidos | 3 |
| `TutorPromptTest`, `TextChunkerTest` | System prompt y división de texto | 8 |

Las piezas falsas (API de Groq, micrófono, voces, base en memoria) están en `TestFixtures.kt`.

## Pruebas manuales en el celular

Filtra Logcat por el tag **`Tutor`** (verás `Whisper: "..."` y `LLM raw: {...}`).

**Conversación (Fases 1-3)**
- [ ] Sin key: banner "Falta GROQ_API_KEY". Con key: sin banner.
- [ ] Primer toque al micrófono → pide permiso.
- [ ] Elegir **Restaurante** → "Que el tutor empiece" → el tutor saluda como mesero y se escucha.
- [ ] Mantener presionado y decir *"Yesterday I go to the beach"* → tu burbuja + **tarjeta de corrección** (al tocarla muestra la explicación en español) + respuesta hablada.
- [ ] Presionar el micrófono mientras el tutor habla → se interrumpe y graba.
- [ ] Toque muy corto → "Mantén presionado…". Modo avión → "Sin conexión…" (la app no se cae).
- [ ] Botón ↻ (arriba) → nueva sesión con el selector de escenarios.
- [ ] Después de algunas sesiones con errores: al iniciar aparece **"📌 Hoy repasamos"** y el tutor te hace usar esas formas.

**Historial y Vocabulario (Fase 2)**
- [ ] Historial: sesiones con escenario, fecha, minutos y mensajes. Al abrir una se ven los mensajes con sus tarjetas. 🗑 borra (pide confirmación).
- [ ] Vocabulario → "Palabras": términos nuevos; ✓ suma "Repasada N veces"; 🗑 borra.
- [ ] Vocabulario → "Errores frecuentes": ordenados por cantidad de veces.
- [ ] Opcional: *App Inspection → Database Inspector* → `english_tutor.db`.

**Ajustes y Fase 4**
- [ ] Cambiar nivel, modelo (`openai/gpt-oss-120b`) y velocidad de voz → se nota en la siguiente respuesta.
- [ ] Escenario por defecto → se usa al abrir la app / nueva sesión.
- [ ] Barra **"Hoy: X / 60 min"** sube al conversar; la meta se cambia en Ajustes.
- [ ] Voz **Orpheus** (de pago): se escucha más natural. Si falla, aparece un aviso y habla la voz de Android. Si Groq pide aceptar términos del modelo, acéptalos en console.groq.com.

## Notas

- **Groq (verificado en la documentación, nov. 2026):** chat `llama-3.3-70b-versatile` / `openai/gpt-oss-120b`, Whisper `whisper-large-v3-turbo`, voz `canopylabs/orpheus-v1-english` (*preview*, USD 22 por millón de caracteres, solo WAV, máx. 200 caracteres por petición; voces: diana, hannah, autumn, austin, daniel, troy).
- **Minutos practicados** = suma de la duración de las sesiones de hoy (desde que empieza hasta tu último turno).
- **Errores frecuentes** se agrupan por frase original + corrección, sin importar mayúsculas.
