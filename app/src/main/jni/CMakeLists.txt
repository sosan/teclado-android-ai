cmake_minimum_required(VERSION 3.22)

project(VoiceTypeKeyboard)

# ==========================================
# CONFIGURACIÓN DE RUTAS
# ==========================================
# Los repositorios whisper.cpp y llama.cpp deben estar en ../../native/
# Es decir: app/src/main/jni/../../native/whisper
# Que equivale a: app/native/whisper desde la raíz del proyecto

set(WHISPER_DIR ${CMAKE_SOURCE_DIR}/../../native/whisper)
set(LLAMA_DIR ${CMAKE_SOURCE_DIR}/../../native/llama)

# ==========================================
# 1. WHISPER.CPP
# ==========================================
if(EXISTS ${WHISPER_DIR}/CMakeLists.txt)
    # Optimizaciones para Android
    set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
    set(WHISPER_BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
    
    add_subdirectory(${WHISPER_DIR} whisper_build)
else()
    message(FATAL_ERROR 
        "Whisper.cpp no encontrado en: ${WHISPER_DIR}\n"
        "Ejecuta desde la raíz del proyecto:\n"
        "  git submodule add https://github.com/ggml-org/whisper.cpp native/whisper\n"
        "  git submodule update --init --recursive"
    )
endif()

# ==========================================
# 2. LLAMA.CPP
# ==========================================
if(EXISTS ${LLAMA_DIR}/CMakeLists.txt)
    # Optimizaciones para Android
    set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
    
    add_subdirectory(${LLAMA_DIR} llama_build)
else()
    message(FATAL_ERROR 
        "Llama.cpp no encontrado en: ${LLAMA_DIR}\n"
        "Ejecuta desde la raíz del proyecto:\n"
        "  git submodule add https://github.com/ggml-org/llama.cpp native/llama\n"
        "  git submodule update --init --recursive"
    )
endif()

# ==========================================
# 3. WRAPPER JNI PARA WHISPER
# ==========================================
add_library(whisper_wrapper SHARED
    whisper_wrapper.cpp
)

target_link_libraries(whisper_wrapper
    whisper_cpp      # Target creado por whisper.cpp
    android
    log
)

# ==========================================
# 4. WRAPPER JNI PARA LLAMA
# ==========================================
add_library(llama_wrapper SHARED
    llama_wrapper.cpp
)

target_link_libraries(llama_wrapper
    llama_cpp        # Target creado por llama.cpp
    android
    log
)
