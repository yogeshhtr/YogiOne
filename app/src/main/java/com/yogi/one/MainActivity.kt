package com.yogi.one

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

/** One screen with five tabs at the bottom. */
class MainActivity : AppCompatActivity() {
    val handler = Handler(Looper.getMainLooper())

    /** Called twice a second while the app is open (clock, timer). */
    var ticker: (() -> Unit)? = null

    private lateinit var content: FrameLayout
    private val nav = mutableListOf<Pair<TextView, TextView>>()
    private var current = 0
    private val tabs = listOf("🏠" to "Today", "⏳" to "Focus", "✅" to "Habits", "💰" to "Money", "🧰" to "Tools")

    private val tick = object : Runnable {
        override fun run() {
            ticker?.invoke()
            handler.postDelayed(this, 500)
        }
    }

    private val back = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (current != 0) {
                show(0)
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        window.statusBarColor = C.BG
        window.navigationBarColor = C.CARD

        val root = vbox()
        root.setBackgroundColor(C.BG)
        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(MATCH, 0, 1f))

        val bar = hbox()
        bar.setBackgroundColor(C.CARD)
        bar.setPadding(0, dp(6), 0, dp(6))
        tabs.forEachIndexed { i, (icon, name) ->
            val item = vbox()
            item.gravity = Gravity.CENTER
            item.background = ripple(C.CARD, 14f)
            item.setPadding(0, dp(4), 0, dp(4))
            item.setOnClickListener { show(i) }
            val ic = label(icon, 21f)
            ic.gravity = Gravity.CENTER
            val tx = label(name, 11f, C.SUB, true)
            tx.gravity = Gravity.CENTER
            item.addView(ic)
            item.addView(tx)
            bar.addView(item, LinearLayout.LayoutParams(0, WRAP, 1f))
            nav.add(ic to tx)
        }
        root.addView(bar, LinearLayout.LayoutParams(MATCH, WRAP))
        setContentView(root)
        onBackPressedDispatcher.addCallback(this, back)
        show(Store.int("tab", 0).coerceIn(0, tabs.size - 1))
    }

    fun show(i: Int) {
        current = i
        Store.setInt("tab", i)
        ticker = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        content.removeAllViews()
        val v: View = when (i) {
            0 -> TodayTab(this).build()
            1 -> FocusTab(this).build()
            2 -> HabitsTab(this).build()
            3 -> MoneyTab(this).build()
            else -> ToolsTab(this).build()
        }
        content.addView(v, FrameLayout.LayoutParams(MATCH, MATCH))
        nav.forEachIndexed { j, (ic, tx) ->
            val on = j == i
            tx.setTextColor(if (on) C.ACCENT else C.SUB)
            ic.alpha = if (on) 1f else 0.5f
            ic.scaleX = if (on) 1.15f else 1f
            ic.scaleY = if (on) 1.15f else 1f
        }
    }

    /** Rebuilds the current tab and keeps the scroll position. */
    fun refresh() {
        val y = (content.getChildAt(0) as? ScrollView)?.scrollY ?: 0
        show(current)
        val sv = content.getChildAt(0) as? ScrollView
        sv?.post { sv.scrollTo(0, y) }
    }

    override fun onResume() {
        super.onResume()
        back.isEnabled = true
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    override fun onDestroy() {
        Torch.off(this)
        super.onDestroy()
    }
}
