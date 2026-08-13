package com.privacykeyboard.app

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.TextView

// Fully offline IME with English, Bangla phonetic and Bangla traditional modes.
// No network permission, no keystroke logging, no persistence of typed text.
class PrivacyInputMethodService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: KeyboardView
    private lateinit var englishKeyboard: Keyboard
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

    override fun onCreate() {
        super.onCreate()
        DictionaryProvider.load(this)
    }

    override fun onCreateInputView(): View {
        val view = LayoutInflater.from(this).inflate(R.layout.input_view, null)

        englishKeyboard = Keyboard(this, R.xml.keys_layout_english)
        traditionalKeyboard = Keyboard(this, R.xml.keys_layout_bangla_traditional)

        keyboardView = view.findViewById(R.id.keyboard_view)
        keyboardView.keyboard = englishKeyboard
        keyboardView.setOnKeyboardActionListener(this)

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
        applyKeyboardForMode()
    }

    private fun applyKeyboardForMode() {
        keyboardView.keyboard = if (mode == InputMode.BANGLA_TRADITIONAL) traditionalKeyboard else englishKeyboard
        keyboardView.invalidateAllKeys()
    }

    private fun resetWordState() {
        rawWordBuffer.clear()
        committedWordLength = 0
        clearSuggestions()
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val ic = currentInputConnection ?: return

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

    private fun toggleShift() {
        isShifted = !isShifted
        englishKeyboard.isShifted = isShifted
        keyboardView.invalidateAllKeys()
    }

    private fun switchMode() {
        mode = mode.next()
        isShifted = false
        englishKeyboard.isShifted = false
        resetWordState()
        applyKeyboardForMode()
    }

    private fun handleCharacter(ic: InputConnection, primaryCode: Int) {
        var typedChar = primaryCode.toChar()
        if (isShifted && mode != InputMode.BANGLA_TRADITIONAL) {
            typedChar = typedChar.uppercaseChar()
        }

        if (typedChar == ' ' || typedChar == '.') {
            commitWordBoundary(ic, typedChar.toString())
        } else {
            appendToWord(ic, typedChar)
        }

        if (isShifted && mode != InputMode.BANGLA_TRADITIONAL) {
            isShifted = false
            englishKeyboard.isShifted = false
            keyboardView.invalidateAllKeys()
        }
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
        resetWordState()
    }

    private fun updateSuggestions() {
        val isBangla = mode != InputMode.ENGLISH
        val query = if (mode == InputMode.BANGLA_PHONETIC) {
            PhoneticEngine.transliterate(rawWordBuffer.toString())
        } else {
            rawWordBuffer.toString()
        }
        val results = DictionaryProvider.suggestions(query, isBangla)
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
