package com.nnazuna.nnastamina

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/** Schedules and shows stamina notifications. */
object Reminders {
    private const val CH_STAMINA = "stamina"
    private const val CH_RESET = "reset"

    const val KIND_FULL = 1
    const val KIND_AT = 2
    const val KIND_RESET = 3

    private const val EXTRA_GAME = "game"
    private const val EXTRA_KIND = "kind"

    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_STAMINA, "Stamina", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "When stamina is full or reaches your target"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_RESET, "Daily reset", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Before a game's daily reset"
            }
        )
    }

    private fun requestCode(id: String, kind: Int) = id.hashCode() * 8 + kind

    private fun pending(ctx: Context, id: String, kind: Int, create: Boolean): PendingIntent? {
        val i = Intent(ctx, AlarmReceiver::class.java)
            .setAction("com.nnazuna.nnastamina.ALARM.$id.$kind")
            .putExtra(EXTRA_GAME, id)
            .putExtra(EXTRA_KIND, kind)
        val flags = PendingIntent.FLAG_IMMUTABLE or
            if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(ctx, requestCode(id, kind), i, flags)
    }

    fun canExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    private fun schedule(ctx: Context, at: Long, id: String, kind: Int) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pending(ctx, id, kind, true) ?: return
        try {
            if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancel(ctx: Context, id: String, kind: Int) {
        val pi = pending(ctx, id, kind, false) ?: return
        ctx.getSystemService(AlarmManager::class.java).cancel(pi)
        pi.cancel()
    }

    /** Recompute every alarm from the saved state. Safe to call often. */
    fun rescheduleAll(ctx: Context) {
        val store = Store(ctx)
        val now = System.currentTimeMillis()
        for (def in store.allGames()) {
            val s = store.state(def)
            for (k in intArrayOf(KIND_FULL, KIND_AT, KIND_RESET)) cancel(ctx, def.id, k)
            if (!s.enabled) continue

            if (s.notifyFull) Calc.fullAt(def, s)?.takeIf { it > now }?.let { schedule(ctx, it, def.id, KIND_FULL) }
            if (s.notifyAt > 0) Calc.reachAt(def, s, s.notifyAt)?.takeIf { it > now }?.let { schedule(ctx, it, def.id, KIND_AT) }
            if (s.notifyReset) {
                Calc.nextReset(def, s, now)?.let { reset ->
                    var at = reset - store.resetLeadMin * Calc.MIN
                    if (at <= now) at += Calc.DAY
                    schedule(ctx, at, def.id, KIND_RESET)
                }
            }
        }
    }

    fun show(ctx: Context, def: GameDef, s: GameState, kind: Int) {
        val now = System.currentTimeMillis()
        val (channel, title, text) = when (kind) {
            KIND_FULL -> Triple(CH_STAMINA, "${def.resource} is full", "${def.name} · ${s.cap}/${s.cap}")
            KIND_AT -> Triple(
                CH_STAMINA, "${def.resource} reached ${s.notifyAt}",
                "${def.name} · ${Calc.current(def, s, now)}/${s.cap}",
            )
            else -> Triple(
                CH_RESET, "Daily reset in ${Calc.fmtDuration(store(ctx).resetLeadMin * Calc.MIN)}",
                "${def.name} · ${Calc.current(def, s, now)}/${s.cap} ${def.resource}",
            )
        }
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, channel)
        else @Suppress("DEPRECATION") Notification.Builder(ctx)
        val n = b.setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setColor(def.accent)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setWhen(now)
            .setShowWhen(true)
            .build()
        try {
            ctx.getSystemService(NotificationManager::class.java)
                .notify(requestCode(def.id, kind), n)
        } catch (_: SecurityException) {
            // Notification permission not granted.
        }
    }

    private fun store(ctx: Context) = Store(ctx)

    fun gameOf(intent: Intent): String? = intent.getStringExtra(EXTRA_GAME)
    fun kindOf(intent: Intent): Int = intent.getIntExtra(EXTRA_KIND, 0)
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val store = Store(ctx)
        val def = store.game(Reminders.gameOf(intent))
        if (def != null) {
            val s = store.state(def)
            if (s.enabled) Reminders.show(ctx, def, s, Reminders.kindOf(intent))
        }
        Reminders.rescheduleAll(ctx)
        StaminaWidget.updateAll(ctx)
    }
}

/** Alarms are cleared on reboot, app update, and clock changes, so set them again. */
class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Reminders.createChannels(ctx)
        Reminders.rescheduleAll(ctx)
        StaminaWidget.updateAll(ctx)
    }
}
