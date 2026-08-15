package com.privacykeyboard.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.util.AttributeSet
import android.view.MotionEvent
import kotlin.math.abs

// Fully custom-drawn keyboard view so letter keys and function keys (shift,
// backspace, enter, ?123, ABC) can have different colors - something the
// stock single keyBackground drawable can't do. Also adds three gestures on
// top of the normal tap:
// 1. Horizontal drag on the spacebar moves the text cursor.
// 2. Long-press on the spacebar (without dragging) switches the typing language.
// 3. Long-press on a key with a registered "hint" inserts that hint character
//    (a lightweight number row on the top letter row).
class BlockVeilKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : KeyboardView(context, attrs) {

    var spaceCursorEnabled: Boolean = true
    // Pixels of drag needed per single cursor step. Smaller = faster/more sensitive.
    var pixelsPerCursorStep: Float = 60f
    var onCursorSwipe: ((steps: Int) -> Unit)? = null
    var onSpaceLongPress: (() -> Unit)? = null

    var hintMap: Map<Int, String> = emptyMap()
    var hintsEnabled: Boolean = true
    var onHintLongPress: ((hint: String) -> Unit)? = null

    // Key codes rendered with the "function key" color instead of the letter color.
    var functionKeyCodes: Set<Int> = setOf(-1, -5, -4, -20, -21, -22, -24, -30)
    // Key codes whose label is a single icon glyph, drawn larger than multi-character labels.
    var iconKeyCodes: Set<Int> = setOf(-1, -5, -4)

    private var trackingSpaceKey = false
    private var isSwiping = false
    private var startX = 0f
    private var accumulatedDeltaX = 0f

    private var pressedKeyCode: Int? = null

    private val longPressHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var longPressTriggered = false
    private var longPressStartX = 0f
    private var longPressStartY = 0f

    private var spaceLongPressRunnable: Runnable? = null
    private var spaceLongPressTriggered = false

    private val density = context.resources.displayMetrics.density
    private val cornerRadius = 8f * density

    private val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFA9E6C4.toInt() }
    private val letterPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8FD0AC.toInt() }
    private val functionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1B4D3E.toInt() }
    private val functionPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF123A2E.toInt() }

    private val letterLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0D1F1A.toInt()
        textAlign = Paint.Align.CENTER
        textSize = context.resources.displayMetrics.scaledDensity * 18f
    }
    private val functionLabelPaint = Paint(letterLabelPaint).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = context.resources.displayMetrics.scaledDensity * 13f
    }
    private val functionIconPaint = Paint(letterLabelPaint).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = context.resources.displayMetrics.scaledDensity * 20f
    }
    private val spaceLabelPaint = Paint(letterLabelPaint).apply {
        color = 0xFF3A5F52.toInt()
        textSize = context.resources.displayMetrics.scaledDensity * 13f
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        color = 0xFF0D1F1A.toInt()
        textSize = density * 10f
    }

    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val kb = keyboard ?: return

        for (key in kb.keys) {
            val code = key.codes.firstOrNull() ?: 0
            val isFunction = functionKeyCodes.contains(code)
            val pressed = pressedKeyCode == code

            val bgPaint = when {
                isFunction && pressed -> functionPressedPaint
                isFunction -> functionPaint
                pressed -> letterPressedPaint
                else -> letterPaint
            }

            rect.set(
                key.x.toFloat(),
                key.y.toFloat(),
                (key.x + key.width).toFloat(),
                (key.y + key.height).toFloat()
            )
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)

            val label = key.label?.toString()
            if (!label.isNullOrEmpty()) {
                val labelPaint = when {
                    code == 32 -> spaceLabelPaint
                    isFunction && iconKeyCodes.contains(code) -> functionIconPaint
                    isFunction -> functionLabelPaint
                    else -> letterLabelPaint
                }
                val textY = rect.centerY() - (labelPaint.descent() + labelPaint.ascent()) / 2f
                canvas.drawText(label, rect.centerX(), textY, labelPaint)
            }
        }

        if (hintsEnabled && hintMap.isNotEmpty()) {
            for (key in kb.keys) {
                val code = key.codes.firstOrNull() ?: continue
                val hint = hintMap[code] ?: continue
                canvas.drawText(
                    hint,
                    (key.x + key.width - 6f * density),
                    (key.y + 16f * density),
                    hintPaint
                )
            }
        }
    }

    override fun onTouchEvent(me: MotionEvent): Boolean {
        when (me.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                trackingSpaceKey = spaceCursorEnabled && isOnSpaceKey(me.x, me.y)
                isSwiping = false
                startX = me.x
                accumulatedDeltaX = 0f

                val key = keyAt(me.x, me.y)
                pressedKeyCode = key?.codes?.firstOrNull()
                invalidate()

                if (trackingSpaceKey) {
                    scheduleSpaceLongPress()
                } else {
                    scheduleLongPressCheck(me.x, me.y)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (trackingSpaceKey) {
                    val dx = me.x - startX
                    if (!isSwiping && abs(dx) > pixelsPerCursorStep / 2f) {
                        isSwiping = true
                        cancelSpaceLongPress()
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
                cancelSpaceLongPress()
                pressedKeyCode = null
                invalidate()

                if (trackingSpaceKey && isSwiping) {
                    trackingSpaceKey = false
                    isSwiping = false
                    return true
                }
                if (spaceLongPressTriggered) {
                    spaceLongPressTriggered = false
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

    private fun scheduleSpaceLongPress() {
        spaceLongPressTriggered = false
        val runnable = Runnable {
            spaceLongPressTriggered = true
            onSpaceLongPress?.invoke()
        }
        spaceLongPressRunnable = runnable
        longPressHandler.postDelayed(runnable, 450)
    }

    private fun cancelSpaceLongPress() {
        spaceLongPressRunnable?.let { longPressHandler.removeCallbacks(it) }
        spaceLongPressRunnable = null
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
