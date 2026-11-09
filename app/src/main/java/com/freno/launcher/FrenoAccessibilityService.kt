package com.freno.launcher

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager

/**
 * Ve qué app está delante, la abras desde donde la abras (inicio, notificación, recientes, enlace).
 * - App con freno sin pase → muestra la pantalla de Freno.
 * - Cuenta el tiempo real de uso.
 * - Avisa con vibración + notificación cada X minutos.
 * - Al acabar el tiempo pactado, te devuelve al inicio.
 */
class FrenoAccessibilityService : AccessibilityService() {
    private lateinit var prefs: FrenoPrefs
    private val handler = Handler(Looper.getMainLooper())
    private var current: String? = null
    private var lastTick = 0L
    private var sessionSeconds = 0
    private var nudgesSent = 0
    private var ignored = emptySet<String>()

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 15_000)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = FrenoPrefs.get(this)
        ignored = buildIgnored()
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, 15_000)
    }

    /** Ventanas que no cuentan como "cambiar de app": barra de estado, teclados. */
    private fun buildIgnored(): Set<String> {
        val imes = runCatching {
            getSystemService(InputMethodManager::class.java).enabledInputMethodList.map { it.packageName }
        }.getOrDefault(emptyList())
        return imes.toSet() + "com.android.systemui"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!::prefs.isInitialized) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in ignored) return
        if (event.className?.toString().orEmpty().contains("Toast")) return
        if (pkg == current) return
        onForeground(pkg)
    }

    private fun onForeground(pkg: String) {
        flushTime()
        current = pkg
        lastTick = SystemClock.elapsedRealtime()
        sessionSeconds = 0
        nudgesSent = 0

        val braked = pkg in prefs.braked
        if (!braked) return

        val end = prefs.passEnd(pkg)
        if (end == null || end <= SystemClock.elapsedRealtime()) {
            prefs.clearPass(pkg)
            showFreno(pkg)
        }
    }

    private fun isScreenOn() = getSystemService(PowerManager::class.java).isInteractive

    /** Suma el tiempo pasado en la app actual (solo apps con freno y con la pantalla encendida). */
    private fun flushTime() {
        val now = SystemClock.elapsedRealtime()
        val pkg = current
        if (pkg != null && pkg in prefs.braked && isScreenOn()) {
            val s = ((now - lastTick) / 1000).toInt()
            if (s > 0) {
                prefs.addSeconds(pkg, s)
                sessionSeconds += s
            }
        }
        lastTick = now
    }

    private fun tick() {
        val pkg = current ?: return
        if (pkg !in prefs.braked) return
        if (!isScreenOn()) {
            lastTick = SystemClock.elapsedRealtime()
            return
        }
        flushTime()

        val end = prefs.passEnd(pkg)
        if (end != null && SystemClock.elapsedRealtime() >= end) {
            timeUp(pkg)
            return
        }

        val every = prefs.nudgeMinutes
        if (every > 0) {
            val due = sessionSeconds / 60 / every
            if (due > nudgesSent) {
                nudgesSent = due
                nudge(pkg, sessionSeconds / 60)
            }
        }
    }

    private fun showFreno(pkg: String) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_BRAKE_PKG, pkg)
        )
    }

    private fun timeUp(pkg: String) {
        prefs.clearPass(pkg)
        vibrate(longArrayOf(0, 200, 120, 200))
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_TIMEUP_PKG, pkg)
            )
        } catch (e: Exception) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    private fun label(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private fun nudge(pkg: String, minutes: Int) {
        vibrate(longArrayOf(0, 120, 100, 120))
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Avisos de uso", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Llevas $minutes min en ${label(pkg)}")
            .setTimeoutAfter(60_000)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_NUDGE, n)
    }

    private fun vibrate(pattern: LongArray) {
        runCatching {
            val v: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
                getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            v.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacks(ticker)
        return super.onUnbind(intent)
    }

    companion object {
        const val EXTRA_BRAKE_PKG = "brake_pkg"
        const val EXTRA_TIMEUP_PKG = "timeup_pkg"
        const val CHANNEL = "avisos"
        const val NOTIF_NUDGE = 2

        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, FrenoAccessibilityService::class.java).flattenToString()
            val list = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                .orEmpty()
            return list.split(':').any { it.equals(me, ignoreCase = true) }
        }
    }
}
