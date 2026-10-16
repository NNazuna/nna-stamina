package com.nnazuna.nnastamina

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the stamina math in [Calc]. Pure JVM, no device needed. */
class CalcTest {

    /** 1 point every 6 minutes, cap 180 (Arknights-like). */
    private val game = Games.builtIn.first { it.id == "arknights" }
    private val regen = 6 * Calc.MIN
    private val t0 = 1_700_000_000_000L

    private fun state(value: Int, anchor: Long = t0, cap: Int = 180) =
        GameState.initial(game, enabled = true).apply {
            this.cap = cap
            this.value = value
            this.anchor = anchor
        }

    @Test
    fun current_addsOnePointPerRegenInterval() {
        val s = state(100)
        assertEquals(100, Calc.current(game, s, t0))
        assertEquals(100, Calc.current(game, s, t0 + regen - 1))
        assertEquals(101, Calc.current(game, s, t0 + regen))
        assertEquals(110, Calc.current(game, s, t0 + 10 * regen))
    }

    @Test
    fun current_stopsAtCap() {
        val s = state(170)
        assertEquals(180, Calc.current(game, s, t0 + 1_000 * regen))
    }

    @Test
    fun current_keepsOverfilledValue() {
        // Rewards can push stamina above the cap; regen must not touch it.
        val s = state(250)
        assertEquals(250, Calc.current(game, s, t0 + 10 * regen))
    }

    @Test
    fun current_returnsValueWhenNeverSet() {
        val s = state(42, anchor = 0L)
        assertEquals(42, Calc.current(game, s, t0))
    }

    @Test
    fun current_ignoresClockGoingBackwards() {
        val s = state(100)
        assertEquals(100, Calc.current(game, s, t0 - 5 * regen))
    }

    @Test
    fun fullAt_isAnchorPlusMissingPointsTimesRegen() {
        val s = state(170)
        assertEquals(t0 + 10 * regen, Calc.fullAt(game, s))
    }

    @Test
    fun fullAt_isNullWhenFullOrNotSet() {
        assertNull(Calc.fullAt(game, state(180)))
        assertNull(Calc.fullAt(game, state(200)))
        assertNull(Calc.fullAt(game, state(10, anchor = 0L)))
    }

    @Test
    fun reachAt_handlesTargetsBelowAndAboveRange() {
        val s = state(100)
        assertEquals(t0 + 20 * regen, Calc.reachAt(game, s, 120))
        assertNull(Calc.reachAt(game, s, 100))
        assertNull(Calc.reachAt(game, s, 90))
        assertNull(Calc.reachAt(game, s, 181))
    }

    @Test
    fun nextTickAt_isNextMultipleOfRegen() {
        val s = state(100)
        assertEquals(t0 + regen, Calc.nextTickAt(game, s, t0))
        assertEquals(t0 + 3 * regen, Calc.nextTickAt(game, s, t0 + 2 * regen + 1))
        assertNull(Calc.nextTickAt(game, s, t0 + 1_000 * regen))
    }

    @Test
    fun fullFor_measuresTimeSpentAtCap() {
        val s = state(179)
        assertEquals(0L, Calc.fullFor(game, s, t0))
        assertEquals(5 * Calc.MIN, Calc.fullFor(game, s, t0 + regen + 5 * Calc.MIN))
    }

    @Test
    fun set_spendingKeepsProgressTowardNextPoint() {
        val s = state(100)
        val now = t0 + 2 * regen + 4 * Calc.MIN // 102 points, 4 min into the next one
        Calc.set(game, s, 72, now)
        assertEquals(72, s.value)
        assertEquals(now - 4 * Calc.MIN, s.anchor)
        // Next point arrives 2 minutes later, not a full 6.
        assertEquals(73, Calc.current(game, s, now + 2 * Calc.MIN))
    }

    @Test
    fun set_raisingValueRestartsTimer() {
        val s = state(100)
        val now = t0 + 4 * Calc.MIN
        Calc.set(game, s, 150, now)
        assertEquals(150, s.value)
        assertEquals(now, s.anchor)
    }

    @Test
    fun set_fromFullBarRestartsTimer() {
        val s = state(180)
        val now = t0 + 4 * Calc.MIN
        Calc.set(game, s, 150, now)
        assertEquals(now, s.anchor)
    }

    @Test
    fun set_clampsNegativeToZero() {
        val s = state(100)
        Calc.set(game, s, -5, t0)
        assertEquals(0, s.value)
    }

    @Test
    fun nextReset_usesServerTimezone() {
        // Arknights Global: 04:00 at UTC-7, which is 11:00 UTC.
        val midnightUtc = 1_700_006_400_000L // 2023-11-15 00:00 UTC
        val s = state(0)
        assertEquals(midnightUtc + 11 * Calc.HOUR, Calc.nextReset(game, s, midnightUtc))
        // Exactly at reset time, the next reset is a day later.
        val atReset = midnightUtc + 11 * Calc.HOUR
        assertEquals(atReset + Calc.DAY, Calc.nextReset(game, s, atReset))
    }

    @Test
    fun fmtDuration_roundsUpToMinutes() {
        assertEquals("0m", Calc.fmtDuration(0))
        assertEquals("1m", Calc.fmtDuration(1))
        assertEquals("59m", Calc.fmtDuration(59 * Calc.MIN))
        assertEquals("1h 0m", Calc.fmtDuration(Calc.HOUR))
        assertEquals("2h 5m", Calc.fmtDuration(2 * Calc.HOUR + 5 * Calc.MIN))
        assertEquals("1d 3h", Calc.fmtDuration(Calc.DAY + 3 * Calc.HOUR + 59 * Calc.MIN))
    }

    @Test
    fun builtInGames_haveSaneDefinitions() {
        val ids = Games.builtIn.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (g in Games.builtIn) {
            assertTrue("${g.id} cap", g.defaultCap > 0)
            assertTrue("${g.id} regen", g.regenSec > 0)
            assertTrue("${g.id} servers", g.servers.isNotEmpty())
            assertTrue("${g.id} presets", g.presets.all { it in 1..g.defaultCap })
        }
        assertTrue(ids.containsAll(Games.defaultEnabled))
    }
}
