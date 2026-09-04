package com.blockveil.keyboard

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
class BlockVeilInputMethodService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: BlockVeilKeyboardView
    private lateinit var keyboardFrame: android.widget.FrameLayout
    private var keyboardFrameBaseHeightPx: Int = 0
    private lateinit var actionToast: LinearLayout
    private lateinit var actionToastText: TextView
    private val actionToastHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var actionToastHideRunnable: Runnable? = null
    private lateinit var suggestionStrip: LinearLayout
    private lateinit var textToolButton: TextView
    private lateinit var toolbarIconsGroup: LinearLayout
    private lateinit var suggestionsGroup: LinearLayout
    private lateinit var clipboardButton: android.widget.ImageView
    private lateinit var clipboardPanel: LinearLayout
    private lateinit var clipboardList: LinearLayout
    private lateinit var icon123Button: View
    private lateinit var iconEmojiButton: View
    private lateinit var emojiPanel: LinearLayout
    private lateinit var emojiCategoryRow: LinearLayout

    // When true, the icon toolbar stays visible even while a word is being composed
    // (the user tapped the T button to peek at it). Resets on the next keystroke.
    private var forceToolbar = false

    private lateinit var englishKeyboardPlain: Keyboard
    private lateinit var englishKeyboardWithNumRow: Keyboard
    private lateinit var englishKeyboardWithNumRowLarge: Keyboard
    private lateinit var traditionalKeyboard: Keyboard
    // Point 3: প্রভাত has its own dedicated file (keys_layout_bangla_traditional.xml,
    // currently a byte-for-byte copy of English's numrow layout) - kept as a
    // separate file rather than reusing English's object directly, so its key
    // labels can later be swapped to actual Bengali characters without
    // touching English at all.
    private lateinit var symbolsKeyboard1: Keyboard
    private lateinit var symbolsKeyboard2: Keyboard
    // Point: Bangla modes (phonetic + traditional) now get their own symbols
    // pages instead of sharing English's, so they can be edited independently.
    private lateinit var banglaSymbolsKeyboard1: Keyboard
    private lateinit var banglaSymbolsKeyboard2: Keyboard
    private lateinit var numpadKeyboard: Keyboard

    private lateinit var suggestion1: TextView
    private lateinit var suggestion2: TextView
    private lateinit var suggestion3: TextView

    private var mode: InputMode = InputMode.ENGLISH
    private var isShifted = false

    // ?123 symbols overlay state. Independent from the letter mode above,
    // exactly like Gboard: pressing ABC returns to whichever letter mode was active.
    private var showingSymbols = false
    private var symbolsPageTwo = false
    // Calculator-style numpad, opened from the top-bar 123 icon.
    private var showingNumpad = false
    private var numpadBangla = false
    private val latinToBanglaDigit = mapOf(
        '0' to '\u09E6', '1' to '\u09E7', '2' to '\u09E8', '3' to '\u09E9', '4' to '\u09EA',
        '5' to '\u09EB', '6' to '\u09EC', '7' to '\u09ED', '8' to '\u09EE', '9' to '\u09EF'
    )

    // Raw keys typed for the current word, drives phonetic conversion and suggestions
    private val rawWordBuffer = StringBuilder()
    // Length of text currently committed to the input field for the active word
    private var committedWordLength = 0

    // Auto-capitalization / double-space-period state
    private var capitalizeNext = true
    // Point 4: tracks whether the last character actually committed to the text
    // field was a "." - used so capitalization kicks in only once a SPACE is
    // typed after the period, not immediately after the period itself (so
    // things like "3.5" or "e.g." don't get wrongly capitalized mid-word).
    private var lastCommittedCharWasPeriod = false
    private var lastCommittedWasSpace = false

    // Long-press hints on the top letter row (q..p -> 1..0), like a lightweight number row.
    private val topRowHints = mapOf(
        113 to "1", 119 to "2", 101 to "3", 114 to "4", 116 to "5",
        121 to "6", 117 to "7", 105 to "8", 111 to "9", 112 to "0"
    )

    // Problem Row1: shows a symbol preview (matching what's on the OTHER
    // symbols page) instead of plain repeated numbers.
    private val symbolsNumberHints = mapOf(
        49 to "~", 50 to "`", 51 to "|", 52 to "\u2022", 53 to "\u221A",
        54 to "\u03C0", 55 to "\u00F7", 56 to "\u00D7", 57 to "{", 48 to "}"
    )

    // Row3: small top-right hints on specific keys, matching the reference.
    private val symbolsRow3Hints = mapOf(
        2547 to "$", 42 to "\u2605", 40 to "<", 41 to ">"
    )

    // Row4: small hint on the ":" key.
    private val symbolsRow4Hints = mapOf(58 to "3")

    private var clipboardManager: ClipboardManager? = null
    private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null

    override fun onCreate() {
        super.onCreate()
        DictionaryProvider.load(this)

        clipboardManager = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            val clip = clipboardManager?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val item = clip.getItemAt(0)
                val text = item.text?.toString()
                if (!text.isNullOrBlank()) {
                    ClipboardStore.addTextItem(this, text)
                } else if (item.uri != null) {
                    storeImageFromUri(item.uri)
                }
            }
        }
        clipboardListener = listener
        clipboardManager?.addPrimaryClipChangedListener(listener)
    }

    // Point 1: automatic screenshot-detection (MediaStore ContentObserver + the
    // storage permission it required) has been removed entirely per request -
    // no permission prompt will be shown. This still captures an image the
    // normal way: whenever something is explicitly copied to the system
    // clipboard (e.g. long-press an image -> Copy, or Share -> Copy).
    private fun storeImageFromUri(uri: android.net.Uri) {
        try {
            val bitmap = android.provider.MediaStore.Images.Media.getBitmap(contentResolver, uri)
            val outputStream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, outputStream)
            val base64 = android.util.Base64.encodeToString(
                outputStream.toByteArray(),
                android.util.Base64.DEFAULT
            )
            bitmap.recycle()
            ClipboardStore.addImageItem(this, base64)
        } catch (e: Exception) {
            // Silent fail - not all URIs can be loaded as images
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // The Keyboard objects (englishKeyboardPlain etc.) compute their key
        // positions from the screen width at the moment they're constructed.
        // On rotation (or any other configuration change) that width can
        // change, so the cached view and all its Keyboard objects need to be
        // rebuilt fresh rather than reused with now-stale measurements. The
        // parent-detach fix in onCreateInputView() means this is always safe
        // even if Android calls it again before this takes effect.
        cachedInputView = null
    }

    override fun onDestroy() {
        clipboardListener?.let { clipboardManager?.removePrimaryClipChangedListener(it) }
        actionToastHideRunnable?.let { actionToastHandler.removeCallbacks(it) }
        cachedInputView = null
        super.onDestroy()
    }

    private var cachedInputView: View? = null

    override fun onCreateInputView(): View {
        // Point 3: Dual keyboard fix - reuse the SAME view instance instead of
        // recreating it every time. CRASH FIX: Android calls
        // onCreateInputView() again on any configuration change (rotation,
        // dark mode toggle, font size change, etc.) and immediately tries to
        // attach whatever we return into ITS OWN container. Since our cached
        // view was still attached to the PREVIOUS container from last time,
        // ViewGroup.addView() threw "The specified child already has a
        // parent" - it must be detached from its old parent first.
        cachedInputView?.let { existing ->
            (existing.parent as? android.view.ViewGroup)?.removeView(existing)
            return existing
        }

        val view = LayoutInflater.from(this).inflate(R.layout.input_view, null)
        cachedInputView = view

        englishKeyboardPlain = Keyboard(this, R.xml.keys_layout_english)
        englishKeyboardWithNumRow = Keyboard(this, R.xml.keys_layout_english_numrow)
        englishKeyboardWithNumRowLarge = Keyboard(this, R.xml.keys_layout_english_numrow_large)
        traditionalKeyboard = Keyboard(this, R.xml.keys_layout_bangla_traditional)
        symbolsKeyboard1 = Keyboard(this, R.xml.keys_layout_symbols1)
        symbolsKeyboard2 = Keyboard(this, R.xml.keys_layout_symbols2)
        banglaSymbolsKeyboard1 = Keyboard(this, R.xml.keys_layout_bangla_symbols1)
        banglaSymbolsKeyboard2 = Keyboard(this, R.xml.keys_layout_bangla_symbols2)
        numpadKeyboard = Keyboard(this, R.xml.keys_layout_numpad)

        keyboardView = view.findViewById(R.id.keyboard_view)
        keyboardView.setOnKeyboardActionListener(this)
        keyboardView.onSpaceSwipe = { switchMode() }
        keyboardView.hintMap = topRowHints
        keyboardView.onHintLongPress = { hint -> insertHintChar(hint) }
        keyboardView.actionLongPressCodes = setOf(COPY_KEY_CODE, CUT_KEY_CODE, PASTE_KEY_CODE)
        keyboardView.onActionLongPress = { code -> handleClipboardAction(code) }
        getDrawable(R.drawable.sentiment_satisfied_24)?.mutate()?.let { drawable ->
            drawable.setTint(0xFFD8E8C2.toInt())
            val enterDrawable = getDrawable(R.drawable.keyboard_return_24)?.mutate()?.apply {
                setTint(0xFFD8E8C2.toInt())
            }
            keyboardView.iconDrawables = buildMap {
                put(EMOJI_PLACEHOLDER_CODE, drawable)
                if (enterDrawable != null) put(-4, enterDrawable)
                getDrawable(R.drawable.backspace_24)?.mutate()?.let { backspaceDrawable ->
                    backspaceDrawable.setTint(0xFF0B5D48.toInt())
                    put(-5, backspaceDrawable)
                }
                // Point 1: space-shortcut key on the symbols page - now a
                // dark function key, so it uses the light icon color.
                getDrawable(R.drawable.horizontal_align_right_24)?.mutate()?.let { shortcutDrawable ->
                    shortcutDrawable.setTint(0xFFD8E8C2.toInt())
                    put(SPACE_SHORTCUT_CODE, shortcutDrawable)
                }
            }
        }
        // Shift icon color matches the rest of the dark-key icon set.
        keyboardView.shiftIconRest = getDrawable(R.drawable.arrow_shape_up_24)?.mutate()?.apply {
            setTint(0xFFD8E8C2.toInt())
        }
        keyboardView.shiftIconActive = getDrawable(R.drawable.arrow_shape_up_24_filled)?.mutate()?.apply {
            setTint(0xFFD8E8C2.toInt())
        }

        // Problem 8 / white gap: the system's IME switcher strip (chevron +
        // keyboard-switch icon) at the very bottom, and any unclaimed space
        // Android reserves for the IME window beyond our own content, both
        // default to a plain background unless the window explicitly paints
        // over them with our own theme color.
        window?.window?.let { imeWindow ->
            imeWindow.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0xFFF6F9F3.toInt()))
            imeWindow.navigationBarColor = 0xFFF6F9F3.toInt()
            if (android.os.Build.VERSION.SDK_INT >= 27) {
                var flags = imeWindow.decorView.systemUiVisibility
                flags = flags or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                imeWindow.decorView.systemUiVisibility = flags
            }
        }

        suggestionStrip = view.findViewById(R.id.suggestion_strip)
        keyboardFrame = view.findViewById(R.id.keyboard_frame)
        keyboardFrameBaseHeightPx = keyboardFrame.layoutParams.height
        actionToast = view.findViewById(R.id.action_toast)
        actionToastText = view.findViewById(R.id.action_toast_text)
        textToolButton = view.findViewById(R.id.icon_text_tool)
        toolbarIconsGroup = view.findViewById(R.id.toolbar_icons_group)
        suggestionsGroup = view.findViewById(R.id.suggestions_group)
        textToolButton.setOnClickListener {
            forceToolbar = true
            var changed = false
            if (showingNumpad) { showingNumpad = false; changed = true }
            if (showingSymbols) { showingSymbols = false; symbolsPageTwo = false; changed = true }
            if (clipboardPanel.visibility == View.VISIBLE) { hideClipboardPanel() }
            if (emojiPanel.visibility == View.VISIBLE) { hideEmojiPanel() }
            if (changed) {
                resetWordState()
                applyKeyboardForMode()
            }
            updateTopStripVisibility()
        }

        suggestion1 = view.findViewById(R.id.suggestion_1)
        suggestion2 = view.findViewById(R.id.suggestion_2)
        suggestion3 = view.findViewById(R.id.suggestion_3)
        listOf(suggestion1, suggestion2, suggestion3).forEach { tv ->
            tv.setOnClickListener { applySuggestion(tv.text.toString()) }
        }

        clipboardButton = view.findViewById(R.id.clipboard_button)
        clipboardButton.setOnClickListener { toggleClipboardPanel() }

        view.findViewById<android.widget.ImageView>(R.id.settings_header_button).setOnClickListener {
            // Point 3: explicitly hide the IME window before launching Settings,
            // otherwise the keyboard window stays floating on top of the newly
            // opened Settings screen since starting a new task doesn't dismiss it.
            requestHideSelf(0)
            // Starting a non-launcher Activity (SettingsMenuActivity) directly as
            // the ROOT of a brand new task via FLAG_ACTIVITY_NEW_TASK from a
            // Service context left that task's back stack in a broken state -
            // navigating to almost any sub-screen from there exited the whole
            // app. MainActivity is the app's actual declared launcher activity,
            // so starting a new task there first (the normal, well-tested case)
            // and then pushing Settings on top of it as an ordinary in-app
            // navigation is the safe way to do this.
            val intent = android.content.Intent(this, MainActivity::class.java)
            intent.putExtra(MainActivity.EXTRA_OPEN_SETTINGS, true)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }

        icon123Button = view.findViewById(R.id.icon_123_top)
        icon123Button.setOnClickListener {
            // Point 4: must hide clipboard/emoji panel first, otherwise keyboardView
            // stays invisible underneath even though the numpad keyboard is applied
            hideClipboardPanel()
            hideEmojiPanel()
            showingNumpad = true
            showingSymbols = false
            numpadBangla = false
            updateNumpadDigitLabels()
            resetWordState()
            applyKeyboardForMode()
        }

        iconEmojiButton = view.findViewById(R.id.icon_emoji_top)
        iconEmojiButton.setOnClickListener { showEmojiPanel() }

        emojiPanel = view.findViewById(R.id.emoji_panel)
        emojiCategoryRow = view.findViewById(R.id.emoji_category_row)
        buildEmojiCategoryIcons()
        view.findViewById<TextView>(R.id.emoji_abc_button).setOnClickListener {
            // Point 1.3: if the emoji panel was opened while on the 123/symbols
            // view, those flags were still true underneath - just hiding the
            // panel brought symbols/numpad back instead of the normal ABC
            // keyboard. Explicitly reset back to the letter keyboard.
            showingSymbols = false
            showingNumpad = false
            symbolsPageTwo = false
            hideEmojiPanel()
            applyKeyboardForMode()
        }

        clipboardPanel = view.findViewById(R.id.clipboard_panel)
        clipboardList = view.findViewById(R.id.clipboard_list)
        view.findViewById<android.widget.ImageView>(R.id.clipboard_close_button).setOnClickListener { hideClipboardPanel() }
        view.findViewById<TextView>(R.id.clipboard_clear_button).setOnClickListener {
            ClipboardStore.clear(this)
            refreshClipboardList()
        }

        return view
    }

    // Point 3: some devices render a separate "extract view" (fullscreen editing
    // strip) above the normal keyboard in certain apps/orientations, which can
    // look exactly like "two keyboards stacked". Force this off unconditionally -
    // this custom keyboard never needs it.
    // Point 4.1/4.2: when enabled, the volume buttons move the text cursor
    // left/right instead of changing the device volume, while a text field is
    // focused. By default this does NOT apply during calls or audio/video
    // playback (volume behaves normally then) unless the second toggle is
    // also on.
    private fun shouldHandleVolumeKeyAsCursor(): Boolean {
        if (!isInputViewShown) return false
        if (!SettingsStore.getBoolean(this, SettingsStore.KEY_VOLUME_KEY_CURSOR, true)) return false
        val audioManager = getSystemService(AUDIO_SERVICE) as? AudioManager
        val inCallOrMedia = audioManager?.mode == AudioManager.MODE_IN_CALL ||
            audioManager?.mode == AudioManager.MODE_IN_COMMUNICATION ||
            audioManager?.isMusicActive == true
        if (!inCallOrMedia) return true
        return SettingsStore.getBoolean(this, SettingsStore.KEY_VOLUME_KEY_CURSOR_MEDIA, false)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (shouldHandleVolumeKeyAsCursor()) {
                val ic = currentInputConnection
                if (ic != null) {
                    val moveCode = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                        KeyEvent.KEYCODE_DPAD_RIGHT
                    } else {
                        KeyEvent.KEYCODE_DPAD_LEFT
                    }
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, moveCode))
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, moveCode))
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (shouldHandleVolumeKeyAsCursor()) {
                // Already handled the cursor move in onKeyDown - just swallow
                // the key-up too so the system never sees it as a volume press.
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Point 1: Dual keyboard issue fixed - view is now cached and reused, 
        // no need for cleanup
        resetWordState()
        capitalizeNext = true
        syncShiftedDisplay(true)
        lastCommittedWasSpace = false
        lastCommittedCharWasPeriod = false
        showingSymbols = false
        symbolsPageTwo = false
        showingNumpad = false
        numpadBangla = false
        applyFieldTypeAutoDetect(info)
        updateNumpadDigitLabels()
        hideClipboardPanel()
        hideEmojiPanel()
        applySettings()
        applyKeyboardForMode()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        // Point 3: make sure no leftover panel/state carries into the next time
        // the keyboard is shown (e.g. right after returning from an Activity we
        // launched, like Settings or Dictionary Unlock).
        hideClipboardPanel()
        hideEmojiPanel()
    }

    // Looks at what kind of field is focused (PIN box, phone number, OTP, date, etc.)
    // and jumps straight to the numeric keypad, the same way Gboard/Ridmik do. Only
    // overrides the very first keyboard shown for this field; the user can still switch
    // back to ABC manually and that choice is respected for the rest of the session.
    private fun applyFieldTypeAutoDetect(info: EditorInfo?) {
        val inputType = info?.inputType ?: EditorInfo.TYPE_NULL
        val fieldClass = inputType and EditorInfo.TYPE_MASK_CLASS
        val isNumericField = fieldClass == EditorInfo.TYPE_CLASS_NUMBER ||
            fieldClass == EditorInfo.TYPE_CLASS_PHONE ||
            fieldClass == EditorInfo.TYPE_CLASS_DATETIME

        if (isNumericField) {
            showingNumpad = true
            numpadBangla = false
        }
    }

    private fun applySettings() {
        // isPreviewEnabled is force-disabled inside BlockVeilKeyboardView itself (crash safety,
        // see its init block). Our own pressed-key highlight color already gives feedback,
        // so the "Popup on keypress" setting no longer needs to toggle the stock popup.
        // Point 6: previously this hid the ENTIRE top strip (including the T/emoji/
        // clipboard/123/settings toolbar icons) when suggestions were turned off.
        // The strip row itself must always stay visible; only the suggestion words
        // next to the T icon should be affected, handled in updateTopStripVisibility().
        suggestionStrip.visibility = View.VISIBLE
        updateTopStripVisibility()

        keyboardView.spaceSwipeEnabled = SettingsStore.getBoolean(this, SettingsStore.KEY_SPACE_CURSOR_ENABLED, true)
        val sensitivityLevel = SettingsStore.getInt(this, SettingsStore.KEY_SPACE_CURSOR_SPEED, 1)
        val density = resources.displayMetrics.density
        keyboardView.spaceSwipeThreshold = when (sensitivityLevel) {
            0 -> 48f * density // Slow, needs more drag before switching
            2 -> 24f * density // Fast, needs less drag before switching
            else -> 36f * density // Default
        }

        applySizingAndOneHanded()
    }

    // Enter key always shows the ic_enter icon now (set in onCreateInputView),
    // matching the reference app's style, so no per-setting label switch is needed here.

    private fun applySizingAndOneHanded() {
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
        // Point 2: pivotX depending on keyboardView.width only makes sense once
        // the view has actually been measured - guard against 0 (not yet laid
        // out) so a stale/zero pivot never causes a lopsided scale on the very
        // first frame after a keyboard switch.
        keyboardView.pivotX = if (oneHandedMode == 2 && keyboardView.width > 0) keyboardView.width.toFloat() else 0f
        keyboardView.scaleX = widthPercent / 100f
    }

    private fun updateTopStripVisibility() {
        if (!::toolbarIconsGroup.isInitialized) return
        // Point 6: if "Show Suggestions" is turned off, always keep the toolbar
        // icons visible and never switch over to the suggestion words - the strip
        // itself is unaffected, only which of these two groups it displays.
        val showSuggestions = SettingsStore.getBoolean(this, SettingsStore.KEY_SHOW_SUGGESTIONS, true)
        val showToolbar = !showSuggestions || rawWordBuffer.isEmpty() || forceToolbar
        toolbarIconsGroup.visibility = if (showToolbar) View.VISIBLE else View.GONE
        suggestionsGroup.visibility = if (showToolbar) View.GONE else View.VISIBLE
    }

    private fun applyKeyboardForMode() {
        val useNumberRow = SettingsStore.getBoolean(this, SettingsStore.KEY_ENABLE_NUMBER_ROW, true)
        val useLarge = SettingsStore.getBoolean(this, SettingsStore.KEY_LARGE_NUMBER_ROW, false)

        keyboardView.keyboard = when {
            showingNumpad -> numpadKeyboard
            showingSymbols && symbolsPageTwo && mode != InputMode.ENGLISH -> banglaSymbolsKeyboard2
            showingSymbols && symbolsPageTwo -> symbolsKeyboard2
            showingSymbols && mode != InputMode.ENGLISH -> banglaSymbolsKeyboard1
            showingSymbols -> symbolsKeyboard1
            mode == InputMode.BANGLA_TRADITIONAL -> traditionalKeyboard
            useNumberRow && useLarge -> englishKeyboardWithNumRowLarge
            useNumberRow -> englishKeyboardWithNumRow
            else -> englishKeyboardPlain
        }

        val hideHints = SettingsStore.getBoolean(this, SettingsStore.KEY_HIDE_LONG_PRESS_HINTS, false)
        keyboardView.hintMap = if (showingSymbols) {
            symbolsNumberHints + symbolsRow3Hints + symbolsRow4Hints
        } else {
            topRowHints
        }
        keyboardView.hintsEnabled = !hideHints && !showingNumpad &&
            (showingSymbols || (!useNumberRow && mode != InputMode.BANGLA_TRADITIONAL))

        val spaceLabel = when {
            mode == InputMode.ENGLISH -> "\u25C0 English \u25B6"
            mode == InputMode.BANGLA_PHONETIC -> "\u25C0 বাংলা ফোনেটিক \u25B6"
            else -> "\u25C0 প্রভাত \u25B6"
        }
        // Problem 1+4: symbols1.xml has TWO keys with codes=32 - a small "space
        // shortcut" icon key and the actual full-width space bar. firstOrNull()
        // was matching the shortcut key by mistake (it's declared first in the
        // XML), so the language label ended up on the wrong key while the real
        // space bar was stuck showing literal "space" even outside the symbols
        // page. The real space bar is always the last one declared.
        keyboardView.keyboard?.keys?.lastOrNull { it.codes.firstOrNull() == 32 }?.label = spaceLabel

        // Point 1: invalidateAllKeys() only triggers a redraw, not a re-measure.
        // Force a fresh layout pass whenever the active keyboard object changes so
        // key positions are always recalculated against the current view width.
        keyboardView.requestLayout()
        keyboardView.invalidateAllKeys()
    }

    // Point 1: fires whenever the cursor/selection changes in the connected
    // app, including an external selection made via the system's own
    // long-press-to-select. Our own word-buffer tracking has no idea about
    // that kind of selection, so without this it could end up deleting the
    // wrong range on the next keypress instead of letting the normal
    // "typing replaces the selection" behavior happen. Resetting our
    // internal buffer whenever the change wasn't caused by our own typing
    // keeps the two in sync.
    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // A real selection (start != end) or a jump we didn't cause ourselves
        // means external state changed - drop our own buffered word so the
        // very next key acts on the CURRENT actual text/selection instead of
        // our stale internal assumptions.
        if (newSelStart != newSelEnd || rawWordBuffer.isNotEmpty() && newSelEnd != oldSelEnd + 1 && newSelEnd != oldSelEnd - 1) {
            resetWordState()
        }
    }

    private fun resetWordState() {
        rawWordBuffer.clear()
        committedWordLength = 0
        clearSuggestions()
        forceToolbar = false
        if (::toolbarIconsGroup.isInitialized) updateTopStripVisibility()
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        try {
            dispatchKey(primaryCode)
        } catch (t: Throwable) {
            android.util.Log.e("BlockVeilIME", "onKey($primaryCode) failed", t)
        }
    }

    private fun dispatchKey(primaryCode: Int) {
        val ic = currentInputConnection ?: return
        giveKeyFeedback()

        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> handleDelete(ic)
            Keyboard.KEYCODE_SHIFT -> toggleShift()
            Keyboard.KEYCODE_DONE -> {
                commitWordBoundary(ic, "")
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                // Point 4: capitalize the first character of the new line.
                if (SettingsStore.getBoolean(this, SettingsStore.KEY_AUTO_CAPITALIZATION, true)) {
                    capitalizeNext = true
                    syncShiftedDisplay(true)
                }
                lastCommittedCharWasPeriod = false
            }
            MODE_SWITCH_CODE -> switchMode()
            EMOJI_PLACEHOLDER_CODE -> showEmojiPanel()
            BlockVeilKeyboardView.SPACER_KEY_CODE -> { /* Decorative spacer, no action */ }
            SPACE_SHORTCUT_CODE -> handleCharacter(ic, 32)
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
                showingNumpad = false
                resetWordState()
                applyKeyboardForMode()
            }
            NUMPAD_ABC_CODE -> {
                showingNumpad = false
                mode = InputMode.ENGLISH
                isShifted = false
                resetWordState()
                applyKeyboardForMode()
            }
            NUMPAD_BANGLA_TOGGLE_CODE -> {
                numpadBangla = !numpadBangla
                updateNumpadDigitLabels()
                keyboardView.invalidateAllKeys()
            }
            else -> handleCharacter(ic, primaryCode)
        }
    }

    private fun insertHintChar(hint: String) {
        val ic = currentInputConnection ?: return
        giveKeyFeedback()
        ic.commitText(hint, 1)
        lastCommittedWasSpace = false
        if (capitalizeNext) {
            capitalizeNext = false
            if (isShifted) syncShiftedDisplay(false)
        }
    }

    // Point: replaces the plain system Toast (which can't reliably show a
    // custom icon on modern Android, especially from a background/IME
    // process) with our own small pill-shaped popup drawn inside the
    // keyboard's own view, showing the app logo next to the message -
    // matching the look of other keyboards' "Text copied" style popups.
    private fun showActionToast(message: String) {
        if (!::actionToast.isInitialized) return
        actionToastText.text = message
        actionToastHideRunnable?.let { actionToastHandler.removeCallbacks(it) }
        actionToast.visibility = View.VISIBLE
        actionToast.alpha = 1f
        val hideRunnable = Runnable {
            actionToast.animate().alpha(0f).setDuration(150).withEndAction {
                actionToast.visibility = View.GONE
            }.start()
        }
        actionToastHideRunnable = hideRunnable
        actionToastHandler.postDelayed(hideRunnable, 1200)
    }

    private fun handleClipboardAction(code: Int) {
        val ic = currentInputConnection ?: return
        giveKeyFeedback()
        when (code) {
            COPY_KEY_CODE -> {
                ic.performContextMenuAction(android.R.id.selectAll)
                ic.performContextMenuAction(android.R.id.copy)
                showActionToast("Text Copied")
            }
            CUT_KEY_CODE -> {
                ic.performContextMenuAction(android.R.id.selectAll)
                ic.performContextMenuAction(android.R.id.cut)
                resetWordState()
                showActionToast("Text Cut")
            }
            PASTE_KEY_CODE -> {
                ic.performContextMenuAction(android.R.id.paste)
                resetWordState()
                showActionToast("Text Pasted")
            }
        }
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

    // Point 1: keeps the keycap display's uppercase/lowercase preview in sync
    // with isShifted across every English Keyboard object.
    private fun syncShiftedDisplay(shifted: Boolean) {
        isShifted = shifted
        englishKeyboardPlain.isShifted = shifted
        englishKeyboardWithNumRow.isShifted = shifted
        englishKeyboardWithNumRowLarge.isShifted = shifted
        if (::keyboardView.isInitialized) keyboardView.invalidateAllKeys()
    }

    private fun toggleShift() {
        syncShiftedDisplay(!isShifted)
        // Point 1: if the keyboard was showing uppercase because of pending
        // auto-capitalization and the user manually taps Shift to cancel that
        // (turning the preview back to lowercase), the next letter should
        // actually type lowercase too - not get force-capitalized anyway.
        if (!isShifted) {
            capitalizeNext = false
        }
    }

    private fun switchMode() {
        mode = mode.next()
        syncShiftedDisplay(false)
        resetWordState()
        applyKeyboardForMode()
    }

    private fun handleCharacter(ic: InputConnection, primaryCode: Int) {
        var typedChar = primaryCode.toChar()
        val directCommitMode = showingSymbols || showingNumpad

        if (isShifted && mode != InputMode.BANGLA_TRADITIONAL && !directCommitMode) {
            typedChar = typedChar.uppercaseChar()
        }

        val autoCap = SettingsStore.getBoolean(this, SettingsStore.KEY_AUTO_CAPITALIZATION, true)
        if (!directCommitMode && autoCap && capitalizeNext && mode == InputMode.ENGLISH && typedChar.isLetter()) {
            typedChar = typedChar.uppercaseChar()
            capitalizeNext = false
            // Point 1: the pending auto-cap has now been used up on this
            // letter - drop the keyboard's uppercase preview back to normal.
            if (isShifted) syncShiftedDisplay(false)
        } else if (typedChar.isLetter()) {
            capitalizeNext = false
        }

        if (typedChar == ' ') {
            handleSpace(ic)
            return
        }

        // Point 0: typing ".", ",", ";", "!" or "?" automatically adds a
        // trailing space right after it too, and starts the next word
        // capitalized - so you don't need to manually press space after
        // ending a sentence or a clause. Works from the symbols page too
        // (where ; and ! live), but not from the numpad (its "." is a
        // decimal point, not sentence punctuation).
        if (!showingNumpad && (typedChar == '.' || typedChar == ',' || typedChar == ';' ||
                typedChar == '!' || typedChar == '?')
        ) {
            if (rawWordBuffer.isNotEmpty()) {
                commitWordBoundary(ic, "")
            }
            ic.commitText("$typedChar ", 1)
            resetWordState()
            lastCommittedWasSpace = true
            lastCommittedCharWasPeriod = false
            if (autoCap) {
                capitalizeNext = true
                syncShiftedDisplay(true)
            }
            return
        }

        if (showingNumpad && numpadBangla && typedChar in latinToBanglaDigit.keys) {
            ic.commitText(latinToBanglaDigit[typedChar].toString(), 1)
            lastCommittedWasSpace = false
        } else if (directCommitMode) {
            ic.commitText(typedChar.toString(), 1)
            lastCommittedWasSpace = false
        } else {
            appendToWord(ic, typedChar)
            lastCommittedWasSpace = false
        }

        if (isShifted && mode != InputMode.BANGLA_TRADITIONAL) {
            syncShiftedDisplay(false)
        }
    }

    private fun updateNumpadDigitLabels() {
        if (!::numpadKeyboard.isInitialized) return
        numpadKeyboard.keys.forEach { key ->
            val code = key.codes.firstOrNull() ?: return@forEach
            if (code in 48..57) {
                val latinDigit = code.toChar()
                key.label = if (numpadBangla) latinToBanglaDigit[latinDigit].toString() else latinDigit.toString()
            } else if (code == NUMPAD_BANGLA_TOGGLE_CODE) {
                // Shows what tapping it will switch TO, not the current state.
                key.label = if (numpadBangla) "123" else "\u09E7\u09E8\u09E9"
            }
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
            val autoCapOn = SettingsStore.getBoolean(this, SettingsStore.KEY_AUTO_CAPITALIZATION, true)
            capitalizeNext = autoCapOn
            if (autoCapOn) syncShiftedDisplay(true)
            lastCommittedWasSpace = false
            resetWordState()
            return
        }

        commitWordBoundary(ic, " ")
        lastCommittedWasSpace = true
        // Point 4: capitalize the next character only now - once a space has
        // actually followed the period, not immediately after the period itself.
        if (lastCommittedCharWasPeriod && SettingsStore.getBoolean(this, SettingsStore.KEY_AUTO_CAPITALIZATION, true)) {
            capitalizeNext = true
            syncShiftedDisplay(true)
        }
        lastCommittedCharWasPeriod = false
    }

    private fun appendToWord(ic: InputConnection, typedChar: Char) {
        forceToolbar = false
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
        updateTopStripVisibility()
    }

    private fun handleDelete(ic: InputConnection) {
        if (showingSymbols || showingNumpad) {
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
        updateTopStripVisibility()
        lastCommittedWasSpace = false
        lastCommittedCharWasPeriod = false
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

    private fun toggleClipboardPanel() {
        if (clipboardPanel.visibility == View.VISIBLE) hideClipboardPanel() else showClipboardPanel()
    }

    private fun showClipboardPanel() {
        hideEmojiPanel()
        refreshClipboardList()
        clipboardPanel.visibility = View.VISIBLE
        keyboardView.visibility = View.INVISIBLE
    }

    private fun hideClipboardPanel() {
        if (!::clipboardPanel.isInitialized) return
        clipboardPanel.visibility = View.GONE
        keyboardView.visibility = View.VISIBLE
    }

    private fun showEmojiPanel() {
        hideClipboardPanel()
        emojiPanel.visibility = View.VISIBLE
        keyboardView.visibility = View.INVISIBLE
        // Point: instead of just hiding the strip (which shrinks the whole
        // window and, since the window is bottom-anchored, pushes the
        // visible content DOWN the screen), grow keyboardFrame by the
        // strip's own height so the total window height stays the same and
        // emoji_panel actually fills that reclaimed space, starting flush
        // at the very top the same way the normal keyboard does.
        val stripHeight = suggestionStrip.height
        suggestionStrip.visibility = View.GONE
        if (stripHeight > 0 && keyboardFrameBaseHeightPx > 0) {
            val params = keyboardFrame.layoutParams
            params.height = keyboardFrameBaseHeightPx + stripHeight
            keyboardFrame.layoutParams = params
        }
    }

    private fun hideEmojiPanel() {
        if (!::emojiPanel.isInitialized) return
        emojiPanel.visibility = View.GONE
        keyboardView.visibility = View.VISIBLE
        if (keyboardFrameBaseHeightPx > 0 && keyboardFrame.layoutParams.height != keyboardFrameBaseHeightPx) {
            val params = keyboardFrame.layoutParams
            params.height = keyboardFrameBaseHeightPx
            keyboardFrame.layoutParams = params
        }
        suggestionStrip.visibility = View.VISIBLE
        updateTopStripVisibility()
    }

    private fun buildEmojiCategoryIcons() {
        val drawableCategories = listOf(
            R.drawable.clock_loader_10_24,   // recent
            R.drawable.sentiment_satisfied_24, // smileys
            R.drawable.emoji_people_24,      // nature -> people (per latest request)
            R.drawable.local_florist_24,     // food -> florist (per latest request)
            R.drawable.sports_soccer_24,     // activities
            R.drawable.directions_car_24,    // travel
            R.drawable.crown_24,             // objects
            R.drawable.bolt_24               // symbols
        )

        emojiCategoryRow.addView(buildEmojiCategoryImage(R.drawable.search_24))
        drawableCategories.forEach { res ->
            emojiCategoryRow.addView(buildEmojiCategoryImage(res))
        }
        emojiCategoryRow.addView(buildEmojiCategoryImage(R.drawable.flag_2_24))
    }

    private fun buildEmojiCategoryImage(drawableRes: Int): android.widget.ImageView {
        return android.widget.ImageView(this).apply {
            setImageResource(drawableRes)
            setColorFilter(0xFF3A5F52.toInt())
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                val inset = (10 * resources.displayMetrics.density).toInt()
                setMargins(inset, inset, inset, inset)
            }
        }
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

        items.forEach { item ->
            val displayText = when (item.type) {
                "text" -> item.text ?: "(empty)"
                "image" -> "\uD83D\uDCCE Image"
                else -> "(unknown)"
            }
            clipboardList.addView(TextView(this).apply {
                this.text = displayText
                setTextColor(resources.getColor(R.color.ime_text_primary))
                textSize = 14f
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dpPx(20), dpPx(12), dpPx(20), dpPx(12))
                setOnClickListener {
                    if (item.type == "text" && item.text != null) {
                        currentInputConnection?.commitText(item.text, 1)
                        hideClipboardPanel()
                    }
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
        const val SPACE_SHORTCUT_CODE = -31
        const val SYMBOLS_TOGGLE_CODE = -20
        const val SYMBOLS_MORE_CODE = -21
        const val ABC_RETURN_CODE = -22
        const val SYMBOLS_LESS_CODE = -24
        const val NUMPAD_ABC_CODE = -25
        const val NUMPAD_BANGLA_TOGGLE_CODE = -26
        const val COPY_KEY_CODE = 99 // 'c'
        const val CUT_KEY_CODE = 120 // 'x'
        const val PASTE_KEY_CODE = 118 // 'v'
    }
}
