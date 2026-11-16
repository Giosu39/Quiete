package com.quiete.app

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlayerState(
    val playing: Boolean = false,
    val noise: NoiseType = NoiseType.BROWN,
    val volume: Float = 0.6f,
    val warmth: Float = 0.2f,
    /** Istante di fine timer in [SystemClock.elapsedRealtime], null = nessun timer. */
    val timerEndAt: Long? = null,
    val fadingOut: Boolean = false,
)

/** Punto unico di controllo della riproduzione, condiviso da UI e servizio. */
object SleepPlayer {
    private val engine = NoiseEngine()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var timerJob: Job? = null
    private var stopJob: Job? = null
    private var switchJob: Job? = null

    var fadeOutSeconds: Int = 60

    /** Applica le preferenze salvate (solo se non sta già suonando). */
    fun restore(s: Settings) {
        fadeOutSeconds = s.fadeOutSeconds
        if (_state.value.playing) return
        engine.type = s.noise
        engine.volume = s.volume
        engine.warmth = s.warmth
        _state.update { it.copy(noise = s.noise, volume = s.volume, warmth = s.warmth) }
    }

    fun play(context: Context, timerMinutes: Int) {
        if (_state.value.playing) return
        val pendingStop = stopJob
        stopJob = null
        _state.update { it.copy(playing = true, fadingOut = false) }
        scope.launch {
            // Se c'è uno stop in corso, aspetta che finisca prima di ripartire.
            pendingStop?.cancelAndJoin()
            engine.duck = 1f
            engine.start(fadeInSeconds = 5f)
        }
        setTimer(timerMinutes)
        ContextCompat.startForegroundService(
            context, Intent(context.applicationContext, NoiseService::class.java)
        )
    }

    fun stop() {
        if (!_state.value.playing) return
        timerJob?.cancel()
        _state.update { it.copy(playing = false, timerEndAt = null, fadingOut = false) }
        stopJob = scope.launch {
            engine.fadeTo(0f, 0.6f)
            delay(650)
            withContext(Dispatchers.Default) { engine.stop() }
        }
    }

    fun setTimer(minutes: Int) {
        setTimerMs(if (minutes > 0) minutes * 60_000L else 0L)
    }

    fun addMinutes(minutes: Int) {
        val s = _state.value
        if (!s.playing) return
        val remaining = s.timerEndAt?.let { (it - SystemClock.elapsedRealtime()).coerceAtLeast(0) } ?: 0L
        setTimerMs(remaining + minutes * 60_000L)
    }

    private fun setTimerMs(totalMs: Long) {
        timerJob?.cancel()
        if (_state.value.fadingOut) engine.fadeTo(1f, 2f)
        if (!_state.value.playing || totalMs <= 0) {
            _state.update { it.copy(timerEndAt = null, fadingOut = false) }
            return
        }
        val endAt = SystemClock.elapsedRealtime() + totalMs
        _state.update { it.copy(timerEndAt = endAt, fadingOut = false) }
        timerJob = scope.launch {
            val fadeMs = minOf(fadeOutSeconds * 1000L, totalMs)
            delay(totalMs - fadeMs)
            _state.update { it.copy(fadingOut = true) }
            engine.fadeTo(0f, fadeMs / 1000f)
            delay(fadeMs + 200)
            _state.update { it.copy(playing = false, timerEndAt = null, fadingOut = false) }
            stopJob = scope.launch { withContext(Dispatchers.Default) { engine.stop() } }
        }
    }

    fun setNoise(type: NoiseType) {
        _state.update { it.copy(noise = type) }
        if (!_state.value.playing || _state.value.fadingOut) {
            engine.type = type
            return
        }
        // Breve dissolvenza incrociata per evitare scatti.
        switchJob?.cancel()
        switchJob = scope.launch {
            engine.fadeTo(0f, 0.25f)
            delay(280)
            engine.type = type
            if (_state.value.playing && !_state.value.fadingOut) engine.fadeTo(1f, 0.6f)
        }
    }

    fun setVolume(v: Float) {
        engine.volume = v
        _state.update { it.copy(volume = v) }
    }

    fun setWarmth(w: Float) {
        engine.warmth = w
        _state.update { it.copy(warmth = w) }
    }

    fun setDuck(level: Float) {
        engine.duck = level
    }
}
