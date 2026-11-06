package com.freno.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat

sealed interface Screen {
    data object Home : Screen
    data object Drawer : Screen
    data object Ajustes : Screen
    data class Freno(val app: AppEntry, val unbrake: Boolean, val back: Screen = Home) : Screen
    data class TimeUp(val label: String, val minutes: Int) : Screen
}

class MainActivity : ComponentActivity() {
    private lateinit var prefs: FrenoPrefs
    private var apps by mutableStateOf<List<AppEntry>>(emptyList())
    private var screen by mutableStateOf<Screen>(Screen.Home)
    private var menuApp by mutableStateOf<AppEntry?>(null)
    private var canOverlay by mutableStateOf(false)

    // Refresca la lista cuando se instala o desinstala una app
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            apps = loadApps(this@MainActivity)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        prefs = FrenoPrefs(this)
        apps = loadApps(this)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        handleTimeUp(intent)

        setContent {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                when (val s = screen) {
                    Screen.Home -> HomeScreen(
                        apps = apps,
                        prefs = prefs,
                        onOpen = ::openApp,
                        onMenu = { menuApp = it },
                        onDrawer = { screen = Screen.Drawer },
                        onSettings = { screen = Screen.Ajustes },
                    )
                    Screen.Drawer -> DrawerScreen(
                        apps = apps,
                        prefs = prefs,
                        onOpen = ::openApp,
                        onMenu = { menuApp = it },
                        onBack = { screen = Screen.Home },
                    )
                    Screen.Ajustes -> SettingsScreen(
                        apps = apps,
                        prefs = prefs,
                        onBack = { screen = Screen.Home },
                        onUnbrake = { screen = Screen.Freno(it, unbrake = true, back = Screen.Ajustes) },
                        onHomeSettings = ::openHomeSettings,
                    )
                    is Screen.Freno -> FrenoScreen(
                        app = s.app,
                        unbrake = s.unbrake,
                        prefs = prefs,
                        canOverlay = canOverlay,
                        onGrantOverlay = ::requestOverlay,
                        onOpen = { limited ->
                            if (limited) LimitService.start(this@MainActivity, s.app.label, prefs.limitMinutes)
                            launchApp(this@MainActivity, s.app)
                            screen = Screen.Home
                        },
                        onDone = { screen = s.back },
                    )
                    is Screen.TimeUp -> TimeUpScreen(
                        label = s.label,
                        minutes = s.minutes,
                        onDone = { screen = Screen.Home },
                    )
                }

                menuApp?.let { app ->
                    AppMenu(
                        app = app,
                        prefs = prefs,
                        onDismiss = { menuApp = null },
                        onUnbrake = {
                            menuApp = null
                            screen = Screen.Freno(app, unbrake = true, back = screen)
                        },
                        onInfo = {
                            menuApp = null
                            startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                            )
                        },
                        onUninstall = {
                            menuApp = null
                            startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")))
                        },
                    )
                }
            }
        }
    }

    private fun openApp(app: AppEntry) {
        if (app.packageName in prefs.braked) {
            screen = Screen.Freno(app, unbrake = false)
        } else {
            launchApp(this, app)
            screen = Screen.Home
        }
    }

    private fun openHomeSettings() {
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun requestOverlay() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    /** Freno vuelve al frente cuando se acaba el tiempo de una app. */
    private fun handleTimeUp(intent: Intent?): Boolean {
        val label = intent?.getStringExtra(LimitService.EXTRA_TIMEUP_LABEL) ?: return false
        intent.removeExtra(LimitService.EXTRA_TIMEUP_LABEL)
        menuApp = null
        screen = Screen.TimeUp(label, intent.getIntExtra(LimitService.EXTRA_MINUTES, prefs.limitMinutes))
        return true
    }

    override fun onResume() {
        super.onResume()
        apps = loadApps(this)
        canOverlay = Settings.canDrawOverlays(this)
        // Has vuelto al inicio antes de tiempo: se cancela la cuenta atrás
        LimitService.stop(this)
    }

    // Botón de inicio pulsado estando ya en Freno: volver a la pantalla principal
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (handleTimeUp(intent)) return
        menuApp = null
        if (screen !is Screen.Freno && screen !is Screen.TimeUp) screen = Screen.Home
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        super.onDestroy()
    }
}
