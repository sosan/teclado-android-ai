# 📦 Guía de Instalación y Configuración

## Requisitos Previos

1. **Android Studio** Hedgehog (2023.1.1) o superior
2. **NDK** r25 o superior
3. **CMake** 3.22+
4. **Git** para clonar submódulos

## Paso 1: Clonar el Proyecto

```bash
cd /workspace
git clone <tu-repositorio> voicetype-keyboard
cd voicetype-keyboard
```

## Paso 2: Inicializar Submódulos Nativos

```bash
# Whisper.cpp
git submodule add https://github.com/ggerganov/whisper.cpp native/whisper

# Llama.cpp  
git submodule add https://github.com/ggerganov/llama.cpp native/llama
```

## Paso 3: Descargar Modelos

### Whisper (ASR)
```bash
# Modelo base (~142MB)
wget -O app/src/main/assets/models/ggml-base.bin \
  https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin
```

### Phi-3 Mini (LLM)
```bash
# Modelo cuantizado 4-bit (~2.3GB)
wget -O app/src/main/assets/models/phi-3-mini-4k-instruct.Q4_K_M.gguf \
  https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf
```

> ⚠️ **Nota**: El modelo Phi-3 es grande. Para dispositivos con poca memoria, considera usar modelos más pequeños como Gemma 2B.

## Paso 4: Configurar Android Studio

1. Abrir el proyecto en Android Studio
2. Esperar a que Gradle sincronice
3. Verificar que NDK esté instalado (Tools → SDK Manager → SDK Tools)

## Paso 5: Construir

```bash
./gradlew assembleDebug
```

El APK se generará en: `app/build/outputs/apk/debug/app-debug.apk`

## Paso 6: Instalar y Activar

### Instalación
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Activación en el dispositivo

1. **Ajustes** → **Sistema** → **Idioma y entrada**
2. **Teclado en pantalla** → **Gestionar teclados**
3. Activar **VoiceType Keyboard**
4. Al escribir, tocar el icono de teclado y seleccionar "VoiceType Keyboard"

## Uso

1. Mantener pulsado el botón 🎤
2. Hablar en español o catalán
3. Soltar el botón
4. Esperar 1-3 segundos
5. El texto limpio aparece automáticamente

## Solución de Problemas

### Error: "WhisperEngine no inicializado"
- Verificar que el modelo `ggml-base.bin` esté en `app/src/main/assets/models/`

### Error: "Permiso de micrófono requerido"
- Conceder permiso en Ajustes → Aplicaciones → VoiceType → Permisos

### La transcripción es lenta
- Usar modelo Whisper `tiny` o `base` en lugar de `large`
- Cerrar otras aplicaciones para liberar RAM

### El LLM no mejora el texto
- Verificar que Phi-3 esté descargado correctamente
- El fallback de reglas simples siempre funciona

## Optimización para Producción

### Reducir tamaño del APK
```gradle
android {
    splits {
        abi {
            enable true
            reset()
            include 'arm64-v8a', 'armeabi-v7a'
        }
    }
}
```

### Usar modelos más ligeros
- Whisper: `ggml-tiny.bin` (~75MB)
- LLM: `gemma-2b-it.Q4_K_M.gguf` (~1.5GB)

## Licencia

MIT License - Ver LICENSE para detalles
