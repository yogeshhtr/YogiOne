package com.yogi.one

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import org.json.JSONArray
import org.json.JSONObject

/** Habits: each one keeps the list of days it was done ("yyyy-MM-dd"). */
object Habits {
    private val SEED = listOf(
        "💧" to "Drink 8 glasses of water",
        "🏃" to "Exercise 20 minutes",
        "📖" to "Read 10 pages",
        "🧘" to "Meditate 5 minutes"
    )

    fun all(): JSONArray {
        if (!Store.has("habits")) {
            val list = JSONArray()
            for ((e, n) in SEED) list.put(JSONObject().put("e", e).put("n", n).put("done", JSONArray()))
            Store.setArr("habits", list)
        }
        return Store.arr("habits")
    }

    fun save(list: JSONArray) { Store.setArr("habits", list) }

    fun done(h: JSONObject, day: String): Boolean {
        val d = h.optJSONArray("done") ?: return false
        for (i in 0 until d.length()) if (d.optString(i) == day) return true
        return false
    }

    fun toggle(index: Int, day: String) {
        val list = all()
        if (index !in 0 until list.length()) return
        val h = list.getJSONObject(index)
        val d = h.optJSONArray("done") ?: JSONArray()
        val n = JSONArray()
        var found = false
        for (i in 0 until d.length()) {
            if (d.optString(i) == day) found = true else n.put(d.optString(i))
        }
        if (!found) n.put(day)
        // keep about one year
        val keep = JSONArray()
        for (i in maxOf(0, n.length() - 400) until n.length()) keep.put(n.optString(i))
        h.put("done", keep)
        save(list)
    }

    fun streak(h: JSONObject): Int {
        var i = if (done(h, Day.key())) 0 else 1
        var n = 0
        while (done(h, Day.ago(i))) { n++; i++ }
        return n
    }

    fun total(h: JSONObject): Int = h.optJSONArray("done")?.length() ?: 0

    fun today(): Pair<Int, Int> {
        val list = all()
        var n = 0
        for (i in 0 until list.length()) if (done(list.getJSONObject(i), Day.key())) n++
        return n to list.length()
    }
}

class HabitsTab(private val a: MainActivity) {

    fun build(): View {
        val (sv, col) = a.page()
        val all = Habits.all()
        val (d, t) = Habits.today()
        col.addView(a.header("Habits", when {
            t == 0 -> "Small steps, every day"
            d == t -> "🎉 All done today — amazing!"
            else -> "$d of $t done today · keep going"
        }))
        if (t > 0) {
            val b = a.bar(d / t.toFloat(), C.GREEN, 10)
            (b.layoutParams as LinearLayout.LayoutParams).setMargins(a.dp(22), a.dp(2), a.dp(22), a.dp(10))
            col.addView(b)
        }

        for (i in 0 until all.length()) {
            val h = all.getJSONObject(i)
            val today = Habits.done(h, Day.key())
            val c = a.card(if (today) 0xFF123026.toInt() else C.CARD)
            val row = a.hbox()
            row.addView(a.label(h.optString("e"), 30f).lin(WRAP, WRAP, r = a.dp(14)))
            val mid = a.vbox()
            mid.addView(a.label(h.optString("n"), 17f, C.TEXT, true))
            val s = Habits.streak(h)
            mid.addView(a.label(
                if (s > 0) "🔥 $s day streak · ${Habits.total(h)} days total" else "Start your streak today",
                12f, if (s > 0) C.GOLD else C.SUB).lin(t = a.dp(2)))
            val dots = a.hbox()
            dots.setPadding(0, a.dp(8), 0, 0)
            for (k in 6 downTo 0) {
                val day = Day.ago(k)
                val on = Habits.done(h, day)
                val dot = a.vbox()
                dot.gravity = Gravity.CENTER_HORIZONTAL
                dot.setOnClickListener { Habits.toggle(i, day); a.refresh() }
                dot.addView(a.label(Day.weekday(k).take(1), 10f, if (k == 0) C.TEXT else C.SUB, k == 0).apply { gravity = Gravity.CENTER })
                val circle = View(a)
                circle.background = rounded(if (on) C.GREEN else C.CARD2, a.dpf(7f))
                dot.addView(circle, LinearLayout.LayoutParams(a.dp(14), a.dp(14)).apply { topMargin = a.dp(3) })
                dots.addView(dot.lin(a.dp(26), WRAP))
            }
            mid.addView(dots)
            row.addView(mid.lin(0, WRAP, 1f))
            val check = a.label(if (today) "✔" else "", 24f, Color.WHITE, true)
            check.gravity = Gravity.CENTER
            check.background = if (today) rounded(C.GREEN, a.dpf(28f)) else rounded(0, a.dpf(28f), a.dp(3), C.LINE)
            row.addView(check.lin(a.dp(56), a.dp(56)))
            c.addView(row)
            c.setOnClickListener { c.tick(); Habits.toggle(i, Day.key()); a.refresh() }
            c.setOnLongClickListener { options(i, h); true }
            col.addView(c)
        }

        col.addView(a.pill("＋  New habit") { edit(-1, null) }.lin(l = a.dp(14), t = a.dp(12), r = a.dp(14)))
        col.addView(a.label("Tap a card = done today · tap a dot = fix that day · long-press = edit",
            11f, C.SUB).apply { gravity = Gravity.CENTER }.lin(t = a.dp(10)))
        return sv
    }

    private fun options(i: Int, h: JSONObject) {
        AlertDialog.Builder(a)
            .setTitle(h.optString("e") + "  " + h.optString("n"))
            .setItems(arrayOf("✏️  Edit", "🗑  Delete")) { _, which ->
                if (which == 0) {
                    edit(i, h)
                } else {
                    AlertDialog.Builder(a)
                        .setMessage("Delete this habit and its history?")
                        .setPositiveButton("Delete") { _, _ -> Habits.save(without(Habits.all(), i)); a.refresh() }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            .show()
    }

    private fun edit(i: Int, h: JSONObject?) {
        val body = a.vbox()
        var emoji = h?.optString("e") ?: "⭐"
        val big = a.label(emoji, 44f)
        big.gravity = Gravity.CENTER
        body.addView(big.lin())
        val name = a.input("Habit name, e.g. Walk 5,000 steps", value = h?.optString("n") ?: "")
        body.addView(name)
        val icons = listOf(
            "⭐", "💧", "🏃", "📖", "🧘", "🥗", "😴", "🙏", "💪", "🚶", "🎸", "✍️",
            "🧹", "📵", "💊", "🌅", "🧠", "💰", "🪥", "☀️", "🎨", "📚", "🍎", "🚭"
        )
        for (rowIcons in icons.chunked(6)) {
            val r = a.hbox()
            for (e in rowIcons) {
                val b = a.label(e, 26f)
                b.gravity = Gravity.CENTER
                b.setOnClickListener { emoji = e; big.text = e }
                r.addView(b.lin(0, a.dp(48), 1f))
            }
            body.addView(r)
        }
        val d = AlertDialog.Builder(a)
            .setTitle(if (h == null) "New habit" else "Edit habit")
            .setView(a.padded(body))
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()
        d.setOnShowListener {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val n = name.text.toString().trim()
                if (n.isEmpty()) {
                    a.toast("Give it a name")
                    return@setOnClickListener
                }
                val list = Habits.all()
                if (h == null) {
                    list.put(JSONObject().put("e", emoji).put("n", n).put("done", JSONArray()))
                } else if (i in 0 until list.length()) {
                    list.getJSONObject(i).put("e", emoji).put("n", n)
                }
                Habits.save(list)
                d.dismiss()
                a.refresh()
            }
        }
        d.show()
    }
}
