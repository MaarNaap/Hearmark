package com.example.util

import java.io.File
import java.io.FileOutputStream

object SampleAudioGenerator {
    /**
     * Generates a valid mono 16-bit PCM WAV file with a pleasant sine wave tone.
     * Duration is in seconds.
     */
    fun generateSineWav(file: File, durationSeconds: Int, frequency: Double = 350.0): Boolean {
        val sampleRate = 11025
        val numSamples = durationSeconds * sampleRate
        val pcmDataSize = numSamples * 2 // 16-bit PCM (2 bytes per sample)
        val totalFileSize = pcmDataSize + 36 // Header size minus 8

        try {
            // Ensure parent directory exists
            file.parentFile?.mkdirs()
            
            FileOutputStream(file).use { out ->
                // Write WAV Header
                out.write("RIFF".toByteArray()) // ChunkID
                out.write(intToByteArray(totalFileSize)) // ChunkSize
                out.write("WAVE".toByteArray()) // Format
                
                out.write("fmt ".toByteArray()) // Subchunk1ID
                out.write(intToByteArray(16)) // Subchunk1Size
                out.write(shortToByteArray(1)) // AudioFormat (1=PCM)
                out.write(shortToByteArray(1)) // NumChannels (1=Mono)
                out.write(intToByteArray(sampleRate)) // SampleRate
                out.write(intToByteArray(sampleRate * 2)) // ByteRate
                out.write(shortToByteArray(2)) // BlockAlign
                out.write(shortToByteArray(16)) // BitsPerSample
                
                out.write("data".toByteArray()) // Subchunk2ID
                out.write(intToByteArray(pcmDataSize)) // Subchunk2Size

                // Write Sine Wave PCM data
                val bufferSize = 1024
                val buffer = ByteArray(bufferSize)
                var bufferIndex = 0

                for (i in 0 until numSamples) {
                    val angle = 2.0 * Math.PI * frequency * (i.toDouble() / sampleRate)
                    // Synthesize soft sine wave tone with vibrato modulation to keep it interesting
                    val modFreq = 2.0 // Slow vibrato
                    val vibrato = 5.0 * Math.sin(2.0 * Math.PI * modFreq * (i.toDouble() / sampleRate))
                    val modulatedAngle = 2.0 * Math.PI * (frequency + vibrato) * (i.toDouble() / sampleRate)

                    val value = (Math.sin(modulatedAngle) * 14000).toInt() // Max 32767
                    
                    buffer[bufferIndex++] = (value and 0xFF).toByte()
                    buffer[bufferIndex++] = ((value shr 8) and 0xFF).toByte()

                    if (bufferIndex >= bufferSize) {
                        out.write(buffer, 0, bufferIndex)
                        bufferIndex = 0
                    }
                }
                if (bufferIndex > 0) {
                    out.write(buffer, 0, bufferIndex)
                }
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }

    private fun shortToByteArray(value: Short): ByteArray {
        return byteArrayOf(
            (value.toInt() and 0xFF).toByte(),
            ((value.toInt() shr 8) and 0xFF).toByte()
        )
    }
}
