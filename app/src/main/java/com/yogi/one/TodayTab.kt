package com.yogi.one

import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Home: clock, weather, today's numbers, exchange rates, a thought and the to-do list. */
class TodayTab(private val a: MainActivity) {

    fun build(): View {
        val (sv, col) = a.page()

        // ---------- greeting + clock
        val hello = a.label("", 15f, C.SUB)
        val name = a.label(Store.str("name", "Yogi") + " ✨", 30f, C.TEXT, true)
        val clock = a.label("", 46f, C.TEXT, true).lin(t = a.dp(6))
        val date = a.label("", 14f, C.ACCENT2, true)
        val head = a.vbox()
        head.setPadding(a.dp(22), a.dp(18), a.dp(22), a.dp(6))
        head.addView(hello)
        head.addView(name)
        head.addView(clock)
        head.addView(date)
        name.setOnClickListener { askName() }
        col.addView(head)
        val clockFmt = SimpleDateFormat("HH:mm:ss", Locale.US)
        val dateFmt = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.US)
        val update: () -> Unit = {
            val now = Date()
            clock.text = clockFmt.format(now)
            date.text = dateFmt.format(now)
            hello.text = greeting()
        }
        update()
        a.ticker = update

        // ---------- today in numbers
        val (hd, ht) = Habits.today()
        val stats = a.hbox()
        stats.setPadding(a.dp(10), a.dp(8), a.dp(10), 0)
        stats.addView(stat("✅", "$hd/$ht", "habits") { a.show(2) })
        stats.addView(stat("⏳", "${Focus.minutes(Day.key())} min", "focus") { a.show(1) })
        stats.addView(stat("💸", rs(Money.spentOn(Day.key())), "spent today") { a.show(3) })
        col.addView(stats)

        // ---------- weather
        col.addView(a.section("WEATHER"))
        val wx = a.card()
        col.addView(wx)
        val cachedWx = Weather.cached()
        fillWeather(wx, cachedWx)
        if (cachedWx == null || Weather.stale()) {
            Net.bg({ Weather.fetch() }) { o ->
                if (wx.isAttachedToWindow) fillWeather(wx, o ?: Weather.cached(), o == null)
            }
        }

        // ---------- exchange rates
        col.addView(a.section("EXCHANGE RATES"))
        val fx = a.card()
        col.addView(fx)
        val cachedFx = Forex.cached()
        fillFx(fx, cachedFx)
        if (cachedFx == null || Forex.stale()) {
            Net.bg({ Forex.fetch() }) { p ->
                if (fx.isAttachedToWindow) fillFx(fx, p ?: Forex.cached(), p == null)
            }
        }

        // ---------- thought for today
        col.addView(a.section("THOUGHT FOR TODAY"))
        val q = Quotes.today()
        val qc = a.card(0xFF1E1A3A.toInt())
        qc.addView(a.label("“${q.first}”", 17f, C.TEXT, true))
        qc.addView(a.label("— ${q.second}", 13f, C.GOLD).lin(t = a.dp(8)))
        col.addView(qc)

        // ---------- to-do
        col.addView(a.section("TO-DO"))
        val todo = a.card()
        col.addView(todo)
        fillTodo(todo, false)

        return sv
    }

    private fun greeting(): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            h < 5 -> "Good night 🌙"
            h < 12 -> "Good morning ☀️"
            h < 17 -> "Good afternoon 🌤️"
            h < 21 -> "Good evening 🌆"
            else -> "Good night 🌙"
        }
    }

    private fun stat(icon: String, value: String, what: String, onClick: () -> Unit): View {
        val v = a.vbox()
        v.gravity = Gravity.CENTER
        v.background = a.ripple(C.CARD, 20f)
        v.setPadding(a.dp(6), a.dp(12), a.dp(6), a.dp(12))
        v.addView(a.label(icon, 20f).apply { gravity = Gravity.CENTER })
        v.addView(a.label(value, 16f, C.TEXT, true).apply { gravity = Gravity.CENTER; maxLines = 1 })
        v.addView(a.label(what, 11f, C.SUB).apply { gravity = Gravity.CENTER })
        v.setOnClickListener { onClick() }
        return v.lin(0, WRAP, 1f, a.dp(4), 0, a.dp(4), 0)
    }

    private fun askName() {
        val inp = a.input("Your name", value = Store.str("name", "Yogi"))
        AlertDialog.Builder(a)
            .setTitle("What should I call you?")
            .setView(a.padded(inp))
            .setPositiveButton("Save") { _, _ ->
                Store.setStr("name", inp.text.toString().trim().ifEmpty { "Yogi" })
                a.refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ================= weather

    private fun JSONObject.whole(k: String): Int {
        val v = optDouble(k, 0.0)
        return if (v.isNaN()) 0 else v.roundToInt()
    }

    private fun fillWeather(card: LinearLayout, o: JSONObject?, failed: Boolean = false) {
        card.removeAllViews()
        card.setOnClickListener { pickCity(card) }
        if (o == null) {
            card.addView(a.label(
                if (failed) "📡 No internet for weather right now. Tap to choose a city or try later."
                else "📡 Loading weather for ${Weather.city()}…", 14f, C.SUB))
            return
        }
        val cur = o.optJSONObject("current") ?: JSONObject()
        val code = cur.optInt("weather_code")
        val isDay = cur.optInt("is_day", 1) == 1
        val top = a.hbox()
        val left = a.vbox()
        left.addView(a.label("📍 ${Weather.city()}  ›", 14f, C.SUB, true))
        left.addView(a.label("${cur.whole("temperature_2m")}°", 54f, C.TEXT, true))
        left.addView(a.label("${Weather.desc(code)} · feels ${cur.whole("apparent_temperature")}°", 14f, C.SUB))
        top.addView(left.lin(0, WRAP, 1f))
        top.addView(a.label(Weather.icon(code, isDay), 64f))
        card.addView(top)

        val daily = o.optJSONObject("daily")
        val sunrise = daily?.optJSONArray("sunrise")?.optString(0)?.substringAfter('T') ?: ""
        val sunset = daily?.optJSONArray("sunset")?.optString(0)?.substringAfter('T') ?: ""
        card.addView(a.label(
            "💧 ${cur.optInt("relative_humidity_2m")}%   💨 ${cur.whole("wind_speed_10m")} km/h   🌅 $sunrise   🌇 $sunset",
            13f, C.SUB).lin(t = a.dp(8)))

        if (daily == null) return
        val times = daily.optJSONArray("time") ?: return
        val codes = daily.optJSONArray("weather_code")
        val maxT = daily.optJSONArray("temperature_2m_max")
        val minT = daily.optJSONArray("temperature_2m_min")
        val rain = daily.optJSONArray("precipitation_probability_max")
        val parse = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dayName = SimpleDateFormat("EEE", Locale.US)
        val row = a.hbox()
        row.setPadding(0, a.dp(14), 0, 0)
        for (i in 0 until minOf(6, times.length())) {
            val dcol = a.vbox()
            dcol.gravity = Gravity.CENTER_HORIZONTAL
            val dn = if (i == 0) "Today" else try {
                dayName.format(parse.parse(times.optString(i)) ?: Date())
            } catch (_: Exception) { "" }
            dcol.addView(a.label(dn, 12f, if (i == 0) C.ACCENT2 else C.SUB, true).apply { gravity = Gravity.CENTER })
            dcol.addView(a.label(Weather.icon(codes?.optInt(i) ?: 0), 22f).apply { gravity = Gravity.CENTER })
            dcol.addView(a.label("${(maxT?.optDouble(i, 0.0) ?: 0.0).roundToInt()}°", 14f, C.TEXT, true).apply { gravity = Gravity.CENTER })
            dcol.addView(a.label("${(minT?.optDouble(i, 0.0) ?: 0.0).roundToInt()}°", 12f, C.SUB).apply { gravity = Gravity.CENTER })
            val rp = rain?.optInt(i) ?: 0
            if (rp >= 30) dcol.addView(a.label("☔$rp%", 10f, C.BLUE).apply { gravity = Gravity.CENTER })
            row.addView(dcol.lin(0, WRAP, 1f))
        }
        card.addView(row)
    }

    private fun pickCity(card: LinearLayout) {
        val body = a.vbox()
        val q = a.input("Search a city, e.g. Pokhara")
        val results = a.vbox()
        body.addView(q)
        var dlg: AlertDialog? = null
        val go = a.pill("🔍  Search") {
            val text = q.text.toString().trim()
            if (text.isEmpty()) return@pill
            results.removeAllViews()
            results.addView(a.label("Searching…", 14f, C.SUB))
            Net.bg({ Weather.search(text) }) { list ->
                results.removeAllViews()
                if (list.isNullOrEmpty()) {
                    results.addView(a.label("Nothing found (or no internet).", 14f, C.SUB))
                    return@bg
                }
                for (p in list) {
                    val row = a.label("📍 ${p.name}\n     ${p.where}", 15f)
                    row.setPadding(0, a.dp(10), 0, a.dp(10))
                    row.setOnClickListener {
                        Weather.setCity(p)
                        dlg?.dismiss()
                        fillWeather(card, null)
                        Net.bg({ Weather.fetch() }) { o -> if (card.isAttachedToWindow) fillWeather(card, o, o == null) }
                    }
                    results.addView(row)
                }
            }
        }
        body.addView(go.lin(t = a.dp(4)))
        body.addView(results)
        dlg = a.sheet("Weather city", body)
    }

    // ================= exchange rates

    private fun fillFx(card: LinearLayout, p: Pair<String, List<Forex.Rate>>?, failed: Boolean = false) {
        card.removeAllViews()
        if (p == null) {
            card.addView(a.label(
                if (failed) "📡 Couldn't reach Nepal Rastra Bank. Open this tab again later."
                else "📡 Loading today's rates…", 14f, C.SUB))
            return
        }
        val (date, rates) = p
        val head = a.hbox()
        head.addView(a.label("🏦 Nepal Rastra Bank · $date", 12f, C.SUB, true).lin(0, WRAP, 1f))
        head.addView(a.label("BUY", 11f, C.SUB, true).apply { gravity = Gravity.END }.lin(a.dp(72), WRAP))
        head.addView(a.label("SELL", 11f, C.SUB, true).apply { gravity = Gravity.END }.lin(a.dp(72), WRAP))
        card.addView(head)
        val byIso = rates.associateBy { it.iso }
        for (iso in Forex.MAIN) {
            val r = byIso[iso] ?: continue
            val row = a.hbox()
            row.setPadding(0, a.dp(7), 0, a.dp(7))
            val unit = if (r.unit > 1) "  ×${r.unit}" else ""
            row.addView(a.label("${Forex.flag(iso)}  $iso$unit", 15f, C.TEXT, true).lin(0, WRAP, 1f))
            row.addView(a.label(num(r.buy), 15f, C.GREEN).apply { gravity = Gravity.END }.lin(a.dp(72), WRAP))
            row.addView(a.label(num(r.sell), 15f, C.RED).apply { gravity = Gravity.END }.lin(a.dp(72), WRAP))
            card.addView(row)
        }
        card.addView(a.pill("💱  Currency converter", C.CARD2) { converter(rates) }.lin(t = a.dp(10)))
    }

    private fun converter(rates: List<Forex.Rate>) {
        val list = rates.filter { it.buy > 0 && it.sell > 0 }
        if (list.isEmpty()) return
        val body = a.vbox()
        var idx = list.indexOfFirst { it.iso == "USD" }.coerceAtLeast(0)
        var toNpr = true
        val amt = a.input("Amount", number = true, value = "1")
        val dirBtn = a.chip("", true) {}
        val out = a.label("", 26f, C.GOLD, true).lin(t = a.dp(10))
        val note = a.label("", 12f, C.SUB)
        fun calc() {
            val r = list[idx]
            val v = amt.text.toString().toDoubleOrNull() ?: 0.0
            val per = r.unit.toDouble()
            if (toNpr) {
                out.text = "= Rs " + num(v * r.buy / per)
                note.text = "${r.iso} → NPR at the buying rate (${num(r.buy)} per ${r.unit} ${r.iso})"
            } else {
                out.text = "= " + num(v * per / r.sell) + " " + r.iso
                note.text = "NPR → ${r.iso} at the selling rate (${num(r.sell)} per ${r.unit} ${r.iso})"
            }
            dirBtn.text = if (toNpr) "⇄  ${r.iso} → NPR" else "⇄  NPR → ${r.iso}"
        }
        body.addView(a.spinner(list.map { "${Forex.flag(it.iso)}  ${it.iso} · ${it.name}" }, idx) { idx = it; calc() })
        body.addView(amt)
        dirBtn.setOnClickListener { toNpr = !toNpr; calc() }
        body.addView(dirBtn)
        body.addView(out)
        body.addView(note)
        amt.doAfterTextChanged { calc() }
        calc()
        a.sheet("Currency converter", body)
    }

    // ================= to-do

    private fun fillTodo(card: LinearLayout, focus: Boolean) {
        card.removeAllViews()
        val items = Store.arr("todos")
        val row = a.hbox()
        val inp = a.input("Add a task…").lin(0, WRAP, 1f)
        row.addView(inp)
        val add: () -> Unit = {
            val t = inp.text.toString().trim()
            if (t.isNotEmpty()) {
                items.put(JSONObject().put("t", t).put("d", false))
                Store.setArr("todos", items)
                fillTodo(card, true)
            }
        }
        row.addView(a.pill("＋") { add() }.lin(WRAP, WRAP, l = a.dp(8)))
        inp.setOnEditorActionListener { _, _, _ -> add(); true }
        card.addView(row)
        if (focus) inp.requestFocus()
        if (items.length() == 0) {
            card.addView(a.label("Nothing to do. Enjoy the day ☕", 14f, C.SUB).lin(t = a.dp(8)))
            return
        }
        var done = 0
        for (i in 0 until items.length()) {
            val o = items.getJSONObject(i)
            val d = o.optBoolean("d")
            if (d) done++
            val line = a.hbox()
            line.setPadding(0, a.dp(9), 0, a.dp(9))
            line.addView(a.label(if (d) "✅" else "⬜", 18f).lin(WRAP, WRAP, r = a.dp(12)))
            val tv = a.label(o.optString("t"), 16f, if (d) C.SUB else C.TEXT)
            if (d) tv.paintFlags = tv.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            line.addView(tv.lin(0, WRAP, 1f))
            line.setOnClickListener {
                o.put("d", !d)
                Store.setArr("todos", items)
                fillTodo(card, false)
            }
            line.setOnLongClickListener {
                Store.setArr("todos", without(items, i))
                fillTodo(card, false)
                a.toast("Deleted")
                true
            }
            card.addView(line)
        }
        if (done > 0) {
            card.addView(a.pill("🧹  Clear $done done", C.CARD2) {
                val keep = JSONArray()
                for (i in 0 until items.length()) {
                    val o = items.getJSONObject(i)
                    if (!o.optBoolean("d")) keep.put(o)
                }
                Store.setArr("todos", keep)
                fillTodo(card, false)
            }.lin(t = a.dp(8)))
        }
        card.addView(a.label("Tap = done · long-press = delete", 11f, C.SUB).lin(t = a.dp(8)))
    }
}
