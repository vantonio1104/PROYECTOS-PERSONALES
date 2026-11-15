package com.englishtutor.app.audio

import android.media.MediaPlayer
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Reproduce un archivo de audio y suspende hasta que termina. */
class AudioPlayer {
    private var player: MediaPlayer? = null

    suspend fun play(file: File) {
        stop()
        suspendCancellableCoroutine { cont ->
            val mp = MediaPlayer()
            player = mp
            mp.setOnCompletionListener { release(mp); if (cont.isActive) cont.resume(Unit) }
            mp.setOnErrorListener { _, what, extra ->
                release(mp)
                if (cont.isActive) cont.resumeWithException(IllegalStateException("MediaPlayer error $what/$extra"))
                true
            }
            cont.invokeOnCancellation { release(mp) }
            try {
                mp.setDataSource(file.absolutePath)
                mp.prepare()
                mp.start()
            } catch (e: Exception) {
                release(mp)
                if (cont.isActive) cont.resumeWithException(e)
            }
        }
    }

    fun stop() {
        player?.let { release(it) }
    }

    private fun release(mp: MediaPlayer) {
        if (player === mp) player = null
        runCatching { mp.stop() }
        mp.release()
    }
}
