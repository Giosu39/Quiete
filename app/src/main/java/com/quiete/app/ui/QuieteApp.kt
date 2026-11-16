package com.quiete.app.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.ProvideTextStyle
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
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
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

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
    // Conto alla rovescia del timer.
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
        ProvideTextStyle(TextStyle(fontFamily = Nunito, color = palette.text)) {
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
                AmbientLayer(phase, palette)

                Column(
                    Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Intestazione
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                greeting(phase, now), color = palette.muted,
                                fontSize = 16.sp, letterSpacing = 0.5.sp,
                            )
                            Text(
                                now.format(TimeFmt), color = palette.text,
                                fontSize = 64.sp, fontWeight = FontWeight.ExtraLight,
                                lineHeight = 68.sp, letterSpacing = (-1).sp,
                            )
                        }
                        RoundButton(palette, onClick = { blackout = true }) {
                            Icon(painterResource(R.drawable.ic_moon), "Oscura schermo", tint = palette.text, modifier = Modifier.size(19.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        RoundButton(palette, onClick = { showSettings = true }) {
                            Icon(Icons.Outlined.Settings, "Impostazioni", tint = palette.text, modifier = Modifier.size(21.dp))
                        }
                    }

                    // Cerchio: occupa lo spazio disponibile, fino a 290 dp.
                    Box(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        BreathingOrb(
                            playing = player.playing,
                            palette = palette,
                            modifier = Modifier.heightIn(max = 290.dp).fillMaxHeight().aspectRatio(1f),
                        ) {
                            if (player.playing) SleepPlayer.stop() else SleepPlayer.play(context, selectedTimer)
                        }
                    }

                    Text(
                        "Rumore " + player.noise.label.lowercase(), color = palette.text,
                        fontSize = 24.sp, fontWeight = FontWeight.Light,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        player.noise.description, color = palette.muted,
                        fontSize = 15.sp, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(14.dp))
                    StatusChip(statusText, player.playing, palette)
                    Spacer(Modifier.height(20.dp))

                    // Pannello dei controlli
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(palette.surface)
                            .padding(horizontal = 16.dp, vertical = 18.dp)
                    ) {
                        Segmented(
                            options = NoiseType.entries,
                            selected = player.noise,
                            label = { it.short },
                            palette = palette,
                        ) { type ->
                            SleepPlayer.setNoise(type)
                            update { it.copy(noise = type) }
                        }

                        Spacer(Modifier.height(16.dp))
                        SliderRow("Volume", "${(player.volume * 100).toInt()}%", player.volume, palette,
                            onChange = { SleepPlayer.setVolume(it) },
                            onDone = { update { it.copy(volume = SleepPlayer.state.value.volume) } },
                        )
                        SliderRow("Tono", warmthLabel(player.warmth), player.warmth, palette,
                            onChange = { SleepPlayer.setWarmth(it) },
                            onDone = { update { it.copy(warmth = SleepPlayer.state.value.warmth) } },
                        )

                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.fillMaxWidth().height(28.dp).padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Timer · minuti", color = palette.muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            if (player.playing && player.timerEndAt != null) {
                                Text(
                                    "+15 min", color = palette.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(palette.primary.copy(alpha = 0.12f))
                                        .clickable { SleepPlayer.addMinutes(15) }
                                        .padding(horizontal = 12.dp, vertical = 3.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Segmented(
                            options = TimerOptions,
                            selected = selectedTimer,
                            label = { if (it == 0) "∞" else it.toString() },
                            palette = palette,
                        ) { m ->
                            update { if (isNight) it.copy(nightTimer = m) else it.copy(dayTimer = m) }
                            if (player.playing) SleepPlayer.setTimer(m)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
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
                    dragHandle = { BottomSheetDefaults.DragHandle(color = palette.muted.copy(alpha = 0.5f)) },
                ) {
                    ProvideTextStyle(TextStyle(fontFamily = Nunito, color = palette.text)) {
                        SettingsContent(settings, palette, update)
                    }
                }
            }
        }
    }
}

/** Sfondo vivo ma discreto: due aloni che si spostano lentamente e, la sera e la notte, stelle che brillano piano. */
private data class Star(val x: Float, val y: Float, val radius: Float, val phase: Float)

@Composable
private fun AmbientLayer(phase: Phase, palette: Palette) {
    val transition = rememberInfiniteTransition(label = "ambient")
    val drift by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(45_000, easing = LinearEasing), RepeatMode.Reverse), label = "drift",
    )
    val twinkle by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(9_000, easing = LinearEasing), RepeatMode.Restart), label = "twinkle",
    )
    val starsAlpha by animateFloatAsState(
        when (phase) { Phase.NIGHT -> 1f; Phase.EVENING -> 0.6f; Phase.DAY -> 0f }, tween(2500), label = "stars",
    )
    val stars = remember {
        val rnd = Random(7)
        List(42) { Star(rnd.nextFloat(), rnd.nextFloat() * 0.55f, 0.5f + rnd.nextFloat() * 1.1f, rnd.nextFloat()) }
    }

    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val c1 = Offset(w * (0.1f + 0.3f * drift), h * (0.22f + 0.06f * drift))
        drawCircle(
            Brush.radialGradient(listOf(palette.glow.copy(alpha = 0.28f), Color.Transparent), center = c1, radius = w * 0.75f),
            radius = w * 0.75f, center = c1,
        )
        val c2 = Offset(w * (0.95f - 0.3f * drift), h * (0.72f - 0.05f * drift))
        drawCircle(
            Brush.radialGradient(listOf(palette.glow.copy(alpha = 0.18f), Color.Transparent), center = c2, radius = w * 0.85f),
            radius = w * 0.85f, center = c2,
        )
        if (starsAlpha > 0.01f) {
            stars.forEach { s ->
                val tw = 0.5f + 0.5f * sin(2 * PI * (twinkle + s.phase)).toFloat()
                drawCircle(
                    palette.text.copy(alpha = (0.15f + 0.45f * tw) * starsAlpha),
                    radius = s.radius.dp.toPx(),
                    center = Offset(s.x * w, s.y * h),
                )
            }
        }
    }
}

@Composable
private fun BreathingOrb(playing: Boolean, palette: Palette, modifier: Modifier, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "breath")
    // 5 s di inspiro + 5 s di espiro: circa 6 respiri al minuto.
    val breath by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(5000, easing = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)),
            RepeatMode.Reverse,
        ),
        label = "breathValue",
    )
    val amount by animateFloatAsState(if (playing) 1f else 0f, tween(1500), label = "amount")

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val b = breath * amount
            val scale = 1f + 0.07f * b
            val r = size.minDimension / 2f
            val core = r * 0.56f * scale

            // Alone esterno
            drawCircle(
                Brush.radialGradient(
                    listOf(palette.glow.copy(alpha = 0.35f + 0.35f * b), Color.Transparent),
                    center = center, radius = r,
                ),
                radius = r,
            )
            // Due anelli sottili che si allargano col respiro
            drawCircle(
                palette.primary.copy(alpha = 0.10f + 0.12f * b),
                radius = core * (1.32f + 0.10f * b),
                style = Stroke(width = 1.dp.toPx()),
            )
            drawCircle(
                palette.primary.copy(alpha = 0.18f + 0.15f * b),
                radius = core * 1.16f,
                style = Stroke(width = 1.2.dp.toPx()),
            )
            // Sfera: luce morbida in alto a sinistra
            val light = lerp(palette.primary, Color.White, 0.28f)
            val shade = lerp(palette.primary, Color.Black, 0.12f)
            drawCircle(
                Brush.radialGradient(
                    listOf(light, palette.primary, shade),
                    center = Offset(center.x - core * 0.35f, center.y - core * 0.4f),
                    radius = core * 1.7f,
                ),
                radius = core,
            )
        }
        Box(
            Modifier
                .fillMaxSize(0.56f)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .semantics { contentDescription = if (playing) "Ferma" else "Avvia" },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize(0.26f)) {
                val w = size.width
                val h = size.height
                if (playing) {
                    val barW = w * 0.22f
                    val radius = CornerRadius(barW / 2, barW / 2)
                    drawRoundRect(palette.onPrimary, Offset(w * 0.2f, h * 0.12f), Size(barW, h * 0.76f), radius)
                    drawRoundRect(palette.onPrimary, Offset(w * 0.58f, h * 0.12f), Size(barW, h * 0.76f), radius)
                } else {
                    val p = Path().apply {
                        moveTo(w * 0.28f, h * 0.12f)
                        lineTo(w * 0.28f, h * 0.88f)
                        lineTo(w * 0.9f, h * 0.5f)
                        close()
                    }
                    drawPath(p, palette.onPrimary)
                }
            }
        }
    }
}

@Composable
private fun StatusChip(text: String, playing: Boolean, palette: Palette) {
    val transition = rememberInfiniteTransition(label = "dot")
    val pulse by transition.animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "pulse",
    )
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(palette.primary.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(7.dp)) {
            drawCircle(palette.primary.copy(alpha = if (playing) pulse else 0.5f))
        }
        Spacer(Modifier.width(8.dp))
        Text(text, color = palette.primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Controllo a segmenti: tutte le opzioni sempre visibili, nessuna tagliata. */
@Composable
private fun <T> Segmented(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    palette: Palette,
    onSelect: (T) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(palette.text.copy(alpha = 0.06f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val bg by animateColorAsState(if (isSelected) palette.primary else Color.Transparent, tween(350), label = "segBg")
            val fg by animateColorAsState(if (isSelected) palette.onPrimary else palette.muted, tween(350), label = "segFg")
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(bg)
                    .clickable { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option), color = fg, fontSize = 14.sp, maxLines = 1, softWrap = false,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    palette: Palette,
    onChange: (Float) -> Unit,
    onDone: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Text(label, color = palette.muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(valueText, color = palette.text, fontSize = 14.sp)
        }
        CalmSlider(value, palette, onChange, onDone)
    }
}

/** Cursore sottile con pomello rotondo, più leggero di quello di Material 3. */
@Composable
private fun CalmSlider(value: Float, palette: Palette, onChange: (Float) -> Unit, onDone: () -> Unit) {
    val change by rememberUpdatedState(onChange)
    val done by rememberUpdatedState(onDone)
    val thumbRadius = 9.dp

    Box(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                setProgress { v -> change(v.coerceIn(0f, 1f)); done(); true }
            }
            .pointerInput(Unit) {
                val inset = thumbRadius.toPx()
                detectTapGestures { pos ->
                    change(((pos.x - inset) / (size.width - 2 * inset)).coerceIn(0f, 1f))
                    done()
                }
            }
            .pointerInput(Unit) {
                val inset = thumbRadius.toPx()
                fun toValue(x: Float) = ((x - inset) / (size.width - 2 * inset)).coerceIn(0f, 1f)
                detectHorizontalDragGestures(
                    onDragStart = { pos -> change(toValue(pos.x)) },
                    onDragEnd = { done() },
                    onDragCancel = { done() },
                ) { pointer, _ ->
                    pointer.consume()
                    change(toValue(pointer.position.x))
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = thumbRadius.toPx()
            val cy = size.height / 2
            val left = r
            val right = size.width - r
            val x = left + (right - left) * value
            val track = 4.dp.toPx()
            drawLine(palette.primary.copy(alpha = 0.18f), Offset(left, cy), Offset(right, cy), track, StrokeCap.Round)
            drawLine(palette.primary, Offset(left, cy), Offset(x, cy), track, StrokeCap.Round)
            drawCircle(palette.primary.copy(alpha = 0.16f), r * 1.75f, Offset(x, cy))
            drawCircle(palette.primary, r, Offset(x, cy))
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
            Text(now.format(TimeFmt), color = Color(0xFF3A2E24), fontSize = 56.sp, fontWeight = FontWeight.ExtraLight)
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
        Text("Impostazioni", fontSize = 24.sp, fontWeight = FontWeight.Light, color = palette.text)

        SectionTitle("Tema", palette)
        Segmented(ThemeMode.entries, settings.themeMode, { it.label }, palette) { mode ->
            update { it.copy(themeMode = mode) }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "In automatico: giorno chiaro, sera indaco dalle %02d:00, notte scura e calda dalle %02d:00 alle %02d:00."
                .format((settings.nightStartHour + 21) % 24, settings.nightStartHour, settings.nightEndHour),
            color = palette.muted, fontSize = 14.sp,
        )

        SectionTitle("Orario della notte", palette)
        HourStepper("Inizia alle", settings.nightStartHour, palette) { h -> update { it.copy(nightStartHour = h) } }
        HourStepper("Finisce alle", settings.nightEndHour, palette) { h -> update { it.copy(nightEndHour = h) } }
        Text(
            "Di notte e di giorno l'app ricorda due timer diversi.",
            color = palette.muted, fontSize = 14.sp,
        )

        SectionTitle("Dissolvenza finale del timer", palette)
        val fades = listOf(10, 30, 60, 180, 300)
        Segmented(fades, settings.fadeOutSeconds, { if (it < 60) "${it}s" else "${it / 60}m" }, palette) { sec ->
            update { it.copy(fadeOutSeconds = sec) }
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
            color = palette.muted, fontSize = 13.sp,
        )
    }
}

@Composable
private fun SectionTitle(text: String, palette: Palette) {
    Spacer(Modifier.height(24.dp))
    Text(text.uppercase(), color = palette.muted, fontSize = 12.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun HourStepper(label: String, hour: Int, palette: Palette, onChange: (Int) -> Unit) {
    val buttonBg = palette.text.copy(alpha = 0.07f)
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = palette.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        RoundButton(palette, size = 38, background = buttonBg, onClick = { onChange((hour + 23) % 24) }) {
            Text("−", color = palette.text, fontSize = 18.sp)
        }
        Text(
            "%02d:00".format(hour), color = palette.text, fontSize = 18.sp,
            textAlign = TextAlign.Center, modifier = Modifier.width(76.dp),
        )
        RoundButton(palette, size = 38, background = buttonBg, onClick = { onChange((hour + 1) % 24) }) {
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
            Text(title, color = palette.text, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, color = palette.muted, fontSize = 14.sp)
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
private fun RoundButton(
    palette: Palette,
    size: Int = 46,
    background: Color = palette.surface,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
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

private fun formatDuration(ms: Long): String {
    val total = (ms + 999) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
