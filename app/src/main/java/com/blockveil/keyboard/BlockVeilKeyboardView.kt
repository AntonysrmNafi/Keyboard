package com.blockveil.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
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
    var functionKeyCodes: Set<Int> = setOf(-1, -5, -4, -20, -21, -22, -24, -25, -26, -30, -31)
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

    // Theme: Light Key Background #C6E8D0, Light Key Text #0A4D4A,
    // Dark Key Background #0D6B5A, Dark Key Icon/Text #D8E8C2
    private val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC6E8D0.toInt() }
    private val letterPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFAEDCB0.toInt() }
    private val functionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0D6B5A.toInt() }
    private val functionPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0A5849.toInt() }
    // Problem: Backspace gets its own distinct key color, not the shared
    // dark-function color.
    private val backspacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8D9A0.toInt() }
    private val backspacePressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFDBC988.toInt() }

    private val boldTypeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)

    private val letterLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0A4D4A.toInt()
        textAlign = Paint.Align.CENTER
        textSize = context.resources.displayMetrics.scaledDensity * 22f
        typeface = boldTypeface
    }
    private val functionLabelPaint = Paint(letterLabelPaint).apply {
        color = 0xFFD8E8C2.toInt()
        textSize = context.resources.displayMetrics.scaledDensity * 13f
    }
    // Point: "=<" (more-options toggle on Symbols page) reads visually bigger
    // than Shift's compact icon at the same 13sp used by ?123/ABC, so it gets
    // its own smaller size instead of shrinking functionLabelPaint (which
    // would also shrink ?123/ABC).
    private val moreOptionsLabelPaint = Paint(letterLabelPaint).apply {
        color = 0xFFD8E8C2.toInt()
        textSize = context.resources.displayMetrics.scaledDensity * 11f
    }
    private val functionIconPaint = Paint(letterLabelPaint).apply {
        color = 0xFFD8E8C2.toInt()
        textSize = context.resources.displayMetrics.scaledDensity * 20f
    }
    // Problem Row5: language name in the middle of the spacebar - normal
    // weight (not bold), 17sp.
    private val spaceLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4A7A6F.toInt()
        textAlign = Paint.Align.CENTER
        textSize = context.resources.displayMetrics.scaledDensity * 17f
        typeface = android.graphics.Typeface.DEFAULT
    }
    // The little arrows on either side of the spacebar's language name -
    // smaller and lighter than the name itself.
    private val spaceArrowPaint = Paint(spaceLabelPaint).apply {
        color = 0xFF4A7A6F.toInt()
        textSize = context.resources.displayMetrics.scaledDensity * 12f
    }
    private val shiftRestIconPaint = Paint(letterLabelPaint).apply {
        color = 0xFF0A4D4A.toInt()
        textSize = context.resources.displayMetrics.scaledDensity * 20f
    }
    // Problem Row5: "." key's label is smaller than the other regular keys.
    private val dotLabelPaint = Paint(letterLabelPaint).apply {
        textSize = context.resources.displayMetrics.scaledDensity * 16f
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        color = 0xFF4A7A6F.toInt()
        textSize = context.resources.displayMetrics.scaledDensity * 12f
        typeface = Typeface.DEFAULT_BOLD
    }
    // Point: a second hint color for specific keys (e.g. Bengali digit hints
    // on the English number row) that should stand out from the regular
    // hint color used everywhere else.
    private val altHintPaint = Paint(hintPaint).apply {
        color = 0xFFC77B3E.toInt()
    }
    var altHintCodes: Set<Int> = emptySet()

    private val rect = RectF()

    // Point: enlarged key-preview bubble shown above the pressed key (like
    // Gboard/stock Android keyboards). A PopupWindow doesn't reliably show
    // from an InputMethodService's own window on all devices/versions, so
    // instead this view just reports where the bubble SHOULD be (screen
    // coordinates + size) and the service positions a plain overlay View
    // that already lives in input_view.xml (key_preview_bubble) - no
    // separate window involved, so there's nothing that can silently fail.
    var onKeyPreview: ((label: String, screenX: Int, screenY: Int, widthPx: Int, heightPx: Int) -> Unit)? = null
    var onHideKeyPreview: (() -> Unit)? = null

    // Codes that shouldn't get an enlarged preview - space (too wide, looks
    // wrong blown up), spacer, and icon-only keys (shift/backspace/enter)
    // where enlarging the icon reads as broken rather than helpful.
    private val previewSkipCodes = setOf(32, SPACER_KEY_CODE, -1, -5, -4)

    private fun showKeyPreview(key: Keyboard.Key) {
        val code = key.codes.firstOrNull() ?: return
        if (code in previewSkipCodes) {
            hideKeyPreview()
            return
        }
        val label = key.label?.toString() ?: return
        val isFunction = functionKeyCodes.contains(code)
        val isSingleLetter = label.length == 1 && label[0].isLetter() && !isFunction
        val shownLabel = if (keyboard?.isShifted == true && isSingleLetter) label.uppercase() else label

        val scale = verticalScale
        val sx = scaleMatrixX()
        val sy = scaleMatrixY()
        val bubbleWidth = (key.width * sx).toInt().coerceAtLeast((40f * density).toInt())
        val bubbleHeight = (key.height * scale * sy * 1.15f).toInt().coerceAtLeast((48f * density).toInt())

        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val pts = floatArrayOf(key.x + key.width / 2f, key.y * scale)
        matrix.mapPoints(pts)
        val screenX = (loc[0] + pts[0] - bubbleWidth / 2f).toInt()
        val screenY = (loc[1] + pts[1] - bubbleHeight).toInt()

        onKeyPreview?.invoke(shownLabel, screenX, screenY, bubbleWidth, bubbleHeight)
    }

    private fun hideKeyPreview() {
        onHideKeyPreview?.invoke()
    }

    // View.scaleX/scaleY (used for the "keyboard width/height %" and
    // one-handed-mode settings) aren't reflected in key.x/key.y directly,
    // but they ARE part of this view's transform matrix, so mapPoints()
    // above already accounts for them. These two helpers extract the
    // current scale factors to size the bubble correctly.
    private fun scaleMatrixX(): Float {
        val pts = floatArrayOf(0f, 0f, 1f, 0f)
        matrix.mapPoints(pts)
        return pts[2] - pts[0]
    }

    private fun scaleMatrixY(): Float {
        val pts = floatArrayOf(0f, 0f, 0f, 1f)
        matrix.mapPoints(pts)
        return pts[3] - pts[1]
    }

    // Point 2: the stock KeyboardView base class ignores the height it's
    // actually given and just uses the Keyboard XML's own computed row-height
    // sum. Since the container is now a fixed height everywhere (letters,
    // shift, symbols, emoji, clipboard all the same), this scales whatever is
    // drawn to exactly fill that fixed height - without needing to touch any
    // XML file's row heights (including English's).
    private val verticalScale: Float
        get() {
            val kbHeight = keyboard?.height ?: 0
            if (kbHeight <= 0 || height <= 0) return 1f
            return height.toFloat() / kbHeight.toFloat()
        }

    init {
        isPreviewEnabled = false
    }

    override fun onDraw(canvas: Canvas) {
        val scale = verticalScale
        canvas.save()
        try {
            canvas.scale(1f, scale)
            drawKeyboard(canvas)
        } catch (t: Throwable) {
            android.util.Log.e("BlockVeilKeyboardView", "onDraw failed, skipping this frame", t)
        } finally {
            canvas.restore()
        }
    }

    private fun drawKeyboard(canvas: Canvas) {
        val kb = keyboard ?: return
        val shiftActive = kb.isShifted

        for (key in kb.keys) {
            val code = key.codes.firstOrNull() ?: 0
            if (code == SPACER_KEY_CODE) continue

            val isShiftKey = code == -1
            val isBackspace = code == -5
            val isFunction = functionKeyCodes.contains(code)
            val pressed = pressedKeyCode == code

            // Shift always wears the same function-key background as Enter, both at
            // rest and while active - only its icon (outline -> solid white) changes.
            val bgPaint = when {
                isShiftKey && pressed -> functionPressedPaint
                isShiftKey -> functionPaint
                isBackspace && pressed -> backspacePressedPaint
                isBackspace -> backspacePaint
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
                // Problem Row4: Backspace icon rendered a bit smaller than
                // the others.
                val sizeFactor = if (isBackspace) 0.42f else 0.6f
                val iconSize = (rect.height() * sizeFactor).toInt()
                val left = (rect.centerX() - iconSize / 2f).toInt()
                val top = (rect.centerY() - iconSize / 2f).toInt()
                drawable.setBounds(left, top, left + iconSize, top + iconSize)
                drawable.draw(canvas)
                continue
            }

            val rawLabel = key.label?.toString()
            if (!rawLabel.isNullOrEmpty()) {
                val isSingleLetter = rawLabel.length == 1 && rawLabel[0].isLetter() && !isFunction && code != 32

                // Problem Row5: the spacebar's language label ("◀ English ▶")
                // draws the arrows smaller/lighter than the language name,
                // instead of one uniform size for the whole string.
                if (code == 32 && rawLabel.length > 2 &&
                    rawLabel.first() == '\u25C0' && rawLabel.last() == '\u25B6'
                ) {
                    val name = rawLabel.substring(1, rawLabel.length - 1).trim()
                    val arrowGap = 6f * density
                    val nameWidth = spaceLabelPaint.measureText(name)
                    val leftArrowWidth = spaceArrowPaint.measureText("\u25C0")
                    val rightArrowWidth = spaceArrowPaint.measureText("\u25B6")
                    val totalWidth = leftArrowWidth + arrowGap + nameWidth + arrowGap + rightArrowWidth
                    var x = rect.centerX() - totalWidth / 2f
                    val arrowY = rect.centerY() - (spaceArrowPaint.descent() + spaceArrowPaint.ascent()) / 2f
                    val nameY = rect.centerY() - (spaceLabelPaint.descent() + spaceLabelPaint.ascent()) / 2f
                    val savedAlign = spaceArrowPaint.textAlign
                    spaceArrowPaint.textAlign = Paint.Align.LEFT
                    canvas.drawText("\u25C0", x, arrowY, spaceArrowPaint)
                    x += leftArrowWidth + arrowGap
                    val savedNameAlign = spaceLabelPaint.textAlign
                    spaceLabelPaint.textAlign = Paint.Align.LEFT
                    canvas.drawText(name, x, nameY, spaceLabelPaint)
                    spaceLabelPaint.textAlign = savedNameAlign
                    x += nameWidth + arrowGap
                    canvas.drawText("\u25B6", x, arrowY, spaceArrowPaint)
                    spaceArrowPaint.textAlign = savedAlign
                    continue
                }

                val label = if (shiftActive && isSingleLetter) rawLabel.uppercase() else rawLabel

                val labelPaint = when {
                    isFunction && iconKeyCodes.contains(code) -> functionIconPaint
                    code == -21 -> moreOptionsLabelPaint
                    isFunction -> functionLabelPaint
                    // Problem Row5: the "." key's text is smaller than other
                    // regular letter/symbol keys.
                    code == 46 -> dotLabelPaint
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
                val paint = if (altHintCodes.contains(code)) altHintPaint else hintPaint
                canvas.drawText(
                    hint,
                    (key.x + key.width - 6f * density),
                    (key.y + 10f * density),
                    paint
                )
            }
        }
    }

    override fun onDetachedFromWindow() {
        hideKeyPreview()
        super.onDetachedFromWindow()
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
            hideKeyPreview()
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
                    key?.let { showKeyPreview(it) } ?: hideKeyPreview()
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
                    if (stillOnSameKey && currentKey != null) {
                        showKeyPreview(currentKey)
                    } else {
                        hideKeyPreview()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelLongPressCheck()
                cancelRepeatCheck()
                pressedKeyCode = null
                hideKeyPreview()
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

    private fun keyAt(x: Float, y: Float): Keyboard.Key? {
        val scale = verticalScale
        val scaledY = if (scale > 0f) y / scale else y
        return keyboard?.keys?.firstOrNull {
            it.codes.firstOrNull() != SPACER_KEY_CODE &&
                x >= it.x && x <= it.x + it.width && scaledY >= it.y && scaledY <= it.y + it.height
        }
    }

    private fun isOnSpaceKey(x: Float, y: Float): Boolean {
        val spaceKey = keyboard?.keys?.lastOrNull { it.codes.isNotEmpty() && it.codes[0] == 32 } ?: return false
        val scale = verticalScale
        val scaledY = if (scale > 0f) y / scale else y
        return x >= spaceKey.x && x <= spaceKey.x + spaceKey.width &&
            scaledY >= spaceKey.y && scaledY <= spaceKey.y + spaceKey.height
    }

    companion object {
        const val SPACER_KEY_CODE = -99
    }
}
