package com.example.wakeorwait.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.wakeorwait.data.model.CustomRingtone
import com.example.wakeorwait.data.repository.AlarmRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sin

class RingtoneManager private constructor(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var previewPlayer: MediaPlayer? = null
    private var synthJob: Job? = null
    private var audioTrack: AudioTrack? = null

    val builtInRingtones = listOf(
        "Siren Blitz",
        "Nuclear Meltdown",
        "Digital Rooster",
        "System Alarm"
    )

    fun startAlarmAudio(ringtoneName: String, ringtoneUriOrPath: String?) {
        stopAlarmAudio()

        // 1. If custom audio file path exists
        if (!ringtoneUriOrPath.isNullOrBlank()) {
            val file = File(ringtoneUriOrPath)
            if (file.exists()) {
                playFileLoop(file)
                return
            }
            // If it's a content URI
            if (ringtoneUriOrPath.startsWith("content://")) {
                try {
                    playUriLoop(Uri.parse(ringtoneUriOrPath))
                    return
                } catch (e: Exception) {
                    Log.e("RingtoneManager", "Failed to play content URI, fallback to synth", e)
                }
            }
        }

        // 2. Check if it's "System Alarm"
        if (ringtoneName == "System Alarm") {
            val defaultUri = android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
            if (defaultUri != null) {
                try {
                    playUriLoop(defaultUri)
                    return
                } catch (e: Exception) {
                    Log.e("RingtoneManager", "Failed to play system alarm, fallback to synth", e)
                }
            }
        }

        // 3. Fallback to our high-energy synthesized alarm tones
        playSynthesizedAlarm(ringtoneName)
    }

    private fun playFileLoop(file: File) {
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(file.absolutePath)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("RingtoneManager", "Error playing file loop, falling back to synth", e)
            playSynthesizedAlarm("Siren Blitz")
        }
    }

    private fun playUriLoop(uri: Uri) {
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(context, uri)
            isLooping = true
            prepare()
            start()
        }
    }

    /**
     * Synthesizes powerful, piercing alarm waveforms in real-time.
     * Guaranteed to work offline with ZERO external files!
     */
    private fun playSynthesizedAlarm(soundType: String) {
        synthJob?.cancel()
        synthJob = CoroutineScope(Dispatchers.Default).launch {
            val sampleRate = 44100
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack = track
            track.play()

            val shortBuffer = ShortArray(bufferSize)
            var sampleIndex = 0L

            while (isActive) {
                for (i in shortBuffer.indices) {
                    val t = sampleIndex.toDouble() / sampleRate
                    val freq = when (soundType) {
                        "Nuclear Meltdown" -> {
                            // Pulsing low-to-mid siren: 440Hz -> 880Hz every 0.8s
                            val cycle = (t % 0.8) / 0.8
                            440.0 + 440.0 * sin(cycle * Math.PI)
                        }
                        "Digital Rooster" -> {
                            // Alternating rapid 800Hz / 1200Hz bursts
                            val step = (t * 5).toInt() % 4
                            if (step == 0) 800.0 else if (step == 1) 1200.0 else 0.0
                        }
                        else -> {
                            // Siren Blitz: rapid European two-tone 750Hz / 950Hz
                            val cycle = (t * 3).toInt() % 2
                            if (cycle == 0) 750.0 else 950.0
                        }
                    }

                    val sampleVal = if (freq > 0) {
                        (sin(2.0 * Math.PI * freq * t) * 30000).toInt().toShort()
                    } else {
                        0.toShort()
                    }
                    shortBuffer[i] = sampleVal
                    sampleIndex++
                }
                track.write(shortBuffer, 0, shortBuffer.size)
            }

            try {
                track.stop()
                track.release()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun stopAlarmAudio() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            // ignore
        }
        mediaPlayer = null

        synthJob?.cancel()
        synthJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            // ignore
        }
        audioTrack = null
    }

    fun previewRingtone(filePathOrUri: String?, name: String, onCompletion: () -> Unit) {
        stopPreview()
        if (!filePathOrUri.isNullOrBlank()) {
            val file = File(filePathOrUri)
            if (file.exists()) {
                try {
                    previewPlayer = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        setDataSource(file.absolutePath)
                        setOnCompletionListener { onCompletion() }
                        prepare()
                        start()
                    }
                    return
                } catch (e: Exception) {
                    Log.e("RingtoneManager", "Preview error", e)
                }
            }
        }

        // Preview synthesized sound for 3 seconds
        startAlarmAudio(name, null)
        CoroutineScope(Dispatchers.Main).launch {
            kotlinx.coroutines.delay(3000)
            stopAlarmAudio()
            onCompletion()
        }
    }

    fun stopPreview() {
        try {
            previewPlayer?.stop()
            previewPlayer?.release()
        } catch (e: Exception) {
            // ignore
        }
        previewPlayer = null
        stopAlarmAudio()
    }

    /**
     * Imports custom audio from content Uri into local app storage.
     * Returns the created CustomRingtone or null on failure.
     */
    fun importAudioFile(uri: Uri): CustomRingtone? {
        val dir = File(context.filesDir, "custom_ringtones").apply { mkdirs() }

        var fileName = "Custom_Audio_${System.currentTimeMillis()}"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    val foundName = cursor.getString(nameIndex)
                    if (!foundName.isNullOrBlank()) {
                        fileName = foundName
                    }
                }
            }
        }

        val targetFile = File(dir, "${System.currentTimeMillis()}_$fileName")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            val displayName = fileName.substringBeforeLast(".")
            val custom = CustomRingtone(
                name = displayName,
                filePath = targetFile.absolutePath
            )
            AlarmRepository.getInstance(context).addCustomRingtone(custom)
            custom
        } catch (e: Exception) {
            Log.e("RingtoneManager", "Failed to import custom audio", e)
            null
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: RingtoneManager? = null

        fun getInstance(context: Context): RingtoneManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RingtoneManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
