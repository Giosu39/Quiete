package com.quiete.app.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quiete.app.NoiseType
import com.quiete.app.Prefs
import com.quiete.app.R
import com.quiete.app.Settings
import com.quiete.app.SleepPlayer
import com.quiete.app.ThemeMode
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val TimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val TimerOptions = listOf(0, 15, 30, 45, 60, 90, 120)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuieteApp(
    prefs: Prefs,
    setBrightness: (Float?) -> Unit,
    setLightSystemBars: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(prefs.load()) }
    val update: ((Settings) -> Settings) -> Unit = { transform ->
        settings = transform(settings)
        prefs.save(settings)
        SleepPlayer.fadeOutSeconds = settings.fadeOutSeconds
    }

    val player by SleepPlayer.state.collectAsStateWithLifecycle()

    // Orologio aggiornato allo scoccare di ogni minuto.
    val now by produceState(LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            delay((60 - LocalTime.now().second) * 1000L + 50)
        }
    }
    // Conto alla rovescia del timer, ogni secondo solo quando serve.
    val nowMs by produceState(SystemClock.elapsedRealtime(), player.timerEndAt) {
        while (true) {
            value = SystemClock.elapsedRealtime()
            delay(1000)
        }
    }

    val phase = resolvePhase(settings, now)
    val palette = animatedPalette(paletteFor(phase))
    val isNight = phase == Phase.NIGHT
    val selectedTimer = if (isNight) settings.nightTimer else settings.dayTimer

    var showSettings by remember { mutableStateOf(false) }
    var blackout by remember { mutableStateOf(false) }
    var lastTouch by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    LaunchedEffect(palette.isLight) { setLightSystemBars(palette.isLight) }

    // Di notte, mentre suona, lo schermo si oscura da solo dopo 20 s senza tocchi.
    LaunchedEffect(isNight, player.playing, lastTouch, settings.autoDimAtNight, blackout, showSettings) {
        if (isNight && player.playing && settings.autoDimAtNight && !blackout && !showSettings) {
            delay(20_000)
            blackout = true
        }
    }
    DisposableEffect(blackout) {
        setBrightness(if (blackout) 0.01f else null)
        onDispose { setBrightness(null) }
    }

    val statusText = when {
        !player.playing -> "Tocca per iniziare"
        player.fadingOut -> "Buon riposo…"
        player.timerEndAt != null ->
            "Si ferma tra " + formatDuration((player.timerEndAt!! - nowMs).coerceAtLeast(0))
        else -> "Senza timer"
    }

    val scheme = if (palette.isLight)
        lightColorScheme(primary = palette.primary, onPrimary = palette.onPrimary, surface = palette.sheet, onSurface = palette.text, background = palette.bgTop)
    else
        darkColorScheme(primary = palette.primary, onPrimary = palette.onPrimary, surface = palette.sheet, onSurface = palette.text, background = palette.bgTop)

    MaterialTheme(colorScheme = scheme) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(palette.bgTop, palette.bgBottom)))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                            lastTouch = SystemClock.elapsedRealtime()
                        }
                    }
                }
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Intestazione
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(greeting(phase, now), color = palette.muted, fontSize = 15.sp, letterSpacing = 1.sp)
                        Text(
                            now.format(TimeFmt), color = palette.text,
                            fontSize = 56.sp, fontWeight = FontWeight.ExtraLight,
                        )
                    }
                    RoundButton(palette, onClick = { blackout = true }) {
                        Icon(painterResource(R.drawable.ic_moon), "Oscura schermo", tint = palette.text, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    RoundButton(palette, onClick = { showSettings = true }) {
                        Icon(Icons.Outlined.Settings, "Impostazioni", tint = palette.text, modifier = Modifier.size(22.dp))
                    }
                }

                Spacer(Modifier.weight(1f))

                BreathingOrb(player.playing, palette) {
                    if (player.playing) SleepPlayer.stop() else SleepPlayer.play(context, selectedTimer)
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "Rumore " + player.noise.label.lowercase(), color = palette.text,
                    fontSize = 22.sp, fontWeight = FontWeight.Light,
                )
                Text(player.noise.description, color = palette.muted, fontSize = 14.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text(statusText, color = palette.primary, fontSize = 15.sp, letterSpacing = 0.5.sp)

                Spacer(Modifier.weight(1f))

                // Tipo di rumore
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NoiseType.entries.forEach { type ->
                        Pill(type.label, player.noise == type, palette) {
                            SleepPlayer.setNoise(type)
                            update { it.copy(noise = type) }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                SoftSlider(
                    label = "Volume", valueText = "${(player.volume * 100).toInt()}%",
                    value = player.volume, palette = palette,
                    onChange = { SleepPlayer.setVolume(it) },
                    onDone = { update { it.copy(volume = SleepPlayer.state.value.volume) } },
                )
                SoftSlider(
                    label = "Tono", valueText = warmthLabel(player.warmth),
                    value = player.warmth, palette = palette,
                    onChange = { SleepPlayer.setWarmth(it) },
                    onDone = { update { it.copy(warmth = SleepPlayer.state.value.warmth) } },
                )

                Spacer(Modifier.height(10.dp))

                // Timer
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Timer", color = palette.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    if (player.playing && player.timerEndAt != null) {
                        Text(
                            "+15 min", color = palette.primary, fontSize = 13.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { SleepPlayer.addMinutes(15) }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TimerOptions.forEach { m ->
                        Pill(timerLabel(m), m == selectedTimer, palette) {
                            update { if (isNight) it.copy(nightTimer = m) else it.copy(dayTimer = m) }
                            if (player.playing) SleepPlayer.setTimer(m)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            AnimatedVisibility(
                visible = blackout,
                enter = fadeIn(tween(1500)),
                exit = fadeOut(tween(400)),
            ) {
                Blackout(now, statusText) { blackout = false }
            }
        }

        if (showSettings) {
            ModalBottomSheet(
                onDismissRequest = { showSettings = false },
                containerColor = palette.sheet,
                contentColor = palette.text,
                dragHandle = { BottomSheetDefaults.DragHandle(color = palette.muted) },
            ) {
                SettingsContent(settings, palette, update)
            }
        }
    }
}

@Composable
private fun BreathingOrb(playing: Boolean, palette: Palette, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "breath")
    // 5 s di inspiro + 5 s di espiro: circa 6 respiri al minuto, il ritmo del rilassamento.
    val breath by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(5000, easing = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)),
            RepeatMode.Reverse,
        ),
        label = "breathValue",
    )
    val amount by animateFloatAsState(if (playing) 1f else 0f, tween(1500), label = "amount")
    val b = breath * amount
    val scale = 1f + 0.08f * b
    val glowAlpha = 0.18f + 0.4f * b

    Box(Modifier.size(270.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(
                Brush.radialGradient(
                    listOf(palette.glow.copy(alpha = glowAlpha), Color.Transparent),
                    center = center, radius = r * scale,
                ),
                radius = r * scale,
            )
            drawCircle(
                palette.primary.copy(alpha = 0.25f + 0.2f * b),
                radius = r * 0.66f * scale,
                style = Stroke(width = 1.2.dp.toPx()),
            )
            drawCircle(
                Brush.radialGradient(
                    listOf(palette.primary, palette.primary.copy(alpha = 0.7f)),
                    center = center, radius = r * 0.55f * scale,
                ),
                radius = r * 0.55f * scale,
            )
        }
        Box(
            Modifier
                .size(150.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(40.dp)) {
                val w = size.width
                val h = size.height
                if (playing) {
                    val barW = w * 0.2f
                    val radius = CornerRadius(barW / 2, barW / 2)
                    drawRoundRect(palette.onPrimary, Offset(w * 0.22f, h * 0.15f), Size(barW, h * 0.7f), radius)
                    drawRoundRect(palette.onPrimary, Offset(w * 0.58f, h * 0.15f), Size(barW, h * 0.7f), radius)
                } else {
                    val p = Path().apply {
                        moveTo(w * 0.3f, h * 0.16f)
                        lineTo(w * 0.3f, h * 0.84f)
                        lineTo(w * 0.86f, h * 0.5f)
                        close()
                    }
                    drawPath(p, palette.onPrimary)
                }
            }
        }
    }
}

@Composable
private fun Blackout(now: LocalTime, status: String, onExit: () -> Unit) {
    // Leggero spostamento ogni minuto per non "segnare" gli schermi OLED.
    val dx = ((now.minute * 7) % 40 - 20).dp
    val dy = ((now.minute * 13) % 80 - 40).dp
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onExit,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.offset(dx, dy), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(now.format(TimeFmt), color = Color(0xFF3A2E24), fontSize = 52.sp, fontWeight = FontWeight.ExtraLight)
            Text(status, color = Color(0xFF2E251D), fontSize = 14.sp)
            Spacer(Modifier.height(28.dp))
            Text("Tocca per tornare", color = Color(0xFF211A15), fontSize = 12.sp)
        }
    }
}

@Composable
private fun SettingsContent(settings: Settings, palette: Palette, update: ((Settings) -> Settings) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
    ) {
        Text("Impostazioni", fontSize = 22.sp, fontWeight = FontWeight.Light, color = palette.text)

        SectionTitle("Tema", palette)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                Pill(mode.label, settings.themeMode == mode, palette) { update { it.copy(themeMode = mode) } }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "In automatico: giorno chiaro, sera indaco dalle %02d:00, notte scura e calda dalle %02d:00 alle %02d:00."
                .format((settings.nightStartHour + 21) % 24, settings.nightStartHour, settings.nightEndHour),
            color = palette.muted, fontSize = 13.sp,
        )

        SectionTitle("Orario della notte", palette)
        HourStepper("Inizia alle", settings.nightStartHour, palette) { h -> update { it.copy(nightStartHour = h) } }
        HourStepper("Finisce alle", settings.nightEndHour, palette) { h -> update { it.copy(nightEndHour = h) } }
        Text(
            "Di notte e di giorno l'app ricorda due timer diversi.",
            color = palette.muted, fontSize = 13.sp,
        )

        SectionTitle("Dissolvenza finale del timer", palette)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(10 to "10 s", 30 to "30 s", 60 to "1 min", 180 to "3 min", 300 to "5 min").forEach { (sec, label) ->
                Pill(label, settings.fadeOutSeconds == sec, palette) { update { it.copy(fadeOutSeconds = sec) } }
            }
        }

        SectionTitle("Comportamento", palette)
        ToggleRow("Ferma se scolleghi le cuffie", null, settings.stopOnUnplug, palette) { v ->
            update { it.copy(stopOnUnplug = v) }
        }
        ToggleRow("Suona insieme ad altre app", "Per ascoltare podcast o audiolibri sopra il rumore", settings.mixWithOthers, palette) { v ->
            update { it.copy(mixWithOthers = v) }
        }
        ToggleRow("Oscura lo schermo di notte", "Dopo 20 secondi senza tocchi, mentre suona", settings.autoDimAtNight, palette) { v ->
            update { it.copy(autoDimAtNight = v) }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Le opzioni su cuffie e altre app valgono dalla prossima riproduzione.",
            color = palette.muted, fontSize = 12.sp,
        )
    }
}

@Composable
private fun SectionTitle(text: String, palette: Palette) {
    Spacer(Modifier.height(22.dp))
    Text(text.uppercase(), color = palette.muted, fontSize = 12.sp, letterSpacing = 1.5.sp)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun HourStepper(label: String, hour: Int, palette: Palette, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = palette.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        RoundButton(palette, size = 38, onClick = { onChange((hour + 23) % 24) }) {
            Text("−", color = palette.text, fontSize = 18.sp)
        }
        Text(
            "%02d:00".format(hour), color = palette.text, fontSize = 17.sp,
            textAlign = TextAlign.Center, modifier = Modifier.width(72.dp),
        )
        RoundButton(palette, size = 38, onClick = { onChange((hour + 1) % 24) }) {
            Text("+", color = palette.text, fontSize = 18.sp)
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String?, checked: Boolean, palette: Palette, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = palette.text, fontSize = 15.sp)
            if (subtitle != null) Text(subtitle, color = palette.muted, fontSize = 13.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = palette.onPrimary,
                checkedTrackColor = palette.primary,
                checkedBorderColor = palette.primary,
                uncheckedThumbColor = palette.muted,
                uncheckedTrackColor = Color.Transparent,
                uncheckedBorderColor = palette.muted,
            ),
        )
    }
}

@Composable
private fun Pill(text: String, selected: Boolean, palette: Palette, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) palette.primary else palette.surface, tween(400), label = "pillBg")
    val fg by animateColorAsState(if (selected) palette.onPrimary else palette.text, tween(400), label = "pillFg")
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(text, color = fg, fontSize = 14.sp)
    }
}

@Composable
private fun RoundButton(palette: Palette, size: Int = 44, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(palette.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun SoftSlider(
    label: String,
    valueText: String,
    value: Float,
    palette: Palette,
    onChange: (Float) -> Unit,
    onDone: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, color = palette.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(valueText, color = palette.muted, fontSize = 13.sp)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onDone,
            colors = SliderDefaults.colors(
                thumbColor = palette.primary,
                activeTrackColor = palette.primary,
                inactiveTrackColor = palette.primary.copy(alpha = 0.18f),
            ),
        )
    }
}

private fun greeting(phase: Phase, now: LocalTime): String = when {
    phase == Phase.NIGHT -> "Buonanotte"
    now.hour in 5..11 -> "Buongiorno"
    now.hour in 12..17 -> "Buon pomeriggio"
    now.hour in 18..21 -> "Buonasera"
    else -> "Buonanotte"
}

private fun warmthLabel(w: Float): String = when {
    w < 0.15f -> "Naturale"
    w < 0.45f -> "Morbido"
    w < 0.75f -> "Caldo"
    else -> "Ovattato"
}

private fun timerLabel(m: Int): String = when {
    m == 0 -> "∞"
    m < 60 -> "$m min"
    m % 60 == 0 -> "${m / 60} h"
    else -> "${m / 60} h ${m % 60}"
}

private fun formatDuration(ms: Long): String {
    val total = (ms + 999) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
