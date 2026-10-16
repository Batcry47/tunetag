package com.tunetag.player.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** The iPod "tick": a tiny click synthesised in code (no audio asset), played through SoundPool for low latency. */
object ClickSound {
    private var pool: SoundPool? = null
    private var soundId = 0
    private var ready = false

    fun init(ctx: Context) {
        if (pool != null) return
        try {
            val f = File(ctx.cacheDir, "tick.wav")
            if (!f.exists()) f.writeBytes(makeWav())
            val p = SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                ).build()
            p.setOnLoadCompleteListener { _, _, status -> ready = status == 0 }
            soundId = p.load(f.absolutePath, 1)
            pool = p
        } catch (_: Exception) {
        }
    }

    fun play(volume: Float = 0.55f) {
        if (ready) pool?.play(soundId, volume, volume, 1, 0, 1f)
    }

    private fun makeWav(): ByteArray {
        val sr = 44100
        val n = (sr * 0.016).toInt()
        val rnd = java.util.Random(7)
        val pcm = ShortArray(n)
        for (i in 0 until n) {
            val t = i / sr.toDouble()
            val env = Math.exp(-t * 420.0)
            val tone = Math.sin(2 * Math.PI * 2400.0 * t) * 0.55 +
                (rnd.nextDouble() * 2 - 1) * 0.35 +
                Math.sin(2 * Math.PI * 220.0 * t) * 0.25 * Math.exp(-t * 180.0)
            pcm[i] = (env * tone * 0.9 * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
        }
        val dataLen = n * 2
        val bb = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()).putInt(36 + dataLen).put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(sr).putInt(sr * 2).putShort(2).putShort(16)
        bb.put("data".toByteArray()).putInt(dataLen)
        for (v in pcm) bb.putShort(v)
        return bb.array()
    }
}
