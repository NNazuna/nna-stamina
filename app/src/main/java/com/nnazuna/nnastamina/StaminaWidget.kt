package com.nnazuna.nnastamina

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Home screen widget: up to five games, most urgent first. */
class StaminaWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val views = build(ctx)
        for (id in ids) mgr.updateAppWidget(id, views)
    }

    companion object {
        private val ROWS = intArrayOf(R.id.row0, R.id.row1, R.id.row2, R.id.row3, R.id.row4)
        private val MONO = intArrayOf(R.id.mono0, R.id.mono1, R.id.mono2, R.id.mono3, R.id.mono4)
        private val NAME = intArrayOf(R.id.name0, R.id.name1, R.id.name2, R.id.name3, R.id.name4)
        private val VALUE = intArrayOf(R.id.value0, R.id.value1, R.id.value2, R.id.value3, R.id.value4)
        private val SUB = intArrayOf(R.id.sub0, R.id.sub1, R.id.sub2, R.id.sub3, R.id.sub4)

        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, StaminaWidget::class.java))
            if (ids.isEmpty()) return
            val views = build(ctx)
            for (id in ids) mgr.updateAppWidget(id, views)
        }

        private fun build(ctx: Context): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget)
            val now = System.currentTimeMillis()
            val games = Ui.sortByUrgency(Store(ctx).enabled(), now)
            val clock = SimpleDateFormat("HH:mm", Locale.getDefault())

            v.setViewVisibility(R.id.empty, if (games.isEmpty()) View.VISIBLE else View.GONE)
            for (i in ROWS.indices) {
                val g = games.getOrNull(i)
                if (g == null) {
                    v.setViewVisibility(ROWS[i], View.GONE)
                    continue
                }
                val (def, s) = g
                v.setViewVisibility(ROWS[i], View.VISIBLE)
                v.setTextViewText(MONO[i], def.mono)
                v.setTextColor(MONO[i], def.accent)
                v.setTextViewText(NAME[i], def.name)
                if (!s.isSet) {
                    v.setTextViewText(VALUE[i], "—")
                    v.setTextViewText(SUB[i], "Not set")
                    continue
                }
                v.setTextViewText(VALUE[i], "${Calc.current(def, s, now)}/${s.cap}")
                val full = Calc.fullAt(def, s)
                v.setTextViewText(
                    SUB[i],
                    when {
                        full == null || full <= now -> "Full"
                        full - now < Calc.DAY -> "Full " + clock.format(Date(full))
                        else -> Calc.fmtDuration(full - now)
                    },
                )
            }

            val open = PendingIntent.getActivity(
                ctx, 1, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            v.setOnClickPendingIntent(R.id.root, open)
            return v
        }
    }
}
