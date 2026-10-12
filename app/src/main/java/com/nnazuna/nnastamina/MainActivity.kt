package com.nnazuna.nnastamina

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var store: Store
    private lateinit var k: Kit
    private val p get() = k.p
    private lateinit var banners: LinearLayout
    private lateinit var list: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private val clock get() = SimpleDateFormat("HH:mm", Locale.getDefault())

    private val ticker = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 15_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        k = Kit(this)
        Ui.styleWindow(this, p)
        Reminders.createChannels(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(k.dp(16f), k.dp(26f), k.dp(16f), k.dp(40f))
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(k.dp(4f), 0, 0, k.dp(10f))
        }
        top.addView(k.text("NNA Stamina", 30f, 2), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(k.textButton("Games") { startActivity(Intent(this, GamesActivity::class.java)) })
        root.addView(top)

        banners = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(banners)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)

        setContentView(ScrollView(this).apply {
            setBackgroundColor(p.bg)
            isVerticalScrollBarEnabled = false
            addView(root)
        })

        if (Build.VERSION.SDK_INT >= 33 && !store.askedNotifications) {
            store.askedNotifications = true
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        changed()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        render()
    }

    /** Save side effects: alarms, widget, screen. */
    private fun changed() {
        Reminders.rescheduleAll(this)
        StaminaWidget.updateAll(this)
        render()
    }

    // ---------- rendering ----------

    private fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun render() {
        banners.removeAllViews()
        if (!notificationsAllowed()) {
            banners.addView(banner("Notifications are off. You won't be told when stamina is full.", "Allow") {
                startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                )
            })
        } else if (!Reminders.canExact(this)) {
            banners.addView(banner("Reminders may arrive a few minutes late.", "Fix") {
                if (Build.VERSION.SDK_INT >= 31) {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
                }
            })
        }

        list.removeAllViews()
        val now = System.currentTimeMillis()
        val games = Ui.sortByUrgency(store.enabled(), now)
        if (games.isEmpty()) {
            list.addView(k.group().apply {
                setPadding(k.dp(16f), k.dp(18f), k.dp(16f), k.dp(8f))
                addView(k.text("No games yet", 17f, 2))
                addView(k.text("Pick the games you play to start tracking.", 14.5f, 0, p.muted).apply {
                    setPadding(0, k.dp(6f), 0, 0)
                })
                addView(k.textButton("Choose games") { startActivity(Intent(this@MainActivity, GamesActivity::class.java)) }.apply {
                    setPadding(0, k.dp(12f), 0, k.dp(10f))
                })
            })
            return
        }
        for ((def, s) in games) list.addView(card(def, s, now))
    }

    private fun banner(msg: String, action: String, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = k.rounded(p.raised, 14f)
            setPadding(k.dp(14f), k.dp(4f), k.dp(4f), k.dp(4f))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = k.dp(6f) }
        }
        row.addView(k.text(msg, 14f, 0, p.ink), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(k.textButton(action) { onClick() })
        return row
    }

    private fun card(def: GameDef, s: GameState, now: Long): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = k.ripple(k.rounded(p.surface, 18f), k.dp(18f).toFloat())
            setPadding(k.dp(16f), k.dp(14f), k.dp(6f), k.dp(14f))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = k.dp(10f) }
            setOnClickListener { quickUpdate(def) }
            setOnLongClickListener { openSettings(def); true }
        }

        // Header: badge, names, value
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(k.badge(def).apply {
            (layoutParams as LinearLayout.LayoutParams).rightMargin = k.dp(12f)
        })
        val names = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        names.addView(k.text(def.name, 16.5f, 2).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        names.addView(k.text(def.resource, 13.5f, 0, p.muted).apply { setPadding(0, k.dp(3f), 0, 0) })
        head.addView(names, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val cur = Calc.current(def, s, now)
        val value = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(k.dp(8f), 0, k.dp(2f), 0)
        }
        value.addView(k.text(if (s.isSet) cur.toString() else "—", 30f, 2, if (s.isSet && cur >= s.cap) def.accent else p.ink).apply {
            fontFeatureSettings = "tnum"
        })
        value.addView(k.text("/${s.cap}", 15f, 1, p.muted).apply {
            fontFeatureSettings = "tnum"
            setPadding(k.dp(2f), 0, 0, k.dp(3f))
        })
        head.addView(value)
        head.addView(k.text("⋮", 22f, 2, p.muted).apply {
            gravity = Gravity.CENTER
            minWidth = k.dp(36f)
            minHeight = k.dp(40f)
            background = k.ripple(null, k.dp(18f).toFloat())
            setOnClickListener { openSettings(def) }
        })
        card.addView(head)

        // Bar
        val meter = Meter(this, p.raised).apply {
            color = def.accent
            fraction = if (s.isSet) cur.toFloat() / s.cap else 0f
            marker = if (s.notifyAt in 1 until s.cap) s.notifyAt.toFloat() / s.cap else -1f
        }
        card.addView(meter, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, k.dp(8f)).apply {
            topMargin = k.dp(14f)
            rightMargin = k.dp(10f)
        })

        // Footer
        val foot = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, k.dp(10f), k.dp(10f), 0)
        }
        val (status, statusColor) = when {
            !s.isSet -> Pair("Tap to enter your current ${def.resource}", p.muted)
            cur >= s.cap -> {
                val lost = (Calc.fullFor(def, s, now) / def.regenMs).toInt()
                Pair(if (lost > 0) "Full · $lost wasted" else "Full", if (lost > 0) p.danger else def.accent)
            }
            else -> {
                val f = Calc.fullAt(def, s)!!
                Pair("Full in ${Calc.fmtDuration(f - now)} · ${dayClock(f, now)}", p.muted)
            }
        }
        foot.addView(k.text(status, 13.5f, 1, statusColor), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        Calc.nextReset(def, s, now)?.let {
            foot.addView(k.text("Reset ${Calc.fmtDuration(it - now)}", 13.5f, 0, p.muted).apply { fontFeatureSettings = "tnum" })
        }
        card.addView(foot)

        if (!s.capConfirmed) {
            card.addView(k.text("Max depends on your level. Tap ⋮ to set yours.", 13f, 1, p.accent).apply {
                setPadding(0, k.dp(6f), k.dp(10f), 0)
            })
        }
        return card
    }

    private fun dayClock(t: Long, now: Long): String {
        val dayNow = Calc.DAY.let { (now + tzOffset(now)) / it }
        val dayT = (t + tzOffset(t)) / Calc.DAY
        val c = clock.format(Date(t))
        return when (dayT - dayNow) {
            0L -> c
            1L -> "tomorrow $c"
            else -> SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(Date(t))
        }
    }

    private fun tzOffset(t: Long) = java.util.TimeZone.getDefault().getOffset(t).toLong()

    private fun openSettings(def: GameDef) {
        startActivity(Intent(this, GameActivity::class.java).putExtra(GameActivity.EXTRA_ID, def.id))
    }

    // ---------- quick update ----------

    private fun quickUpdate(def: GameDef) {
        val s = store.state(def)
        val now = System.currentTimeMillis()
        val cur = Calc.current(def, s, now)

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(k.dp(22f), k.dp(6f), k.dp(22f), 0)
        }
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(if (s.isSet) cur.toString() else "")
            hint = "0–${s.cap}"
            setSelection(text.length)
            typeface = Ui.font(this@MainActivity, 2)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
            fontFeatureSettings = "tnum"
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        row.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(k.text("/ ${s.cap}", 18f, 1, p.muted).apply { setPadding(k.dp(8f), 0, 0, k.dp(14f)) })
        box.addView(row)

        Calc.nextTickAt(def, s, now)?.let {
            val left = (it - now) / 1000
            box.addView(k.text(
                String.format(Locale.US, "Next +1 in %d:%02d", left / 60, left % 60), 13.5f, 0, p.muted,
            ).apply { setPadding(k.dp(4f), 0, 0, k.dp(6f)) })
        }

        fun currentInput(): Int = input.text.toString().toIntOrNull() ?: cur
        val chips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, k.dp(6f), 0, k.dp(4f))
        }
        val gap = { v: View -> chips.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = k.dp(8f) }) }
        for (n in def.presets) {
            gap(k.chip("−$n", p.ink, p.raised) {
                input.setText((currentInput() - n).coerceAtLeast(0).toString())
                input.setSelection(input.text.length)
            })
        }
        gap(k.chip("Full", 0xFF14171A.toInt(), def.accent) {
            input.setText(s.cap.toString())
            input.setSelection(input.text.length)
        })
        box.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        })

        val d = AlertDialog.Builder(this, Ui.dialogTheme(p))
            .setTitle(def.resource)
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val v = input.text.toString().toIntOrNull() ?: return@setPositiveButton
                val st = store.state(def)
                Calc.set(def, st, v, System.currentTimeMillis())
                store.save(def, st)
                changed()
            }
            .setNegativeButton("Cancel", null)
            .create()
        d.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        d.show()
    }
}
