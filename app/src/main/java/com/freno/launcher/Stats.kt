package com.freno.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle

@Composable
fun StatsScreen(apps: List<AppEntry>, prefs: FrenoPrefs, onBack: () -> Unit) {
    BackHandler { onBack() }
    var range by remember { mutableIntStateOf(7) }
    val days = prefs.lastDays(range)
    val label = { pkg: String -> apps.firstOrNull { it.packageName == pkg }?.label ?: pkg }

    val total = days.sumOf { it.totalSeconds }
    val activeDays = days.count { it.totalSeconds > 0 }.coerceAtLeast(1)
    val attempts = days.sumOf { it.attempts }
    val stopped = days.sumOf { it.stopped }
    val opens = days.sumOf { d -> d.opens.values.sum() }
    val perApp = days.flatMap { it.seconds.entries }
        .groupBy({ it.key }, { it.value })
        .mapValues { it.value.sum() }
        .toList()
        .sortedByDescending { it.second }
    val opensPerApp = days.flatMap { it.opens.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }
    val reasons = days.flatMap { it.reasons.entries }
        .groupBy({ it.key }, { it.value })
        .mapValues { it.value.sum() }
        .toList()
        .sortedByDescending { it.second }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(32.dp)
    ) {
        T("Estadísticas", 28, Ink, FontWeight.Light)
        Spacer(Modifier.height(16.dp))
        Row {
            listOf(7 to "Semana", 30 to "Mes").forEach { (n, name) ->
                val on = range == n
                T(
                    name, 17, if (on) Ink else Dim, if (on) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.padding(end = 24.dp, top = 4.dp, bottom = 4.dp).tap { range = n },
                )
            }
        }

        Section("En apps con freno")
        T(fmtTime(total), 44, Ink, FontWeight.Light)
        T("Media: ${fmtTime(total / activeDays)} al día", 14, Quiet)

        Spacer(Modifier.height(24.dp))
        DayBars(days)

        Section("Pausas")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("$attempts", "intentos")
            Stat("$stopped", "frenadas")
            Stat("$opens", "abiertas")
        }
        if (attempts > 0) {
            Spacer(Modifier.height(8.dp))
            T("Te frenaste en ${stopped * 100 / attempts} % de los intentos.", 14, Quiet)
        }

        Section("Por app")
        if (perApp.isEmpty()) T("Sin datos todavía", 15, Dim)
        perApp.forEach { (pkg, sec) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                T(label(pkg), 17, Ink)
                val n = opensPerApp[pkg] ?: 0
                T("${fmtTime(sec)} · $n ${if (n == 1) "vez" else "veces"}", 15, Quiet)
            }
        }

        Section("Para qué las abriste")
        if (reasons.isEmpty()) T("Sin datos todavía", 15, Dim)
        reasons.forEach { (i, n) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                T(Reasons[i], 17, Ink)
                T("$n", 15, Quiet)
            }
        }

        Spacer(Modifier.height(24.dp))
        T("El tiempo se cuenta con la protección activada (Ajustes).", 12, Dim)
    }
}

@Composable
private fun Stat(value: String, name: String) {
    Column {
        T(value, 28, Ink, FontWeight.Light)
        T(name, 13, Quiet)
    }
}

/** Minutos por día: una barra por día, hoy resaltado. */
@Composable
private fun DayBars(days: List<DayStats>) {
    val max = (days.maxOfOrNull { it.totalSeconds } ?: 0).coerceAtLeast(60)
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val n = days.size
        val slot = size.width / n
        val bar = (slot * 0.6f).coerceAtMost(28.dp.toPx())
        days.forEachIndexed { i, d ->
            val h = (d.totalSeconds.toFloat() / max) * size.height
            val x = i * slot + (slot - bar) / 2
            val isToday = i == n - 1
            // línea base para los días sin uso
            drawRect(Dim.copy(alpha = 0.5f), Offset(x, size.height - 1.dp.toPx()), Size(bar, 1.dp.toPx()))
            if (h > 0f) {
                drawRoundRect(
                    color = if (isToday) Ink else Quiet,
                    topLeft = Offset(x, size.height - h),
                    size = Size(bar, h),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    if (days.size <= 7) {
        Row(Modifier.fillMaxWidth()) {
            days.forEachIndexed { i, d ->
                val name = d.date.dayOfWeek.getDisplayName(JTextStyle.NARROW, Spanish).uppercase()
                T(
                    name, 12, if (i == days.size - 1) Ink else Dim,
                    center = true, modifier = Modifier.weight(1f),
                )
            }
        }
    } else {
        val f = DateTimeFormatter.ofPattern("d MMM", Spanish)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            T(days.first().date.format(f), 12, Dim)
            T("hoy", 12, Ink)
        }
    }
}
