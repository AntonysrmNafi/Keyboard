package com.blockveil.keyboard

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

// Diagnostic-only screen shown by BlockVeilApplication's crash handler.
// Displays the full stack trace of whatever just crashed, selectable so it
// can be copied and shared for debugging. Nothing here touches the network.
class CrashDisplayActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val trace = intent.getStringExtra(EXTRA_TRACE) ?: "Unknown error"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(40), dp(20), dp(20))
            setBackgroundColor(0xFF0D1F1A.toInt())
        }

        root.addView(TextView(this).apply {
            text = "BlockVeil crashed - here's why"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(16))
        })

        val scroll = ScrollView(this)
        val traceView = TextView(this).apply {
            text = trace
            setTextColor(0xFFE8FFF4.toInt())
            textSize = 12f
            setTextIsSelectable(true)
        }
        scroll.addView(traceView)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        setContentView(root)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_TRACE = "trace"
    }
}
