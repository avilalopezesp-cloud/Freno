package com.freno.launcher

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate

/** Apps que llevan freno la primera vez que se abre Freno. */
val DefaultBraked = setOf(
    "com.instagram.android",
    "com.zhiliaoapp.musically",   // TikTok
    "com.ss.android.ugc.trill",   // TikTok (otras regiones)
    "com.facebook.katana",        // Facebook
    "com.facebook.lite",          // Facebook Lite
)

/** Ajustes y estadísticas, guardados solo en el móvil. */
class FrenoPrefs(context: Context) {
    private val sp = context.getSharedPreferences("freno", Context.MODE_PRIVATE)

    init {
        if (!sp.getBoolean("init", false)) {
            sp.edit().putStringSet("braked", DefaultBraked).putBoolean("init", true).apply()
        }
    }

    var favorites by mutableStateOf(
        sp.getString("favorites", "").orEmpty().split("|").filter { it.isNotBlank() }
    )
        private set
    var braked by mutableStateOf(sp.getStringSet("braked", emptySet()).orEmpty().toSet())
        private set
    var hidden by mutableStateOf(sp.getStringSet("hidden", emptySet()).orEmpty().toSet())
        private set
    var waitSeconds by mutableIntStateOf(sp.getInt("wait", 8))
        private set
    var limitMinutes by mutableIntStateOf(sp.getInt("limit", 10))
        private set
    private var statsVersion by mutableIntStateOf(0)

    fun setLimit(minutes: Int) {
        limitMinutes = minutes
        sp.edit().putInt("limit", minutes).apply()
    }

    fun toggleFavorite(pkg: String) {
        favorites = if (pkg in favorites) favorites - pkg else favorites + pkg
        sp.edit().putString("favorites", favorites.joinToString("|")).apply()
    }

    fun setBraked(pkg: String, on: Boolean) {
        braked = if (on) braked + pkg else braked - pkg
        sp.edit().putStringSet("braked", braked).apply()
    }

    fun toggleHidden(pkg: String) {
        hidden = if (pkg in hidden) hidden - pkg else hidden + pkg
        sp.edit().putStringSet("hidden", hidden).apply()
    }

    fun setWait(seconds: Int) {
        waitSeconds = seconds
        sp.edit().putInt("wait", seconds).apply()
    }

    // --- Estadísticas del día ---
    private fun day() = LocalDate.now().toString()

    private fun bump(key: String) {
        sp.edit().putInt(key, sp.getInt(key, 0) + 1).apply()
        statsVersion++
    }

    fun recordAttempt() = bump("att_${day()}")
    fun recordStopped() = bump("stop_${day()}")
    fun recordReason(index: Int) = bump("why_${day()}_$index")

    fun todayAttempts(): Int {
        statsVersion // para que la pantalla se actualice
        return sp.getInt("att_${day()}", 0)
    }

    fun todayStopped(): Int {
        statsVersion
        return sp.getInt("stop_${day()}", 0)
    }

    fun todayReasons(): Map<Int, Int> {
        statsVersion
        val d = day()
        return Reasons.indices.associateWith { sp.getInt("why_${d}_$it", 0) }.filterValues { it > 0 }
    }
}
