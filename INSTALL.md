# 📦 Guía de Instalación - VoiceType Keyboard

## ⚠️ IMPORTANTE: Sobre los archivos de whisper.cpp

Los archivos `.zip` que ves en las releases de GitHub (`whisper-bin-Win32.zip`, `whisper-bin-x64.zip`, etc.) **NO SON COMPATIBLES CON ANDROID**.

### ❌ ¿Qué NO funciona en Android?
- `whisper-bin-Win32.zip` → Binarios para Windows 32-bit
- `whisper-bin-x64.zip` → Binarios para Windows/Linux 64-bit
- `whisper-blas-bin-*.zip` → Binarios con BLAS para Windows
- `whisper-cublas-*.zip` → Binarios con CUDA para Windows (GPU NVIDIA)
- `whisper-v1.8.4-xcframework.zip` → Para iOS/macOS
- `whispercpp.jar.zip` → Para Java Desktop (no Android)

### ✅ ¿Qué SÍ necesitas para Android?

**Código fuente + NDK para compilar tú mismo:**

```bash
# 1. Clonar el repositorio completo (código fuente C++)
git clone https://github.com/ggml-org/whisper.cpp

# 2. El sistema de build de Android (NDK + CMake) lo compilará para ARM
```

---

## 🚀 Pasos de Instalación Completos

### Paso 1: Clonar submódulos en tu proyecto

Desde la raíz del proyecto `/workspace`:

```bash
# Crear directorio native
mkdir -p native

# Clonar whisper.cpp como submódulo
git submodule add https://github.com/ggml-org/whisper.cpp native/whisper

# Clonar llama.cpp como submódulo
git submodule add https://github.com/ggml-org/llama.cpp native/llama

# Inicializar submódulos recursivos (dependencias internas)
git submodule update --init --recursive
```

**Verificación:**
```bash
ls -la native/whisper/CMakeLists.txt  # Debe existir
ls -la native/llama/CMakeLists.txt    # Debe existir
```

---

### Paso 2: Descargar Modelos

Los modelos son archivos binarios pre-entrenados en formato GGUF.

#### 2.1 Whisper (ASR - Speech-to-Text)

```bash
mkdir -p app/src/main/assets/models

cd app/src/main/assets/models

# Opción A: Modelo base (recomendado - equilibrio precisión/velocidad)
wget https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin
# Tamaño: ~145 MB | Tiempo inferencia: 0.5-2s

# Opción B: Modelo tiny (más rápido, menos preciso)
# wget https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin
# Tamaño: ~75 MB | Tiempo inferencia: 0.3-1s

# Opción C: Modelo small (más preciso, más lento)
# wget https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin
# Tamaño: ~244 MB | Tiempo inferencia: 1-3s
```

#### 2.2 Phi-3 Mini (LLM - Mejora de texto)

```bash
cd app/src/main/assets/models

# Phi-3 Mini 4K Instruct cuantizado 4-bit (recomendado)
wget https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf
# Tamaño: ~2.3 GB | Tiempo inferencia: 0.3-1s

# Alternativa más ligera: Gemma 2B
# wget https://huggingface.co/google/gemma-2b-it-GGUF/resolve/main/gemma-2b-it-q4_k_m.gguf
# Tamaño: ~1.5 GB | Tiempo inferencia: 0.2-0.8s
```

**Verificación:**
```bash
ls -lh app/src/main/assets/models/
# Deberías ver:
# ggml-base.bin (~145M)
# Phi-3-mini-4k-instruct-q4.gguf (~2.3G)
```

---

### Paso 3: Configurar Android Studio

1. **Abrir proyecto en Android Studio**
   ```bash
   cd /workspace
   # Abrir Android Studio → File → Open → Seleccionar /workspace
   ```

2. **Sincronizar Gradle**
   - Android Studio detectará automáticamente los cambios
   - Pulsar "Sync Now" cuando aparezca el banner

3. **Verificar NDK instalado**
   - Ir a: `Tools → SDK Manager → SDK Tools`
   - Marcar "NDK (Side by side)" versión r25 o superior
   - Aplicar y esperar instalación

---

### Paso 4: Compilar APK

#### Opción A: Desde terminal
```bash
cd /workspace
./gradlew assembleDebug
```

#### Opción B: Desde Android Studio
- `Build → Make Project` (Ctrl+F9)
- O botón martillo 🔨 en la barra superior

**Tiempo esperado del primer build:**
- Compilación de whisper.cpp: ~3-5 minutos
- Compilación de llama.cpp: ~5-8 minutos
- Build de la app: ~1-2 minutos
- **Total: 10-15 minutos** (el primer build)

Builds posteriores: ~30 segundos (usa caché)

---

### Paso 5: Instalar en dispositivo

```bash
# Conectar dispositivo Android vía USB con depuración activada
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

O desde Android Studio:
- Botón ▶️ "Run" (selecciona tu dispositivo)

---

### Paso 6: Activar teclado

1. **Ajustes → Sistema → Idioma y entrada → Teclado virtual**
2. Activar "VoiceType Keyboard"
3. Cambiar teclado actual a "VoiceType Keyboard"
   - Método rápido: Espacio + Shift en cualquier campo de texto
   - O notificación flotante al escribir

---

## 🛠️ Solución de Problemas

### Error: "Whisper.cpp no encontrado"
```
CMake Error at CMakeLists.txt:27 (message):
  Whisper.cpp no encontrado en: /workspace/native/whisper
```

**Solución:**
```bash
cd /workspace
git submodule add https://github.com/ggml-org/whisper.cpp native/whisper
git submodule update --init --recursive
```

### Error: "Modelo no encontrado"
```
java.io.FileNotFoundException: models/ggml-base.bin
```

**Solución:**
```bash
mkdir -p app/src/main/assets/models
cd app/src/main/assets/models
wget https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin
```

### Error: "NDK no encontrado"

**Solución:**
1. Android Studio → `Tools → SDK Manager → SDK Tools`
2. Marcar "NDK (Side by side)" 
3. Versión recomendada: 25.x o 26.x
4. Aplicar cambios

### Build muy lento

**Optimizaciones:**
```bash
# Editar app/build.gradle.kts y añadir:
android {
    ndk {
        abiFilters += listOf("arm64-v8a")  // Solo ARM 64-bit (móviles modernos)
    }
}
```

Esto reduce tiempo de build en ~50% al compilar solo para arquitecturas modernas.

---

## 📊 Estructura Final Esperada

```
/workspace/
├── native/
│   ├── whisper/          # Código fuente whisper.cpp (clonado)
│   └── llama/            # Código fuente llama.cpp (clonado)
├── app/
│   └── src/main/
│       ├── assets/
│       │   └── models/
│       │       ├── ggml-base.bin              # Whisper
│       │       └── Phi-3-mini-4k-instruct-q4.gguf  # LLM
│       ├── jni/
│       │   ├── CMakeLists.txt
│       │   ├── whisper_wrapper.cpp
│       │   └── llama_wrapper.cpp
│       └── java/com/voicetype/keyboard/
│           ├── VoiceTypeIME.kt
│           ├── AudioRecorder.kt
│           ├── WhisperEngine.kt
│           ├── LLMEngine.kt
│           └── ui/SettingsActivity.kt
├── README.md
├── INSTALL.md              # Este archivo
└── ARQUITECTURA_HIBRIDA.md
```

---

## ✅ Checklist de Verificación

- [ ] Submódulos clonados en `native/whisper` y `native/llama`
- [ ] Archivos `CMakeLists.txt` existen en ambos directorios
- [ ] Modelos descargados en `app/src/main/assets/models/`
- [ ] NDK instalado en Android Studio
- [ ] Primer build completado sin errores
- [ ] APK instalado en dispositivo
- [ ] Teclado activado en ajustes
- [ ] Prueba de grabación y transcripción exitosa

---

## 📞 Soporte

Si encuentras problemas:
1. Revisa los logs con `adb logcat | grep VoiceType`
2. Verifica que todos los pasos están completados
3. Consulta issues en GitHub de whisper.cpp y llama.cpp

¡Disfruta de tu teclado con IA 100% offline! 🎤🧠⌨️
