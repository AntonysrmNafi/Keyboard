package com.privacykeyboard.app

import android.content.ClipboardManager
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.media.AudioManager
import android.text.TextUtils
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.LinearLayout
import android.widget.TextView

// Fully offline IME with English, Bangla phonetic and Bangla traditional modes,
// a two-page symbols/numbers keyboard, an offline clipboard history panel, and
// one-handed / resizing support. Wired to user-configurable preferences from
// SettingsStore. No network permission, no keystroke logging, nothing persisted
// beyond a small local clipboard history that never leaves the phone.
class PrivacyInputMethodService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: BlockVeilKeyboardView
    private lateinit var suggestionStrip: LinearLayout
    private lateinit var clipboardButton: TextView
    private lateinit var oneHandedButton: TextView
    private lateinit var clipboardPanel: LinearLayout
    private lateinit var clipboardList: LinearLayout

    private lateinit var englishKeyboardPlain: Keyboard
    private lateinit var englishKeyboardWithNumRow: Keyboard
    private lateinit var englishKeyboardWithNumRowLarge: Keyboard
    private lateinit var traditionalKeyboard: Keyboard
    private lateinit var symbolsKeyboard1: Keyboard
    private lateinit var symbolsKeyboard2: Keyboard

    private lateinit var suggestion1: TextView
    private lateinit var suggestion2: TextView
    private lateinit var suggestion3: TextView

    private var mode: InputMode = InputMode.ENGLISH
    private var isShifted = false

    // ?123 symbols overlay state. Independent from the letter mode above,
    // exactly like Gboard: pressing ABC returns to whichever letter mode was active.
    private var showingSymbols = false
    private var symbolsPageTwo = false

    // Raw keys typed for the current word, drives phonetic conversion and suggestions
    private val rawWordBuffer = StringBuilder()
    // Length of text currently committed to the input field for the active word
    private var committedWordLength = 0

    // Auto-capitalization / double-space-period state
    private var capitalizeNext = true
    private var lastCommittedWasSpace = false

    // A small, editable blocklist. Add more words as needed, one per entry.
    private val offensiveWordsEn = setOf<String>()
    private val offensiveWordsBn = setOf<String>()

    // Long-press hints on the top letter row (q..p -> 1..0), like a lightweight number row.
    private val topRowHints = mapOf(
        113 to "1", 119 to "2", 101 to "3", 114 to "4", 116 to "5",
        121 to "6", 117 to "7", 105 to "8", 111 to "9", 112 to "0"
    )

    private var clipboardManager: ClipboardManager? = null
    private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null

    override fun onCreate() {
        super.onCreate()
        DictionaryProvider.load(this)

        clipboardManager = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            val clip = clipboardManager?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).coerceToText(this)?.toString()
                if (!text.isNullOrBlank()) ClipboardStore.addItem(this, text)
            }
        }
        clipboardListener = listener
        clipboardManager?.addPrimaryClipChangedListener(listener)
    }

    override fun onDestroy() {
        clipboardListener?.let { clipboardManager?.removePrimaryClipChangedListener(it) }
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val view = LayoutInflater.from(this).inflate(R.layout.input_view, null)

        englishKeyboardPlain = Keyboard(this, R.xml.keys_layout_english)
        englishKeyboardWithNumRow = Keyboard(this, R.xml.keys_layout_english_numrow)
        englishKeyboardWithNumRowLarge = Keyboard(this, R.xml.keys_layout_english_numrow_large)
        traditionalKeyboard = Keyboard(this, R.xml.keys_layout_bangla_traditional)
        symbolsKeyboard1 = Keyboard(this, R.xml.keys_layout_symbols1)
        symbolsKeyboard2 = Keyboard(this, R.xml.keys_layout_symbols2)

        keyboardView = view.findViewById(R.id.keyboard_view)
        keyboardView.setOnKeyboardActionListener(this)
        keyboardView.onCursorSwipe = { steps -> moveCursor(steps) }
        keyboardView.onSpaceLongPress = { switchMode() }
        keyboardView.hintMap = topRowHints
        keyboardView.onHintLongPress = { hint -> insertHintChar(hint) }

        suggestionStrip = view.findViewById(R.id.suggestion_strip)
        suggestion1 = view.findViewById(R.id.suggestion_1)
        suggestion2 = view.findViewById(R.id.suggestion_2)
        suggestion3 = view.findViewById(R.id.suggestion_3)
        listOf(suggestion1, suggestion2, suggestion3).forEach { tv ->
            tv.setOnClickListener { applySuggestion(tv.text.toString()) }
        }

        clipboardButton = view.findViewById(R.id.clipboard_button)
        clipboardButton.setOnClickListener { toggleClipboardPanel() }

        oneHandedButton = view.findViewById(R.id.one_handed_button)
        oneHandedButton.setOnClickListener { cycleOneHandedMode() }

        clipboardPanel = view.findViewById(R.id.clipboard_panel)
        clipboardList = view.findViewById(R.id.clipboard_list)
        view.findViewById<TextView>(R.id.clipboard_close_button).setOnClickListener { hideClipboardPanel() }
        view.findViewById<TextView>(R.id.clipboard_clear_button).setOnClickListener {
            ClipboardStore.clear(this)
            refreshClipboardList()
        }

        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        resetWordState()
        isShifted = false
        capitalizeNext = true
        lastCommittedWasSpace = false
        showingSymbols = false
        symbolsPageTwo = false
        hideClipboardPanel()
        applySettings()
        applyKeyboardForMode()
    }

    private fun applySettings() {
        keyboardView.isPreviewEnabled = SettingsStore.getBoolean(this, SettingsStore.KEY_POPUP_ON_KEYPRESS, true)
        val showSuggestions = SettingsStore.getBoolean(this, SettingsStore.KEY_SHOW_SUGGESTIONS, true)
        suggestionStrip.visibility = if (showSuggestions) View.VISIBLE else View.GONE

        keyboardView.spaceCursorEnabled = SettingsStore.getBoolean(this, SettingsStore.KEY_SPACE_CURSOR_ENABLED, true)
        val speedLevel = SettingsStore.getInt(this, SettingsStore.KEY_SPACE_CURSOR_SPEED, 1)
        val density = resources.displayMetrics.density
        keyboardView.pixelsPerCursorStep = when (speedLevel) {
            0 -> 48f * density
            2 -> 24f * density
            else -> 36f * density
        }

        applyEnterKeyLabel()
        applySizingAndOneHanded()
    }

    private fun applyEnterKeyLabel() {
        val forced = SettingsStore.getBoolean(this, SettingsStore.KEY_FORCED_ENTER_BUTTON, true)
        val label = if (forced) "\u21B5" else "Go"
        listOf(
            englishKeyboardPlain, englishKeyboardWithNumRow, englishKeyboardWithNumRowLarge,
            traditionalKeyboard, symbolsKeyboard1, symbolsKeyboard2
        ).forEach { kb ->
            kb.keys.firstOrNull { it.codes.isNotEmpty() && it.codes[0] == -4 }?.label = label
        }
        keyboardView.invalidateAllKeys()
    }

    private fun applySizingAndOneHanded() {
        keyboardView.post {
            val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

            val resizeEnabled = SettingsStore.getBoolean(this, SettingsStore.KEY_KEYBOARD_RESIZE_ENABLED, false)
            val heightPercent = if (resizeEnabled) {
                SettingsStore.getInt(
                    this,
                    if (landscape) SettingsStore.KEY_KEYBOARD_HEIGHT_LANDSCAPE else SettingsStore.KEY_KEYBOARD_HEIGHT_PORTRAIT,
                    80
                )
            } else 100
            keyboardView.pivotY = 0f
            keyboardView.scaleY = heightPercent / 100f

            val oneHandedMode = SettingsStore.getInt(this, SettingsStore.KEY_ONE_HANDED_MODE, 0)
            val widthPercent = if (oneHandedMode != 0) {
                SettingsStore.getInt(
                    this,
                    if (landscape) SettingsStore.KEY_ONE_HANDED_WIDTH_LANDSCAPE else SettingsStore.KEY_ONE_HANDED_WIDTH_PORTRAIT,
                    if (landscape) 40 else 85
                )
            } else 100
            keyboardView.pivotX = if (oneHandedMode == 2) keyboardView.width.toFloat() else 0f
            keyboardView.scaleX = widthPercent / 100f

            oneHandedButton.text = when (oneHandedMode) {
                1 -> "\u25C0"
                2 -> "\u25B6"
                else -> getString(R.string.icon_one_handed)
            }
        }
    }

    private fun cycleOneHandedMode() {
        val current = SettingsStore.getInt(this, SettingsStore.KEY_ONE_HANDED_MODE, 0)
        SettingsStore.setInt(this, SettingsStore.KEY_ONE_HANDED_MODE, (current + 1) % 3)
        applySizingAndOneHanded()
    }

    private fun moveCursor(steps: Int) {
        val ic = currentInputConnection ?: return
        val keyCode = if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        repeat(kotlin.math.abs(steps)) {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
    }

    private fun applyKeyboardForMode() {
        val useNumberRow = SettingsStore.getBoolean(this, SettingsStore.KEY_ENABLE_NUMBER_ROW, true)
        val useLarge = SettingsStore.getBoolean(this, SettingsStore.KEY_LARGE_NUMBER_ROW, false)

        keyboardView.keyboard = when {
            showingSymbols && symbolsPageTwo -> symbolsKeyboard2
            showingSymbols -> symbolsKeyboard1
            mode == InputMode.BANGLA_TRADITIONAL -> traditionalKeyboard
            useNumberRow && useLarge -> englishKeyboardWithNumRowLarge
            useNumberRow -> englishKeyboardWithNumRow
            else -> englishKeyboardPlain
        }

        val hideHints = SettingsStore.getBoolean(this, SettingsStore.KEY_HIDE_LONG_PRESS_HINTS, false)
        keyboardView.hintsEnabled = !hideHints && !useNumberRow && !showingSymbols && mode != InputMode.BANGLA_TRADITIONAL

        val spaceLabel = when {
            showingSymbols -> "space"
            mode == InputMode.ENGLISH -> "English"
            mode == InputMode.BANGLA_PHONETIC -> "বাংলা ফোনেটিক"
            else -> "বাংলা"
        }
        keyboardView.keyboard?.keys?.firstOrNull { it.codes.firstOrNull() == 32 }?.label = spaceLabel

        keyboardView.invalidateAllKeys()
    }

    private fun resetWordState() {
        rawWordBuffer.clear()
        committedWordLength = 0
        clearSuggestions()
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val ic = currentInputConnection ?: return
        giveKeyFeedback()

        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> handleDelete(ic)
            Keyboard.KEYCODE_SHIFT -> toggleShift()
            Keyboard.KEYCODE_DONE -> {
                commitWordBoundary(ic, "")
                ic.performEditorAction(EditorInfo.IME_ACTION_DONE)
            }
            MODE_SWITCH_CODE -> switchMode()
            EMOJI_PLACEHOLDER_CODE -> { /* Emoji panel not implemented yet - deliberately does nothing */ }
            SYMBOLS_TOGGLE_CODE -> {
                showingSymbols = true
                symbolsPageTwo = false
                resetWordState()
                applyKeyboardForMode()
            }
            SYMBOLS_MORE_CODE -> {
                symbolsPageTwo = true
                applyKeyboardForMode()
            }
            SYMBOLS_LESS_CODE -> {
                symbolsPageTwo = false
                applyKeyboardForMode()
            }
            ABC_RETURN_CODE -> {
                showingSymbols = false
                symbolsPageTwo = false
                resetWordState()
                applyKeyboardForMode()
            }
            else -> handleCharacter(ic, primaryCode)
        }
    }

    private fun insertHintChar(hint: String) {
        val ic = currentInputConnection ?: return
        giveKeyFeedback()
        ic.commitText(hint, 1)
        lastCommittedWasSpace = false
        capitalizeNext = false
    }

    private fun giveKeyFeedback() {
        if (SettingsStore.getBoolean(this, SettingsStore.KEY_VIBRATE_ON_KEYPRESS, false)) {
            keyboardView.performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
        }
        if (SettingsStore.getBoolean(this, SettingsStore.KEY_SOUND_ON_KEYPRESS, false)) {
            val audioManager = getSystemService(AUDIO_SERVICE) as? AudioManager
            audioManager?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD)
        }
    }

    private fun toggleShift() {
        isShifted = !isShifted
        englishKeyboardPlain.isShifted = isShifted
        englishKeyboardWithNumRow.isShifted = isShifted
        englishKeyboardWithNumRowLarge.isShifted = isShifted
        keyboardView.invalidateAllKeys()
    }

    private fun switchMode() {
        mode = mode.next()
        isShifted = false
        englishKeyboardPlain.isShifted = false
        englishKeyboardWithNumRow.isShifted = false
        englishKeyboardWithNumRowLarge.isShifted = false
        resetWordState()
        applyKeyboardForMode()
    }

    private fun handleCharacter(ic: InputConnection, primaryCode: Int) {
        var typedChar = primaryCode.toChar()
        if (isShifted && mode != InputMode.BANGLA_TRADITIONAL && !showingSymbols) {
            typedChar = typedChar.uppercaseChar()
        }

        val autoCap = SettingsStore.getBoolean(this, SettingsStore.KEY_AUTO_CAPITALIZATION, true)
        if (!showingSymbols && autoCap && capitalizeNext && mode == InputMode.ENGLISH && typedChar.isLetter()) {
            typedChar = typedChar.uppercaseChar()
            capitalizeNext = false
        } else if (typedChar.isLetter()) {
            capitalizeNext = false
        }

        if (typedChar == ' ') {
            handleSpace(ic)
        } else if (typedChar == '.' && !showingSymbols) {
            commitWordBoundary(ic, ".")
            if (autoCap) capitalizeNext = true
            lastCommittedWasSpace = false
        } else if (showingSymbols) {
            ic.commitText(typedChar.toString(), 1)
            lastCommittedWasSpace = false
        } else {
            appendToWord(ic, typedChar)
            lastCommittedWasSpace = false
        }

        if (isShifted && mode != InputMode.BANGLA_TRADITIONAL) {
            isShifted = false
            englishKeyboardPlain.isShifted = false
            englishKeyboardWithNumRow.isShifted = false
            englishKeyboardWithNumRowLarge.isShifted = false
            keyboardView.invalidateAllKeys()
        }
    }

    private fun handleSpace(ic: InputConnection) {
        if (showingSymbols) {
            ic.commitText(" ", 1)
            lastCommittedWasSpace = true
            return
        }

        val doubleSpaceTab = SettingsStore.getBoolean(this, SettingsStore.KEY_DOUBLE_SPACE_TAB, false)
        val doubleSpacePeriod = SettingsStore.getBoolean(this, SettingsStore.KEY_DOUBLE_SPACE_PERIOD, true)

        if (doubleSpaceTab && lastCommittedWasSpace && rawWordBuffer.isEmpty()) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText("\t", 1)
            lastCommittedWasSpace = false
            resetWordState()
            return
        }

        if (!doubleSpaceTab && doubleSpacePeriod && lastCommittedWasSpace && rawWordBuffer.isEmpty()) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
            capitalizeNext = SettingsStore.getBoolean(this, SettingsStore.KEY_AUTO_CAPITALIZATION, true)
            lastCommittedWasSpace = false
            resetWordState()
            return
        }

        commitWordBoundary(ic, " ")
        lastCommittedWasSpace = true
    }

    private fun appendToWord(ic: InputConnection, typedChar: Char) {
        when (mode) {
            InputMode.ENGLISH, InputMode.BANGLA_TRADITIONAL -> {
                ic.commitText(typedChar.toString(), 1)
                rawWordBuffer.append(typedChar)
                committedWordLength = rawWordBuffer.length
            }
            InputMode.BANGLA_PHONETIC -> {
                rawWordBuffer.append(typedChar)
                val newText = PhoneticEngine.transliterate(rawWordBuffer.toString())
                if (committedWordLength > 0) {
                    ic.deleteSurroundingText(committedWordLength, 0)
                }
                ic.commitText(newText, 1)
                committedWordLength = newText.length
            }
        }
        updateSuggestions()
    }

    private fun handleDelete(ic: InputConnection) {
        if (showingSymbols) {
            ic.deleteSurroundingText(1, 0)
            return
        }

        if (rawWordBuffer.isNotEmpty()) {
            rawWordBuffer.deleteCharAt(rawWordBuffer.length - 1)

            if (mode == InputMode.BANGLA_PHONETIC) {
                val newText = PhoneticEngine.transliterate(rawWordBuffer.toString())
                ic.deleteSurroundingText(committedWordLength, 0)
                if (newText.isNotEmpty()) ic.commitText(newText, 1)
                committedWordLength = newText.length
            } else {
                ic.deleteSurroundingText(1, 0)
                committedWordLength = rawWordBuffer.length
            }
            updateSuggestions()
        } else {
            ic.deleteSurroundingText(1, 0)
            clearSuggestions()
        }
        lastCommittedWasSpace = false
    }

    private fun commitWordBoundary(ic: InputConnection, boundaryText: String) {
        if (boundaryText.isNotEmpty()) {
            ic.commitText(boundaryText, 1)
        }
        resetWordState()
    }

    private fun applySuggestion(word: String) {
        if (word.isBlank()) return
        val ic = currentInputConnection ?: return
        if (committedWordLength > 0) {
            ic.deleteSurroundingText(committedWordLength, 0)
        }
        ic.commitText("$word ", 1)
        lastCommittedWasSpace = true
        resetWordState()
    }

    private fun updateSuggestions() {
        if (!SettingsStore.getBoolean(this, SettingsStore.KEY_SHOW_SUGGESTIONS, true)) return

        val isBangla = mode != InputMode.ENGLISH
        val query = if (mode == InputMode.BANGLA_PHONETIC) {
            PhoneticEngine.transliterate(rawWordBuffer.toString())
        } else {
            rawWordBuffer.toString()
        }

        var results = DictionaryProvider.suggestions(query, isBangla)

        if (SettingsStore.getBoolean(this, SettingsStore.KEY_BLOCK_OFFENSIVE_WORDS, true)) {
            val blocklist = if (isBangla) offensiveWordsBn else offensiveWordsEn
            results = results.filterNot { blocklist.contains(it.lowercase()) }
        }

        listOf(suggestion1, suggestion2, suggestion3).forEachIndexed { index, tv ->
            tv.text = results.getOrNull(index) ?: ""
        }
    }

    private fun clearSuggestions() {
        suggestion1.text = ""
        suggestion2.text = ""
        suggestion3.text = ""
    }

    private fun toggleClipboardPanel() {
        if (clipboardPanel.visibility == View.VISIBLE) hideClipboardPanel() else showClipboardPanel()
    }

    private fun showClipboardPanel() {
        refreshClipboardList()
        clipboardPanel.visibility = View.VISIBLE
        keyboardView.visibility = View.INVISIBLE
    }

    private fun hideClipboardPanel() {
        if (!::clipboardPanel.isInitialized) return
        clipboardPanel.visibility = View.GONE
        keyboardView.visibility = View.VISIBLE
    }

    private fun refreshClipboardList() {
        clipboardList.removeAllViews()
        val items = ClipboardStore.getItems(this)

        if (items.isEmpty()) {
            clipboardList.addView(TextView(this).apply {
                text = getString(R.string.clipboard_empty)
                setTextColor(resources.getColor(R.color.ime_text_secondary))
                textSize = 13f
                setPadding(dpPx(20), dpPx(12), dpPx(20), dpPx(12))
            })
            return
        }

        items.forEach { text ->
            clipboardList.addView(TextView(this).apply {
                this.text = text
                setTextColor(resources.getColor(R.color.ime_text_primary))
                textSize = 14f
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dpPx(20), dpPx(12), dpPx(20), dpPx(12))
                setOnClickListener {
                    currentInputConnection?.commitText(text, 1)
                    hideClipboardPanel()
                }
            })
            clipboardList.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                setBackgroundColor(0x22000000)
            })
        }
    }

    private fun dpPx(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onPress(primaryCode: Int) {}
    override fun onRelease(primaryCode: Int) {}
    override fun onText(text: CharSequence?) {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}

    companion object {
        const val MODE_SWITCH_CODE = -10
        const val EMOJI_PLACEHOLDER_CODE = -30
        const val SYMBOLS_TOGGLE_CODE = -20
        const val SYMBOLS_MORE_CODE = -21
        const val ABC_RETURN_CODE = -22
        const val SYMBOLS_LESS_CODE = -24
    }
}
