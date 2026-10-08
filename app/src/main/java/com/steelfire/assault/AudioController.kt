package com.steelfire.assault

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.util.concurrent.ConcurrentHashMap

/** Small SoundPool facade with named cues and a looped industrial bed. */
class AudioController(context: Context) {
    private val pool = SoundPool.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .setMaxStreams(12)
        .build()
    private val ids = ConcurrentHashMap<String, Int>()
    private val streams = ConcurrentHashMap<String, Int>()
    private var musicId = 0
    private var musicStream = 0
    private var enabled = true

    init {
        pool.setOnLoadCompleteListener { soundPool, sampleId, status ->
            if (status == 0 && sampleId == musicId && enabled && musicStream == 0) {
                musicStream = soundPool.play(sampleId, 0.16f, 0.16f, 1, -1, 1f)
            }
        }
        val cues = mapOf(
            "rifle" to "audio/kenney/laser_large_000.ogg",
            "shotgun" to "audio/sfx/shotgun.wav",
            "hit" to "audio/kenney/impact_metal_000.ogg",
            "explosion" to "audio/kenney/explosion_crunch_000.ogg",
            "jump" to "audio/cc0/steps_platform.ogg",
            "ui" to "audio/kenney/computer_noise_000.ogg",
            "damage" to "audio/sfx/damage.wav",
            "boss" to "audio/kenney/force_field_000.ogg",
            "voiceover" to "audio/cg/intro_voiceover.wav"
        )
        cues.forEach { (name, path) ->
            context.assets.openFd(path).use { ids[name] = pool.load(it, 1) }
        }
        context.assets.openFd("audio/industrial_loop.wav").use { musicId = pool.load(it, 1) }
    }

    fun play(name: String, volume: Float = 0.8f) {
        if (!enabled) return
        ids[name]?.let { sampleId ->
            val stream = pool.play(sampleId, volume, volume, 2, 0, 1f)
            if (stream != 0) streams[name] = stream
        }
    }

    fun stop(name: String) {
        streams.remove(name)?.let(pool::stop)
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) pool.autoPause() else pool.autoResume()
    }

    fun release() { pool.release() }
}
