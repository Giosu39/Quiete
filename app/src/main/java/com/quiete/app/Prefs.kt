package com.quiete.app

import android.content.Context
import androidx.annotation.StringRes

enum class ThemeMode(@StringRes val label: Int) {
    AUTO(R.string.theme_auto), DAY(R.string.theme_day), NIGHT(R.string.theme_night)
}

data class Settings(
    val noise: NoiseType = NoiseType.BROWN,
    val volume: Float = 0.6f,
    val warmth: Float = 0.2f,
    /** Timer ricordato di giorno, in minuti (0 = nessun timer). */
    val dayTimer: Int = 30,
    /** Timer ricordato di notte, in minuti (0 = nessun timer). */
    val nightTimer: Int = 60,
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val nightStartHour: Int = 23,
    val nightEndHour: Int = 7,
    val fadeOutSeconds: Int = 60,
    val stopOnUnplug: Boolean = true,
    val mixWithOthers: Boolean = false,
    val autoDimAtNight: Boolean = true,
)

class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("quiete", Context.MODE_PRIVATE)

    fun load(): Settings {
        val d = Settings()
        return Settings(
            noise = runCatching { NoiseType.valueOf(sp.getString("noise", d.noise.name)!!) }.getOrDefault(d.noise),
            volume = sp.getFloat("volume", d.volume),
            warmth = sp.getFloat("warmth", d.warmth),
            dayTimer = sp.getInt("dayTimer", d.dayTimer),
            nightTimer = sp.getInt("nightTimer", d.nightTimer),
            themeMode = runCatching { ThemeMode.valueOf(sp.getString("themeMode", d.themeMode.name)!!) }.getOrDefault(d.themeMode),
            nightStartHour = sp.getInt("nightStartHour", d.nightStartHour),
            nightEndHour = sp.getInt("nightEndHour", d.nightEndHour),
            fadeOutSeconds = sp.getInt("fadeOutSeconds", d.fadeOutSeconds),
            stopOnUnplug = sp.getBoolean("stopOnUnplug", d.stopOnUnplug),
            mixWithOthers = sp.getBoolean("mixWithOthers", d.mixWithOthers),
            autoDimAtNight = sp.getBoolean("autoDimAtNight", d.autoDimAtNight),
        )
    }

    fun save(s: Settings) {
        sp.edit()
            .putString("noise", s.noise.name)
            .putFloat("volume", s.volume)
            .putFloat("warmth", s.warmth)
            .putInt("dayTimer", s.dayTimer)
            .putInt("nightTimer", s.nightTimer)
            .putString("themeMode", s.themeMode.name)
            .putInt("nightStartHour", s.nightStartHour)
            .putInt("nightEndHour", s.nightEndHour)
            .putInt("fadeOutSeconds", s.fadeOutSeconds)
            .putBoolean("stopOnUnplug", s.stopOnUnplug)
            .putBoolean("mixWithOthers", s.mixWithOthers)
            .putBoolean("autoDimAtNight", s.autoDimAtNight)
            .apply()
    }
}
