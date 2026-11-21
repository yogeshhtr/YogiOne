package com.yogi.one

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.core.widget.doAfterTextChanged
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.roundToInt
import kotlin.random.Random

/** Phone flashlight (Android 6+). No camera permission needed. */
object Torch {
    var on = false
        private set

    fun toggle(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 23) return false
        return try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (id == null) {
                false
            } else {
                cm.setTorchMode(id, !on)
                on = !on
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    fun off(ctx: Context) {
        if (on) toggle(ctx)
    }
}

class ToolsTab(private val a: MainActivity) {
    private class Tool(val icon: String, val name: String, val desc: String, val color: Int, val open: () -> Unit)

    fun build(): View {
        val (sv, col) = a.page()
        col.addView(a.header("Tools", "Everything handy, in one place"))
        val more = MoreTools(a)
        val tools = listOf(
            Tool("📷", "QR codes", "Scan or make one", C.ACCENT) { more.qr() },
            Tool("📝", "Notes", "${Store.arr("notes").length()} saved", C.GOLD) { more.notes() },
            Tool("🔦", "Torch", if (Torch.on) "ON — tap to turn off" else "Phone flashlight", C.GOLD) {
                if (!Torch.toggle(a)) a.toast("No flashlight on this phone") else a.window.decorView.tick()
                a.refresh()
            },
            Tool("🧭", "Compass", "Find your direction", C.BLUE) { compass() },
            Tool("📐", "Level", "Is it straight?", C.GREEN) { level() },
            Tool("📏", "Units & land", "Ropani, aana, bigha…", C.ORANGE) { units() },
            Tool("🏦", "Loan EMI", "Monthly instalment", C.ACCENT) { emi() },
            Tool("🧾", "Split bill", "Share with friends", C.ACCENT2) { split() },
            Tool("⏱", "Stopwatch", "With laps", C.RED) { stopwatch() },
            Tool("🎂", "Age", "Exact age & birthday", C.PINK) { age() },
            Tool("⚖️", "BMI", "Body mass index", C.GREEN) { bmi() },
            Tool("🔐", "Password", "Strong & random", C.BLUE) { more.password() },
            Tool("🎲", "Dice & coin", "Let luck decide", C.GOLD) { dice() }
        )
        for (pair in tools.chunked(2)) {
            val r = a.hbox()
            r.setPadding(a.dp(10), 0, a.dp(10), 0)
            for (t in pair) r.addView(tile(t))
            if (pair.size == 1) r.addView(View(a), LinearLayout.LayoutParams(0, 1, 1f))
            col.addView(r)
        }
        return sv
    }

    private fun tile(tool: Tool): View {
        val v = a.vbox()
        val lit = tool.name == "Torch" && Torch.on
        v.background = a.ripple(if (lit) 0xFF3A3215.toInt() else C.CARD, 22f)
        v.setPadding(a.dp(16), a.dp(16), a.dp(16), a.dp(16))
        val ic = a.label(tool.icon, 26f)
        ic.gravity = Gravity.CENTER
        ic.background = rounded((tool.color and 0x00FFFFFF) or 0x33000000, a.dpf(18f))
        v.addView(ic, LinearLayout.LayoutParams(a.dp(56), a.dp(56)))
        v.addView(a.label(tool.name, 16f, C.TEXT, true).lin(t = a.dp(12)))
        v.addView(a.label(tool.desc, 12f, if (lit) C.GOLD else C.SUB))
        v.setOnClickListener { tool.open() }
        return v.lin(0, WRAP, 1f, a.dp(4), a.dp(4), a.dp(4), a.dp(4))
    }

    // ================= sensors

    private fun compass() {
        val sm = a.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val mag = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        if (acc == null || mag == null) {
            a.toast("This phone has no compass sensor")
            return
        }
        val view = CompassView(a)
        val body = a.vbox()
        body.gravity = Gravity.CENTER_HORIZONTAL
        body.addView(view, LinearLayout.LayoutParams(a.dp(280), a.dp(280)))
        body.addView(a.label("Hold the phone flat. If it looks wrong, wave it in a figure-8.", 12f, C.SUB)
            .apply { gravity = Gravity.CENTER }.lin(t = a.dp(8)))
        val g = FloatArray(3)
        val m = FloatArray(3)
        val rot = FloatArray(9)
        val ori = FloatArray(3)
        var hasG = false
        var hasM = false
        var sx = 1f
        var sy = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val target = if (e.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    hasG = true
                    g
                } else {
                    hasM = true
                    m
                }
                for (i in 0..2) target[i] = target[i] + 0.15f * (e.values[i] - target[i])
                if (hasG && hasM && SensorManager.getRotationMatrix(rot, null, g, m)) {
                    SensorManager.getOrientation(rot, ori)
                    // smooth the angle as a direction (no jumping between 359° and 0°)
                    sx = sx * 0.8f + cos(ori[0]) * 0.2f
                    sy = sy * 0.8f + sin(ori[0]) * 0.2f
                    view.heading = (Math.toDegrees(atan2(sy, sx).toDouble()).toFloat() + 360f) % 360f
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, acc, SensorManager.SENSOR_DELAY_UI)
        sm.registerListener(listener, mag, SensorManager.SENSOR_DELAY_UI)
        a.sheet("🧭 Compass", body) { sm.unregisterListener(listener) }
    }

    private fun level() {
        val sm = a.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (acc == null) {
            a.toast("This phone has no motion sensor")
            return
        }
        val view = LevelView(a)
        val body = a.vbox()
        body.gravity = Gravity.CENTER_HORIZONTAL
        body.addView(view, LinearLayout.LayoutParams(a.dp(280), a.dp(280)))
        body.addView(a.label("Lay the phone on the surface. Green = perfectly level.", 12f, C.SUB)
            .apply { gravity = Gravity.CENTER }.lin(t = a.dp(8)))
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { view.set(e.values[0], e.values[1], e.values[2]) }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, acc, SensorManager.SENSOR_DELAY_UI)
        a.sheet("📐 Level", body) { sm.unregisterListener(listener) }
    }

    // ================= calculators

    private val unitTable: Map<String, List<Pair<String, Double>>> = linkedMapOf(
        "🏞 Land (Nepal)" to listOf(
            "Ropani" to 508.737, "Aana" to 31.7961, "Paisa" to 7.94901, "Daam" to 1.98725,
            "Bigha" to 6772.63, "Kattha" to 338.632, "Dhur" to 16.9316,
            "Sq. feet" to 0.092903, "Sq. metre" to 1.0, "Acre" to 4046.86, "Hectare" to 10000.0
        ),
        "📏 Length" to listOf(
            "Millimetre" to 0.001, "Centimetre" to 0.01, "Metre" to 1.0, "Kilometre" to 1000.0,
            "Inch" to 0.0254, "Foot" to 0.3048, "Yard" to 0.9144, "Mile" to 1609.344, "Haat" to 0.4572
        ),
        "⚖️ Weight" to listOf(
            "Gram" to 0.001, "Kilogram" to 1.0, "Tola" to 0.0116638, "Pau" to 0.199526,
            "Dharni" to 2.39431, "Pound" to 0.453592, "Ounce" to 0.0283495, "Quintal" to 100.0, "Tonne" to 1000.0
        ),
        "🌡 Temperature" to listOf("Celsius" to 0.0, "Fahrenheit" to 0.0, "Kelvin" to 0.0)
    )

    private fun temp(v: Double, from: String, to: String): Double {
        val c = when (from) {
            "Fahrenheit" -> (v - 32) * 5 / 9
            "Kelvin" -> v - 273.15
            else -> v
        }
        return when (to) {
            "Fahrenheit" -> c * 9 / 5 + 32
            "Kelvin" -> c + 273.15
            else -> c
        }
    }

    private fun units() {
        val body = a.vbox()
        val cats = unitTable.keys.toList()
        var cat = 0
        var from = 0
        val amt = a.input("Value", number = true, value = "1")
        val out = a.vbox()
        val fromHolder = a.vbox()
        val catRow = a.hbox()
        fun calc() {
            out.removeAllViews()
            val list = unitTable.getValue(cats[cat])
            val v = amt.text.toString().toDoubleOrNull() ?: 0.0
            for ((i, u) in list.withIndex()) {
                if (i == from) continue
                val res = if (cats[cat].contains("Temperature")) temp(v, list[from].first, u.first)
                else v * list[from].second / u.second
                out.addView(a.resultRow(u.first, fmtNum(res)))
            }
        }
        fun fromSpinner() {
            fromHolder.removeAllViews()
            from = 0
            fromHolder.addView(a.picker(unitTable.getValue(cats[cat]).map { it.first }, 0) { from = it; calc() })
        }
        fun drawCats() {
            catRow.removeAllViews()
            cats.forEachIndexed { i, c ->
                catRow.addView(a.chip(c, i == cat) {
                    cat = i
                    drawCats()
                    fromSpinner()
                    calc()
                })
            }
        }
        drawCats()
        fromSpinner()
        body.addView(a.hscroll(catRow))
        body.addView(amt)
        body.addView(a.label("FROM", 11f, C.SUB, true).lin(t = a.dp(4)))
        body.addView(fromHolder)
        body.addView(a.label("EQUALS", 11f, C.SUB, true).lin(t = a.dp(8)))
        body.addView(out)
        amt.doAfterTextChanged { calc() }
        calc()
        a.sheet("📏 Units & land", body)
    }

    private fun emi() {
        val body = a.vbox()
        val p = a.input("Loan amount (Rs)", true, "1000000")
        val r = a.input("Interest rate (% per year)", true, "11")
        val y = a.input("Years", true, "5")
        val out = a.vbox()
        fun calc() {
            out.removeAllViews()
            val principal = p.text.toString().toDoubleOrNull() ?: 0.0
            val rate = (r.text.toString().toDoubleOrNull() ?: 0.0) / 12 / 100
            val n = ((y.text.toString().toDoubleOrNull() ?: 0.0) * 12).roundToInt()
            if (principal <= 0 || n <= 0) return
            val emi = if (rate == 0.0) principal / n
            else principal * rate * (1 + rate).pow(n) / ((1 + rate).pow(n) - 1)
            val total = emi * n
            out.addView(a.resultRow("Monthly EMI", rs(emi), C.GOLD, true))
            out.addView(a.resultRow("Total interest", rs(total - principal), C.RED))
            out.addView(a.resultRow("Total to pay", rs(total)))
            out.addView(a.resultRow("Number of months", "$n"))
            out.addView(a.bar((principal / total).toFloat(), C.ACCENT, 10))
            out.addView(a.label("Purple = loan amount · grey = interest", 11f, C.SUB).lin(t = a.dp(4)))
        }
        body.addView(a.label("LOAN AMOUNT", 11f, C.SUB, true)); body.addView(p)
        body.addView(a.label("INTEREST % PER YEAR", 11f, C.SUB, true)); body.addView(r)
        body.addView(a.label("YEARS", 11f, C.SUB, true)); body.addView(y)
        body.addView(out.lin(t = a.dp(8)))
        for (e in listOf(p, r, y)) e.doAfterTextChanged { calc() }
        calc()
        a.sheet("🏦 Loan EMI", body)
    }

    private fun split() {
        val body = a.vbox()
        val bill = a.input("Bill amount (Rs)", true)
        val people = a.input("People", true, "2")
        val tip = a.input("Tip / service %", true, "0")
        val out = a.vbox()
        fun calc() {
            out.removeAllViews()
            val b = bill.text.toString().toDoubleOrNull() ?: return
            val n = (people.text.toString().toDoubleOrNull() ?: 1.0).roundToInt().coerceAtLeast(1)
            val t = b * (tip.text.toString().toDoubleOrNull() ?: 0.0) / 100
            out.addView(a.resultRow("Each person pays", rs((b + t) / n), C.GOLD, true))
            out.addView(a.resultRow("Tip", rs(t)))
            out.addView(a.resultRow("Total", rs(b + t)))
        }
        body.addView(a.label("BILL", 11f, C.SUB, true)); body.addView(bill)
        body.addView(a.label("PEOPLE", 11f, C.SUB, true)); body.addView(people)
        body.addView(a.label("TIP %", 11f, C.SUB, true)); body.addView(tip)
        body.addView(out.lin(t = a.dp(8)))
        for (e in listOf(bill, people, tip)) e.doAfterTextChanged { calc() }
        a.sheet("🧾 Split bill", body)
        bill.requestFocus()
    }

    private fun bmi() {
        val body = a.vbox()
        val w = a.input("Weight (kg)", true)
        val h = a.input("Height (cm)", true)
        val out = a.vbox()
        fun calc() {
            out.removeAllViews()
            val kg = w.text.toString().toDoubleOrNull() ?: return
            val cm = h.text.toString().toDoubleOrNull() ?: return
            if (kg <= 0 || cm <= 0) return
            val m = cm / 100
            val v = kg / (m * m)
            val (word, color) = when {
                v < 18.5 -> "Underweight" to C.BLUE
                v < 25 -> "Healthy 👍" to C.GREEN
                v < 30 -> "Overweight" to C.GOLD
                else -> "Obese" to C.RED
            }
            out.addView(a.resultRow("BMI", String.format(Locale.US, "%.1f", v), color, true))
            out.addView(a.resultRow("Result", word, color))
            out.addView(a.resultRow("Healthy weight for you",
                "${(18.5 * m * m).roundToInt()}–${(24.9 * m * m).roundToInt()} kg"))
        }
        body.addView(a.label("WEIGHT (KG)", 11f, C.SUB, true)); body.addView(w)
        body.addView(a.label("HEIGHT (CM)", 11f, C.SUB, true)); body.addView(h)
        body.addView(out.lin(t = a.dp(8)))
        for (e in listOf(w, h)) e.doAfterTextChanged { calc() }
        a.sheet("⚖️ BMI", body)
    }

    // ================= fun & time

    private fun stopwatch() {
        val h = Handler(Looper.getMainLooper())
        var startAt = 0L
        var acc = 0L
        var running = false
        val body = a.vbox()
        val time = a.label("00:00.00", 52f, C.TEXT, true)
        time.gravity = Gravity.CENTER
        time.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        val laps = a.vbox()
        fun elapsed(): Long = acc + (if (running) System.currentTimeMillis() - startAt else 0L)
        fun fmt(ms: Long): String =
            String.format(Locale.US, "%02d:%02d.%02d", ms / 60_000, (ms / 1000) % 60, (ms / 10) % 100)
        val loop = object : Runnable {
            override fun run() {
                time.text = fmt(elapsed())
                if (running) h.postDelayed(this, 33)
            }
        }
        val startBtn = a.pill("▶  Start", C.GREEN) {}
        val lapBtn = a.pill("↺  Reset", C.CARD2) {}
        startBtn.setOnClickListener {
            if (running) {
                acc += System.currentTimeMillis() - startAt
                running = false
                startBtn.text = "▶  Start"
                lapBtn.text = "↺  Reset"
                time.text = fmt(elapsed())
            } else {
                startAt = System.currentTimeMillis()
                running = true
                startBtn.text = "⏸  Stop"
                lapBtn.text = "🏁  Lap"
                h.post(loop)
            }
        }
        lapBtn.setOnClickListener {
            if (running) {
                laps.addView(a.resultRow("Lap ${laps.childCount + 1}", fmt(elapsed())), 0)
            } else {
                acc = 0
                laps.removeAllViews()
                time.text = fmt(0)
            }
        }
        body.addView(time.lin(t = a.dp(10), b = a.dp(14)))
        val row = a.hbox()
        row.addView(startBtn.lin(0, WRAP, 1f, r = a.dp(6)))
        row.addView(lapBtn.lin(0, WRAP, 1f, l = a.dp(6)))
        body.addView(row)
        body.addView(laps.lin(t = a.dp(10)))
        a.sheet("⏱ Stopwatch", body) {
            running = false
            h.removeCallbacksAndMessages(null)
        }
    }

    private fun age() {
        val now = Calendar.getInstance()
        val saved = Store.str("birth")
        val parts = saved.split("-").mapNotNull { it.toIntOrNull() }
        val (y0, m0, d0) = if (parts.size == 3) Triple(parts[0], parts[1], parts[2])
        else Triple(now.get(Calendar.YEAR) - 25, 0, 1)
        val dp = DatePickerDialog(a, { _, y, m, d ->
            Store.setStr("birth", "$y-$m-$d")
            showAge(y, m, d)
        }, y0, m0, d0)
        dp.datePicker.maxDate = System.currentTimeMillis()
        dp.setTitle("Choose the birthday")
        dp.show()
    }

    private fun showAge(y: Int, m: Int, d: Int) {
        val now = Calendar.getInstance()
        val birth = Calendar.getInstance()
        birth.set(y, m, d, 0, 0, 0)
        var years = now.get(Calendar.YEAR) - y
        var months = now.get(Calendar.MONTH) - m
        var days = now.get(Calendar.DAY_OF_MONTH) - d
        if (days < 0) {
            months--
            val prev = now.clone() as Calendar
            prev.add(Calendar.MONTH, -1)
            days += prev.getActualMaximum(Calendar.DAY_OF_MONTH)
        }
        if (months < 0) {
            years--
            months += 12
        }
        val lived = ((now.timeInMillis - birth.timeInMillis) / 86_400_000L)
        val next = Calendar.getInstance()
        next.set(now.get(Calendar.YEAR), m, d, 0, 0, 0)
        val todayStart = Calendar.getInstance()
        todayStart.set(Calendar.HOUR_OF_DAY, 0); todayStart.set(Calendar.MINUTE, 0); todayStart.set(Calendar.SECOND, 0)
        if (next.before(todayStart)) next.add(Calendar.YEAR, 1)
        val toNext = ((next.timeInMillis - todayStart.timeInMillis + 43_200_000L) / 86_400_000L)

        val body = a.vbox()
        body.addView(a.label("$years", 60f, C.GOLD, true).apply { gravity = Gravity.CENTER })
        body.addView(a.label("years old", 14f, C.SUB).apply { gravity = Gravity.CENTER })
        body.addView(a.resultRow("Exact age", "$years y  $months m  $days d").lin(t = a.dp(14)))
        body.addView(a.resultRow("Days lived", String.format(Locale.US, "%,d", lived)))
        body.addView(a.resultRow("Born on a", SimpleDateFormat("EEEE", Locale.US).format(birth.time)))
        body.addView(a.resultRow("Next birthday",
            if (toNext == 0L) "TODAY! 🎉" else "in $toNext days", if (toNext == 0L) C.GOLD else C.TEXT))
        a.sheet("🎂 Age", body)
    }

    private fun dice() {
        val faces = listOf("⚀", "⚁", "⚂", "⚃", "⚄", "⚅")
        val h = Handler(Looper.getMainLooper())
        var count = 2
        val body = a.vbox()
        val show = a.label("🎲", 80f)
        show.gravity = Gravity.CENTER
        val result = a.label("", 18f, C.GOLD, true)
        result.gravity = Gravity.CENTER
        fun roll(times: Int) {
            val vals = List(count) { Random.nextInt(6) }
            show.text = vals.joinToString(" ") { faces[it] }
            result.text = if (times == 0) "Total: ${vals.sum() + count}" else ""
            if (times > 0) h.postDelayed({ roll(times - 1) }, 70)
        }
        fun flip(times: Int) {
            val heads = Random.nextBoolean()
            show.text = if (heads) "🌕" else "🌑"
            result.text = if (times == 0) (if (heads) "HEADS" else "TAILS") else ""
            if (times > 0) h.postDelayed({ flip(times - 1) }, 90)
        }
        val countRow = a.hbox()
        countRow.gravity = Gravity.CENTER
        fun drawCount() {
            countRow.removeAllViews()
            for (n in 1..3) countRow.addView(a.chip("$n ${if (n == 1) "die" else "dice"}", n == count) {
                count = n
                drawCount()
            })
        }
        drawCount()
        body.addView(show.lin(t = a.dp(6)))
        body.addView(result.lin(b = a.dp(12)))
        body.addView(countRow)
        val btns = a.hbox()
        btns.addView(a.pill("🎲  Roll") { roll(8) }.lin(0, WRAP, 1f, r = a.dp(6)))
        btns.addView(a.pill("🪙  Flip coin", C.CARD2) { flip(6) }.lin(0, WRAP, 1f, l = a.dp(6)))
        body.addView(btns)
        a.sheet("🎲 Dice & coin", body) { h.removeCallbacksAndMessages(null) }
    }
}
