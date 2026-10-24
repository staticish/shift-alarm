package com.cyber1sh.shiftalarm

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * A made-up alarm sound for when the phone's own tone cannot be opened (before the first unlock,
 * or a deleted tone file): two short beeps and a pause, looped. It needs no file at all.
 */
object Beeper {
    private const val RATE = 44_100

    fun start(audio: AudioAttributes): AudioTrack? =
        runCatching {
            val pcm = pattern()
            val track =
                AudioTrack.Builder()
                    .setAudioAttributes(audio)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    ).setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
            track.write(pcm, 0, pcm.size)
            track.setLoopPoints(0, pcm.size, -1)
            track.play()
            track
        }.getOrNull()

    private fun pattern(): ShortArray {
        val out = ShortArray((RATE * 1.4).toInt())
        fun tone(fromS: Double, lengthS: Double, hz: Double) {
            val from = (fromS * RATE).toInt()
            val length = (lengthS * RATE).toInt()
            val ramp = 0.008 * RATE
            for (i in 0 until length) {
                val envelope = min(1.0, min(i / ramp, (length - i) / ramp))
                out[from + i] = (sin(2 * PI * hz * i / RATE) * envelope * 0.7 * Short.MAX_VALUE).toInt().toShort()
            }
        }
        tone(0.0, 0.16, 988.0)
        tone(0.24, 0.16, 988.0)
        return out
    }
}
