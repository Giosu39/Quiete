package com.quiete.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.quiete.app.Settings
import com.quiete.app.ThemeMode
import java.time.LocalTime

enum class Phase { DAY, EVENING, NIGHT }

data class Palette(
    val bgTop: Color,
    val bgBottom: Color,
    val surface: Color,
    val sheet: Color,
    val primary: Color,
    val onPrimary: Color,
    val text: Color,
    val muted: Color,
    val glow: Color,
    val isLight: Boolean,
)

/** Giorno: salvia e nebbia. Sera: indaco e lavanda. Notte: nero caldo e ambra tenue (pochissimo blu). */
private val DayPalette = Palette(
    bgTop = Color(0xFFEEF3EF), bgBottom = Color(0xFFD3E2DA),
    surface = Color(0x99FFFFFF), sheet = Color(0xFFF4F8F5),
    primary = Color(0xFF5B8A78), onPrimary = Color(0xFFFFFFFF),
    text = Color(0xFF26352F), muted = Color(0xFF6B7F77),
    glow = Color(0xFF9CC5B3), isLight = true,
)
private val EveningPalette = Palette(
    bgTop = Color(0xFF23253F), bgBottom = Color(0xFF45375A),
    surface = Color(0x1AFFFFFF), sheet = Color(0xFF2C2C49),
    primary = Color(0xFFC7A6E0), onPrimary = Color(0xFF23253F),
    text = Color(0xFFECE6F5), muted = Color(0xFFA99FBF),
    glow = Color(0xFF8E7BB5), isLight = false,
)
private val NightPalette = Palette(
    bgTop = Color(0xFF050608), bgBottom = Color(0xFF110D14),
    surface = Color(0x12FFB98A), sheet = Color(0xFF0E0B10),
    primary = Color(0xFFA8784A), onPrimary = Color(0xFF110D14),
    text = Color(0xFFAE9D88), muted = Color(0xFF6A5E51),
    glow = Color(0xFF5E3820), isLight = false,
)

fun paletteFor(phase: Phase) = when (phase) {
    Phase.DAY -> DayPalette
    Phase.EVENING -> EveningPalette
    Phase.NIGHT -> NightPalette
}

/** Notte tra inizio e fine (anche a cavallo della mezzanotte); sera = le 3 ore prima della notte. */
fun resolvePhase(s: Settings, now: LocalTime): Phase {
    when (s.themeMode) {
        ThemeMode.DAY -> return Phase.DAY
        ThemeMode.NIGHT -> return Phase.NIGHT
        ThemeMode.AUTO -> Unit
    }
    val m = now.hour * 60 + now.minute
    val start = s.nightStartHour * 60
    val end = s.nightEndHour * 60
    val eveningStart = ((start - 180) % 1440 + 1440) % 1440
    return when {
        inRange(m, start, end) -> Phase.NIGHT
        inRange(m, eveningStart, start) -> Phase.EVENING
        else -> Phase.DAY
    }
}

private fun inRange(m: Int, from: Int, to: Int): Boolean =
    if (from == to) false
    else if (from < to) m in from until to
    else m >= from || m < to

@Composable
fun animatedPalette(target: Palette): Palette {
    val spec = tween<Color>(durationMillis = 2500)
    val bgTop by animateColorAsState(target.bgTop, spec, label = "bgTop")
    val bgBottom by animateColorAsState(target.bgBottom, spec, label = "bgBottom")
    val surface by animateColorAsState(target.surface, spec, label = "surface")
    val sheet by animateColorAsState(target.sheet, spec, label = "sheet")
    val primary by animateColorAsState(target.primary, spec, label = "primary")
    val onPrimary by animateColorAsState(target.onPrimary, spec, label = "onPrimary")
    val text by animateColorAsState(target.text, spec, label = "text")
    val muted by animateColorAsState(target.muted, spec, label = "muted")
    val glow by animateColorAsState(target.glow, spec, label = "glow")
    return Palette(bgTop, bgBottom, surface, sheet, primary, onPrimary, text, muted, glow, target.isLight)
}
