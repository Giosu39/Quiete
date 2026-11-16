package com.quiete.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Tiene vivo il processo mentre suona (anche a schermo spento) e mostra
 * una notifica con il tempo rimanente e i comandi rapidi.
 */
class NoiseService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observeJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var focusRequest: AudioFocusRequest? = null
    private var noisyRegistered = false

    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) SleepPlayer.stop()
        }
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> SleepPlayer.stop()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> SleepPlayer.setDuck(0f)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> SleepPlayer.setDuck(0.3f)
            AudioManager.AUDIOFOCUS_GAIN -> SleepPlayer.setDuck(1f)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW).apply {
            description = getString(R.string.channel_desc)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> SleepPlayer.stop()
            ACTION_ADD_15 -> SleepPlayer.addMinutes(15)
        }

        // Va chiamato sempre e subito, altrimenti Android chiude l'app.
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(SleepPlayer.state.value),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        )

        if (!SleepPlayer.state.value.playing) {
            shutdown()
            return START_NOT_STICKY
        }

        if (observeJob == null) {
            setupSession()
            observeJob = scope.launch {
                // Aggiorna la notifica solo quando cambia qualcosa che vi compare (non a ogni movimento del volume).
                SleepPlayer.state
                    .distinctUntilChanged { a, b ->
                        a.playing == b.playing && a.noise == b.noise &&
                            a.timerEndAt == b.timerEndAt && a.fadingOut == b.fadingOut
                    }
                    .collect { s ->
                    if (!s.playing) shutdown()
                    else notificationManager.notify(NOTIFICATION_ID, buildNotification(s))
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun setupSession() {
        val settings = Prefs(this).load()

        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Quiete::noise")
            .apply { acquire(12 * 60 * 60 * 1000L) }

        if (settings.stopOnUnplug) {
            ContextCompat.registerReceiver(
                this, noisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            noisyRegistered = true
        }

        if (!settings.mixWithOthers) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            audioManager.requestAudioFocus(req)
            focusRequest = req
        }
    }

    private fun shutdown() {
        observeJob?.cancel()
        observeJob = null
        releaseSession()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseSession() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        if (noisyRegistered) {
            runCatching { unregisterReceiver(noisyReceiver) }
            noisyRegistered = false
        }
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    override fun onDestroy() {
        releaseSession()
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(s: PlayerState): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, NoiseService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val add = PendingIntent.getService(
            this, 2, Intent(this, NoiseService::class.java).setAction(ACTION_ADD_15),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_moon)
            .setContentTitle(getString(s.noise.title))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, getString(R.string.action_stop), stop)
            .addAction(0, getString(R.string.action_add15), add)

        val end = s.timerEndAt
        if (end != null) {
            val remaining = (end - SystemClock.elapsedRealtime()).coerceAtLeast(0)
            val wallEnd = System.currentTimeMillis() + remaining
            val endTime = DateFormat.getTimeFormat(this).format(Date(wallEnd))
            builder.setContentText(
                if (s.fadingOut) getString(R.string.notif_fading) else getString(R.string.notif_stops_at, endTime)
            )
                .setWhen(wallEnd)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        } else {
            builder.setContentText(getString(R.string.no_timer)).setShowWhen(false)
        }
        return builder.build()
    }

    companion object {
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 42
        const val ACTION_STOP = "com.quiete.app.STOP"
        const val ACTION_ADD_15 = "com.quiete.app.ADD_15"
    }
}
