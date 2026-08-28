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
// Touch handling is also fully custom (never delegates to the stock
// KeyboardView.onTouchEvent). The stock implementation has internal state tied
// to sticky/modifier keys (used by our Shift key) that reproducibly crashed
// the keyboard once we started fully overriding onDraw, so we detect keys,
// long-presses, and the spacebar gestures ourselves and dispatch straight to
// the OnKeyboardActionListener.
//
// Gestures:
// 1. Swipe (drag) on the spacebar switches the typing language.
// 2. A plain press-and-hold on the spacebar (no drag) just types a space,
//    same as a normal tap.
// 3. Long-press on a key with a registered "hint" inserts that hint character
//    (a lightweight number row on the top letter row).
// 4. Long-press on a registered "action" key (c/x/v) copies, cuts, or pastes
//    the whole text field.
class BlockVeilKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : KeyboardView(context, attrs) {

    var spaceSwipeEnabled: Boolean = true
    // Horizontal drag distance (px) needed before a spacebar swipe triggers the language switch.
    var spaceSwipeThreshold: Float = 60f
    var onSpaceSwipe: (() -> Unit)? = null

    var hintMap: Map<Int, String> = emptyMap()
    var hintsEnabled: Boolean = true
    var onHintLongPress: ((hint: String) -> Unit)? = null

    // Keys that trigger an action on long-press instead of (or in addition to) a hint.
    var actionLongPressCodes: Set<Int> = emptySet()
    var onActionLongPress: ((code: Int) -> Unit)? = null

    // Point 3: keys that repeat (with acceleration) while held down, e.g.
    // Backspace - starts at a normal pace and speeds up the longer it's held.
    var repeatableKeyCodes: Set<Int> = setOf(-5)

    // Key codes rendered with the "function key" color instead of the letter color.
    // Point 3: only true action/state-changing keys belong here (backspace, enter,
    // shift, mode switches, emoji, more-symbols) - keys that just type a character
    // (including symbol keys like +, %, *, -) stay in the regular letter color so
    // every "this types something" key looks visually consistent, and only
    // "this changes what the keyboard is doing" keys stand out.
    var functionKeyCodes: Set<Int> = setOf(-1, -5, -4, -20, -21, -22, -24, -25, -26, -30)
    // Key codes whose label is a single icon glyph, drawn larger than multi-character labels.
    var iconKeyCodes: Set<Int> = setOf(-1, -5, -4)
    // Key codes drawn with an icon Drawable instead of a text label.
    var iconDrawables: Map<Int, Drawable> = emptyMap()
    // Shift renders as one of these two dedicated icons (already colored blue in the
    // drawable itself) instead of a text label - outline at rest, solid when active.
    var shiftIconRest: Drawable? = null
    var shiftIconActive: Drawable? = null

    // Our own listener reference. We deliberately do NOT forward to the base
    // class's setOnKeyboardActionListener/onTouchEvent - see class doc above.
    private var keyListener: OnKeyboardActionListener? = null

    override fun setOnKeyboardActionListener(listener: OnKeyboardActionListener) {
        keyListener = listener
    }

    private var trackingSpaceKey = false
    private var isSwiping = false
    private var startX = 0f

    private var pressedKeyCode: Int? = null
    private var downKeyCode: Int? = null

    private val longPressHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var longPressTriggered = false
    private var longPressStartX = 0f
    private var longPressStartY = 0f

    // Point 3: Backspace (and anything else in repeatableKeyCodes) repeats
    // while held, accelerating the longer it's held down.
    private var repeatRunnable: Runnable? = null
    private var repeatTriggered = false
    private var repeatCount = 0

    private val density = context.resources.displayMetrics.density
    private val cornerRadius = 8f * density

    private val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFA9E6C4.toInt() }
    private val letterPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8FD0AC.toInt() }
    private val functionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1B4D3E.toInt() }
    private val functionPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF123A2E.toInt() }

    private val boldTypeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)

    private val letterLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0D1F1A.toInt()
        textAlign = Paint.Align.CENTER
        textSize = context.resources.displayMetrics.scaledDensity * 18f
        typeface = boldTypeface
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

    init {
        isPreviewEnabled = false
    }

    override fun onDraw(canvas: Canvas) {
        try {
            drawKeyboard(canvas)
        } catch (t: Throwable) {
            android.util.Log.e("BlockVeilKeyboardView", "onDraw failed, skipping this frame", t)
        }
    }

    private fun drawKeyboard(canvas: Canvas) {
        val kb = keyboard ?: return
        val shiftActive = kb.isShifted

        for (key in kb.keys) {
            val code = key.codes.firstOrNull() ?: 0
            if (code == SPACER_KEY_CODE) continue

            val isShiftKey = code == -1
            val isFunction = functionKeyCodes.contains(code)
            val pressed = pressedKeyCode == code

            // Shift always wears the same function-key background as Enter, both at
            // rest and while active - only its icon (outline -> solid white) changes.
            val bgPaint = when {
                isShiftKey && pressed -> functionPressedPaint
                isShiftKey -> functionPaint
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

            if (isShiftKey) {
                val shiftDrawable = if (shiftActive) shiftIconActive else shiftIconRest
                if (shiftDrawable != null) {
                    val iconSize = (rect.height() * 0.5f).toInt()
                    val left = (rect.centerX() - iconSize / 2f).toInt()
                    val top = (rect.centerY() - iconSize / 2f).toInt()
                    shiftDrawable.setBounds(left, top, left + iconSize, top + iconSize)
                    shiftDrawable.draw(canvas)
                    continue
                }
            }

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
        try {
            handleTouch(me)
        } catch (t: Throwable) {
            android.util.Log.e("BlockVeilKeyboardView", "onTouchEvent failed", t)
            pressedKeyCode = null
            downKeyCode = null
            trackingSpaceKey = false
            isSwiping = false
        }
        return true
    }

    private fun handleTouch(me: MotionEvent) {
        when (me.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                trackingSpaceKey = spaceSwipeEnabled && isOnSpaceKey(me.x, me.y)
                isSwiping = false
                startX = me.x

                val key = keyAt(me.x, me.y)
                val code = key?.codes?.firstOrNull()
                pressedKeyCode = code
                downKeyCode = code
                invalidate()

                code?.let { keyListener?.onPress(it) }

                if (!trackingSpaceKey) {
                    scheduleLongPressCheck(me.x, me.y)
                    scheduleRepeatCheck(code)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (trackingSpaceKey) {
                    val dx = me.x - startX
                    if (!isSwiping && abs(dx) > spaceSwipeThreshold) {
                        isSwiping = true
                        onSpaceSwipe?.invoke()
                    }
                } else if (longPressRunnable != null) {
                    val moved = abs(me.x - longPressStartX) + abs(me.y - longPressStartY)
                    if (moved > 24f) cancelLongPressCheck()
                }
                if (repeatRunnable != null) {
                    val currentKey = keyAt(me.x, me.y)
                    if (currentKey?.codes?.firstOrNull() != downKeyCode) cancelRepeatCheck()
                }

                // If the finger has moved off the originally-pressed key, cancel the
                // press highlight (standard "drag off to cancel" behavior).
                if (!trackingSpaceKey && downKeyCode != null) {
                    val currentKey = keyAt(me.x, me.y)
                    val stillOnSameKey = currentKey?.codes?.firstOrNull() == downKeyCode
                    val newPressed = if (stillOnSameKey) downKeyCode else null
                    if (pressedKeyCode != newPressed) {
                        pressedKeyCode = newPressed
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelLongPressCheck()
                cancelRepeatCheck()
                pressedKeyCode = null
                invalidate()

                val wasSwipe = trackingSpaceKey && isSwiping
                val wasHintOrActionLongPress = longPressTriggered
                val wasRepeat = repeatTriggered
                longPressTriggered = false
                repeatTriggered = false
                trackingSpaceKey = false
                isSwiping = false

                val downCode = downKeyCode
                downKeyCode = null

                if (me.actionMasked == MotionEvent.ACTION_UP &&
                    !wasSwipe && !wasHintOrActionLongPress && !wasRepeat &&
                    downCode != null
                ) {
                    val releaseKey = keyAt(me.x, me.y)
                    if (releaseKey?.codes?.firstOrNull() == downCode) {
                        keyListener?.onKey(downCode, null)
                    }
                }

                downCode?.let { keyListener?.onRelease(it) }
            }
        }
    }

    private fun scheduleRepeatCheck(code: Int?) {
        if (code == null || !repeatableKeyCodes.contains(code)) return
        repeatTriggered = false
        repeatCount = 0

        lateinit var runnable: Runnable
        runnable = Runnable {
            repeatTriggered = true
            keyListener?.onKey(code, null)
            repeatCount++
            // Point 3: starts at a comfortable ~150ms pace and accelerates down
            // to a floor of ~35ms the longer the key is held, so it starts
            // deleting noticeably faster the longer you hold it.
            val nextDelay = (150 * Math.pow(0.85, repeatCount.toDouble())).toLong().coerceAtLeast(35)
            longPressHandler.postDelayed(runnable, nextDelay)
        }
        repeatRunnable = runnable
        // Initial delay before the first repeat kicks in (the first tap itself
        // is handled normally by the ACTION_UP path if released before this).
        longPressHandler.postDelayed(runnable, 400)
    }

    private fun cancelRepeatCheck() {
        repeatRunnable?.let { longPressHandler.removeCallbacks(it) }
        repeatRunnable = null
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
