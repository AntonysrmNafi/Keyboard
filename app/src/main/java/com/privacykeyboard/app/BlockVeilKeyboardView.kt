package com.privacykeyboard.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.util.AttributeSet
import android.view.MotionEvent
import kotlin.math.abs

// Extends the stock KeyboardView with two extra gestures:
// 1. Horizontal drag on the spacebar moves the text cursor instead of typing spaces.
// 2. Long-press on a key with a registered "hint" (small corner digit on the top
//    letter row) inserts that hint character instead of the normal letter.
// A normal tap on either falls through to the regular key-press flow.
class BlockVeilKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : KeyboardView(context, attrs) {

    var spaceCursorEnabled: Boolean = true
    // Pixels of drag needed per single cursor step. Smaller = faster/more sensitive.
    var pixelsPerCursorStep: Float = 60f
    var onCursorSwipe: ((steps: Int) -> Unit)? = null

    // Maps a key's primary code to the hint character shown in its corner and
    // inserted on long-press. Empty map / hintsEnabled=false means no hints at all.
    var hintMap: Map<Int, String> = emptyMap()
    var hintsEnabled: Boolean = true
    var onHintLongPress: ((hint: String) -> Unit)? = null

    private var trackingSpaceKey = false
    private var isSwiping = false
    private var startX = 0f
    private var accumulatedDeltaX = 0f

    private val longPressHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var longPressTriggered = false
    private var longPressStartX = 0f
    private var longPressStartY = 0f

    private val hintPaint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.RIGHT
        color = 0xFF0D1F1A.toInt()
        textSize = context.resources.displayMetrics.density * 10f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!hintsEnabled || hintMap.isEmpty()) return

        val kb = keyboard ?: return
        for (key in kb.keys) {
            val code = key.codes.firstOrNull() ?: continue
            val hint = hintMap[code] ?: continue
            canvas.drawText(
                hint,
                (key.x + key.width - 6f * resources.displayMetrics.density),
                (key.y + 16f * resources.displayMetrics.density),
                hintPaint
            )
        }
    }

    override fun onTouchEvent(me: MotionEvent): Boolean {
        when (me.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                trackingSpaceKey = spaceCursorEnabled && isOnSpaceKey(me.x, me.y)
                isSwiping = false
                startX = me.x
                accumulatedDeltaX = 0f

                if (!trackingSpaceKey) {
                    scheduleLongPressCheck(me.x, me.y)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (trackingSpaceKey) {
                    val dx = me.x - startX
                    if (!isSwiping && abs(dx) > pixelsPerCursorStep / 2f) {
                        isSwiping = true
                    }
                    if (isSwiping) {
                        accumulatedDeltaX += (me.x - startX)
                        startX = me.x
                        val steps = (accumulatedDeltaX / pixelsPerCursorStep).toInt()
                        if (steps != 0) {
                            onCursorSwipe?.invoke(steps)
                            accumulatedDeltaX -= steps * pixelsPerCursorStep
                        }
                        return true
                    }
                } else if (longPressRunnable != null) {
                    val moved = abs(me.x - longPressStartX) + abs(me.y - longPressStartY)
                    if (moved > 24f) cancelLongPressCheck()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelLongPressCheck()
                if (trackingSpaceKey && isSwiping) {
                    trackingSpaceKey = false
                    isSwiping = false
                    return true
                }
                if (longPressTriggered) {
                    longPressTriggered = false
                    trackingSpaceKey = false
                    isSwiping = false
                    return true
                }
                trackingSpaceKey = false
                isSwiping = false
            }
        }

        return super.onTouchEvent(me)
    }

    private fun scheduleLongPressCheck(x: Float, y: Float) {
        if (!hintsEnabled || hintMap.isEmpty()) return
        val key = keyAt(x, y) ?: return
        val hint = hintMap[key.codes.firstOrNull()] ?: return

        longPressStartX = x
        longPressStartY = y
        longPressTriggered = false
        val runnable = Runnable {
            longPressTriggered = true
            onHintLongPress?.invoke(hint)
        }
        longPressRunnable = runnable
        longPressHandler.postDelayed(runnable, 350)
    }

    private fun cancelLongPressCheck() {
        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
        longPressRunnable = null
    }

    private fun keyAt(x: Float, y: Float): Keyboard.Key? =
        keyboard?.keys?.firstOrNull {
            x >= it.x && x <= it.x + it.width && y >= it.y && y <= it.y + it.height
        }

    private fun isOnSpaceKey(x: Float, y: Float): Boolean {
        val spaceKey = keyboard?.keys?.firstOrNull { it.codes.isNotEmpty() && it.codes[0] == 32 } ?: return false
        return x >= spaceKey.x && x <= spaceKey.x + spaceKey.width &&
            y >= spaceKey.y && y <= spaceKey.y + spaceKey.height
    }
}
