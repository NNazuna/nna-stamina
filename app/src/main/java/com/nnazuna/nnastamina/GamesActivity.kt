package com.nnazuna.nnastamina

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.Toast

/** Choose which games appear on the home screen, and add your own. */
class GamesActivity : Activity() {

    private lateinit var store: Store
    private lateinit var k: Kit
    private val p get() = k.p
    private lateinit var builtIn: LinearLayout
    private lateinit var custom: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        k = Kit(this)
        Ui.styleWindow(this, p)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(k.dp(16f), k.dp(18f), k.dp(16f), k.dp(40f))
        }
        root.addView(backBar("Games"))
        root.addView(k.sectionHeader("Popular"))
        builtIn = k.group()
        root.addView(builtIn)
        root.addView(k.sectionHeader("Your games"))
        custom = k.group()
        root.addView(custom)

        setContentView(ScrollView(this).apply {
            setBackgroundColor(p.bg)
            addView(root)
        })
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun backBar(title: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(k.text("‹", 30f, 1).apply {
            gravity = Gravity.CENTER
            minWidth = k.dp(40f)
            minHeight = k.dp(44f)
            background = k.ripple(null, k.dp(20f).toFloat())
            setOnClickListener { finish() }
        })
        addView(k.text(title, 26f, 2).apply { setPadding(k.dp(4f), 0, 0, 0) })
    }

    private fun render() {
        builtIn.removeAllViews()
        Games.builtIn.forEachIndexed { i, def ->
            if (i > 0) builtIn.addView(k.divider(72f))
            builtIn.addView(gameRow(def))
        }
        custom.removeAllViews()
        val mine = store.customGames()
        mine.forEach { def ->
            custom.addView(gameRow(def))
            custom.addView(k.divider(72f))
        }
        custom.addView(k.textButton("Add a game") { addGame() }.apply {
            setPadding(k.dp(16f), k.dp(16f), k.dp(16f), k.dp(16f))
        })
    }

    private fun gameRow(def: GameDef): LinearLayout {
        val s = store.state(def)
        val sw = Switch(this).apply {
            isChecked = s.enabled
            setOnCheckedChangeListener { _, on ->
                val st = store.state(def)
                st.enabled = on
                store.save(def, st)
                Reminders.rescheduleAll(this@GamesActivity)
                StaminaWidget.updateAll(this@GamesActivity)
            }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = k.dp(68f)
            setPadding(k.dp(14f), k.dp(10f), k.dp(12f), k.dp(10f))
            background = k.ripple(null)
            setOnClickListener {
                startActivity(Intent(this@GamesActivity, GameActivity::class.java).putExtra(GameActivity.EXTRA_ID, def.id))
            }
        }
        row.addView(k.badge(def, 42f).apply {
            (layoutParams as LinearLayout.LayoutParams).rightMargin = k.dp(14f)
        })
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(k.text(def.name, 16f, 1))
        val rate = def.regenSec / 60.0
        val rateText = if (rate == Math.floor(rate)) "${rate.toInt()} min" else String.format(java.util.Locale.US, "%.1f min", rate)
        col.addView(k.text("${def.resource} · max ${s.cap} · 1 per $rateText", 13f, 0, p.muted).apply {
            setPadding(0, k.dp(3f), 0, 0)
        })
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(sw)
        return row
    }

    private fun addGame() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(k.dp(22f), k.dp(4f), k.dp(22f), 0)
        }
        fun field(label: String, hint: String, numeric: Boolean): EditText {
            box.addView(k.text(label, 13.5f, 1, p.muted).apply { setPadding(0, k.dp(10f), 0, 0) })
            return EditText(this).apply {
                this.hint = hint
                inputType = if (numeric) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
                typeface = Ui.font(this@GamesActivity, 1)
                box.addView(this)
            }
        }
        val name = field("Game", "e.g. Reverse: 1999", false)
        val res = field("Stamina name", "e.g. Activity", false)
        val cap = field("Max", "e.g. 240", true)
        val rate = field("Minutes per point", "e.g. 6", true)

        AlertDialog.Builder(this, Ui.dialogTheme(p))
            .setTitle("Add a game")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Add") { _, _ ->
                val n = name.text.toString().trim()
                val c = cap.text.toString().toIntOrNull()
                val r = rate.text.toString().replace(',', '.').toDoubleOrNull()
                if (n.isEmpty() || c == null || c <= 0 || r == null || r <= 0) {
                    Toast.makeText(this, "Fill in game, max, and minutes per point", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                store.addCustom(n, res.text.toString().trim(), c, (r * 60).toInt())
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
