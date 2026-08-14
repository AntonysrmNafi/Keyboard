package com.privacykeyboard.app

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.LinearLayout
import android.widget.TextView

// Fully offline IME with English, Bangla phonetic and Bangla traditional modes,
// wired to user-configurable preferences from SettingsStore. No network permission,
// no keystroke logging, no persistence of typed text.
class PrivacyInputMethodService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: BlockVeilKeyboardView
    private lateinit var suggestionStrip: LinearLayout

    private lateinit var englishKeyboardPlain: Keyboard
    private lateinit var englishKeyboardWithNumRow: Keyboard
    private lateinit var traditionalKeyboard: Keyboard

    private lateinit var suggestion1: TextView
    private lateinit var suggestion2: TextView
    private lateinit var suggestion3: TextView

    private var mode: InputMode = InputMode.ENGLISH
    private var isShifted = false

    // Raw keys typed for the current word, drives phonetic conversion and suggestions
    private val rawWordBuffer = StringBuilder()
    // Length of text currently committed to the input field for the active word
    private var committedWordLength = 0

    // Auto-capitalization / double-space-period state
    private var capitalizeNext = true
    private var lastCommittedWasSpace = false

    private var vibrator: Vibrator? = null

    // A small, editable blocklist. Add more words as needed, one per entry.
    private val offensiveWordsEn = setOf<String>()
    private val offensiveWordsBn = setOf<String>()

    override fun onCreate() {
        super.onCreate()
        DictionaryProvider.load(this)
        vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
    }

    override fun onCreateInputView(): View {
        val view = LayoutInflater.from(this).inflate(R.layout.input_view, null)

        englishKeyboardPlain = Keyboard(this, R.xml.keys_layout_english)
        englishKeyboardWithNumRow = Keyboard(this, R.xml.keys_layout_english_numrow)
        traditionalKeyboard = Keyboard(this, R.xml.keys_layout_bangla_traditional)

        keyboardView = view.findViewById(R.id.keyboard_view)
        keyboardView.setOnKeyboardActionListener(this)
        keyboardView.onCursorSwipe = { steps -> moveCursor(steps) }

        suggestionStrip = view.findViewById(R.id.suggestion_strip)
        suggestion1 = view.findViewById(R.id.suggestion_1)
        suggestion2 = view.findViewById(R.id.suggestion_2)
        suggestion3 = view.findViewById(R.id.suggestion_3)
        listOf(suggestion1, suggestion2, suggestion3).forEach { tv ->
            tv.setOnClickListener { applySuggestion(tv.text.toString()) }
        }

        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        resetWordState()
        isShifted = false
        capitalizeNext = true
        lastCommittedWasSpace = false
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
            0 -> 48f * density // Slow, needs more drag per step
            2 -> 24f * density // Fast, needs less drag per step
            else -> 36f * density // Default
        }
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
        val useNumberRow = SettingsStore.getBoolean(this, SettingsStore.KEY_ENABLE_NUMBER_ROW, false)
        keyboardView.keyboard = when (mode) {
            InputMode.BANGLA_TRADITIONAL -> traditionalKeyboard
            else -> if (useNumberRow) englishKeyboardWithNumRow else englishKeyboardPlain
        }
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
            else -> handleCharacter(ic, primaryCode)
        }
    }

    private fun giveKeyFeedback() {
        if (SettingsStore.getBoolean(this, SettingsStore.KEY_VIBRATE_ON_KEYPRESS, false)) {
            val v = vibrator
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(12)
                }
            }
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
        keyboardView.invalidateAllKeys()
    }

    private fun switchMode() {
        mode = mode.next()
        isShifted = false
        englishKeyboardPlain.isShifted = false
        englishKeyboardWithNumRow.isShifted = false
        resetWordState()
        applyKeyboardForMode()
    }

    private fun handleCharacter(ic: InputConnection, primaryCode: Int) {
        var typedChar = primaryCode.toChar()
        if (isShifted && mode != InputMode.BANGLA_TRADITIONAL) {
            typedChar = typedChar.uppercaseChar()
        }

        // Auto-capitalization: force the first letter of a new sentence to uppercase
        val autoCap = SettingsStore.getBoolean(this, SettingsStore.KEY_AUTO_CAPITALIZATION, true)
        if (autoCap && capitalizeNext && mode == InputMode.ENGLISH && typedChar.isLetter()) {
            typedChar = typedChar.uppercaseChar()
            capitalizeNext = false
        } else if (typedChar.isLetter()) {
            capitalizeNext = false
        }

        if (typedChar == ' ') {
            handleSpace(ic)
        } else if (typedChar == '.') {
            commitWordBoundary(ic, ".")
            if (autoCap) capitalizeNext = true
            lastCommittedWasSpace = false
        } else {
            appendToWord(ic, typedChar)
            lastCommittedWasSpace = false
        }

        if (isShifted && mode != InputMode.BANGLA_TRADITIONAL) {
            isShifted = false
            englishKeyboardPlain.isShifted = false
            englishKeyboardWithNumRow.isShifted = false
            keyboardView.invalidateAllKeys()
        }
    }

    private fun handleSpace(ic: InputConnection) {
        val doubleSpaceTab = SettingsStore.getBoolean(this, SettingsStore.KEY_DOUBLE_SPACE_TAB, false)
        val doubleSpacePeriod = SettingsStore.getBoolean(this, SettingsStore.KEY_DOUBLE_SPACE_PERIOD, true)

        if (doubleSpaceTab && lastCommittedWasSpace && rawWordBuffer.isEmpty()) {
            // Replace the previous lone space with a tab character
            ic.deleteSurroundingText(1, 0)
            ic.commitText("\t", 1)
            lastCommittedWasSpace = false
            resetWordState()
            return
        }

        if (!doubleSpaceTab && doubleSpacePeriod && lastCommittedWasSpace && rawWordBuffer.isEmpty()) {
            // Replace the previous lone space with ". "
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
                // Comma is a hasant trigger inside the buffer, not committed on its own.
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

    override fun onPress(primaryCode: Int) {}
    override fun onRelease(primaryCode: Int) {}
    override fun onText(text: CharSequence?) {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}

    companion object {
        const val MODE_SWITCH_CODE = -10
    }
}
