package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.sin

object AmbientSoundManager {
    private const val TAG = "AmbientSoundManager"
    private var mediaPlayer: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    
    private const val NORMAL_VOLUME = 0.35f
    private const val DUCKED_VOLUME = 0.08f

    // Generate and cache files
    suspend fun getAudioFile(context: Context, type: String): File = withContext(Dispatchers.IO) {
        val cacheDir = context.cacheDir
        val fileName = when (type) {
            "Calm Meditation" -> "calm_meditation.wav"
            "Gentle Waves" -> "gentle_waves.wav"
            "Zen Bells" -> "zen_bells.wav"
            else -> "calm_meditation.wav"
        }
        val file = File(cacheDir, fileName)
        if (file.exists() && file.length() > 1000) {
            return@withContext file
        }

        val pcm = when (type) {
            "Calm Meditation" -> generateCalmDrone()
            "Gentle Waves" -> generateOceanWaves()
            "Zen Bells" -> generateZenBells()
            else -> generateCalmDrone()
        }
        writeWavFile(pcm, file)
        file
    }

    private fun generateCalmDrone(sampleRate: Int = 22050, durationSec: Int = 10): ByteArray {
        val totalSamples = sampleRate * durationSec
        val buffer = ByteArray(totalSamples * 2)
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            // Rich minor or major spacey chords: Root, minor 3rd, 5th, minor 7th, 9th
            val s1 = sin(2 * PI * 110.0 * t) // A2
            val s2 = sin(2 * PI * 130.81 * t) // C3
            val s3 = sin(2 * PI * 164.81 * t) // E3
            val s4 = sin(2 * PI * 196.00 * t) // G3
            val wave = (s1 * 0.4 + s2 * 0.25 + s3 * 0.2 + s4 * 0.15)
            // LFO for sweet shimmer
            val lfo = 0.7 + 0.3 * sin(2 * PI * 0.2 * t)
            
            // Envelope for fade out at extreme ends to loop smoothly
            val env = if (i < 20000) i / 20000.0 else if (i > totalSamples - 20000) (totalSamples - i) / 20000.0 else 1.0
            val sample = (wave * lfo * env * 15000).toInt().coerceIn(-32768, 32767)
            buffer[2 * i] = (sample and 0xFF).toByte()
            buffer[2 * i + 1] = ((sample shr 8) and 0xFF).toByte()
        }
        return buffer
    }

    private fun generateOceanWaves(sampleRate: Int = 22050, durationSec: Int = 10): ByteArray {
        val totalSamples = sampleRate * durationSec
        val buffer = ByteArray(totalSamples * 2)
        var lastValue = 0.0
        val random = java.util.Random()
        for (i in 0 until totalSamples) {
            val white = random.nextDouble() * 2.0 - 1.0
            lastValue = 0.94 * lastValue + 0.06 * white
            // Slow breath modulation
            val waveMod = 0.2 + 0.8 * sin(2 * PI * (i.toDouble() / sampleRate) / 5.0)
            val valOut = lastValue * waveMod
            val env = if (i < 20000) i / 20000.0 else if (i > totalSamples - 20000) (totalSamples - i) / 20000.0 else 1.0
            val sample = (valOut * env * 12000).toInt().coerceIn(-32768, 32767)
            buffer[2 * i] = (sample and 0xFF).toByte()
            buffer[2 * i + 1] = ((sample shr 8) and 0xFF).toByte()
        }
        return buffer
    }

    private fun generateZenBells(sampleRate: Int = 22050, durationSec: Int = 12): ByteArray {
        val totalSamples = sampleRate * durationSec
        val buffer = ByteArray(totalSamples * 2)
        // Set triggers for chimes
        val strikes = listOf(
            Pair(0.2, 329.63),  // E4
            Pair(2.8, 392.00),  // G4
            Pair(5.5, 440.00),  // A4
            Pair(8.2, 523.25),  // C5
            Pair(10.5, 587.33)  // D5
        )
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            var sampleVal = 0.0
            for ((strikeTime, freq) in strikes) {
                val elapsed = t - strikeTime
                if (elapsed > 0) {
                    val decay = Math.exp(-0.8 * elapsed)
                    if (decay > 0.001) {
                        val wave = sin(2 * PI * freq * elapsed) * 0.5 +
                                   sin(2 * PI * (freq * 1.5) * elapsed) * 0.25 +
                                   sin(2 * PI * (freq * 2.0) * elapsed) * 0.15 +
                                   sin(2 * PI * (freq * 2.7) * elapsed) * 0.1
                        sampleVal += wave * decay
                    }
                }
            }
            // Add a very low warm drone pad as background to make it beautiful
            val backgroundPad = 0.12 * sin(2 * PI * 110.0 * t) + 0.08 * sin(2 * PI * 165.0 * t)
            val finalWave = sampleVal + backgroundPad
            val env = if (i < 10000) i / 10000.0 else if (i > totalSamples - 10000) (totalSamples - i) / 10000.0 else 1.0
            val sample = (finalWave * env * 14000).toInt().coerceIn(-32768, 32767)
            buffer[2 * i] = (sample and 0xFF).toByte()
            buffer[2 * i + 1] = ((sample shr 8) and 0xFF).toByte()
        }
        return buffer
    }

    private fun writeWavFile(pcmData: ByteArray, outputFile: File, sampleRate: Int = 22050) {
        val totalAudioLen = pcmData.size.toLong()
        val totalDataLen = totalAudioLen + 36
        val byteRate = (sampleRate * 2).toLong()

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()     // RIFF
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()     // WAVE
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()    // 'fmt ' chunk
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1                    // PCM
        header[21] = 0
        header[22] = 1                    // mono
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = 2                    // block align
        header[33] = 0
        header[34] = 16                   // bit rate
        header[35] = 0
        header[36] = 'd'.code.toByte()    // data
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        FileOutputStream(outputFile).use { out ->
            out.write(header)
            out.write(pcmData)
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (audioManager == null) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .build()
            focusRequest = request
            audioManager?.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    fun startPlaying(context: Context, file: File) {
        try {
            stopPlaying()
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            requestAudioFocus()

            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                isLooping = true
                setVolume(NORMAL_VOLUME, NORMAL_VOLUME)
                prepare()
                start()
            }
            Log.d(TAG, "Started ambient playback of ${file.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play ambient sound", e)
        }
    }

    fun stopPlaying() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
            mediaPlayer = null
            
            if (audioManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
                    audioManager?.abandonAudioFocusRequest(focusRequest!!)
                    focusRequest = null
                } else {
                    @Suppress("DEPRECATION")
                    audioManager?.abandonAudioFocus(null)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop sound playback", e)
        }
    }

    fun duckVolume() {
        try {
            mediaPlayer?.setVolume(DUCKED_VOLUME, DUCKED_VOLUME)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to duck volume", e)
        }
    }

    fun restoreVolume() {
        try {
            mediaPlayer?.setVolume(NORMAL_VOLUME, NORMAL_VOLUME)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore volume", e)
        }
    }
}
