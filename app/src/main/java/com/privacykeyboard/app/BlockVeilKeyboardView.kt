package com.privacykeyboard.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.util.AttributeSet
import android.view.MotionEvent
import kotlin.math.abs

// Fully custom-drawn keyboard view so letter keys and function keys (shift,
// backspace, enter, ?123, ABC) can have different colors, the shift key can
// show an active/highlighted state, and letter keys preview uppercase while
// shifted - none of which the stock single keyBackground drawable can do.
//
// Gestures on top of the normal tap:
// 1. Horizontal drag on the spacebar moves the text cursor.
// 2. Long-press on the spacebar (without dragging) switches the typing language.
// 3. Long-press on a key with a registered "hint" inserts that hint character
//    (a lightweight number row on the top letter row).
// 4. Long-press on a registered "action" key (c/x/v) copies, cuts, or pastes
//    the whole text field.
class BlockVeilKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : KeyboardView(context, attrs) {

    var spaceCursorEnabled: Boolean = true
    var pixelsPerCursorStep: Float = 60f
    var onCursorSwipe: ((steps: Int) -> Unit)? = null
    var onSpaceLongPress: (() -> Unit)? = null

    var hintMap: Map<Int, String> = emptyMap()
    var hintsEnabled: Boolean = true
    var onHintLongPress: ((hint: String) -> Unit)? = null

    // Keys that trigger an action on long-press instead of (or in addition to) a hint.
    var actionLongPressCodes: Set<Int> = emptySet()
    var onActionLongPress: ((code: Int) -> Unit)? = null

    // Key codes rendered with the "function key" color instead of the letter color.
    var functionKeyCodes: Set<Int> = setOf(-1, -5, -4, -20, -21, -22, -24, -30)
    // Key codes whose label is a single icon glyph, drawn larger than multi-character labels.
    var iconKeyCodes: Set<Int> = setOf(-1, -5, -4)
    // Key codes drawn with an icon Drawable instead of a text label.
    var iconDrawables: Map<Int, Drawable> = emptyMap()

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
        isFakeBoldText = true
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
    // Shift key icon when NOT active: dark icon on the normal mint key color, matching
    // the reference app's resting look (shift isn't treated as a "function" key at rest).
    private val shiftRestIconPaint = Paint(letterLabelPaint).apply {
        color = 0xFF0D1F1A.toInt()
        textSize = context.resources.displayMetrics.scaledDensity * 20f
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        color = 0xFF0D1F1A.toInt()
        textSize = density * 10f
    }

    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val kb = keyboard ?: return
        val shiftActive = kb.isShifted

        for (key in kb.keys) {
            val code = key.codes.firstOrNull() ?: 0
            if (code == SPACER_KEY_CODE) continue

            val isShiftKey = code == -1
            val isFunction = functionKeyCodes.contains(code)
            val pressed = pressedKeyCode == code

            val bgPaint = when {
                isShiftKey && shiftActive && pressed -> functionPressedPaint
                isShiftKey && shiftActive -> functionPaint
                isShiftKey && pressed -> letterPressedPaint
                isShiftKey -> letterPaint
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

            val drawable = iconDrawables[code]
            if (drawable != null) {
                val iconSize = (rect.height() * 0.5f).toInt()
                val left = (rect.centerX() - iconSize / 2f).toInt()
                val top = (rect.centerY() - iconSize / 2f).toInt()
                drawable.setBounds(left, top, left + iconSize, top + iconSize)
                drawable.draw(canvas)
                continue
            }

            val rawLabel = key.label?.toString()
            if (!rawLabel.isNullOrEmpty()) {
                val isSingleLetter = rawLabel.length == 1 && rawLabel[0].isLetter() && !isFunction && code != 32
                val label = if (shiftActive && isSingleLetter) rawLabel.uppercase() else rawLabel

                val labelPaint = when {
                    isShiftKey && shiftActive -> functionIconPaint
                    isShiftKey -> shiftRestIconPaint
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
                if (code == SPACER_KEY_CODE) continue
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
        val key = keyAt(x, y) ?: return
        val code = key.codes.firstOrNull() ?: return

        val hint = if (hintsEnabled) hintMap[code] else null
        val isActionKey = actionLongPressCodes.contains(code)
        if (hint == null && !isActionKey) return

        longPressStartX = x
        longPressStartY = y
        longPressTriggered = false
        val runnable = Runnable {
            longPressTriggered = true
            if (hint != null) {
                onHintLongPress?.invoke(hint)
            } else {
                onActionLongPress?.invoke(code)
            }
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
            it.codes.firstOrNull() != SPACER_KEY_CODE &&
                x >= it.x && x <= it.x + it.width && y >= it.y && y <= it.y + it.height
        }

    private fun isOnSpaceKey(x: Float, y: Float): Boolean {
        val spaceKey = keyboard?.keys?.firstOrNull { it.codes.isNotEmpty() && it.codes[0] == 32 } ?: return false
        return x >= spaceKey.x && x <= spaceKey.x + spaceKey.width &&
            y >= spaceKey.y && y <= spaceKey.y + spaceKey.height
    }

    companion object {
        const val SPACER_KEY_CODE = -99
    }
}
