package com.nnazuna.nnastamina

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Settings for one game. */
class GameActivity : Activity() {

    companion object {
        const val EXTRA_ID = "id"
    }

    private lateinit var store: Store
    private lateinit var k: Kit
    private val p get() = k.p
    private lateinit var def: GameDef

    private lateinit var capValue: TextView
    private var serverValue: TextView? = null
    private lateinit var atValue: TextView
    private var rateValue: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        k = Kit(this)
        Ui.styleWindow(this, p)
        def = store.game(intent.getStringExtra(EXTRA_ID)) ?: run { finish(); return }
        build()
    }

    private fun state() = store.state(def)

    private fun update(block: (GameState) -> Unit) {
        val s = state()
        block(s)
        store.save(def, s)
        Reminders.rescheduleAll(this)
        StaminaWidget.updateAll(this)
        refresh()
    }

    private fun build() {
        val s = state()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(k.dp(16f), k.dp(18f), k.dp(16f), k.dp(40f))
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(k.text("‹", 30f, 1).apply {
            gravity = Gravity.CENTER
            minWidth = k.dp(40f)
            minHeight = k.dp(44f)
            background = k.ripple(null, k.dp(20f).toFloat())
            setOnClickListener { finish() }
        })
        root.addView(top)

        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(k.dp(4f), k.dp(8f), 0, k.dp(6f))
        }
        head.addView(k.badge(def, 56f).apply {
            (layoutParams as LinearLayout.LayoutParams).rightMargin = k.dp(14f)
        })
        val names = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        names.addView(k.text(def.name, 24f, 2))
        names.addView(k.text(def.resource, 15f, 0, p.muted).apply { setPadding(0, k.dp(4f), 0, 0) })
        head.addView(names)
        root.addView(head)

        // ---- Stamina ----
        root.addView(k.sectionHeader("Stamina"))
        val g1 = k.group()
        k.valueRow("Max", if (def.capVaries) "Depends on your account level" else null) {
            Ui.numberDialog(this, "Max ${def.resource}", state().cap.toString()) { v ->
                v.toIntOrNull()?.takeIf { it > 0 }?.let { c -> update { it.cap = c; it.capConfirmed = true } }
            }
        }.let { (row, value) -> capValue = value; g1.addView(row) }
        g1.addView(k.divider())
        val (rateRow, rv) = k.valueRow("Recovery", null, if (def.custom) ({ editCustom() }) else null)
        rateValue = rv
        g1.addView(rateRow)
        root.addView(g1)

        // ---- Server ----
        if (def.servers.size > 1 || (def.servers.size == 1 && def.resetHour >= 0)) {
            root.addView(k.sectionHeader("Server"))
            val g2 = k.group()
            val (row, value) = k.valueRow("Server", null, if (def.servers.size > 1) ({ pickServer() }) else null)
            serverValue = value
            g2.addView(row)
            root.addView(g2)
        }

        // ---- Reminders ----
        root.addView(k.sectionHeader("Reminders"))
        val g3 = k.group()
        g3.addView(k.switchRow("When full", null, s.notifyFull) { on -> update { it.notifyFull = on } })
        g3.addView(k.divider())
        k.valueRow("Also at", "Get a heads-up before it caps") {
            Ui.numberDialog(this, "Remind me at", state().notifyAt.toString(), "0 = off") { v ->
                v.toIntOrNull()?.let { n -> update { it.notifyAt = n.coerceIn(0, it.cap) } }
            }
        }.let { (row, value) -> atValue = value; g3.addView(row) }
        if (def.resetHour >= 0) {
            g3.addView(k.divider())
            g3.addView(k.switchRow("Before daily reset", "${store.resetLeadMin / 60} hour before", s.notifyReset) { on ->
                update { it.notifyReset = on }
            })
        }
        root.addView(g3)

        // ---- Actions ----
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, k.dp(18f), 0, 0)
        }
        actions.addView(k.textButton(if (s.enabled) "Remove from home" else "Show on home") {
            update { it.enabled = !it.enabled }
            finish()
        })
        if (def.custom) {
            actions.addView(k.textButton("Delete game", p.danger) {
                AlertDialog.Builder(this, Ui.dialogTheme(p))
                    .setTitle("Delete ${def.name}?")
                    .setPositiveButton("Delete") { _, _ ->
                        store.deleteCustom(def.id)
                        Reminders.rescheduleAll(this)
                        StaminaWidget.updateAll(this)
                        finish()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            })
        }
        root.addView(actions)

        setContentView(ScrollView(this).apply {
            setBackgroundColor(p.bg)
            addView(root)
        })
        refresh()
    }

    private fun refresh() {
        val s = state()
        capValue.text = s.cap.toString()
        val rate = def.regenSec / 60.0
        rateValue?.text = if (rate == Math.floor(rate)) "1 per ${rate.toInt()} min"
        else String.format(Locale.US, "1 per %.1f min", rate)
        atValue.text = if (s.notifyAt in 1..s.cap) s.notifyAt.toString() else "Off"
        serverValue?.text = serverLabel(s.server)
    }

    /** "Asia · resets 03:00" in the phone's local time. */
    private fun serverLabel(index: Int): String {
        val srv = def.servers.getOrNull(index) ?: return ""
        if (def.resetHour < 0) return srv.name
        val probe = GameState.initial(def, true).apply { server = index }
        val reset = Calc.nextReset(def, probe, System.currentTimeMillis()) ?: return srv.name
        return "${srv.name} · resets ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(reset))}"
    }

    private fun pickServer() {
        val labels = def.servers.indices.map { serverLabel(it) }.toTypedArray()
        AlertDialog.Builder(this, Ui.dialogTheme(p))
            .setTitle("Server")
            .setSingleChoiceItems(labels, state().server) { d, which ->
                update { it.server = which }
                d.dismiss()
            }
            .show()
    }

    private fun editCustom() {
        Ui.numberDialog(this, "Minutes per point", (def.regenSec / 60.0).toString(), decimal = true) { v ->
            val m = v.toDoubleOrNull() ?: return@numberDialog
            if (m <= 0) return@numberDialog
            store.updateCustom(def, def.name, def.resource, (m * 60).toInt())
            def = store.game(def.id) ?: return@numberDialog
            Reminders.rescheduleAll(this)
            StaminaWidget.updateAll(this)
            refresh()
        }
    }
}
