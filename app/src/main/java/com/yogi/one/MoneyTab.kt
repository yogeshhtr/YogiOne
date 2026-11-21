package com.yogi.one

import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Income and expenses, saved as {t: time, a: amount, c: category, n: note, i: income?}. */
object Money {
    val EXP = listOf(
        "🍔 Food", "🚌 Travel", "🛒 Shopping", "🏠 Home", "💡 Bills", "📱 Mobile",
        "💊 Health", "🎓 Study", "🎉 Fun", "🎁 Gifts", "🙏 Puja", "📦 Other"
    )
    val INC = listOf("💼 Salary", "💻 Business", "🎁 Gift", "📈 Other")

    /** 0 = this month, 1 = last month, … */
    var offset = 0

    fun all(): JSONArray = Store.arr("money")

    fun add(amount: Double, cat: String, note: String, income: Boolean) {
        val list = all()
        list.put(JSONObject().put("t", System.currentTimeMillis()).put("a", amount)
            .put("c", cat).put("n", note).put("i", income))
        Store.setArr("money", list)
    }

    fun delete(t: Long) {
        val list = all()
        val keep = JSONArray()
        for (i in 0 until list.length()) {
            val o = list.getJSONObject(i)
            if (o.optLong("t") != t) keep.put(o)
        }
        Store.setArr("money", keep)
    }

    fun monthCal(): Calendar {
        val c = Calendar.getInstance()
        c.set(Calendar.DAY_OF_MONTH, 1)
        c.add(Calendar.MONTH, -offset)
        return c
    }

    fun inMonth(c: Calendar): List<JSONObject> {
        val y = c.get(Calendar.YEAR)
        val m = c.get(Calendar.MONTH)
        val list = all()
        val out = ArrayList<JSONObject>()
        val t = Calendar.getInstance()
        for (i in 0 until list.length()) {
            val o = list.getJSONObject(i)
            t.timeInMillis = o.optLong("t")
            if (t.get(Calendar.YEAR) == y && t.get(Calendar.MONTH) == m) out.add(o)
        }
        return out.sortedByDescending { it.optLong("t") }
    }

    fun spentOn(day: String): Double {
        val list = all()
        var s = 0.0
        for (i in 0 until list.length()) {
            val o = list.getJSONObject(i)
            if (!o.optBoolean("i") && Day.key(o.optLong("t")) == day) s += o.optDouble("a", 0.0)
        }
        return s
    }

    fun budget(): Double = Store.str("budget", "0").toDoubleOrNull() ?: 0.0
}

class MoneyTab(private val a: MainActivity) {

    fun build(): View {
        val (sv, col) = a.page()
        val mc = Money.monthCal()
        val items = Money.inMonth(mc)
        var inc = 0.0
        var exp = 0.0
        val byCat = LinkedHashMap<String, Double>()
        for (o in items) {
            val v = o.optDouble("a", 0.0)
            if (o.optBoolean("i")) {
                inc += v
            } else {
                exp += v
                val c = o.optString("c")
                byCat[c] = (byCat[c] ?: 0.0) + v
            }
        }

        col.addView(a.header("Money", "Know where every rupee goes"))

        // ---------- month switcher
        val ms = a.hbox()
        ms.gravity = Gravity.CENTER
        ms.setPadding(a.dp(16), 0, a.dp(16), 0)
        ms.addView(a.chip("  ◀  ", false) { Money.offset++; a.refresh() })
        val monthName = a.label(SimpleDateFormat("MMMM yyyy", Locale.US).format(mc.time), 17f, C.TEXT, true)
        monthName.gravity = Gravity.CENTER
        ms.addView(monthName.lin(0, WRAP, 1f))
        ms.addView(a.chip("  ▶  ", false) { if (Money.offset > 0) { Money.offset--; a.refresh() } })
        col.addView(ms)

        // ---------- summary
        val sum = a.card(0xFF1E1A3A.toInt())
        val r = a.hbox()
        r.addView(a.statBox("⬇️", rs(inc), "income", C.GREEN))
        r.addView(a.statBox("⬆️", rs(exp), "spent", C.RED))
        r.addView(a.statBox("💼", rs(inc - exp), "balance", if (inc - exp >= 0) C.TEXT else C.RED))
        sum.addView(r)
        val b = Money.budget()
        if (b > 0) {
            val f = (exp / b).toFloat()
            sum.addView(a.label("🎯 Budget ${rs(b)} · ${(f * 100).roundToInt()}% used · ${rs((b - exp).coerceAtLeast(0.0))} left",
                13f, C.SUB).lin(t = a.dp(14)))
            sum.addView(a.bar(f, if (f < 0.7f) C.GREEN else if (f < 1f) C.GOLD else C.RED, 10))
        } else {
            sum.addView(a.label("🎯 Tap here to set a monthly budget", 13f, C.ACCENT2, true).lin(t = a.dp(12)))
        }
        sum.setOnClickListener { setBudget() }
        col.addView(sum)

        // ---------- add buttons
        val add = a.hbox()
        add.setPadding(a.dp(14), a.dp(6), a.dp(14), 0)
        add.addView(a.pill("－  Expense", 0xFFE5485D.toInt()) { addDialog(false) }.lin(0, WRAP, 1f, r = a.dp(6)))
        add.addView(a.pill("＋  Income", 0xFF1F9D6B.toInt()) { addDialog(true) }.lin(0, WRAP, 1f, l = a.dp(6)))
        col.addView(add)

        // ---------- where it went
        if (byCat.isNotEmpty()) {
            col.addView(a.section("WHERE IT WENT"))
            val bc = a.card()
            val maxV = byCat.values.maxOrNull() ?: 1.0
            val colors = listOf(C.ACCENT, C.ACCENT2, C.GOLD, C.ORANGE, C.BLUE, C.PINK, C.GREEN, C.RED)
            byCat.entries.sortedByDescending { it.value }.forEachIndexed { i, (cat, v) ->
                val row = a.hbox().lin(t = if (i == 0) 0 else a.dp(10))
                row.addView(a.label(cat, 14f, C.TEXT, true).lin(0, WRAP, 1f))
                row.addView(a.label(rs(v) + "  ·  ${(v / exp * 100).roundToInt()}%", 13f, C.SUB))
                bc.addView(row)
                bc.addView(a.bar((v / maxV).toFloat(), colors[i % colors.size]))
            }
            val days = if (Money.offset == 0) Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
            else mc.getActualMaximum(Calendar.DAY_OF_MONTH)
            bc.addView(a.label("📊 Daily average: ${rs(exp / days)}", 13f, C.SUB).lin(t = a.dp(14)))
            col.addView(bc)
        }

        // ---------- list
        col.addView(a.section("TRANSACTIONS"))
        val tc = a.card()
        if (items.isEmpty()) tc.addView(a.label("No entries this month yet.\nTap “Expense” to add your first one.", 14f, C.SUB))
        var lastDay = ""
        val dayFmt = SimpleDateFormat("EEE, d MMM", Locale.US)
        val timeFmt = SimpleDateFormat("HH:mm", Locale.US)
        for (o in items) {
            val time = o.optLong("t")
            val dk = Day.key(time)
            if (dk != lastDay) {
                lastDay = dk
                tc.addView(a.label(dayFmt.format(Date(time)).uppercase(Locale.US), 11f, C.SUB, true)
                    .lin(t = if (tc.childCount == 0) 0 else a.dp(12), b = a.dp(2)))
            }
            val cat = o.optString("c")
            val isInc = o.optBoolean("i")
            val row = a.hbox()
            row.setPadding(0, a.dp(7), 0, a.dp(7))
            row.addView(a.label(cat.substringBefore(" "), 22f).lin(WRAP, WRAP, r = a.dp(12)))
            val mid = a.vbox()
            mid.addView(a.label(o.optString("n").ifEmpty { cat.substringAfter(" ") }, 15f, C.TEXT, true))
            mid.addView(a.label(cat.substringAfter(" ") + " · " + timeFmt.format(Date(time)), 12f, C.SUB))
            row.addView(mid.lin(0, WRAP, 1f))
            row.addView(a.label((if (isInc) "+ " else "− ") + rs(o.optDouble("a", 0.0)), 15f,
                if (isInc) C.GREEN else C.RED, true))
            row.setOnLongClickListener {
                AlertDialog.Builder(a)
                    .setMessage("Delete this entry?")
                    .setPositiveButton("Delete") { _, _ -> Money.delete(time); a.refresh() }
                    .setNegativeButton("Cancel", null)
                    .show()
                true
            }
            tc.addView(row)
        }
        col.addView(tc)
        if (items.isNotEmpty()) {
            col.addView(a.label("Long-press an entry to delete it", 11f, C.SUB)
                .apply { gravity = Gravity.CENTER }.lin(t = a.dp(8)))
        }
        return sv
    }

    private fun setBudget() {
        val inp = a.input("Monthly budget (Rs)", number = true,
            value = Money.budget().let { if (it > 0) it.roundToInt().toString() else "" })
        AlertDialog.Builder(a)
            .setTitle("🎯 Monthly budget")
            .setView(a.padded(inp))
            .setPositiveButton("Save") { _, _ ->
                Store.setStr("budget", (inp.text.toString().toDoubleOrNull() ?: 0.0).toString())
                a.refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addDialog(income: Boolean) {
        val cats = if (income) Money.INC else Money.EXP
        var cat = cats[0]
        val body = a.vbox()
        val amt = a.input("Amount (Rs)", number = true)
        body.addView(amt)
        body.addView(a.label("CATEGORY", 11f, C.SUB, true).lin(t = a.dp(8), b = a.dp(6)))
        val grid = a.vbox()
        fun drawCats() {
            grid.removeAllViews()
            for (rowCats in cats.chunked(3)) {
                val r = a.hbox()
                for (c in rowCats) {
                    val chip = a.chip(c, c == cat) { cat = c; drawCats() }
                    val lp = chip.layoutParams as LinearLayout.LayoutParams
                    lp.width = 0
                    lp.weight = 1f
                    r.addView(chip)
                }
                repeat(3 - rowCats.size) { r.addView(View(a), LinearLayout.LayoutParams(0, 1, 1f)) }
                grid.addView(r)
            }
        }
        drawCats()
        body.addView(grid)
        val note = a.input("Note (optional)")
        body.addView(note)
        val d = AlertDialog.Builder(a)
            .setTitle(if (income) "Add income" else "Add expense")
            .setView(a.padded(body))
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()
        d.setOnShowListener {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val v = amt.text.toString().toDoubleOrNull()
                if (v == null || v <= 0) {
                    a.toast("Enter an amount")
                    return@setOnClickListener
                }
                Money.add(v, cat, note.text.toString().trim(), income)
                Money.offset = 0
                d.dismiss()
                a.refresh()
            }
        }
        d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        d.show()
        amt.requestFocus()
    }
}
