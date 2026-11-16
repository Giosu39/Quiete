package com.quiete.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

enum class NoiseType(val label: String, val short: String, val description: String) {
    BROWN("Marrone", "Marrone", "Profondo e avvolgente"),
    DEEP_BROWN("Marrone profondo", "Profondo", "Ancora più cupo, come un rombo lontano"),
    WAVES("Onde", "Onde", "Rumore marrone che respira come il mare"),
    PINK("Rosa", "Rosa", "Bilanciato, ricorda una pioggia fitta"),
    WHITE("Bianco", "Bianco", "Brillante, copre quasi ogni rumore"),
}

/**
 * Genera il rumore in tempo reale (nessun file audio, nessun loop percepibile)
 * e lo scrive su un AudioTrack stereo in virgola mobile.
 */
class NoiseEngine {

    @Volatile var type: NoiseType = NoiseType.BROWN
    /** Volume scelto dall'utente, 0..1 (curva percettiva applicata internamente). */
    @Volatile var volume: Float = 0.6f
    /** 0 = suono naturale, 1 = molto caldo/ovattato (filtro passa-basso). */
    @Volatile var warmth: Float = 0.2f
    /** Abbassamento temporaneo (es. durante una telefonata), 0..1. */
    @Volatile var duck: Float = 1f

    @Volatile private var fadeGain = 0f
    @Volatile private var fadeTarget = 0f
    @Volatile private var fadeStep = 1f
    @Volatile private var running = false

    private var thread: Thread? = null
    private var smoothedVol = 0f

    private val left = Channel(0x1234567)
    private val right = Channel(0x7654321)
    private var wavePhase = 0.0
    private var wavePhase2 = 0.0

    val isRunning: Boolean get() = running

    fun start(fadeInSeconds: Float) {
        if (running) {
            fadeTo(1f, fadeInSeconds)
            return
        }
        running = true
        fadeGain = 0f
        fadeTo(1f, fadeInSeconds)
        thread = Thread({ loop() }, "quiete-noise").apply { start() }
    }

    /** Porta il guadagno di dissolvenza a [target] in [seconds] secondi, in modo lineare. */
    fun fadeTo(target: Float, seconds: Float) {
        val samples = max(1f, seconds * SAMPLE_RATE)
        fadeStep = max(abs(target - fadeGain) / samples, 1e-7f)
        fadeTarget = target
    }

    fun stop() {
        running = false
        thread?.join(800)
        thread = null
    }

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(max(minBuf, FRAMES * 2 * 4) * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        val buffer = FloatArray(FRAMES * 2)
        try {
            track.play()
            while (running) {
                render(buffer)
                var offset = 0
                while (offset < buffer.size && running) {
                    val written = track.write(buffer, offset, buffer.size - offset, AudioTrack.WRITE_BLOCKING)
                    if (written < 0) { running = false; break }
                    offset += written
                }
            }
        } catch (_: Exception) {
            running = false
        } finally {
            try { track.pause(); track.flush() } catch (_: Exception) {}
            track.release()
        }
    }

    private fun render(out: FloatArray) {
        val t = type
        val targetVol = volume * volume * duck
        // Frequenza di taglio: da ~18 kHz (neutro) a ~250 Hz (molto caldo), scala logaritmica.
        val w = warmth.coerceIn(0f, 1f)
        val cutoff = 18000.0 * exp(ln(250.0 / 18000.0) * w)
        val alpha = (1.0 - exp(-2.0 * PI * cutoff / SAMPLE_RATE)).toFloat()
        val makeUp = 1f + 0.9f * w // compensa la perdita di volume del filtro

        var g = fadeGain
        val tgt = fadeTarget
        val step = fadeStep
        var v = smoothedVol

        for (i in 0 until FRAMES) {
            if (g < tgt) g = min(tgt, g + step) else if (g > tgt) g = max(tgt, g - step)
            v += (targetVol - v) * 0.0004f

            var mod = 1f
            if (t == NoiseType.WAVES) {
                wavePhase += WAVE_INC
                wavePhase2 += WAVE_INC2
                if (wavePhase > 2 * PI) wavePhase -= 2 * PI
                if (wavePhase2 > 2 * PI) wavePhase2 -= 2 * PI
                val s = 0.5 + 0.5 * sin(wavePhase) * (0.75 + 0.25 * sin(wavePhase2))
                mod = (0.3 + 0.7 * s * s).toFloat() * 1.25f
            }

            val gain = g * v * mod * makeUp
            out[2 * i] = softClip(left.next(t, alpha) * gain)
            out[2 * i + 1] = softClip(right.next(t, alpha) * gain)
        }
        fadeGain = g
        smoothedVol = v
    }

    private fun softClip(x: Float): Float {
        val a = abs(x)
        if (a < 0.8f) return x
        val y = 0.8f + 0.2f * tanh((a - 0.8f) / 0.2f)
        return if (x < 0) -y else y
    }

    /** Stato indipendente per ciascun canale: così il suono è ampio e naturale. */
    private class Channel(seed: Int) {
        private var s = seed
        private var brown = 0f
        private var deep = 0f
        private var warm = 0f
        private var b0 = 0f; private var b1 = 0f; private var b2 = 0f
        private var b3 = 0f; private var b4 = 0f; private var b5 = 0f; private var b6 = 0f

        private fun white(): Float {
            s = s xor (s shl 13)
            s = s xor (s ushr 17)
            s = s xor (s shl 5)
            return s.toFloat() / Int.MAX_VALUE
        }

        fun next(type: NoiseType, alpha: Float): Float {
            val w = white()
            val raw = when (type) {
                NoiseType.BROWN, NoiseType.WAVES -> {
                    brown = (brown + 0.02f * w) / 1.02f
                    brown * 3.5f
                }
                NoiseType.DEEP_BROWN -> {
                    brown = (brown + 0.02f * w) / 1.02f
                    deep += 0.04f * (brown - deep)
                    deep * 4.5f
                }
                NoiseType.PINK -> {
                    // Filtro di Paul Kellet
                    b0 = 0.99886f * b0 + w * 0.0555179f
                    b1 = 0.99332f * b1 + w * 0.0750759f
                    b2 = 0.96900f * b2 + w * 0.1538520f
                    b3 = 0.86650f * b3 + w * 0.3104856f
                    b4 = 0.55000f * b4 + w * 0.5329522f
                    b5 = -0.7616f * b5 - w * 0.0168980f
                    val p = b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362f
                    b6 = w * 0.115926f
                    p * 0.11f
                }
                NoiseType.WHITE -> w * 0.25f
            }
            warm += alpha * (raw - warm)
            return warm
        }
    }

    companion object {
        const val SAMPLE_RATE = 44100
        private const val FRAMES = 2048
        private const val WAVE_INC = 2 * PI * 0.09 / SAMPLE_RATE   // ~11 s per onda
        private const val WAVE_INC2 = 2 * PI * 0.031 / SAMPLE_RATE // irregolarità lenta
    }
}
