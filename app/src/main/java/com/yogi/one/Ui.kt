package com.yogi.one

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** Colours of the whole app (dark theme). */
object C {
    val BG = 0xFF0D1117.toInt()
    val CARD = 0xFF161B26.toInt()
    val CARD2 = 0xFF232B3B.toInt()
    val LINE = 0xFF2E3850.toInt()
    val TEXT = 0xFFF1F4F9.toInt()
    val SUB = 0xFF8F9BB0.toInt()
    val ACCENT = 0xFF8B6CFF.toInt()
    val ACCENT2 = 0xFF2EC4C4.toInt()
    val GREEN = 0xFF34D399.toInt()
    val RED = 0xFFFF6B7A.toInt()
    val GOLD = 0xFFFFC857.toInt()
    val BLUE = 0xFF4DA3FF.toInt()
    val ORANGE = 0xFFFF9F43.toInt()
    val PINK = 0xFFFF7EB6.toInt()
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

fun rounded(color: Int, radius: Float, strokeW: Int = 0, strokeColor: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
        if (strokeW > 0) setStroke(strokeW, strokeColor)
    }

fun Context.ripple(color: Int, radiusDp: Float): RippleDrawable =
    RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), rounded(color, dpf(radiusDp)), null)

fun Context.label(s: CharSequence, size: Float = 15f, color: Int = C.TEXT, bold: Boolean = false): TextView =
    TextView(this).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

fun Context.vbox(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

fun Context.hbox(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
}

/** Sets LinearLayout params (sizes and margins in px). */
fun <T : View> T.lin(
    w: Int = MATCH, h: Int = WRAP, weight: Float = 0f,
    l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0
): T {
    layoutParams = LinearLayout.LayoutParams(w, h, weight).also { it.setMargins(l, t, r, b) }
    return this
}

fun Context.card(color: Int = C.CARD): LinearLayout = vbox().apply {
    background = rounded(color, dpf(22f))
    val p = dp(16)
    setPadding(p, p, p, p)
}.lin(l = dp(14), t = dp(6), r = dp(14), b = dp(6))

fun Context.section(s: String): TextView = label(s, 12f, C.SUB, true).apply {
    letterSpacing = 0.12f
    setPadding(dp(24), dp(18), dp(24), dp(4))
}

fun Context.header(big: String, small: String): View = vbox().apply {
    setPadding(dp(22), dp(18), dp(22), dp(8))
    addView(label(big, 30f, C.TEXT, true))
    addView(label(small, 14f, C.SUB))
}

fun Context.pill(s: String, color: Int = C.ACCENT, onClick: () -> Unit): TextView =
    label(s, 15f, Color.WHITE, true).apply {
        gravity = Gravity.CENTER
        background = ripple(color, 16f)
        setPadding(dp(18), dp(13), dp(18), dp(13))
        setOnClickListener { onClick() }
    }

fun Context.chip(s: String, selected: Boolean, onClick: () -> Unit): TextView =
    label(s, 14f, if (selected) Color.WHITE else C.TEXT, selected).apply {
        gravity = Gravity.CENTER
        background = ripple(if (selected) C.ACCENT else C.CARD2, 14f)
        setPadding(dp(14), dp(9), dp(14), dp(9))
        setOnClickListener { onClick() }
    }.lin(WRAP, WRAP, r = dp(8), b = dp(8))

fun Context.hscroll(row: View): HorizontalScrollView = HorizontalScrollView(this).apply {
    isHorizontalScrollBarEnabled = false
    addView(row)
}

fun Context.input(hintText: String, number: Boolean = false, value: String = ""): EditText = EditText(this).apply {
    hint = hintText
    setText(value)
    setHintTextColor(C.SUB)
    setTextColor(C.TEXT)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
    background = rounded(C.CARD2, dpf(14f))
    setPadding(dp(14), dp(12), dp(14), dp(12))
    setSingleLine(true)
    inputType = if (number) {
        InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
    } else {
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
    }
}.lin(t = dp(6), b = dp(6))

/** A progress bar: [frac] of the width in [color]. */
fun Context.bar(frac: Float, color: Int, heightDp: Int = 8): LinearLayout {
    val f = frac.coerceIn(0f, 1f)
    val row = LinearLayout(this)
    row.background = rounded(C.CARD2, dpf(heightDp / 2f))
    val fill = View(this)
    fill.background = rounded(color, dpf(heightDp / 2f))
    row.addView(fill, LinearLayout.LayoutParams(0, MATCH, f))
    row.addView(View(this), LinearLayout.LayoutParams(0, MATCH, 1f - f))
    return row.lin(h = dp(heightDp), t = dp(6))
}

/** Small centred number box: icon, value, caption. Fills 1/n of a row. */
fun Context.statBox(icon: String, value: String, what: String, valueColor: Int = C.TEXT): View = vbox().apply {
    gravity = Gravity.CENTER_HORIZONTAL
    addView(label(icon, 18f).apply { gravity = Gravity.CENTER })
    addView(label(value, 16f, valueColor, true).apply { gravity = Gravity.CENTER; maxLines = 1 })
    addView(label(what, 11f, C.SUB).apply { gravity = Gravity.CENTER })
}.lin(0, WRAP, 1f)

/** Left text, right bold value. */
fun Context.resultRow(name: String, value: String, color: Int = C.TEXT, big: Boolean = false): View = hbox().apply {
    setPadding(0, dp(6), 0, dp(6))
    addView(label(name, 14f, C.SUB).lin(0, WRAP, 1f))
    addView(label(value, if (big) 22f else 16f, color, true))
}

/** Vertical page that scrolls. */
fun Context.page(): Pair<ScrollView, LinearLayout> {
    val col = vbox()
    col.setPadding(0, dp(6), 0, dp(28))
    val sv = ScrollView(this)
    sv.isVerticalScrollBarEnabled = false
    sv.addView(col)
    return sv to col
}

/** Puts a view inside a padded, scrollable frame for dialogs. */
fun Context.padded(v: View): View {
    val f = FrameLayout(this)
    f.setPadding(dp(20), dp(8), dp(20), dp(4))
    f.addView(v)
    val sv = ScrollView(this)
    sv.addView(f)
    return sv
}

/** A dialog with a Close button. [onClose] runs when it goes away. */
fun Context.sheet(title: String, body: View, onClose: (() -> Unit)? = null): AlertDialog {
    val d = AlertDialog.Builder(this)
        .setTitle(title)
        .setView(padded(body))
        .setPositiveButton("Close", null)
        .create()
    d.setOnDismissListener { onClose?.invoke() }
    d.show()
    return d
}

fun Context.toast(s: String) {
    Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}

fun Context.spinner(items: List<String>, sel: Int, onPick: (Int) -> Unit): Spinner {
    val sp = Spinner(this)
    sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
    sp.setSelection(sel)
    sp.background = rounded(C.CARD2, dpf(14f))
    sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, rowId: Long) { onPick(pos) }
        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }
    return sp.lin(t = dp(6), b = dp(6))
}

private val inFmt: NumberFormat = NumberFormat.getNumberInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 }

/** Rupees with Indian grouping: Rs 1,25,000 */
fun rs(v: Double): String = "Rs " + synchronized(inFmt) { inFmt.format(v) }

fun num(v: Double, digits: Int = 2): String = String.format(Locale.US, "%,." + digits + "f", v)

/** Short readable number: 1,234.5 / 0.0254 / 1.2340e-07 */
fun fmtNum(v: Double): String {
    if (v.isNaN() || v.isInfinite()) return "—"
    if (v != 0.0 && (abs(v) >= 1e12 || abs(v) < 0.0001)) return String.format(Locale.US, "%.4e", v)
    return String.format(Locale.US, "%,.4f", v).trimEnd('0').trimEnd('.')
}
