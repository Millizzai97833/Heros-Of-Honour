package com.example.game

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedural DSP combat audio synthesizer backed by SoundPool.
 * Generates crisp 16-bit PCM WAV buffers at initialization so all combat SFX
 * (tank cannons, jet autocannons, naval salvos, explosions, thunder, repairs, UI)
 * play with zero latency and zero missing external asset risk.
 */
class SoundEffectsEngine(context: Context) {

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(10)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    var muted: Boolean = false
    var masterVolume: Float = 0.85f

    private var cannonId: Int = 0
    private var autocannonId: Int = 0
    private var navalSalvoId: Int = 0
    private var missileId: Int = 0
    private var explosionHeavyId: Int = 0
    private var explosionLightId: Int = 0
    private var hitMarkerId: Int = 0
    private var repairId: Int = 0
    private var uiClickId: Int = 0
    private var coinRewardId: Int = 0
    private var thunderId: Int = 0

    init {
        val cache = File(context.cacheDir, "combat_sfx").apply { mkdirs() }
        cannonId = loadSynth(cache, "cannon.wav", synthesizeCannon(0.55f, 110.0))
        autocannonId = loadSynth(cache, "autocannon.wav", synthesizeAutocannon(0.11f))
        navalSalvoId = loadSynth(cache, "naval.wav", synthesizeCannon(0.70f, 75.0))
        missileId = loadSynth(cache, "missile.wav", synthesizeMissile(0.45f))
        explosionHeavyId = loadSynth(cache, "exp_heavy.wav", synthesizeExplosion(0.85f, deep = true))
        explosionLightId = loadSynth(cache, "exp_light.wav", synthesizeExplosion(0.42f, deep = false))
        hitMarkerId = loadSynth(cache, "hit.wav", synthesizeHitMarker())
        repairId = loadSynth(cache, "repair.wav", synthesizeChime(520.0, 780.0, 0.25f))
        uiClickId = loadSynth(cache, "click.wav", synthesizeClick())
        coinRewardId = loadSynth(cache, "coin.wav", synthesizeChime(880.0, 1320.0, 0.28f))
        thunderId = loadSynth(cache, "thunder.wav", synthesizeExplosion(1.1f, deep = true))
    }

    private fun loadSynth(dir: File, name: String, samples: ShortArray): Int {
        return try {
            val file = File(dir, name)
            writeWav(file, samples, SAMPLE_RATE)
            soundPool.load(file.absolutePath, 1)
        } catch (_: Exception) {
            0
        }
    }

    private fun play(id: Int, rate: Float = 1.0f, volScale: Float = 1.0f) {
        if (muted || id == 0) return
        val v = (masterVolume * volScale).coerceIn(0f, 1f)
        soundPool.play(id, v, v, 1, 0, rate)
    }

    fun playCannonFire() = play(cannonId, rate = 0.95f + Random.nextFloat() * 0.1f, volScale = 0.95f)
    fun playAutocannon() = play(autocannonId, rate = 0.94f + Random.nextFloat() * 0.12f, volScale = 0.72f)
    fun playNavalSalvo() = play(navalSalvoId, rate = 0.92f + Random.nextFloat() * 0.08f, volScale = 1.0f)
    fun playMissileLaunch() = play(missileId, volScale = 0.85f)
    fun playExplosion(heavy: Boolean) = play(if (heavy) explosionHeavyId else explosionLightId, volScale = if (heavy) 1.0f else 0.78f)
    fun playHitMarker() = play(hitMarkerId, volScale = 0.8f)
    fun playRepair() = play(repairId, volScale = 0.75f)
    fun playUiClick() = play(uiClickId, volScale = 0.65f)
    fun playCoinReward() = play(coinRewardId, volScale = 0.85f)
    fun playThunder() = play(thunderId, rate = 0.78f, volScale = 0.65f)

    fun release() {
        soundPool.release()
    }

    private fun synthesizeCannon(durationSec: Float, baseFreq: Double): ShortArray {
        val count = (SAMPLE_RATE * durationSec).toInt()
        val out = ShortArray(count)
        var lp = 0.0
        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val pitch = baseFreq * exp(-t * 7.5) + 34.0
            val punch = sin(2.0 * PI * pitch * t) * exp(-t * 6.0)
            val noise = (Random.nextDouble() * 2.0 - 1.0) * exp(-t * 9.5)
            lp = lp * 0.82 + noise * 0.18
            val breech = if (t < 0.04) sin(2.0 * PI * 980.0 * t) * exp(-t * 60.0) * 0.25 else 0.0
            val sample = (punch * 0.72 + lp * 0.65 + breech).coerceIn(-1.0, 1.0)
            out[i] = (sample * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthesizeAutocannon(durationSec: Float): ShortArray {
        val count = (SAMPLE_RATE * durationSec).toInt()
        val out = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val crack = sin(2.0 * PI * (240.0 * exp(-t * 25.0) + 90.0) * t) * exp(-t * 24.0)
            val snap = (Random.nextDouble() * 2.0 - 1.0) * exp(-t * 32.0) * 0.65
            val sample = (crack * 0.65 + snap).coerceIn(-1.0, 1.0)
            out[i] = (sample * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthesizeMissile(durationSec: Float): ShortArray {
        val count = (SAMPLE_RATE * durationSec).toInt()
        val out = ShortArray(count)
        var lp = 0.0
        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val env = sin(PI * (t / durationSec).coerceIn(0.0, 1.0))
            val noise = Random.nextDouble() * 2.0 - 1.0
            lp = lp * 0.68 + noise * 0.32
            val whine = sin(2.0 * PI * (420.0 + t * 560.0) * t) * 0.22
            val sample = ((lp * 0.75 + whine) * env).coerceIn(-1.0, 1.0)
            out[i] = (sample * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthesizeExplosion(durationSec: Float, deep: Boolean): ShortArray {
        val count = (SAMPLE_RATE * durationSec).toInt()
        val out = ShortArray(count)
        var lp = 0.0
        val decay = if (deep) 3.4 else 6.2
        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val noise = Random.nextDouble() * 2.0 - 1.0
            lp = lp * 0.88 + noise * 0.12
            val sub = sin(2.0 * PI * (52.0 * exp(-t * 2.5) + 24.0) * t) * exp(-t * decay)
            val env = exp(-t * decay)
            val sample = ((lp * 0.9 + sub * 0.65) * env).coerceIn(-1.0, 1.0)
            out[i] = (sample * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthesizeHitMarker(): ShortArray {
        val count = (SAMPLE_RATE * 0.07f).toInt()
        val out = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val s = sin(2.0 * PI * 1850.0 * t) * exp(-t * 55.0) * 0.7
            out[i] = (s.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthesizeClick(): ShortArray {
        val count = (SAMPLE_RATE * 0.035f).toInt()
        val out = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val s = sin(2.0 * PI * 1150.0 * t) * exp(-t * 95.0) * 0.55
            out[i] = (s.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthesizeChime(f1: Double, f2: Double, durationSec: Float): ShortArray {
        val count = (SAMPLE_RATE * durationSec).toInt()
        val out = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val freq = if (t < durationSec * 0.45) f1 else f2
            val s = sin(2.0 * PI * freq * t) * exp(-t * 8.0) * 0.65
            out[i] = (s.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun writeWav(file: File, samples: ShortArray, sampleRate: Int) {
        val dataSize = samples.size * 2
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray())
        buffer.putInt(36 + dataSize)
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(1)
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * 2)
        buffer.putShort(2)
        buffer.putShort(16)
        buffer.put("data".toByteArray())
        buffer.putInt(dataSize)
        for (s in samples) {
            buffer.putShort(s)
        }
        FileOutputStream(file).use { it.write(buffer.array()) }
    }

    private companion object {
        const val SAMPLE_RATE = 22050
    }
}
