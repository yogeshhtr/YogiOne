package com.yogi.one

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Everything is saved on the phone (no account, no cloud).
 * Lists / objects are parsed once and kept in memory (faster tabs); writes update both.
 */
object Store {
    private lateinit var sp: SharedPreferences
    private val cache = HashMap<String, Any>()

    fun init(ctx: Context) {
        if (!::sp.isInitialized) sp = ctx.applicationContext.getSharedPreferences("yogione", Context.MODE_PRIVATE)
    }

    fun has(k: String): Boolean = sp.contains(k)
    fun str(k: String, d: String = ""): String = sp.getString(k, d) ?: d
    fun setStr(k: String, v: String) {
        synchronized(cache) { cache.remove(k) }
        sp.edit().putString(k, v).apply()
    }
    fun int(k: String, d: Int = 0): Int = sp.getInt(k, d)
    fun setInt(k: String, v: Int) { sp.edit().putInt(k, v).apply() }
    fun long(k: String, d: Long = 0L): Long = sp.getLong(k, d)
    fun setLong(k: String, v: Long) { sp.edit().putLong(k, v).apply() }

    fun arr(k: String): JSONArray = synchronized(cache) {
        (cache[k] as? JSONArray) ?: (try { JSONArray(str(k, "[]")) } catch (_: Exception) { JSONArray() }).also { cache[k] = it }
    }
    fun setArr(k: String, a: JSONArray) {
        setStr(k, a.toString())
        synchronized(cache) { cache[k] = a }
    }
    fun obj(k: String): JSONObject = synchronized(cache) {
        (cache[k] as? JSONObject) ?: (try { JSONObject(str(k, "{}")) } catch (_: Exception) { JSONObject() }).also { cache[k] = it }
    }
    fun setObj(k: String, o: JSONObject) {
        setStr(k, o.toString())
        synchronized(cache) { cache[k] = o }
    }

    /** All data as one JSON text (backup file). Downloaded weather/rates are left out. */
    fun export(): String {
        val data = JSONObject()
        for ((k, v) in sp.all) {
            if (k == "wx" || k == "fx" || k == "wx_t" || k == "fx_t") continue
            val o = JSONObject()
            when (v) {
                is String -> o.put("t", "s").put("v", v)
                is Int -> o.put("t", "i").put("v", v)
                is Long -> o.put("t", "l").put("v", v)
                is Boolean -> o.put("t", "b").put("v", v)
                is Float -> o.put("t", "f").put("v", v.toDouble())
                else -> continue
            }
            data.put(k, o)
        }
        return JSONObject().put("app", "YogiOne").put("version", 1)
            .put("saved", Day.key()).put("data", data).toString(1)
    }

    /** Replaces everything with a backup. False = not a Yogi One backup. */
    fun restore(json: String): Boolean {
        try {
            val root = JSONObject(json)
            if (root.optString("app") != "YogiOne") return false
            val data = root.getJSONObject("data")
            val e = sp.edit().clear()
            for (k in data.keys()) {
                val o = data.getJSONObject(k)
                when (o.optString("t")) {
                    "s" -> e.putString(k, o.optString("v"))
                    "i" -> e.putInt(k, o.optInt("v"))
                    "l" -> e.putLong(k, o.optLong("v"))
                    "b" -> e.putBoolean(k, o.optBoolean("v"))
                    "f" -> e.putFloat(k, o.optDouble("v", 0.0).toFloat())
                }
            }
            e.commit()
            synchronized(cache) { cache.clear() }
            return true
        } catch (_: Exception) {
            return false
        }
    }
}

fun without(a: JSONArray, index: Int): JSONArray {
    val out = JSONArray()
    for (i in 0 until a.length()) if (i != index) out.put(a.get(i))
    return out
}

object Day {
    private val keyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val dayFmt = SimpleDateFormat("EEE", Locale.US)
    fun key(t: Long = System.currentTimeMillis()): String = synchronized(keyFmt) { keyFmt.format(Date(t)) }
    fun cal(daysAgo: Int): Calendar {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, -daysAgo)
        return c
    }
    fun ago(n: Int): String = key(cal(n).timeInMillis)
    fun weekday(n: Int): String = synchronized(dayFmt) { dayFmt.format(cal(n).time) }
}

object Net {
    private val main = Handler(Looper.getMainLooper())

    fun get(url: String): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "YogiOne/1.0 (Android)")
        try {
            if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            c.disconnect()
        }
    } catch (_: Exception) {
        null
    }

    /** Runs [work] in the background, then [done] on the main thread (null = failed). */
    fun <T> bg(work: () -> T?, done: (T?) -> Unit) {
        Thread {
            val r = try { work() } catch (_: Exception) { null }
            main.post { done(r) }
        }.start()
    }
}

/** Weather from Open-Meteo (free, no key). */
object Weather {
    class Place(val name: String, val lat: Double, val lon: Double, val where: String)

    fun city(): String = Store.str("city", "Kathmandu")

    fun setCity(p: Place) {
        Store.setStr("city", p.name)
        Store.setStr("lat", p.lat.toString())
        Store.setStr("lon", p.lon.toString())
        Store.setLong("wx_t", 0)
        Store.setStr("wx", "")
    }

    fun cached(): JSONObject? {
        val s = Store.str("wx")
        if (s.isEmpty()) return null
        return try { JSONObject(s) } catch (_: Exception) { null }
    }

    fun stale(): Boolean = System.currentTimeMillis() - Store.long("wx_t") > 30 * 60_000L

    fun fetch(): JSONObject? {
        val lat = Store.str("lat", "27.7172")
        val lon = Store.str("lon", "85.3240")
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_probability_max" +
            "&timezone=auto&forecast_days=6"
        val s = Net.get(url) ?: return null
        val o = JSONObject(s)
        if (!o.has("current")) return null
        Store.setStr("wx", s)
        Store.setLong("wx_t", System.currentTimeMillis())
        return o
    }

    fun search(q: String): List<Place> {
        val s = Net.get("https://geocoding-api.open-meteo.com/v1/search?count=8&language=en&name=" +
            URLEncoder.encode(q, "UTF-8")) ?: return emptyList()
        val r = JSONObject(s).optJSONArray("results") ?: return emptyList()
        return (0 until r.length()).map { i ->
            val o = r.getJSONObject(i)
            Place(
                o.optString("name"), o.optDouble("latitude", 0.0), o.optDouble("longitude", 0.0),
                listOf(o.optString("admin1"), o.optString("country")).filter { it.isNotEmpty() }.joinToString(", ")
            )
        }
    }

    fun icon(code: Int, day: Boolean = true): String = when (code) {
        0 -> if (day) "☀️" else "🌙"
        1, 2 -> if (day) "🌤️" else "☁️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        in 51..57 -> "🌦️"
        in 61..67, in 80..82 -> "🌧️"
        in 71..77, 85, 86 -> "🌨️"
        in 95..99 -> "⛈️"
        else -> "🌡️"
    }

    fun desc(code: Int): String = when (code) {
        0 -> "Clear sky"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Foggy"
        in 51..57 -> "Drizzle"
        in 61..65 -> "Rain"
        66, 67 -> "Freezing rain"
        in 71..77 -> "Snow"
        in 80..82 -> "Rain showers"
        85, 86 -> "Snow showers"
        in 95..99 -> "Thunderstorm"
        else -> "—"
    }
}

/** Official exchange rates from Nepal Rastra Bank (free, no key). */
object Forex {
    class Rate(val iso: String, val name: String, val unit: Int, val buy: Double, val sell: Double)

    val MAIN = listOf("USD", "EUR", "GBP", "AUD", "CAD", "INR", "JPY", "CNY", "QAR", "AED", "SAR", "MYR", "KRW")

    private val FLAGS = mapOf(
        "USD" to "🇺🇸", "EUR" to "🇪🇺", "GBP" to "🇬🇧", "INR" to "🇮🇳", "AUD" to "🇦🇺", "CAD" to "🇨🇦",
        "JPY" to "🇯🇵", "CNY" to "🇨🇳", "QAR" to "🇶🇦", "SAR" to "🇸🇦", "AED" to "🇦🇪", "MYR" to "🇲🇾",
        "KRW" to "🇰🇷", "SGD" to "🇸🇬", "CHF" to "🇨🇭", "THB" to "🇹🇭", "KWD" to "🇰🇼", "BHD" to "🇧🇭",
        "OMR" to "🇴🇲", "HKD" to "🇭🇰", "SEK" to "🇸🇪", "DKK" to "🇩🇰"
    )

    fun flag(iso: String): String = FLAGS[iso] ?: "💱"

    fun cached(): Pair<String, List<Rate>>? = parse(Store.str("fx"))

    fun stale(): Boolean = System.currentTimeMillis() - Store.long("fx_t") > 3 * 3600_000L

    fun fetch(): Pair<String, List<Rate>>? {
        val url = "https://www.nrb.org.np/api/forex/v1/rates?page=1&per_page=10&from=${Day.ago(6)}&to=${Day.key()}"
        val s = Net.get(url) ?: return null
        val p = parse(s) ?: return null
        Store.setStr("fx", s)
        Store.setLong("fx_t", System.currentTimeMillis())
        return p
    }

    /** The newest day in the answer. */
    private fun parse(s: String): Pair<String, List<Rate>>? {
        if (s.isEmpty()) return null
        return try {
            val pay = JSONObject(s).getJSONObject("data").getJSONArray("payload")
            if (pay.length() == 0) return null
            var best = pay.getJSONObject(0)
            for (i in 1 until pay.length()) {
                val o = pay.getJSONObject(i)
                if (o.optString("date") > best.optString("date")) best = o
            }
            val rates = best.getJSONArray("rates")
            val list = (0 until rates.length()).map { i ->
                val r = rates.getJSONObject(i)
                val c = r.getJSONObject("currency")
                Rate(
                    c.optString("iso3"), c.optString("name"), c.optInt("unit", 1).coerceAtLeast(1),
                    r.optString("buy").toDoubleOrNull() ?: 0.0, r.optString("sell").toDoubleOrNull() ?: 0.0
                )
            }
            best.optString("date") to list
        } catch (_: Exception) {
            null
        }
    }
}

/** One thought a day. */
object Quotes {
    private val LIST = listOf(
        "The journey of a thousand miles begins with a single step." to "Lao Tzu",
        "Well begun is half done." to "Aristotle",
        "Drop by drop, the pot fills up. (Thopa thopa milera ghaito bharincha.)" to "Nepali proverb",
        "One hand alone cannot clap." to "Nepali proverb",
        "Work done with patience bears the sweetest fruit." to "Nepali proverb",
        "You don't need more time. You need one hour with your phone in another room." to "Yogi One",
        "Small daily wins beat rare heroic efforts." to "Yogi One",
        "Be the calm in the room. Everything else gets easier." to "Yogi One",
        "Finish what you start today, and tomorrow starts lighter." to "Yogi One",
        "Spend less than you earn, and you will never be poor." to "Yogi One",
        "A walk, a glass of water, and a deep breath fix more than you think." to "Yogi One",
        "Knowledge is the one wealth that grows when you share it." to "Nepali saying",
        "Like the mountain, be steady; like the river, keep moving." to "Yogi One",
        "Do the hard thing first. The rest of the day is a reward." to "Yogi One",
        "It always seems impossible until it's done." to "Nelson Mandela",
        "The best time to plant a tree was 20 years ago. The second best time is now." to "Proverb",
        "What we think, we become." to "Buddha",
        "Health is the greatest gift, contentment the greatest wealth." to "Buddha",
        "You are what you do every day, not what you say once." to "Yogi One",
        "Learn something today that your future self will thank you for." to "Yogi One",
        "Silence and a cup of tea solve half of life's problems." to "Yogi One",
        "Count your blessings before your problems." to "Yogi One",
        "Discipline is choosing what you want most over what you want now." to "Yogi One",
        "Even the tallest Himalaya is climbed one step at a time." to "Yogi One",
        "Call your mother. She's thinking of you." to "Yogi One",
        "Focus is saying no to a hundred good things." to "Yogi One",
        "Rest is not lazy. It's how the bow gets its strength back." to "Yogi One",
        "Peace comes from within. Do not seek it without." to "Buddha",
        "A goal without a plan is only a wish." to "Proverb",
        "Today's effort is tomorrow's ease." to "Yogi One"
    )

    fun today(): Pair<String, String> = LIST[Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % LIST.size]
}
