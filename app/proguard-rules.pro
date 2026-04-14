# Add project specific ProGuard rules here.
# Keep native methods for JNI
-keepclassmembers class com.voicetype.keyboard.** {
    public native <methods>;
}

# Keep Whisper and Llama classes
-keep class com.voicetype.keyboard.WhisperEngine
-keep class com.voicetype.keyboard.LLMEngine
-keep class com.voicetype.keyboard.AudioRecorder
