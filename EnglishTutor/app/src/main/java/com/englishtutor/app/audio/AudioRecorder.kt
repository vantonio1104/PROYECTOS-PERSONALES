package com.englishtutor.app.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

/** Graba audio del micrófono en un .m4a (AAC) para el modo push-to-talk. */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAt = 0L

    fun start() {
        stopAndRelease() // por si quedó una grabación colgada
        val file = File(context.cacheDir, "turn.m4a")
        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context)
        else @Suppress("DEPRECATION") MediaRecorder()

        mr.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioChannels(1)
            setAudioSamplingRate(16_000)   // Whisper trabaja a 16 kHz: archivo más liviano
            setAudioEncodingBitRate(64_000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = mr
        outputFile = file
        startedAt = System.currentTimeMillis()
    }

    /** Detiene la grabación. Devuelve null si fue demasiado corta o falló. */
    fun stop(): File? {
        val duration = System.currentTimeMillis() - startedAt
        val ok = stopAndRelease()
        return outputFile?.takeIf { ok && duration >= MIN_DURATION_MS && it.length() > 0 }
    }

    private fun stopAndRelease(): Boolean {
        val mr = recorder ?: return false
        recorder = null
        return try {
            mr.stop() // lanza RuntimeException si casi no se grabó nada
            true
        } catch (e: RuntimeException) {
            Log.w("Tutor", "Grabación demasiado corta", e)
            false
        } finally {
            mr.release()
        }
    }

    companion object {
        private const val MIN_DURATION_MS = 500L
    }
}
