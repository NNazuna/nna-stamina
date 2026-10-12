package com.nnazuna.nnastamina

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** What the player has told us about one game. */
class GameState(
    var enabled: Boolean,
    var cap: Int,
    /** Stamina at [anchor]. */
    var value: Int,
    /** Epoch ms when [value] was true; 0 = never set. */
    var anchor: Long,
    var server: Int,
    var notifyFull: Boolean,
    /** Extra notification at this amount; 0 = off. */
    var notifyAt: Int,
    var notifyReset: Boolean,
    /** Player confirmed their cap (matters for games whose cap depends on level). */
    var capConfirmed: Boolean,
) {
    val isSet: Boolean get() = anchor > 0

    fun toJson() = JSONObject().apply {
        put("enabled", enabled); put("cap", cap); put("value", value); put("anchor", anchor)
        put("server", server); put("notifyFull", notifyFull); put("notifyAt", notifyAt)
        put("notifyReset", notifyReset); put("capConfirmed", capConfirmed)
    }

    companion object {
        fun fromJson(o: JSONObject, def: GameDef) = GameState(
            enabled = o.optBoolean("enabled", false),
            cap = o.optInt("cap", def.defaultCap).coerceAtLeast(1),
            value = o.optInt("value", 0),
            anchor = o.optLong("anchor", 0L),
            server = o.optInt("server", 0).coerceIn(0, (def.servers.size - 1).coerceAtLeast(0)),
            notifyFull = o.optBoolean("notifyFull", true),
            notifyAt = o.optInt("notifyAt", 0),
            notifyReset = o.optBoolean("notifyReset", false),
            capConfirmed = o.optBoolean("capConfirmed", !def.capVaries),
        )

        fun initial(def: GameDef, enabled: Boolean) = GameState(
            enabled = enabled, cap = def.defaultCap, value = 0, anchor = 0L, server = 0,
            notifyFull = true, notifyAt = 0, notifyReset = false, capConfirmed = !def.capVaries,
        )
    }
}

/** Stamina math. All times are epoch milliseconds. */
object Calc {
    const val MIN = 60_000L
    const val HOUR = 60 * MIN
    const val DAY = 24 * HOUR

    fun current(def: GameDef, s: GameState, now: Long): Int {
        if (!s.isSet || s.value >= s.cap) return s.value
        val gained = ((now - s.anchor).coerceAtLeast(0) / def.regenMs).toInt()
        return minOf(s.cap, s.value + gained)
    }

    /** When the bar fills; null if already full (or overfilled) or not set. */
    fun fullAt(def: GameDef, s: GameState): Long? {
        if (!s.isSet || s.value >= s.cap) return null
        return s.anchor + (s.cap - s.value) * def.regenMs
    }

    /** When the bar reaches [target]; null if already there or the target is above the cap. */
    fun reachAt(def: GameDef, s: GameState, target: Int): Long? {
        if (!s.isSet || target <= s.value || target > s.cap) return null
        return s.anchor + (target - s.value) * def.regenMs
    }

    /** Next +1 tick; null when full. */
    fun nextTickAt(def: GameDef, s: GameState, now: Long): Long? {
        if (!s.isSet || current(def, s, now) >= s.cap) return null
        val ticks = (now - s.anchor) / def.regenMs + 1
        return s.anchor + ticks * def.regenMs
    }

    /** Ms since the bar became full; 0 if not full via regen. */
    fun fullFor(def: GameDef, s: GameState, now: Long): Long {
        val f = fullAt(def, s) ?: return 0
        return (now - f).coerceAtLeast(0)
    }

    /**
     * Set a new amount. Spending keeps the progress toward the next point; any other change
     * (typing a higher number, or coming from a full bar) starts the timer now.
     */
    fun set(def: GameDef, s: GameState, newValue: Int, now: Long) {
        val v = newValue.coerceAtLeast(0)
        val cur = current(def, s, now)
        val wasRegenerating = s.isSet && cur < s.cap
        val partial = if (wasRegenerating) (now - s.anchor).coerceAtLeast(0) % def.regenMs else 0L
        s.value = v
        s.anchor = if (v <= cur && wasRegenerating && v < s.cap) now - partial else now
    }

    fun nextReset(def: GameDef, s: GameState, now: Long): Long? {
        if (def.resetHour < 0 || def.servers.isEmpty()) return null
        val off = def.servers[s.server.coerceIn(0, def.servers.size - 1)].utcOffsetMin * MIN
        val local = now + off
        var t = Math.floorDiv(local, DAY) * DAY + def.resetHour * HOUR
        if (t <= local) t += DAY
        return t - off
    }

    fun fmtDuration(ms: Long): String {
        val totalMin = (ms + MIN - 1) / MIN
        val d = totalMin / (24 * 60)
        val h = (totalMin / 60) % 24
        val m = totalMin % 60
        return when {
            d > 0 -> "${d}d ${h}h"
            h > 0 -> "${h}h ${m}m"
            else -> "${m}m"
        }
    }
}

/** Saves game states, custom games, and app settings. */
class Store(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("stamina", Context.MODE_PRIVATE)

    // ---------- custom games ----------

    fun customGames(): List<GameDef> {
        val arr = runCatching { JSONArray(prefs.getString("custom", "[]")) }.getOrElse { JSONArray() }
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                GameDef(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    resource = o.optString("resource", "Stamina"),
                    defaultCap = o.optInt("cap", 100),
                    regenSec = o.optInt("regenSec", 360).coerceAtLeast(1),
                    capVaries = false,
                    servers = emptyList(),
                    resetHour = -1,
                    accent = o.optInt("accent", Games.customAccents[0]),
                    mono = o.optString("mono", "?"),
                    presets = intArrayOf(10, 20, 40),
                    custom = true,
                )
            }.getOrNull()
        }
    }

    private fun saveCustom(list: List<GameDef>) {
        val arr = JSONArray()
        for (g in list) arr.put(JSONObject().apply {
            put("id", g.id); put("name", g.name); put("resource", g.resource)
            put("cap", g.defaultCap); put("regenSec", g.regenSec); put("accent", g.accent); put("mono", g.mono)
        })
        prefs.edit().putString("custom", arr.toString()).apply()
    }

    fun addCustom(name: String, resource: String, cap: Int, regenSec: Int): GameDef {
        val list = customGames()
        val mono = name.split(' ', '-', ':').filter { it.isNotBlank() }
            .take(3).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
        val def = GameDef(
            id = "custom-" + UUID.randomUUID().toString().take(8),
            name = name, resource = resource.ifBlank { "Stamina" },
            defaultCap = cap, regenSec = regenSec, capVaries = false,
            servers = emptyList(), resetHour = -1,
            accent = Games.customAccents[list.size % Games.customAccents.size],
            mono = mono, presets = intArrayOf(10, 20, 40), custom = true,
        )
        saveCustom(list + def)
        save(def, GameState.initial(def, enabled = true))
        return def
    }

    fun updateCustom(def: GameDef, name: String, resource: String, regenSec: Int) {
        saveCustom(customGames().map {
            if (it.id != def.id) it else GameDef(
                it.id, name, resource.ifBlank { "Stamina" }, it.defaultCap, regenSec.coerceAtLeast(1),
                false, emptyList(), -1, it.accent, it.mono, it.presets, true,
            )
        })
    }

    fun deleteCustom(id: String) {
        saveCustom(customGames().filter { it.id != id })
        prefs.edit().remove("g_$id").apply()
    }

    fun allGames(): List<GameDef> = Games.builtIn + customGames()

    fun game(id: String?): GameDef? = allGames().firstOrNull { it.id == id }

    // ---------- state ----------

    fun state(def: GameDef): GameState {
        val raw = prefs.getString("g_${def.id}", null)
            ?: return GameState.initial(def, enabled = def.id in Games.defaultEnabled)
        return runCatching { GameState.fromJson(JSONObject(raw), def) }
            .getOrElse { GameState.initial(def, enabled = false) }
    }

    fun save(def: GameDef, s: GameState) {
        prefs.edit().putString("g_${def.id}", s.toJson().toString()).apply()
    }

    fun enabled(): List<Pair<GameDef, GameState>> =
        allGames().map { it to state(it) }.filter { it.second.enabled }

    // ---------- settings ----------

    var askedNotifications: Boolean
        get() = prefs.getBoolean("asked_notif", false)
        set(v) = prefs.edit().putBoolean("asked_notif", v).apply()

    /** Minutes before the daily reset to remind. */
    var resetLeadMin: Int
        get() = prefs.getInt("reset_lead", 60)
        set(v) = prefs.edit().putInt("reset_lead", v.coerceIn(5, 600)).apply()
}
