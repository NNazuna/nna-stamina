package com.nnazuna.nnastamina

/** A game server with its own clock (used for the daily reset). */
data class Server(val name: String, val utcOffsetMin: Int)

/**
 * Stamina rules for one game. Values are defaults; the player can change the cap
 * (some games raise it with account level) and pick their server.
 */
class GameDef(
    val id: String,
    val name: String,
    val resource: String,
    val defaultCap: Int,
    /** Seconds to regenerate one point. */
    val regenSec: Int,
    /** True when the cap depends on account level, so the player should set their own. */
    val capVaries: Boolean,
    val servers: List<Server>,
    /** Daily reset hour in server time; -1 = no daily reset shown. */
    val resetHour: Int,
    val accent: Int,
    /** Short label shown in the badge. */
    val mono: String,
    /** Quick "spend" amounts. */
    val presets: IntArray,
    val custom: Boolean = false,
) {
    val regenMs: Long get() = regenSec * 1000L
}

object Games {
    private val HOYO = listOf(
        Server("Asia", 8 * 60),
        Server("America", -5 * 60),
        Server("Europe", 1 * 60),
        Server("TW, HK, MO", 8 * 60),
    )
    private val KURO = listOf(
        Server("Asia / SEA / HMT", 8 * 60),
        Server("America", -5 * 60),
        Server("Europe", 1 * 60),
    )
    private val ENDFIELD = listOf(
        Server("Asia", 8 * 60),
        Server("Americas", -5 * 60),
        Server("Europe", 1 * 60),
    )

    /** Built-in games, roughly ordered by current popularity. */
    val builtIn: List<GameDef> = listOf(
        GameDef(
            "genshin", "Genshin Impact", "Original Resin",
            defaultCap = 200, regenSec = 8 * 60, capVaries = false,
            servers = HOYO, resetHour = 4,
            accent = 0xFFD9AE4E.toInt(), mono = "GI", presets = intArrayOf(20, 40, 60),
        ),
        GameDef(
            "hsr", "Honkai: Star Rail", "Trailblaze Power",
            defaultCap = 300, regenSec = 6 * 60, capVaries = false,
            servers = HOYO, resetHour = 4,
            accent = 0xFF8C93E8.toInt(), mono = "HSR", presets = intArrayOf(10, 30, 40, 60),
        ),
        GameDef(
            "zzz", "Zenless Zone Zero", "Battery Charge",
            defaultCap = 240, regenSec = 6 * 60, capVaries = false,
            servers = HOYO, resetHour = 4,
            accent = 0xFFF2B630.toInt(), mono = "ZZZ", presets = intArrayOf(20, 40, 60),
        ),
        GameDef(
            "wuwa", "Wuthering Waves", "Waveplate",
            defaultCap = 240, regenSec = 6 * 60, capVaries = false,
            servers = KURO, resetHour = 4,
            accent = 0xFF4DB8C4.toInt(), mono = "WW", presets = intArrayOf(40, 60),
        ),
        GameDef(
            "arknights", "Arknights", "Sanity",
            defaultCap = 180, regenSec = 6 * 60, capVaries = true,
            servers = listOf(Server("Global", -7 * 60)), resetHour = 4,
            accent = 0xFF6F9FD8.toInt(), mono = "AK", presets = intArrayOf(21, 30, 36),
        ),
        GameDef(
            "endfield", "Arknights: Endfield", "Sanity",
            defaultCap = 240, regenSec = 432, capVaries = true,
            servers = ENDFIELD, resetHour = 4,
            accent = 0xFFB9C94A.toInt(), mono = "EF", presets = intArrayOf(10, 20, 40),
        ),
        GameDef(
            "uma", "Uma Musume: Pretty Derby", "Training Points",
            defaultCap = 100, regenSec = 10 * 60, capVaries = false,
            servers = listOf(Server("Global", 9 * 60)), resetHour = 0,
            accent = 0xFF5CB874.toInt(), mono = "UMA", presets = intArrayOf(30),
        ),
        GameDef(
            "bluearchive", "Blue Archive", "AP",
            defaultCap = 240, regenSec = 6 * 60, capVaries = true,
            servers = listOf(Server("Global", 9 * 60)), resetHour = 4,
            accent = 0xFF4AA8F0.toInt(), mono = "BA", presets = intArrayOf(10, 15, 20),
        ),
    )

    /** Enabled on first launch. */
    val defaultEnabled = setOf("genshin", "hsr", "zzz", "wuwa", "arknights")

    val customAccents = intArrayOf(
        0xFFA389E6.toInt(), 0xFFE37B8B.toInt(), 0xFF5FC9A8.toInt(), 0xFFE39A5B.toInt(),
    )
}
