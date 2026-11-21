package com.yogi.one

import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import java.util.Locale

/** Focus timer (pomodoro). The end time is saved, so it keeps counting when the app is closed. */
object Focus {
    private fun now() = System.currentTimeMillis()

    fun mode(): String = Store.str("f_mode", "work")
    fun work(): Int = Store.int("f_len", 25)
    fun rest(): Int = Store.int("f_brk", 5)
    fun totalMs(): Long = (if (mode() == "work") work() else rest()) * 60_000L
    fun running(): Boolean = Store.long("f_end") > 0

    fun leftMs(): Long =
        if (running()) (Store.long("f_end") - now()).coerceAtLeast(0L)
        else Store.long("f_left").takeIf { it > 0 } ?: totalMs()

    /** Starts (or continues) and sets an alarm, so a notification comes even when the app is closed. */
    fun start(ctx: Context) {
        val end = now() + leftMs()
        Store.setLong("f_end", end)
        Reminders.scheduleFocus(ctx, end)
    }

    fun pause(ctx: Context) {
        val l = leftMs()
        Store.setLong("f_end", 0)
        Store.setLong("f_left", l)
        Reminders.cancelFocus(ctx)
    }

    fun reset(ctx: Context) {
        Store.setLong("f_end", 0)
        Store.setLong("f_left", 0)
        Reminders.cancelFocus(ctx)
    }

    /** True when the timer just ended (and was saved). */
    fun checkDone(ctx: Context): Boolean {
        val end = Store.long("f_end")
        if (end <= 0 || now() < end) return false
        finish(ctx, alert = now() - end < 60_000, counted = true)
        return true
    }

    fun finish(ctx: Context, alert: Boolean, counted: Boolean) {
        if (mode() == "work" && counted) {
            add("f_log", work())
            add("f_ses", 1)
        }
        Store.setStr("f_mode", if (mode() == "work") "break" else "work")
        reset(ctx)
        if (alert) ring(ctx)
    }

    private fun add(k: String, v: Int) {
        val o = Store.obj(k)
        val d = Day.key()
        o.put(d, o.optInt(d) + v)
        Store.setObj(k, o)
    }

    fun minutes(day: String): Int = Store.obj("f_log").optInt(day)
    fun sessions(day: String): Int = Store.obj("f_ses").optInt(day)

    fun totalMinutes(): Int {
        val o = Store.obj("f_log")
        var s = 0
        o.keys().forEach { s += o.optInt(it) }
        return s
    }

    fun streak(): Int {
        var i = if (minutes(Day.key()) > 0) 0 else 1
        var n = 0
        while (minutes(Day.ago(i)) > 0) { n++; i++ }
        return n
    }

    @Suppress("DEPRECATION")
    private fun ring(ctx: Context) {
        try {
            RingtoneManager.getRingtone(ctx, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.play()
        } catch (_: Exception) {
        }
        try {
            val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            val pattern = longArrayOf(0, 400, 200, 400, 200, 700)
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(pattern, -1))
            else v.vibrate(pattern, -1)
        } catch (_: Exception) {
        }
    }
}

class FocusTab(private val a: MainActivity) {

    fun build(): View {
        val (sv, col) = a.page()
        val work = Focus.mode() == "work"
        val tint = if (work) C.ACCENT else C.ACCENT2
        col.addView(a.header("Focus",
            if (work) "🧠 Deep work — one thing at a time" else "☕ Break — breathe, stretch, drink water"))

        val ring = RingView(a)
        ring.ringColor = tint
        ring.small = if (work) "FOCUS" else "BREAK"
        val size = a.dp(250)
        col.addView(ring, LinearLayout.LayoutParams(size, size).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = a.dp(8)
        })

        val btns = a.hbox()
        btns.gravity = Gravity.CENTER
        btns.setPadding(a.dp(16), a.dp(18), a.dp(16), 0)
        val main = a.pill("▶  Start", tint) {}
        fun update() {
            if (Focus.checkDone(a)) {
                a.refresh()
                return
            }
            val l = Focus.leftMs()
            ring.progress = l.toFloat() / Focus.totalMs()
            ring.big = String.format(Locale.US, "%02d:%02d", l / 60_000, (l / 1000) % 60)
            ring.invalidate()
            val run = Focus.running()
            main.text = if (run) "⏸  Pause" else "▶  Start"
            if (run) a.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else a.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        main.setOnClickListener {
            if (Focus.running()) Focus.pause(a) else { Focus.start(a); a.askNotify() }
            main.tick()
            update()
        }
        btns.addView(main.lin(0, WRAP, 1.4f, r = a.dp(8)))
        btns.addView(a.pill("↺", C.CARD2) { Focus.reset(a); update() }.lin(0, WRAP, 0.6f, r = a.dp(8)))
        btns.addView(a.pill("⏭", C.CARD2) {
            Focus.finish(a, alert = false, counted = false)
            a.refresh()
        }.lin(0, WRAP, 0.6f))
        col.addView(btns)
        update()
        a.ticker = { update() }

        // ---------- lengths
        col.addView(a.section("FOCUS LENGTH"))
        val wr = a.hbox()
        wr.setPadding(a.dp(20), 0, a.dp(20), 0)
        for (m in listOf(15, 25, 45, 60, 90)) {
            wr.addView(a.chip("$m min", Focus.work() == m) {
                Store.setInt("f_len", m)
                if (!Focus.running()) Focus.reset(a)
                a.refresh()
            })
        }
        col.addView(a.hscroll(wr))
        col.addView(a.section("BREAK LENGTH"))
        val br = a.hbox()
        br.setPadding(a.dp(20), 0, a.dp(20), 0)
        for (m in listOf(5, 10, 15, 20)) {
            br.addView(a.chip("$m min", Focus.rest() == m) {
                Store.setInt("f_brk", m)
                if (!Focus.running()) Focus.reset(a)
                a.refresh()
            })
        }
        col.addView(a.hscroll(br))

        // ---------- progress
        col.addView(a.section("YOUR PROGRESS"))
        val st = a.card()
        val r1 = a.hbox()
        r1.addView(a.statBox("⏱", "${Focus.minutes(Day.key())} min", "today"))
        r1.addView(a.statBox("🍅", "${Focus.sessions(Day.key())}", "sessions"))
        r1.addView(a.statBox("🔥", "${Focus.streak()}", "day streak", C.GOLD))
        r1.addView(a.statBox("🏆", num(Focus.totalMinutes() / 60.0, 1) + " h", "all time"))
        st.addView(r1)
        val chart = BarChart(a)
        chart.values = (6 downTo 0).map { Focus.minutes(Day.ago(it)).toFloat() }
        chart.labels = (6 downTo 0).map { if (it == 0) "Today" else Day.weekday(it) }
        chart.barColor = C.ACCENT
        st.addView(chart, LinearLayout.LayoutParams(MATCH, a.dp(150)).apply { topMargin = a.dp(16) })
        st.addView(a.label("Minutes of focus, last 7 days", 11f, C.SUB).apply { gravity = Gravity.CENTER }.lin(t = a.dp(4)))
        col.addView(st)

        val tip = a.card(0xFF1E1A3A.toInt())
        tip.addView(a.label("💡 How it works", 15f, C.TEXT, true))
        tip.addView(a.label(
            "Work on one thing until the ring runs out, then take the break. After 4 rounds, take a longer break. " +
                "The timer keeps going even if you close the app; finished rounds count toward your streak.",
            13f, C.SUB).lin(t = a.dp(6)))
        col.addView(tip)
        return sv
    }
}
