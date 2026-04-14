package com.voicetype.keyboard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * AudioRecorder - Grabación de audio en formato WAV mono 16kHz
 * 
 * Formato recomendado para Whisper.cpp:
 * - Sample rate: 16000 Hz
 * - Canales: 1 (mono)
 * - Bits: 16-bit PCM
 * - Formato: WAV
 */
class AudioRecorder {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null
    
    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_MULTIPLIER = 4
    }

    fun hasPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startRecording(outputFile: File) {
        if (isRecording) return

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * BUFFER_SIZE_MULTIPLIER
        
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("Failed to initialize AudioRecord")
        }

        audioRecord?.startRecording()
        isRecording = true

        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            writeAudioDataToFile(outputFile)
        }
    }

    fun stopRecording() {
        if (!isRecording) return

        isRecording = false
        recordingJob?.cancel()
        
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            // Ignorar errores al parar
        }
    }

    fun release() {
        stopRecording()
        audioRecord?.release()
        audioRecord = null
    }

    private suspend fun writeAudioDataToFile(outputFile: File) {
        val bufferSize = audioRecord?.bufferSizeInBytes ?: return
        
        try {
            FileOutputStream(outputFile).use { fos ->
                val buffer = ByteArray(bufferSize)
                val totalBytes = mutableListOf<Byte>()
                
                while (isRecording) {
                    val read = audioRecord?.read(buffer, 0, bufferSize) ?: -1
                    if (read > 0) {
                        totalBytes.addAll(buffer.take(read).toList())
                    }
                }

                // Escribir header WAV + datos
                val wavData = createWavFile(totalBytes.toByteArray(), SAMPLE_RATE)
                fos.write(wavData)
            }
        } catch (e: Exception) {
            throw e
        }
    }

    private fun createWavFile(data: ByteArray, sampleRate: Int): ByteArray {
        val totalDataLen = data.size + 44
        val channels: Short = 1
        val bitsPerSample: Short = 16
        val byteRate = (sampleRate * channels * bitsPerSample / 8).toLong()
        
        return ByteBuffer.allocate(totalDataLen).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            
            // RIFF header
            put("RIFF".toByteArray())
            putInt(totalDataLen - 8)
            put("WAVE".toByteArray())
            
            // fmt chunk
            put("fmt ".toByteArray())
            putInt(16) // chunk size
            putShort(1) // audio format (PCM)
            putShort(channels)
            putInt(sampleRate)
            putInt(byteRate.toInt())
            putShort((channels * bitsPerSample / 8).toShort()) // block align
            putShort(bitsPerSample)
            
            // data chunk
            put("data".toByteArray())
            putInt(data.size)
            put(data)
            
            flip()
        }.array()
    }
}
