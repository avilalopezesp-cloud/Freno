package com.freno.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

private const val CYCLE = 8_000L // una respiración: 4 s subir (inhala) + 4 s bajar (exhala)
private const val HALF = CYCLE / 2

/** 0 = abajo, 1 = arriba, con aceleración suave como una respiración. */
private fun level(t: Long): Float {
    val p = (t % CYCLE).toFloat() / HALF
    val x = if (p <= 1f) p else 2f - p
    return ((1 - cos(PI * x)) / 2).toFloat()
}

/**
 * Respiración guiada con el dedo: un círculo sube (inhala) y baja (exhala) por una línea.
 * La cuenta solo avanza mientras el dedo lo sigue; si lo sueltas o te alejas, se para.
 */
@Composable
fun BreathGuide(breaths: Int, onDone: () -> Unit) {
    val need = breaths * CYCLE
    val done by rememberUpdatedState(onDone)
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val radius = with(density) { 30.dp.toPx() }
    val tolerance = with(density) { 80.dp.toPx() }

    var clock by remember { mutableLongStateOf(0L) }
    var progress by remember { mutableLongStateOf(0L) }
    var finger by remember { mutableStateOf<Offset?>(null) }
    var following by remember { mutableStateOf(false) }
    var box by remember { mutableStateOf(IntSize(1, 1)) }
    var completed by remember { mutableIntStateOf(0) }

    fun guideY(t: Long): Float {
        val h = box.height.toFloat()
        return radius + (1f - level(t)) * (h - 2 * radius)
    }

    LaunchedEffect(Unit) {
        var last = withFrameMillis { it }
        while (progress < need) {
            val now = withFrameMillis { it }
            val dt = (now - last).coerceAtMost(100)
            last = now
            clock += dt
            val f = finger
            following = f != null &&
                abs(f.y - guideY(clock)) < tolerance &&
                abs(f.x - box.width / 2f) < tolerance * 1.5f
            if (following) {
                progress += dt
                val c = (progress / CYCLE).toInt()
                if (c > completed) {
                    completed = c
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
        }
        done()
    }

    val inhale = clock % CYCLE < HALF
    val current = (completed + 1).coerceAtMost(breaths)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        T(if (inhale) "Inhala" else "Exhala", 24, Ink, FontWeight.Light)
        Spacer(Modifier.height(4.dp))
        T("$current de $breaths", 13, Dim)
        Spacer(Modifier.height(16.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .height(320.dp)
                .onSizeChanged { box = it }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent()
                            val c = e.changes.firstOrNull()
                            finger = if (c != null && c.pressed) c.position else null
                            e.changes.forEach { it.consume() }
                        }
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                // Línea por la que sube y baja
                drawLine(
                    Dim.copy(alpha = 0.6f),
                    Offset(cx, radius), Offset(cx, size.height - radius),
                    strokeWidth = 1.dp.toPx(),
                )
                // Progreso de esta respiración, como un arco alrededor del círculo
                val y = guideY(clock)
                val within = (progress % CYCLE).toFloat() / CYCLE
                if (following || within > 0f) {
                    drawArc(
                        Quiet,
                        startAngle = -90f, sweepAngle = 360f * within, useCenter = false,
                        topLeft = Offset(cx - radius - 8.dp.toPx(), y - radius - 8.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size((radius + 8.dp.toPx()) * 2, (radius + 8.dp.toPx()) * 2),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
                if (following) {
                    drawCircle(Ink, radius = radius, center = Offset(cx, y))
                } else {
                    drawCircle(Ink, radius = radius, center = Offset(cx, y), style = Stroke(width = 2.dp.toPx()))
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        T(
            when {
                finger == null -> "Pon el dedo en el círculo y síguelo"
                !following -> "Sigue el círculo"
                else -> " "
            },
            14, Quiet, center = true,
        )
    }
}
