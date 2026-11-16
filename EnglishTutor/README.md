# English Tutor

App Android personal para practicar inglés **hablando** con un tutor de IA (Groq), que corrige tus errores en español sin cortar la conversación. Ver `CLAUDE.md` para el diseño original.

## Estado

| Fase | Contenido | Estado |
|---|---|---|
| 1 | Grabar → Whisper → LLM → voz, chat en pantalla | ✅ compila + pruebas automáticas |
| 2 | Room (sesiones, mensajes, correcciones, vocabulario), tarjetas de corrección, Historial, Vocabulario/Errores, Ajustes | ✅ compila + pruebas automáticas |
| 3 | Escenarios de roleplay y repaso de errores frecuentes al inicio | ✅ compila + pruebas automáticas |
| 4 | Contador de minutos diarios con meta + voz Orpheus opcional (de pago) | ✅ compila + pruebas automáticas |
| Extra | **⭐ Mis palabras**: di "guarda la palabra …" y se guarda con su significado; el tutor las practica contigo | ✅ compila + pruebas automáticas |

> ⚠️ Todo fue desarrollado y probado **sin celular** (71 pruebas automáticas en la JVM). Falta la prueba manual en un dispositivo real: ver la lista de abajo.

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

### Opción A: instalar el APK (sin PC)
1. Copia `EnglishTutor-1.0.apk` al celular y ábrelo. Android pedirá permitir **"Instalar apps desconocidas"** para la app con que lo abriste (Archivos, Drive, etc.).
2. Abre la app → **Ajustes → API key de Groq** → pega tu key (créala gratis en https://console.groq.com/keys) → **Guardar**.
3. Vuelve a **Conversar**: el aviso 🔑 desaparece y ya puedes hablar.

La key se guarda solo en el teléfono (DataStore privado de la app; `allowBackup` está desactivado).

### Opción B: compilar en Android Studio
1. Abre la carpeta `EnglishTutor` en Android Studio y espera el *Gradle Sync*.
2. Opcional: en `local.properties` agrega `GROQ_API_KEY=gsk_...` (ver `local.properties.example`; el archivo está en `.gitignore`). Si no, pégala en Ajustes como en la opción A. **La key de Ajustes tiene prioridad.**
3. *Build → Rebuild Project* y ejecuta en el celular.

### Firma y actualizaciones
El proyecto incluye `app/debug.keystore` (llave de debug estándar, contraseña `android`). Por eso el APK de la nube y el que compiles en tu PC tienen **la misma firma**: puedes instalar uno sobre otro sin desinstalar y **sin perder tus datos**. No es una llave secreta; sirve solo porque la app es personal y no va a Play Store.

> La base de datos va en la **versión 2** (columna `isMine` para Mis palabras). Desde la versión 1 de `c5f8cc7` se actualiza sola sin perder datos (migración probada en `MigrationTest`). Solo si instalaste el commit `f9558a1` (paso 2.1) tendrás que **desinstalar la app una vez**.

## ⭐ Mis palabras (palabras que te cuestan)

Hay 3 formas de guardar una palabra en la pestaña **Vocabulario → ⭐ Mis palabras**:

1. **Hablando**, en medio de la conversación: *"guarda la palabra though"* o *"save the word though"*. El tutor la reconoce (no la toma como error), la guarda con su **significado en español y un ejemplo**, te lo confirma en voz alta y sigue conversando. Aparece el aviso "⭐ Guardado en Mis palabras: though (aunque)".
2. **Escribiéndola**: botón **+** arriba → escribe la palabra → el tutor busca el significado y un ejemplo (corrige la ortografía si te equivocas).
3. **Con la estrella ☆** en una palabra de la pestaña "Del tutor".

Al iniciar cada sesión, el tutor recibe tus 5 palabras menos repasadas, las usa en la conversación y te invita a usarlas ("⭐ Palabras para usar" en la tarjeta de repaso). Con ✓ marcas una palabra como repasada; las más repasadas dejan lugar a las demás.

Cómo funciona: el contrato JSON del tutor tiene un campo extra `saved_words` (mismo formato que `new_vocab`). Se detecta con el LLM y no con una frase exacta, porque Whisper está configurado en inglés y puede transcribir "guarda la palabra" de forma imperfecta.

## Pruebas automáticas (sin celular)

```powershell
cd EnglishTutor
.\gradlew.bat testDebugUnitTest
```
En Android Studio: clic derecho en `app/src/test` → **Run 'Tests in…'**. Reporte HTML: `app/build/reports/tests/testDebugUnitTest/index.html`.

| Archivo | Qué prueba | Tests |
|---|---|---|
| `TutorRepositoryNetworkTest` | Peticiones reales contra un **servidor HTTP falso** (MockWebServer): formato del chat, `json_object`, API key (y que se use la vigente), historial de 20 mensajes, mensaje de inicio, JSON mal formado, reintento 429, errores 401/5xx/sin red, multipart de Whisper, Orpheus WAV, `saved_words`, buscar significado | 17 |
| `TutorRepositoryPersistenceTest` | Room en memoria: turno completo, vocabulario sin duplicados, repasadas, errores frecuentes agrupados, borrado en cascada, minutos practicados, orden del historial, Mis palabras | 12 |
| `ConversationViewModelTest` | Flujo completo con micrófono/voces falsas: estados, correcciones, guardado, escenario, modelo y nivel de ajustes, grabación corta, transcripción vacía, error y recuperación, Orpheus y su respaldo, nueva sesión, repaso de errores, contador diario, "guarda la palabra", palabras difíciles en el prompt, aviso de API key | 17 |
| `VocabViewModelTest` | Agregar palabra a mano (éxito y error), separar Mis palabras / Del tutor, estrella | 3 |
| `ComposeUiTest` | Pantallas dibujadas con Robolectric: tarjeta que se expande, inicio (escenarios, repaso, progreso), chat con correcciones y error, ajustes, Mis palabras | 6 |
| `MigrationTest` | Actualizar la base de datos v1 → v2 conserva los datos | 1 |
| `SettingsRepositoryTest` | DataStore: valores por defecto, guardar/leer, valores inválidos, prioridad de la API key | 4 |
| `TutorPromptTest`, `TextChunkerTest` | System prompt y división de texto | 11 |

> El diálogo "Agregar palabra" no tiene prueba de pantalla: en Robolectric, cualquier `AlertDialog` de Material 3 con un campo de texto se queda esperando para siempre (lo comprobé con un ejemplo mínimo, sin código de la app). Su lógica sí está probada en `VocabViewModelTest`; pruébalo a mano en el celular.

Las piezas falsas (API de Groq, micrófono, voces, base en memoria) están en `TestFixtures.kt`.

## Pruebas manuales en el celular

Filtra Logcat por el tag **`Tutor`** (verás `Whisper: "..."` y `LLM raw: {...}`).

**Conversación (Fases 1-3)**
- [ ] Sin key: aviso "🔑 Falta tu API key de Groq". Al guardarla en Ajustes el aviso desaparece.
- [ ] Key incorrecta → "API key inválida o vacía. Revísala en Ajustes."
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

**⭐ Mis palabras**
- [ ] Conversando, di *"save the word though"* (o *"guarda la palabra though"*) → el tutor confirma, sigue la charla, aparece el aviso ⭐ y la palabra está en Vocabulario → Mis palabras con su significado.
- [ ] Botón **+** → escribe *"thorough"* → aparece con significado y ejemplo. Prueba también una palabra mal escrita.
- [ ] En "Del tutor", toca ☆ → la palabra pasa a Mis palabras. Toca ⭐ → vuelve.
- [ ] Nueva sesión → la tarjeta de repaso muestra "⭐ Palabras para usar" y el tutor las usa.

**Ajustes y Fase 4**
- [ ] Cambiar nivel, modelo (`openai/gpt-oss-120b`) y velocidad de voz → se nota en la siguiente respuesta.
- [ ] Escenario por defecto → se usa al abrir la app / nueva sesión.
- [ ] Barra **"Hoy: X / 60 min"** sube al conversar; la meta se cambia en Ajustes.
- [ ] Voz **Orpheus** (de pago): se escucha más natural. Si falla, aparece un aviso y habla la voz de Android. Si Groq pide aceptar términos del modelo, acéptalos en console.groq.com.

## Notas

- **Groq (verificado en la documentación, nov. 2026):** chat `llama-3.3-70b-versatile` / `openai/gpt-oss-120b`, Whisper `whisper-large-v3-turbo`, voz `canopylabs/orpheus-v1-english` (*preview*, USD 22 por millón de caracteres, solo WAV, máx. 200 caracteres por petición; voces: diana, hannah, autumn, austin, daniel, troy).
- **Minutos practicados** = suma de la duración de las sesiones de hoy (desde que empieza hasta tu último turno).
- **Errores frecuentes** se agrupan por frase original + corrección, sin importar mayúsculas.
