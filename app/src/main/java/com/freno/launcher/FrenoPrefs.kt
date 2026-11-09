package com.freno.launcher

import android.content.Context
import android.os.SystemClock
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

data class DayStats(
    val date: LocalDate,
    val seconds: Map<String, Int>,
    val opens: Map<String, Int>,
    val attempts: Int,
    val stopped: Int,
    val reasons: Map<Int, Int>,
) {
    val totalSeconds: Int get() = seconds.values.sum()
}

/** Ajustes y estadísticas, guardados solo en el móvil. Una única instancia para la app y el servicio. */
class FrenoPrefs private constructor(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("freno", Context.MODE_PRIVATE)

    init {
        if (!sp.getBoolean("init", false)) {
            sp.edit().putStringSet("braked", DefaultBraked).putBoolean("init", true).apply()
        }
        prune()
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
    /** Cada cuántos minutos avisar (vibración + notificación) mientras usas una app con freno. 0 = nunca. */
    var nudgeMinutes by mutableIntStateOf(sp.getInt("nudge", 5))
        private set
    private var statsVersion by mutableIntStateOf(0)

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

    fun setLimit(minutes: Int) {
        limitMinutes = minutes
        sp.edit().putInt("limit", minutes).apply()
    }

    fun setNudge(minutes: Int) {
        nudgeMinutes = minutes
        sp.edit().putInt("nudge", minutes).apply()
    }

    // --- Pases: una app con freno abierta pasando por la pausa, válida durante limitMinutes ---
    private val passes = mutableMapOf<String, Long>()

    fun grantPass(pkg: String) {
        passes[pkg] = SystemClock.elapsedRealtime() + limitMinutes * 60_000L
    }

    fun passEnd(pkg: String): Long? = passes[pkg]
    fun clearPass(pkg: String) {
        passes.remove(pkg)
    }

    // --- Estadísticas ---
    private fun day() = LocalDate.now().toString()

    private fun bump(key: String, by: Int = 1) {
        sp.edit().putInt(key, sp.getInt(key, 0) + by).apply()
        statsVersion++
    }

    fun recordAttempt() = bump("att_${day()}")
    fun recordStopped() = bump("stop_${day()}")
    fun recordReason(index: Int) = bump("why_${day()}_$index")
    fun recordOpen(pkg: String) = bump("open_${day()}_$pkg")
    fun addSeconds(pkg: String, seconds: Int) = bump("sec_${day()}_$pkg", seconds)

    fun todayStopped(): Int {
        statsVersion // para que la pantalla se actualice
        return sp.getInt("stop_${day()}", 0)
    }

    fun todaySeconds(): Int {
        statsVersion
        val prefix = "sec_${day()}_"
        return sp.all.entries.filter { it.key.startsWith(prefix) }.sumOf { (it.value as? Int) ?: 0 }
    }

    /** Los últimos [n] días, del más antiguo a hoy. */
    fun lastDays(n: Int): List<DayStats> {
        statsVersion
        val all = sp.all
        val today = LocalDate.now()
        return (n - 1 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            val ds = d.toString()
            fun perPkg(kind: String): Map<String, Int> {
                val prefix = "${kind}_${ds}_"
                return all.filterKeys { it.startsWith(prefix) }
                    .map { it.key.removePrefix(prefix) to ((it.value as? Int) ?: 0) }
                    .toMap()
            }
            DayStats(
                date = d,
                seconds = perPkg("sec"),
                opens = perPkg("open"),
                attempts = (all["att_$ds"] as? Int) ?: 0,
                stopped = (all["stop_$ds"] as? Int) ?: 0,
                reasons = Reasons.indices.associateWith { (all["why_${ds}_$it"] as? Int) ?: 0 }.filterValues { it > 0 },
            )
        }
    }

    /** Borra estadísticas de hace más de 120 días. */
    private fun prune() {
        val limit = LocalDate.now().minusDays(120)
        val re = Regex("^(sec|open|att|stop|why)_(\\d{4}-\\d{2}-\\d{2}).*")
        val old = sp.all.keys.filter { k ->
            val m = re.matchEntire(k) ?: return@filter false
            runCatching { LocalDate.parse(m.groupValues[2]) < limit }.getOrDefault(false)
        }
        if (old.isNotEmpty()) sp.edit().apply { old.forEach { remove(it) } }.apply()
    }

    companion object {
        @Volatile private var instance: FrenoPrefs? = null
        fun get(context: Context): FrenoPrefs =
            instance ?: synchronized(this) { instance ?: FrenoPrefs(context).also { instance = it } }
    }
}
