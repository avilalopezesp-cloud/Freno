package com.freno.launcher

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
    data object Stats : Screen
    data class Freno(val app: AppEntry, val unbrake: Boolean, val back: Screen = Home) : Screen
    data class TimeUp(val label: String, val minutes: Int) : Screen
}

/** Lo que la pantalla de Ajustes necesita saber del sistema. */
data class SystemState(
    val a11y: Boolean = false,
    val notifications: Boolean = true,
    val grayPermission: Boolean = false,
)

class MainActivity : ComponentActivity() {
    private lateinit var prefs: FrenoPrefs
    private var apps by mutableStateOf<List<AppEntry>>(emptyList())
    private var screen by mutableStateOf<Screen>(Screen.Home)
    private var menuApp by mutableStateOf<AppEntry?>(null)
    private var system by mutableStateOf(SystemState())

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshSystem() }

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
        prefs = FrenoPrefs.get(this)
        apps = loadApps(this)
        refreshSystem()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        handleExtras(intent)

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
                        onStats = { screen = Screen.Stats },
                    )
                    Screen.Drawer -> DrawerScreen(
                        apps = apps,
                        prefs = prefs,
                        onOpen = ::openApp,
                        onMenu = { menuApp = it },
                        onBack = { screen = Screen.Home },
                    )
                    Screen.Stats -> StatsScreen(
                        apps = apps,
                        prefs = prefs,
                        onBack = { screen = Screen.Home },
                    )
                    Screen.Ajustes -> SettingsScreen(
                        apps = apps,
                        prefs = prefs,
                        system = system,
                        onBack = { screen = Screen.Home },
                        onUnbrake = { screen = Screen.Freno(it, unbrake = true, back = Screen.Ajustes) },
                        onHomeSettings = ::openHomeSettings,
                        onA11ySettings = ::openA11ySettings,
                        onAppInfo = { openAppInfo(packageName) },
                        onNudge = ::setNudge,
                        onStats = { screen = Screen.Stats },
                    )
                    is Screen.Freno -> FrenoScreen(
                        app = s.app,
                        unbrake = s.unbrake,
                        prefs = prefs,
                        a11y = system.a11y,
                        onEnableA11y = ::openA11ySettings,
                        onOpen = {
                            prefs.grantPass(s.app.packageName)
                            prefs.recordOpen(s.app.packageName)
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
                            openAppInfo(app.packageName)
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

    private fun appFor(pkg: String): AppEntry =
        apps.firstOrNull { it.packageName == pkg }
            ?: loadApps(this).firstOrNull { it.packageName == pkg }
            ?: AppEntry(pkg, pkg, "")

    /** El servicio de accesibilidad nos trae al frente: pausa o tiempo acabado. */
    private fun handleExtras(intent: Intent?): Boolean {
        intent ?: return false
        intent.getStringExtra(FrenoAccessibilityService.EXTRA_BRAKE_PKG)?.let { pkg ->
            intent.removeExtra(FrenoAccessibilityService.EXTRA_BRAKE_PKG)
            menuApp = null
            screen = Screen.Freno(appFor(pkg), unbrake = false)
            return true
        }
        intent.getStringExtra(FrenoAccessibilityService.EXTRA_TIMEUP_PKG)?.let { pkg ->
            intent.removeExtra(FrenoAccessibilityService.EXTRA_TIMEUP_PKG)
            menuApp = null
            screen = Screen.TimeUp(appFor(pkg).label, prefs.limitMinutes)
            return true
        }
        return false
    }

    private fun refreshSystem() {
        system = SystemState(
            a11y = FrenoAccessibilityService.isEnabled(this),
            notifications = Build.VERSION.SDK_INT < 33 ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
            grayPermission = Grayscale.canUse(this),
        )
    }

    private fun setNudge(minutes: Int) {
        prefs.setNudge(minutes)
        if (minutes > 0 && Build.VERSION.SDK_INT >= 33 && !system.notifications) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun openHomeSettings() {
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun openA11ySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openAppInfo(pkg: String) {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
    }

    override fun onResume() {
        super.onResume()
        apps = loadApps(this)
        refreshSystem()
    }

    // Botón de inicio pulsado estando ya en Freno: volver a la pantalla principal
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (handleExtras(intent)) return
        menuApp = null
        if (screen !is Screen.Freno && screen !is Screen.TimeUp) screen = Screen.Home
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        super.onDestroy()
    }
}
