cmake_minimum_required(VERSION 3.22)

project(VoiceTypeKeyboard)

# Whisper.cpp
add_subdirectory(whisper)

# Llama.cpp
add_subdirectory(llama)

# Wrapper para Whisper
add_library(whisper SHARED
    whisper_wrapper.cpp
)

target_link_libraries(whisper
    whisper_cpp
    android
    log
)

# Wrapper para Llama
add_library(llama SHARED
    llama_wrapper.cpp
)

target_link_libraries(llama
    llama_cpp
    android
    log
)
