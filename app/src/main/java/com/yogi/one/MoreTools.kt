package com.yogi.one

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject
import java.net.URLEncoder
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.roundToInt

/** QR codes, notes and passwords. */
class MoreTools(private val a: MainActivity) {

    // ================= QR codes

    fun qr() {
        val body = a.vbox()
        var dlg: AlertDialog? = null
        body.addView(a.pill("📷  Scan a QR code / barcode") { dlg?.dismiss(); scan() })
        body.addView(a.pill("✏️  Make a QR code", C.CARD2) { dlg?.dismiss(); make() }.lin(t = a.dp(10)))
        val hist = Store.arr("qr_hist")
        if (hist.length() > 0) {
            body.addView(a.label("RECENT SCANS", 11f, C.SUB, true).lin(t = a.dp(18), b = a.dp(4)))
            for (i in hist.length() - 1 downTo 0) {
                val raw = hist.optString(i)
                val row = a.label("${kind(raw).first}  $raw", 14f)
                row.maxLines = 2
                row.ellipsize = TextUtils.TruncateAt.END
                row.setPadding(0, a.dp(9), 0, a.dp(9))
                row.setOnClickListener { dlg?.dismiss(); result(raw) }
                body.addView(row)
            }
        }
        dlg = a.sheet("📷 QR codes", body)
    }

    private fun scan() {
        try {
            GmsBarcodeScanning.getClient(a).startScan()
                .addOnSuccessListener { b ->
                    val raw = b.rawValue ?: ""
                    if (raw.isNotEmpty()) {
                        remember(raw)
                        a.window.decorView.tick()
                        result(raw)
                    }
                }
                .addOnFailureListener {
                    a.toast("The scanner is being set up by Google Play services. Try again in a minute.")
                }
        } catch (_: Exception) {
            a.toast("The QR scanner needs Google Play services")
        }
    }

    private fun remember(raw: String) {
        val h = Store.arr("qr_hist")
        val out = org.json.JSONArray()
        for (i in 0 until h.length()) if (h.optString(i) != raw) out.put(h.optString(i))
        out.put(raw)
        val keep = org.json.JSONArray()
        for (i in maxOf(0, out.length() - 20) until out.length()) keep.put(out.optString(i))
        Store.setArr("qr_hist", keep)
    }

    /** Icon + name of what a scanned text is. */
    private fun kind(raw: String): Pair<String, String> {
        val l = raw.lowercase(Locale.US)
        return when {
            l.startsWith("wifi:") -> "📶" to "Wi-Fi network"
            l.startsWith("http://") || l.startsWith("https://") -> "🌐" to "Link"
            l.startsWith("tel:") -> "📞" to "Phone number"
            l.startsWith("mailto:") -> "✉️" to "Email"
            l.startsWith("smsto:") || l.startsWith("sms:") -> "💬" to "Message"
            l.startsWith("geo:") -> "📍" to "Location"
            l.startsWith("begin:vcard") -> "👤" to "Contact"
            else -> "🔤" to "Text"
        }
    }

    /** WIFI:T:WPA;S:name;P:password;; → fields (handles \; escapes). */
    private fun wifi(raw: String): Map<String, String> {
        val parts = ArrayList<String>()
        val cur = StringBuilder()
        var esc = false
        for (ch in raw.substring(5)) {
            when {
                esc -> { cur.append(ch); esc = false }
                ch == '\\' -> esc = true
                ch == ';' -> { parts.add(cur.toString()); cur.setLength(0) }
                else -> cur.append(ch)
            }
        }
        parts.add(cur.toString())
        val out = HashMap<String, String>()
        for (p in parts) {
            val i = p.indexOf(':')
            if (i > 0) out[p.substring(0, i).uppercase(Locale.US)] = p.substring(i + 1)
        }
        return out
    }

    private fun result(raw: String) {
        val (icon, name) = kind(raw)
        val body = a.vbox()
        body.addView(a.label("$icon  $name", 18f, C.ACCENT2, true))
        val lower = raw.lowercase(Locale.US)
        if (lower.startsWith("wifi:")) {
            val w = wifi(raw)
            body.addView(a.resultRow("Network", w["S"] ?: "?").lin(t = a.dp(8)))
            body.addView(a.resultRow("Password", w["P"]?.ifEmpty { "(none)" } ?: "(none)", C.GOLD))
            body.addView(a.resultRow("Security", w["T"]?.ifEmpty { "Open" } ?: "Open"))
            val pw = w["P"] ?: ""
            if (pw.isNotEmpty()) body.addView(a.pill("📋  Copy password") { a.copy(pw, "Password copied") }.lin(t = a.dp(8)))
        } else {
            val tv = a.label(raw, 16f)
            tv.setTextIsSelectable(true)
            tv.background = rounded(C.CARD2, a.dpf(14f))
            tv.setPadding(a.dp(14), a.dp(12), a.dp(14), a.dp(12))
            body.addView(tv.lin(t = a.dp(10)))
        }
        val opens = listOf("http://", "https://", "tel:", "mailto:", "smsto:", "sms:", "geo:")
        if (opens.any { lower.startsWith(it) }) {
            body.addView(a.pill(if (lower.startsWith("tel:")) "📞  Call" else "🌐  Open") {
                try {
                    a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(raw)))
                } catch (_: Exception) {
                    a.toast("No app can open this")
                }
            }.lin(t = a.dp(10)))
        } else if (!lower.startsWith("wifi:") && !lower.startsWith("begin:")) {
            body.addView(a.pill("🔍  Search the web", C.CARD2) {
                try {
                    a.startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/search?q=" + URLEncoder.encode(raw, "UTF-8"))))
                } catch (_: Exception) {
                    a.toast("No browser found")
                }
            }.lin(t = a.dp(10)))
        }
        val row = a.hbox()
        row.addView(a.pill("📋  Copy", C.CARD2) { a.copy(raw) }.lin(0, WRAP, 1f, r = a.dp(6)))
        row.addView(a.pill("↗  Share", C.CARD2) { a.shareText(raw) }.lin(0, WRAP, 1f, l = a.dp(6)))
        body.addView(row.lin(t = a.dp(10)))
        a.sheet("Scanned", body)
    }

    private fun qrBitmap(text: String, size: Int): Bitmap? = try {
        val hints = mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8")
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val w = m.width
        val h = m.height
        val px = IntArray(w * h)
        for (y in 0 until h) {
            val off = y * w
            for (x in 0 until w) px[off + x] = if (m.get(x, y)) Color.BLACK else Color.WHITE
        }
        Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    } catch (_: Exception) {
        null
    }

    private fun wifiEsc(s: String): String =
        s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:").replace("\"", "\\\"")

    private fun make() {
        val body = a.vbox()
        var wifiMode = false
        val modes = a.hbox()
        val text = a.textArea("Text or link for the QR code", lines = 3)
        val ssid = a.input("Wi-Fi name")
        val pass = a.input("Wi-Fi password (empty = open network)")
        val img = ImageView(a)
        img.setBackgroundColor(Color.WHITE)
        val pad = a.dp(8)
        img.setPadding(pad, pad, pad, pad)
        val hint = a.label("", 12f, C.SUB)
        hint.gravity = Gravity.CENTER
        fun content(): String =
            if (wifiMode) {
                val s = ssid.text.toString()
                val p = pass.text.toString()
                if (s.isEmpty()) "" else "WIFI:T:${if (p.isEmpty()) "nopass" else "WPA"};S:${wifiEsc(s)};P:${wifiEsc(p)};;"
            } else {
                text.text.toString()
            }
        fun render() {
            val c = content()
            val bmp = if (c.isBlank()) null else qrBitmap(c, 600)
            if (bmp == null) {
                img.visibility = View.GONE
                hint.text = if (wifiMode) "Type your Wi-Fi name to make its QR code" else "Type something to make its QR code"
            } else {
                img.setImageBitmap(bmp)
                img.visibility = View.VISIBLE
                hint.text = if (wifiMode) "Friends scan this with their camera to join your Wi-Fi — no typing"
                else "Scan it with any phone camera · take a screenshot to keep it"
            }
        }
        fun drawModes() {
            modes.removeAllViews()
            modes.addView(a.chip("🔤 Text / link", !wifiMode) { wifiMode = false; drawModes() })
            modes.addView(a.chip("📶 Wi-Fi", wifiMode) { wifiMode = true; drawModes() })
            text.visibility = if (wifiMode) View.GONE else View.VISIBLE
            ssid.visibility = if (wifiMode) View.VISIBLE else View.GONE
            pass.visibility = if (wifiMode) View.VISIBLE else View.GONE
            render()
        }
        body.addView(modes)
        body.addView(text)
        body.addView(ssid)
        body.addView(pass)
        val s = a.dp(240)
        body.addView(img, LinearLayout.LayoutParams(s, s).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = a.dp(10)
        })
        body.addView(hint.lin(t = a.dp(8)))
        for (e in listOf(text, ssid, pass)) e.doAfterTextChanged { render() }
        drawModes()
        a.sheet("✏️ Make a QR code", body)
    }

    // ================= notes

    fun notes() {
        val body = a.vbox()
        var dlg: AlertDialog? = null
        body.addView(a.pill("＋  New note") { dlg?.dismiss(); editNote(-1) })
        val list = Store.arr("notes")
        if (list.length() == 0) {
            body.addView(a.label("No notes yet. Ideas, lists, numbers — keep them here.", 14f, C.SUB).lin(t = a.dp(12)))
        }
        val fmt = SimpleDateFormat("d MMM, HH:mm", Locale.US)
        for (i in list.length() - 1 downTo 0) {
            val o = list.getJSONObject(i)
            val card = a.vbox()
            card.background = a.ripple(C.CARD2, 16f)
            card.setPadding(a.dp(14), a.dp(12), a.dp(14), a.dp(12))
            val t = o.optString("title")
            card.addView(a.label(t.ifEmpty { "Untitled" }, 16f, C.TEXT, true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
            val b = o.optString("body")
            if (b.isNotEmpty()) {
                card.addView(a.label(b, 13f, C.SUB).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END })
            }
            card.addView(a.label(fmt.format(Date(o.optLong("time"))), 11f, C.SUB).lin(t = a.dp(4)))
            card.setOnClickListener { dlg?.dismiss(); editNote(i) }
            card.setOnLongClickListener {
                AlertDialog.Builder(a)
                    .setMessage("Delete “${t.ifEmpty { "Untitled" }}”?")
                    .setPositiveButton("Delete") { _, _ ->
                        Store.setArr("notes", without(Store.arr("notes"), i))
                        dlg?.dismiss()
                        notes()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                true
            }
            body.addView(card.lin(t = a.dp(10)))
        }
        if (list.length() > 0) body.addView(a.label("Tap to open · long-press to delete", 11f, C.SUB)
            .apply { gravity = Gravity.CENTER }.lin(t = a.dp(10)))
        dlg = a.sheet("📝 Notes", body)
    }

    private fun editNote(i: Int) {
        val list = Store.arr("notes")
        val o = if (i in 0 until list.length()) list.getJSONObject(i) else null
        val body = a.vbox()
        val title = a.input("Title", value = o?.optString("title") ?: "")
        val text = a.textArea("Write here…", o?.optString("body") ?: "", 8)
        body.addView(title)
        body.addView(text)
        val b = AlertDialog.Builder(a)
            .setTitle(if (o == null) "New note" else "Edit note")
            .setView(a.padded(body))
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel") { _, _ -> notes() }
        if (o != null) b.setNeutralButton("Share", null)
        val d = b.create()
        d.setOnShowListener {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val t = title.text.toString().trim()
                val x = text.text.toString().trim()
                if (t.isEmpty() && x.isEmpty()) {
                    a.toast("The note is empty")
                    return@setOnClickListener
                }
                val now = System.currentTimeMillis()
                val all = Store.arr("notes")
                if (o == null) {
                    all.put(JSONObject().put("title", t).put("body", x).put("time", now))
                } else if (i in 0 until all.length()) {
                    all.getJSONObject(i).put("title", t).put("body", x).put("time", now)
                }
                Store.setArr("notes", all)
                d.dismiss()
                a.refresh()
                notes()
            }
            d.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                a.shareText((title.text.toString() + "\n\n" + text.text.toString()).trim())
            }
        }
        d.show()
    }

    // ================= passwords

    fun password() {
        val rnd = SecureRandom()
        var len = 16
        var symbols = true
        val body = a.vbox()
        val out = a.label("", 20f, C.GOLD, true)
        out.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        out.gravity = Gravity.CENTER
        out.setTextIsSelectable(true)
        out.background = rounded(C.CARD2, a.dpf(14f))
        out.setPadding(a.dp(12), a.dp(16), a.dp(12), a.dp(16))
        val strength = a.label("", 13f, C.SUB)
        strength.gravity = Gravity.CENTER
        fun gen() {
            // no look-alike letters (0/O, 1/l/I)
            val pool = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789" + (if (symbols) "!@#\$%&*?-_+=" else "")
            out.text = (1..len).map { pool[rnd.nextInt(pool.length)] }.joinToString("")
            val bits = (len * ln(pool.length.toDouble()) / ln(2.0)).roundToInt()
            strength.text = "≈ $bits bits · " + when {
                bits >= 100 -> "💪 Very strong"
                bits >= 70 -> "👍 Strong"
                else -> "⚠️ OK for small things"
            }
        }
        val lens = a.hbox()
        val opts = a.hbox()
        fun drawChips() {
            lens.removeAllViews()
            for (n in listOf(8, 12, 16, 20, 24, 32)) lens.addView(a.chip("$n", n == len) { len = n; drawChips(); gen() })
            opts.removeAllViews()
            opts.addView(a.chip(if (symbols) "✔ Symbols !@#" else "Symbols !@#", symbols) { symbols = !symbols; drawChips(); gen() })
        }
        drawChips()
        body.addView(out.lin(t = a.dp(4)))
        body.addView(strength.lin(t = a.dp(6), b = a.dp(12)))
        body.addView(a.label("LENGTH", 11f, C.SUB, true))
        body.addView(a.hscroll(lens).lin(t = a.dp(6)))
        body.addView(opts)
        val row = a.hbox()
        row.addView(a.pill("🔄  New") { gen() }.lin(0, WRAP, 1f, r = a.dp(6)))
        row.addView(a.pill("📋  Copy", C.CARD2) { a.copy(out.text.toString(), "Password copied") }.lin(0, WRAP, 1f, l = a.dp(6)))
        body.addView(row.lin(t = a.dp(6)))
        gen()
        a.sheet("🔐 Password", body)
    }
}
