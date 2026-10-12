package com.nnazuna.nnastamina

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class Pal(
    val bg: Int,
    val surface: Int,
    val raised: Int,
    val ink: Int,
    val muted: Int,
    val line: Int,
    val accent: Int,
    val onAccent: Int,
    val danger: Int,
    val dark: Boolean,
)

object Ui {
    private val light = Pal(
        bg = 0xFFE9ECEF.toInt(),
        surface = 0xFFFAFBFC.toInt(),
        raised = 0xFFEEF1F4.toInt(),
        ink = 0xFF16191D.toInt(),
        muted = 0xFF5E6670.toInt(),
        line = 0xFFD6DBE0.toInt(),
        accent = 0xFFB97E12.toInt(),
        onAccent = 0xFFFFFFFF.toInt(),
        danger = 0xFFC2343F.toInt(),
        dark = false,
    )
    private val dark = Pal(
        bg = 0xFF111417.toInt(),
        surface = 0xFF1A1E22.toInt(),
        raised = 0xFF242A2F.toInt(),
        ink = 0xFFE6EAEE.toInt(),
        muted = 0xFF95A0AA.toInt(),
        line = 0xFF2A3036.toInt(),
        accent = 0xFFF0B850.toInt(),
        onAccent = 0xFF14171A.toInt(),
        danger = 0xFFF06A72.toInt(),
        dark = true,
    )

    fun pal(ctx: Context): Pal {
        val night = ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return if (night == Configuration.UI_MODE_NIGHT_YES) dark else light
    }

    private val fonts = HashMap<Int, Typeface>()

    /** 0 = Regular, 1 = Medium, 2 = SemiBold (Barlow). */
    fun font(ctx: Context, weight: Int = 0): Typeface = fonts.getOrPut(weight) {
        val name = when (weight) { 1 -> "Medium"; 2 -> "SemiBold"; else -> "Regular" }
        try {
            Typeface.createFromAsset(ctx.assets, "fonts/Barlow-$name.ttf")
        } catch (e: Exception) {
            if (weight >= 2) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    fun dp(ctx: Context, v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics).toInt()

    @Suppress("DEPRECATION")
    fun styleWindow(a: Activity, p: Pal) {
        a.window.statusBarColor = p.bg
        a.window.navigationBarColor = p.bg
        a.window.decorView.setBackgroundColor(p.bg)
        if (!p.dark && Build.VERSION.SDK_INT >= 23) {
            var f = a.window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            if (Build.VERSION.SDK_INT >= 26) f = f or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            a.window.decorView.systemUiVisibility = f
        }
    }

    fun dialogTheme(p: Pal): Int =
        if (p.dark) android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert

    fun numberDialog(
        a: Activity, title: String, value: String, help: String? = null, decimal: Boolean = false,
        onOk: (String) -> Unit,
    ) {
        val p = pal(a)
        val input = EditText(a).apply {
            inputType = if (decimal) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            else InputType.TYPE_CLASS_NUMBER
            setText(value)
            setSelection(text.length)
            typeface = font(a, 1)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        }
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(a, 22f), dp(a, 8f), dp(a, 22f), 0)
            if (help != null) addView(TextView(a).apply {
                text = help
                typeface = font(a)
                setTextColor(p.muted)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            })
            addView(input)
        }
        val d = AlertDialog.Builder(a, dialogTheme(p))
            .setTitle(title)
            .setView(box)
            .setPositiveButton("Save") { _, _ -> onOk(input.text.toString().trim().replace(',', '.')) }
            .setNegativeButton("Cancel", null)
            .create()
        d.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        d.show()
    }

    /** Full bars first (longest-wasting at the top), then by time to full; unset games last. */
    fun sortByUrgency(list: List<Pair<GameDef, GameState>>, now: Long): List<Pair<GameDef, GameState>> =
        list.sortedWith(compareBy<Pair<GameDef, GameState>>(
            { if (it.second.isSet) 0 else 1 },
            { (d, s) ->
                val f = Calc.fullAt(d, s)
                when {
                    !s.isSet -> Long.MAX_VALUE
                    f == null -> Long.MIN_VALUE
                    else -> f
                }
            },
        ))
}

/** Small view factory with a consistent style. */
class Kit(val a: Activity) {
    val p: Pal = Ui.pal(a)
    fun dp(v: Float) = Ui.dp(a, v)

    fun text(s: CharSequence, sizeSp: Float = 15f, weight: Int = 0, color: Int = p.ink) = TextView(a).apply {
        text = s
        typeface = Ui.font(a, weight)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        includeFontPadding = false
        setLineSpacing(0f, 1.15f)
    }

    fun ripple(base: Drawable?, radius: Float = 0f): Drawable {
        val mask = GradientDrawable().apply { cornerRadius = radius; setColor(0xFFFFFFFF.toInt()) }
        val rc = if (p.dark) 0x22FFFFFF else 0x1A16191D
        return RippleDrawable(ColorStateList.valueOf(rc), base, mask)
    }

    fun rounded(color: Int, radiusDp: Float, strokeColor: Int? = null) = GradientDrawable().apply {
        cornerRadius = dp(radiusDp).toFloat()
        setColor(color)
        if (strokeColor != null) setStroke(dp(1f), strokeColor)
    }

    fun group(): LinearLayout = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(p.surface, 16f)
        clipToOutline = true
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(8f) }
    }

    fun divider(insetDp: Float = 16f) = View(a).apply {
        setBackgroundColor(p.line)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
            leftMargin = dp(insetDp)
        }
    }

    fun sectionHeader(title: String): View = text(title, 17f, 2).apply {
        setPadding(dp(4f), dp(26f), 0, dp(2f))
    }

    fun textButton(label: String, color: Int = p.accent, onClick: () -> Unit) = text(label, 15f, 2, color).apply {
        setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
        background = ripple(null, dp(10f).toFloat())
        setOnClickListener { onClick() }
    }

    /** Short game label on a colored tile. */
    fun badge(def: GameDef, sizeDp: Float = 44f) = text(def.mono, if (def.mono.length > 2) 13f else 15f, 2, 0xFF14171A.toInt()).apply {
        gravity = Gravity.CENTER
        background = rounded(def.accent, 12f)
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

    /** Settings row: label (+ optional sub) on the left, value on the right. */
    fun valueRow(label: String, sub: String? = null, onClick: (() -> Unit)? = null): Pair<LinearLayout, TextView> {
        val row = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56f)
            setPadding(dp(16f), dp(10f), dp(16f), dp(10f))
            if (onClick != null) {
                background = ripple(null)
                setOnClickListener { onClick() }
            }
        }
        val left = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        left.addView(text(label, 16f, 1))
        if (sub != null) left.addView(text(sub, 13f, 0, p.muted).apply { setPadding(0, dp(3f), 0, 0) })
        row.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val value = text("", 16f, 1, p.muted).apply {
            fontFeatureSettings = "tnum"
            setPadding(dp(12f), 0, 0, 0)
        }
        row.addView(value)
        return Pair(row, value)
    }

    fun switchRow(label: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        val sw = android.widget.Switch(a).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, c -> onChange(c) }
        }
        val row = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56f)
            setPadding(dp(16f), dp(10f), dp(12f), dp(10f))
            background = ripple(null)
            setOnClickListener { sw.toggle() }
        }
        val left = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        left.addView(text(label, 16f, 1))
        if (sub != null) left.addView(text(sub, 13f, 0, p.muted).apply { setPadding(0, dp(3f), 0, 0) })
        row.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(sw)
        return row
    }

    /** Pill-shaped chip. */
    fun chip(label: String, fg: Int, bg: Int, onClick: () -> Unit) = text(label, 15f, 2, fg).apply {
        gravity = Gravity.CENTER
        fontFeatureSettings = "tnum"
        setPadding(dp(16f), dp(10f), dp(16f), dp(10f))
        background = ripple(rounded(bg, 20f), dp(20f).toFloat())
        setOnClickListener { onClick() }
    }
}

/** Stamina bar with an optional target marker. */
class Meter(ctx: Context, private val track: Int) : View(ctx) {
    var fraction = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    var marker = -1f
        set(v) { field = v; invalidate() }
    var color = 0xFFFFFFFF.toInt()
        set(v) { field = v; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    override fun onDraw(c: Canvas) {
        val h = height.toFloat()
        val rad = h / 2
        paint.color = track
        r.set(0f, 0f, width.toFloat(), h)
        c.drawRoundRect(r, rad, rad, paint)
        if (fraction > 0f) {
            paint.color = color
            r.set(0f, 0f, maxOf(h, width * fraction), h)
            c.drawRoundRect(r, rad, rad, paint)
        }
        if (marker in 0f..1f) {
            paint.color = if (fraction >= marker) 0x99000000.toInt() else color
            val x = width * marker
            c.drawRect(x - h * 0.12f, 0f, x + h * 0.12f, h, paint)
        }
    }
}
