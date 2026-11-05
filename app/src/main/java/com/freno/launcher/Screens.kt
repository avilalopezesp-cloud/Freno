package com.freno.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import java.text.Normalizer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// --- Estilo: negro, texto y nada más ---
val Ink = Color(0xFFEDEDED)
val Quiet = Color(0xFF8A8A8A)
val Dim = Color(0xFF555555)

val Reasons = listOf("Aburrimiento", "Responder a alguien", "Publicar algo", "Costumbre")

private val Spanish: Locale = Locale.forLanguageTag("es-ES")

@Composable
fun T(
    text: String,
    size: Int,
    color: Color = Ink,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    center: Boolean = false,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size.sp,
            fontWeight = weight,
            textAlign = if (center) TextAlign.Center else TextAlign.Start,
        ),
    )
}

/** Toque y pulsación larga, sin efecto visual. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.tap(onLong: (() -> Unit)? = null, onClick: () -> Unit): Modifier =
    this.combinedClickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onLongClick = onLong,
        onClick = onClick,
    )

fun norm(s: String): String =
    Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

// ============ INICIO ============
@Composable
fun HomeScreen(
    apps: List<AppEntry>,
    prefs: FrenoPrefs,
    onOpen: (AppEntry) -> Unit,
    onMenu: (AppEntry) -> Unit,
    onDrawer: () -> Unit,
    onSettings: () -> Unit,
) {
    BackHandler(enabled = true) {} // en inicio, "atrás" no hace nada

    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(5_000)
        }
    }
    val drawer by rememberUpdatedState(onDrawer)
    val settings by rememberUpdatedState(onSettings)

    val date = now.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Spanish))
        .replaceFirstChar { it.uppercase() }
    val favs = prefs.favorites.mapNotNull { p -> apps.firstOrNull { it.packageName == p } }
    val stopped = prefs.todayStopped()

    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var acc = 0f
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (acc < -60f) drawer()
                        acc = 0f
                    },
                    onDragCancel = { acc = 0f },
                ) { _, dy -> acc += dy }
            }
            .pointerInput(Unit) { detectTapGestures(onLongPress = { settings() }) }
            .systemBarsPadding()
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        Spacer(Modifier.height(32.dp))
        T(now.format(DateTimeFormatter.ofPattern("HH:mm")), 72, weight = FontWeight.Light)
        T(date, 15, Quiet)

        Spacer(Modifier.weight(1f))
        if (favs.isEmpty()) {
            T("Mantén pulsada una app en la lista\npara añadirla aquí.", 14, Dim)
        }
        favs.forEach { app ->
            T(
                app.label, 26,
                if (app.packageName in prefs.braked) Quiet else Ink,
                modifier = Modifier.padding(vertical = 10.dp).tap(onLong = { onMenu(app) }) { onOpen(app) },
            )
        }
        Spacer(Modifier.weight(1f))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            T(if (stopped == 1) "Hoy: 1 frenada" else "Hoy: $stopped frenadas", 13, Dim)
            T("apps ↑", 13, Quiet, modifier = Modifier.padding(8.dp).tap { onDrawer() })
        }
    }
}

// ============ LISTA DE APPS ============
@Composable
fun DrawerScreen(
    apps: List<AppEntry>,
    prefs: FrenoPrefs,
    onOpen: (AppEntry) -> Unit,
    onMenu: (AppEntry) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }

    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }

    val q = norm(query)
    // Las apps ocultas solo aparecen al buscarlas
    val list = apps.filter { if (q.isEmpty()) it.packageName !in prefs.hidden else norm(it.label).contains(q) }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 32.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = Ink, fontSize = 22.sp),
            cursorBrush = SolidColor(Ink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { list.firstOrNull()?.let(onOpen) }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) T("buscar", 22, Dim)
                    inner()
                }
            },
        )
        Spacer(Modifier.height(16.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(list, key = { it.packageName + "/" + it.activity }) { app ->
                T(
                    app.label, 20,
                    if (app.packageName in prefs.braked) Dim else Ink,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 11.dp)
                        .tap(onLong = { onMenu(app) }) { onOpen(app) },
                )
            }
        }
    }
}

// ============ MENÚ AL MANTENER PULSADO ============
@Composable
fun MenuOption(label: String, onClick: () -> Unit) {
    T(label, 17, Ink, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).tap { onClick() })
}

@Composable
fun AppMenu(
    app: AppEntry,
    prefs: FrenoPrefs,
    onDismiss: () -> Unit,
    onUnbrake: () -> Unit,
    onInfo: () -> Unit,
    onUninstall: () -> Unit,
) {
    val pkg = app.packageName
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF141414), RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            T(app.label, 18, Quiet)
            Spacer(Modifier.height(12.dp))
            if (pkg in prefs.braked) {
                // Quitar el freno también pasa por la pantalla de respiración
                MenuOption("Quitar freno") { onUnbrake() }
            } else {
                MenuOption("Poner freno") { prefs.setBraked(pkg, true); onDismiss() }
            }
            MenuOption(if (pkg in prefs.favorites) "Quitar de inicio" else "Añadir a inicio") {
                prefs.toggleFavorite(pkg); onDismiss()
            }
            MenuOption(if (pkg in prefs.hidden) "Mostrar en la lista" else "Ocultar de la lista") {
                prefs.toggleHidden(pkg); onDismiss()
            }
            MenuOption("Info de la app") { onInfo() }
            MenuOption("Desinstalar") { onUninstall() }
        }
    }
}

// ============ FRENO ============
@Composable
fun FrenoScreen(
    app: AppEntry,
    unbrake: Boolean,
    prefs: FrenoPrefs,
    onOpen: () -> Unit,
    onDone: () -> Unit,
) {
    val stop = {
        if (!unbrake) prefs.recordStopped()
        onDone()
    }
    BackHandler { stop() }

    LaunchedEffect(app.packageName, unbrake) {
        if (!unbrake) prefs.recordAttempt()
    }

    var remaining by remember { mutableIntStateOf(prefs.waitSeconds) }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1_000)
            remaining--
        }
    }
    var reason by remember { mutableStateOf<Int?>(null) }

    val breath = rememberInfiniteTransition(label = "breath")
    val scale by breath.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale",
    )

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        T(app.label, 15, Quiet)
        Spacer(Modifier.weight(1f))

        Canvas(Modifier.size(200.dp)) {
            drawCircle(Ink, radius = size.minDimension / 2f * scale, style = Stroke(width = 2.dp.toPx()))
        }
        Spacer(Modifier.height(32.dp))

        val chosen = reason
        when {
            remaining > 0 -> T("Respira · $remaining", 16, Quiet)

            unbrake -> {
                T("¿Quitar el freno a ${app.label}?", 20, Ink, center = true)
                Spacer(Modifier.height(20.dp))
                T("Quitar freno", 18, Ink, modifier = Modifier.padding(12.dp).tap {
                    prefs.setBraked(app.packageName, false)
                    onDone()
                })
                T("Mantenerlo", 18, Quiet, modifier = Modifier.padding(12.dp).tap { onDone() })
            }

            chosen == null -> {
                T("¿Para qué la abres?", 20, Ink, center = true)
                Spacer(Modifier.height(12.dp))
                Reasons.forEachIndexed { i, r ->
                    T(r, 17, Quiet, modifier = Modifier.padding(vertical = 10.dp).tap {
                        reason = i
                        prefs.recordReason(i)
                    })
                }
            }

            else -> {
                T(Reasons[chosen ?: 0], 15, Quiet)
                Spacer(Modifier.height(16.dp))
                T("Abrir ${app.label}", 18, Ink, modifier = Modifier.padding(12.dp).tap { onOpen() })
            }
        }

        Spacer(Modifier.weight(1f))
        if (!unbrake) {
            T("Mejor no", 17, Ink, FontWeight.Medium, modifier = Modifier.padding(16.dp).tap { stop() })
        }
    }
}

// ============ AJUSTES ============
@Composable
fun Section(title: String) {
    Spacer(Modifier.height(28.dp))
    T(title.uppercase(), 12, Dim, FontWeight.Medium)
    Spacer(Modifier.height(8.dp))
}

@Composable
fun SettingsScreen(
    apps: List<AppEntry>,
    prefs: FrenoPrefs,
    onBack: () -> Unit,
    onUnbrake: (AppEntry) -> Unit,
    onHomeSettings: () -> Unit,
) {
    BackHandler { onBack() }

    val braked = prefs.braked.mapNotNull { p -> apps.firstOrNull { it.packageName == p } }
        .sortedBy { it.label.lowercase() }
    val hidden = prefs.hidden.mapNotNull { p -> apps.firstOrNull { it.packageName == p } }
        .sortedBy { it.label.lowercase() }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(32.dp)
    ) {
        T("Ajustes", 28, Ink, FontWeight.Light)

        Section("Hoy")
        T("Intentos: ${prefs.todayAttempts()}    Frenadas: ${prefs.todayStopped()}", 16, Ink)
        prefs.todayReasons().forEach { (i, n) ->
            T("${Reasons[i]}: $n", 14, Quiet, modifier = Modifier.padding(top = 4.dp))
        }

        Section("Espera del freno")
        Row {
            listOf(5, 8, 12, 20).forEach { s ->
                val on = prefs.waitSeconds == s
                T(
                    "$s s", 17,
                    if (on) Ink else Dim,
                    if (on) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.padding(end = 24.dp, top = 4.dp, bottom = 4.dp).tap { prefs.setWait(s) },
                )
            }
        }

        Section("Apps con freno")
        if (braked.isEmpty()) T("Ninguna", 15, Dim)
        braked.forEach { app ->
            T(app.label, 17, Ink, modifier = Modifier.padding(vertical = 8.dp).tap { onUnbrake(app) })
        }
        T("Toca una para quitarle el freno. Para poner freno, mantén pulsada la app en la lista.", 12, Dim)

        Section("Apps ocultas")
        if (hidden.isEmpty()) T("Ninguna", 15, Dim)
        hidden.forEach { app ->
            T(app.label, 17, Ink, modifier = Modifier.padding(vertical = 8.dp).tap { prefs.toggleHidden(app.packageName) })
        }
        if (hidden.isNotEmpty()) T("Toca una para volver a mostrarla.", 12, Dim)

        Section("Inicio")
        T("Cambiar app de inicio", 17, Ink, modifier = Modifier.padding(vertical = 8.dp).tap { onHomeSettings() })

        Spacer(Modifier.height(40.dp))
        T("Freno 0.1", 12, Dim)
    }
}
