package com.privacykeyboard.app

import android.content.Context
import android.inputmethodservice.KeyboardView
import android.util.AttributeSet
import android.view.MotionEvent
import kotlin.math.abs

// Extends the stock KeyboardView with one extra gesture: horizontal drag on the
// spacebar moves the text cursor instead of typing spaces. A normal tap (little
// or no horizontal movement) still falls through to the regular key-press flow.
class BlockVeilKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : KeyboardView(context, attrs) {

    var spaceCursorEnabled: Boolean = true
    // Pixels of drag needed per single cursor step. Smaller = faster/more sensitive.
    var pixelsPerCursorStep: Float = 60f

    var onCursorSwipe: ((steps: Int) -> Unit)? = null

    private var trackingSpaceKey = false
    private var isSwiping = false
    private var startX = 0f
    private var accumulatedDeltaX = 0f

    override fun onTouchEvent(me: MotionEvent): Boolean {
        if (!spaceCursorEnabled) {
            return super.onTouchEvent(me)
        }

        when (me.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                trackingSpaceKey = isOnSpaceKey(me.x, me.y)
                isSwiping = false
                startX = me.x
                accumulatedDeltaX = 0f
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
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (trackingSpaceKey && isSwiping) {
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

    private fun isOnSpaceKey(x: Float, y: Float): Boolean {
        val spaceKey = keyboard?.keys?.firstOrNull { it.codes.isNotEmpty() && it.codes[0] == 32 } ?: return false
        return x >= spaceKey.x && x <= spaceKey.x + spaceKey.width &&
            y >= spaceKey.y && y <= spaceKey.y + spaceKey.height
    }
}
