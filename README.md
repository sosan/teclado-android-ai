# VoiceType Keyboard - Teclado Android con Transcripción Offline

## 🎯 Descripción

Teclado Android (IME) que permite:
- 🎤 Grabar audio manteniendo pulsado el botón
- 🗣️ Transcribir offline usando Whisper.cpp
- 🧠 Mejorar el texto con LLM (Phi-3 Mini)
- ⌨️ Insertar texto limpio en cualquier app

**100% offline, sin streaming, sin nube.**

## 🏗️ Arquitectura

```
┌─────────────────────────────────────────────────────┐
│                  InputMethodService                  │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────┐ │
│  │   Audio     │  │  Whisper.cpp │  │  llama.cpp │ │
│  │   Recorder  │→ │   (ASR)      │→ │  (LLM)     │ │
│  └─────────────┘  └──────────────┘  └────────────┘ │
│         ↓                ↓                 ↓        │
│      WAV 16kHz      Transcripción     Texto limpio │
│                                            ↓       │
│                                    commitText()    │
└─────────────────────────────────────────────────────┘
```

## 📋 Requisitos

- Android Studio Hedgehog o superior
- NDK r25 o superior
- CMake 3.22+
- Dispositivo con Android 8.0+ (API 26+)

## 🚀 Características

### Soporte de Idiomas
- 🇪🇸 Español
- 🇨🇦 Catalán
- 🔍 Autodetección automática

### Modelos
- **ASR**: Whisper.cpp (modelo base)
- **LLM**: Phi-3 Mini (GGUF 4-bit)

### Rendimiento Esperado
- Whisper base: 0.5–2s
- Phi-3 Mini: 0.3–1s
- **Total**: 1–3 segundos por mensaje

## 📁 Estructura del Proyecto

```
app/
├── src/main/
│   ├── java/com/voicetype/keyboard/
│   │   ├── VoiceTypeIME.kt       # InputMethodService principal
│   │   ├── AudioRecorder.kt      # Grabación de audio
│   │   ├── WhisperEngine.kt      # Motor ASR
│   │   ├── LLMEngine.kt          # Motor LLM
│   │   ├── TextProcessor.kt      # Post-procesamiento
│   │   └── ui/                   # Componentes UI
│   ├── jni/
│   │   ├── CMakeLists.txt
│   │   ├── whisper_wrapper.cpp
│   │   └── llama_wrapper.cpp
│   ├── res/
│   │   ├── layout/
│   │   ├── values/
│   │   └── xml/
│   └── AndroidManifest.xml
├── build.gradle.kts
└── ...
```

## 🔧 Configuración

### 1. Clonar repositorios nativos

```bash
git submodule add https://github.com/ggerganov/whisper.cpp native/whisper
git submodule add https://github.com/ggerganov/llama.cpp native/llama
```

### 2. Descargar modelos

Colocar en `app/src/main/assets/models/`:
- `ggml-base.bin` (Whisper base)
- `phi-3-mini-4k-instruct.Q4_K_M.gguf` (Phi-3 Mini cuantizado)

### 3. Construir

```bash
./gradlew assembleDebug
```

## 🎤 Uso

1. Activar el teclado en Ajustes → Sistema → Idioma y entrada
2. Seleccionar "VoiceType Keyboard"
3. Mantener pulsado 🎤 para grabar
4. Soltar para procesar
5. El texto limpio se inserta automáticamente

## 📝 Prompt del LLM

```
Clean and rewrite the following speech transcription.
- Detect if it's Spanish or Catalan and keep the same language
- Remove filler words (eh, mm, vale, bueno...)
- Fix grammar and punctuation
- Keep the original meaning
- Make it natural and concise

Text:
"{transcription}"
```

## 🛡️ Privacidad

- ✅ Todo el procesamiento es local
- ✅ Sin conexión a internet requerida
- ✅ Sin envío de datos a servidores
- ✅ Sin permisos de red necesarios

## 📄 Licencia

MIT License
