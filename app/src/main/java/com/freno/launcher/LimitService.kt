package com.freno.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock

/**
 * Cuenta atrás mientras usas una app con freno.
 * Al acabar el tiempo trae Freno al frente (necesita el permiso "Mostrar sobre otras apps").
 */
class LimitService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var endAt = 0L
    private var label = ""
    private var minutes = 10

    private val tick = object : Runnable {
        override fun run() {
            if (SystemClock.elapsedRealtime() >= endAt) timeUp() else handler.postDelayed(this, 5_000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
        minutes = intent.getIntExtra(EXTRA_MINUTES, 10)
        endAt = SystemClock.elapsedRealtime() + minutes * 60_000L

        val notification = buildNotification(System.currentTimeMillis() + minutes * 60_000L)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 5_000)
        return START_NOT_STICKY
    }

    private fun buildNotification(endWallClock: Long): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Tiempo de uso", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(label)
            .setContentText("Tiempo de uso: $minutes min")
            .setWhen(endWallClock)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .build()
    }

    private fun timeUp() {
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_TIMEUP_LABEL, label)
                    .putExtra(EXTRA_MINUTES, minutes)
            )
        } catch (e: Exception) {
            // Sin el permiso, Android puede bloquearlo: no hacemos nada más
        }
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "limite"
        const val NOTIF_ID = 1
        const val EXTRA_LABEL = "label"
        const val EXTRA_MINUTES = "minutes"
        const val EXTRA_TIMEUP_LABEL = "timeup_label"

        fun start(context: Context, label: String, minutes: Int) {
            val i = Intent(context, LimitService::class.java)
                .putExtra(EXTRA_LABEL, label)
                .putExtra(EXTRA_MINUTES, minutes)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LimitService::class.java))
        }
    }
}
