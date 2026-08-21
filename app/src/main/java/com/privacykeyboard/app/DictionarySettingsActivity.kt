package com.privacykeyboard.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

// Two tabs:
// - BlockVeil Dictionary: browse the bundled offline word list per language
//   (search + alphabet jump chips), the same words used for typing suggestions.
// - My Dictionary: personal dictionary management, fully wired to UserDictionaryStore
//   and completely independent from the bundled BlockVeil word list above (words never
//   mix between the two). Optionally PIN-locked per language via DictionaryLockStore;
//   the lock control lives under the language picker, only on this tab.
//
// Each tab keeps its own language choice: switching tabs always returns to the
// language picker for that tab instead of carrying the previous tab's language over.
class DictionarySettingsActivity : Activity() {

    private lateinit var container: LinearLayout

    private var currentTab: String = TAB_BLOCKVEIL
    private var currentLanguageIsBangla: Boolean? = null
    private var searchQuery: String = ""
    private var selectedLetter: String? = null
    private var myDictScreen: String = MY_DICT_MENU

    private val colorBg by lazy { resources.getColor(R.color.bg_dark) }
    private val colorCard by lazy { resources.getColor(R.color.keyboard_bg) }
    private val colorAccent by lazy { resources.getColor(R.color.accent_mint) }
    private val colorTextPrimary by lazy { resources.getColor(R.color.text_primary) }
    private val colorTextSecondary by lazy { resources.getColor(R.color.text_secondary) }
    private val colorDanger = 0xFFE0685A.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_section)

        DictionaryProvider.load(this)

        findViewById<TextView>(R.id.section_title).text = "Dictionary"
        findViewById<TextView>(R.id.back_button).setOnClickListener { finish() }

        container = findViewById(R.id.row_container)
        render()
    }

    private fun render() {
        container.removeAllViews()
        container.setPadding(0, dp(4), 0, dp(24))

        container.addView(buildTabBar())

        val language = currentLanguageIsBangla
        if (language == null) {
            container.addView(buildLanguagePicker())
            return
        }

        container.addView(buildBreadcrumb(language))
        if (currentTab == TAB_BLOCKVEIL) {
            buildBlockVeilDictionaryContent(language)
        } else {
            buildMyDictionaryContent(language)
        }
    }

    private fun buildTabBar(): LinearLayout {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(4))
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(buildTabSegment("BlockVeil Dictionary", TAB_BLOCKVEIL))
        row.addView(buildTabSegment("My Dictionary", TAB_MY))
        outer.addView(row)
        return outer
    }

    private fun buildTabSegment(title: String, tab: String): LinearLayout {
        val selected = currentTab == tab
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (currentTab != tab) {
                    currentTab = tab
                    // Each tab always starts from its own language picker, so picking
                    // English under one tab never silently carries into the other tab.
                    currentLanguageIsBangla = null
                    searchQuery = ""
                    selectedLetter = null
                    myDictScreen = MY_DICT_MENU
                    render()
                }
            }
        }
        column.addView(TextView(this).apply {
            text = title
            textSize = 14f
            setTextColor(if (selected) colorAccent else colorTextSecondary)
            if (selected) setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(10), dp(4), dp(10))
        })
        column.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(3))
            setBackgroundColor(if (selected) colorAccent else Color.TRANSPARENT)
        })
        return column
    }

    private fun buildLanguagePicker(): LinearLayout {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(8))
        }
        column.addView(TextView(this).apply {
            text = "Choose a language"
            setTextColor(colorTextSecondary)
            textSize = 13f
            setPadding(dp(4), 0, 0, dp(12))
        })
        column.addView(buildLanguageCard("English", DictionaryProvider.wordCount(false).toString() + " words", "EN", false))
        if (currentTab == TAB_MY) column.addView(buildDictionaryLockRow(false))
        column.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, dp(12)) })
        column.addView(buildLanguageCard("বাংলা", DictionaryProvider.wordCount(true).toString() + " words", "বা", true))
        if (currentTab == TAB_MY) column.addView(buildDictionaryLockRow(true))
        return column
    }

    private fun buildLanguageCard(title: String, subtitle: String, badge: String, isBangla: Boolean): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cardDrawable(colorCard, 16f)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            isClickable = true
            isFocusable = true
            setOnClickListener { openLanguage(isBangla) }
        }
        row.addView(buildBadge(badge, colorAccent, colorBg, 44))
        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(14)
            }
        }
        textColumn.addView(TextView(this).apply {
            text = title
            setTextColor(colorTextPrimary)
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        textColumn.addView(TextView(this).apply {
            text = subtitle
            setTextColor(colorTextSecondary)
            textSize = 12f
        })
        row.addView(textColumn)
        row.addView(TextView(this).apply {
            text = "\u203A"
            setTextColor(colorTextSecondary)
            textSize = 20f
        })
        return row
    }

    // Only "My Dictionary" can be PIN-locked, and only that tab checks the lock here.
    private fun openLanguage(isBangla: Boolean) {
        if (currentTab == TAB_MY && DictionaryLockStore.isLocked(this, isBangla)) {
            promptPin(isBangla) { selectLanguage(isBangla) }
        } else {
            selectLanguage(isBangla)
        }
    }

    private fun selectLanguage(isBangla: Boolean) {
        currentLanguageIsBangla = isBangla
        searchQuery = ""
        selectedLetter = null
        myDictScreen = MY_DICT_MENU
        render()
    }

    private fun buildBadge(text: String, bg: Int, fg: Int, sizeDp: Int): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(fg)
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
            background = circleDrawable(bg)
        }
    }

    private fun buildBreadcrumb(isBangla: Boolean): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(4))
        }
        row.addView(buildBadge(if (isBangla) "বা" else "EN", colorAccent, colorBg, 26))
        row.addView(TextView(this).apply {
            text = if (isBangla) "  বাংলা" else "  English"
            setTextColor(colorTextPrimary)
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        row.addView(TextView(this).apply {
            text = "Change"
            setTextColor(colorAccent)
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            gravity = Gravity.END
            setOnClickListener {
                currentLanguageIsBangla = null
                myDictScreen = MY_DICT_MENU
                render()
            }
        })
        return row
    }

    // --- Dictionary Lock (Point 8 / 8.1) ---------------------------------------------

    private fun buildDictionaryLockRow(isBangla: Boolean): LinearLayout {
        val locked = DictionaryLockStore.isLocked(this, isBangla)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(8), dp(6), dp(4))
            isClickable = true
            isFocusable = true
            setOnClickListener { showLockManageDialog(isBangla) }
        }
        row.addView(TextView(this).apply {
            text = "\uD83D\uDD12"
            textSize = 13f
            setPadding(0, 0, dp(8), 0)
        })
        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textColumn.addView(TextView(this).apply {
            text = "Dictionary Lock"
            setTextColor(colorTextPrimary)
            textSize = 13f
        })
        textColumn.addView(TextView(this).apply {
            text = if (locked) "On \u00B7 PIN protected" else "Off \u00B7 Tap to set a PIN"
            setTextColor(colorTextSecondary)
            textSize = 11f
        })
        row.addView(textColumn)
        return row
    }

    private fun showLockManageDialog(isBangla: Boolean) {
        if (!DictionaryLockStore.isLocked(this, isBangla)) {
            showSetPinDialog(isBangla)
            return
        }
        promptPin(isBangla) {
            AlertDialog.Builder(this)
                .setTitle("Dictionary Lock")
                .setItems(arrayOf("Change PIN", "Remove Lock")) { _, which ->
                    if (which == 0) {
                        showSetPinDialog(isBangla)
                    } else {
                        DictionaryLockStore.clearPin(this, isBangla)
                        Toast.makeText(this, "Lock removed", Toast.LENGTH_SHORT).show()
                        render()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showSetPinDialog(isBangla: Boolean) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "4 to 6 digit PIN"
            setTextColor(colorTextPrimary)
            setHintTextColor(colorTextSecondary)
        }
        val wrapper = FrameLayout(this).apply {
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle("Set Dictionary PIN")
            .setView(wrapper)
            .setPositiveButton("Save") { _, _ ->
                val pin = input.text.toString()
                if (pin.length < 4) {
                    Toast.makeText(this, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show()
                } else {
                    DictionaryLockStore.setPin(this, isBangla, pin)
                    Toast.makeText(this, "Dictionary locked", Toast.LENGTH_SHORT).show()
                    render()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptPin(isBangla: Boolean, onSuccess: () -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Enter PIN"
            setTextColor(colorTextPrimary)
            setHintTextColor(colorTextSecondary)
        }
        val wrapper = FrameLayout(this).apply {
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle("Dictionary Locked")
            .setMessage("Enter the PIN for your ${if (isBangla) "বাংলা" else "English"} My Dictionary")
            .setView(wrapper)
            .setPositiveButton("Unlock") { _, _ ->
                if (DictionaryLockStore.verifyPin(this, isBangla, input.text.toString())) {
                    onSuccess()
                } else {
                    Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- BlockVeil Dictionary (bundled, read-only) -------------------------------------

    private fun buildBlockVeilDictionaryContent(isBangla: Boolean) {
        val total = DictionaryProvider.wordCount(isBangla)

        container.addView(TextView(this).apply {
            text = "$total words \u00B7 100% offline \u00B7 bundled with the app"
            setTextColor(colorTextSecondary)
            textSize = 11f
            setPadding(dp(20), 0, dp(20), dp(10))
        })

        val searchRow = FrameLayout(this).apply {
            background = pillDrawable(colorCard, 24f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(20); rightMargin = dp(20); bottomMargin = dp(12)
            }
        }
        val searchInner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(4))
        }
        searchInner.addView(TextView(this).apply {
            text = "\uD83D\uDD0D"
            textSize = 14f
            setPadding(0, 0, dp(8), 0)
        })
        val searchBox = EditText(this).apply {
            hint = if (isBangla) "শব্দ খুঁজুন" else "Search words"
            setHintTextColor(colorTextSecondary)
            setTextColor(colorTextPrimary)
            textSize = 14f
            background = null
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(0, dp(10), 0, dp(10))
            setText(searchQuery)
            setSelection(searchQuery.length)
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    searchQuery = s?.toString() ?: ""
                    selectedLetter = null
                    refreshWordListOnly(isBangla)
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }
        searchInner.addView(searchBox)
        searchRow.addView(searchInner)
        container.addView(searchRow)

        container.addView(buildAlphabetChips(isBangla))

        val listHolder = LinearLayout(this).apply {
            id = LIST_HOLDER_ID
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, 0)
        }
        container.addView(listHolder)
        populateWordList(listHolder, isBangla)
    }

    private fun buildAlphabetChips(isBangla: Boolean): HorizontalScrollView {
        val letters = DictionaryProvider.allWords(isBangla)
            .mapNotNull { it.firstOrNull() }
            .map { if (isBangla) it.toString() else it.uppercaseChar().toString() }
            .distinct()
            .sorted()

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), 0, dp(16), dp(6))
        }
        letters.forEach { letter ->
            val selected = selectedLetter == letter
            row.addView(TextView(this).apply {
                text = letter
                textSize = 13f
                setTextColor(if (selected) colorBg else colorTextPrimary)
                background = pillDrawable(if (selected) colorAccent else colorCard, 16f)
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(6), dp(12), dp(6))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dp(6)
                }
                setOnClickListener {
                    selectedLetter = if (selectedLetter == letter) null else letter
                    searchQuery = ""
                    render()
                }
            })
        }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    private fun refreshWordListOnly(isBangla: Boolean) {
        val holder = container.findViewById<LinearLayout>(LIST_HOLDER_ID) ?: return
        populateWordList(holder, isBangla)
    }

    private fun populateWordList(holder: LinearLayout, isBangla: Boolean) {
        holder.removeAllViews()
        val all = DictionaryProvider.allWords(isBangla)

        val filtered = when {
            searchQuery.isNotBlank() -> {
                val q = if (isBangla) searchQuery else searchQuery.lowercase()
                all.filter { (if (isBangla) it else it.lowercase()).contains(q) }
            }
            selectedLetter != null -> {
                all.filter {
                    val first = it.firstOrNull()?.let { c -> if (isBangla) c.toString() else c.uppercaseChar().toString() }
                    first == selectedLetter
                }
            }
            else -> all
        }

        if (filtered.isEmpty()) {
            holder.addView(TextView(this).apply {
                text = if (isBangla) "কোনো শব্দ পাওয়া যায়নি।" else "No matching words."
                setTextColor(colorTextSecondary)
                textSize = 13f
                setPadding(dp(20), dp(20), dp(20), dp(20))
                gravity = Gravity.CENTER
            })
            return
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardDrawable(colorCard, 14f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(20); rightMargin = dp(20); topMargin = dp(4)
            }
        }

        var lastLetter: String? = null
        val shown = filtered.take(400)
        shown.forEachIndexed { index, word ->
            val letter = word.firstOrNull()?.let { c -> if (isBangla) c.toString() else c.uppercaseChar().toString() }
            if (letter != lastLetter && searchQuery.isBlank()) {
                lastLetter = letter
                // Point 1.1: bigger, bolder section header with a divider line right
                // under it (like an <hr>), sitting above the words in that letter group.
                card.addView(TextView(this).apply {
                    text = letter ?: ""
                    setTextColor(colorAccent)
                    textSize = 20f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(dp(16), if (index == 0) dp(14) else dp(18), dp(16), dp(6))
                })
                card.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(2)).apply {
                        leftMargin = dp(16); rightMargin = dp(16); bottomMargin = dp(6)
                    }
                    setBackgroundColor(colorAccent)
                })
            }
            card.addView(TextView(this).apply {
                text = word
                setTextColor(colorTextPrimary)
                textSize = 15f
                setPadding(dp(16), dp(8), dp(16), dp(8))
            })
            if (index < shown.size - 1) {
                card.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
                        marginStart = dp(16); marginEnd = dp(16)
                    }
                    setBackgroundColor(0x14FFFFFF)
                })
            }
        }
        holder.addView(card)

        if (filtered.size > 400) {
            holder.addView(TextView(this).apply {
                text = "+ " + (filtered.size - 400) + " more \u2014 narrow your search to see them"
                setTextColor(colorTextSecondary)
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(10), dp(20), dp(4))
            })
        }
    }

    // --- My Dictionary (personal, editable, never mixed with the bundled list) --------

    private fun buildMyDictionaryContent(isBangla: Boolean) {
        when (myDictScreen) {
            MY_DICT_VIEW -> buildMyDictionaryWordList(isBangla, editable = false)
            MY_DICT_EDIT -> buildMyDictionaryWordList(isBangla, editable = true)
            else -> buildMyDictionaryMenu(isBangla)
        }
    }

    private fun buildMyDictionaryMenu(isBangla: Boolean) {
        val count = UserDictionaryStore.getWords(this, isBangla).size
        container.addView(TextView(this).apply {
            text = if (isBangla) "$count টি ব্যক্তিগত শব্দ সংরক্ষিত" else "$count personal word(s) saved"
            setTextColor(colorTextSecondary)
            textSize = 11f
            setPadding(dp(20), 0, dp(20), dp(10))
        })

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardDrawable(colorCard, 14f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(20); rightMargin = dp(20)
            }
        }

        data class RowInfo(val icon: String, val title: String, val subtitle: String, val danger: Boolean)

        // Dictionary Lock used to live here as a row; it now lives under the language
        // picker on this tab instead (Point 8), so it is not listed below anymore.
        val rows = listOf(
            RowInfo("\uD83D\uDC41", "View My Words", "See every word you've added", false),
            RowInfo("\u2795", "Add New Word", "Add a word to your dictionary", false),
            RowInfo("\u270E", "Edit / Delete Word", "Change or remove a saved word", false),
            RowInfo("\u21C5", "Backup & Restore Dictionary", "Save or load your word list", false),
            RowInfo("\uD83D\uDDD1", "Clear All Words", "Delete every personal word", true)
        )

        rows.forEachIndexed { index, info ->
            card.addView(buildMyDictionaryRow(info.icon, info.title, info.subtitle, info.danger) {
                when (info.title) {
                    "View My Words" -> { myDictScreen = MY_DICT_VIEW; render() }
                    "Add New Word" -> showAddWordDialog(isBangla)
                    "Edit / Delete Word" -> { myDictScreen = MY_DICT_EDIT; render() }
                    "Clear All Words" -> {
                        AlertDialog.Builder(this)
                            .setTitle("Clear all words?")
                            .setMessage("Are you sure? All words will be deleted.")
                            .setPositiveButton("Delete") { _, _ ->
                                UserDictionaryStore.clear(this, isBangla)
                                Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show()
                                render()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                    else -> Toast.makeText(this, "Coming in a future update", Toast.LENGTH_SHORT).show()
                }
            })
            if (index < rows.size - 1) {
                card.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
                        marginStart = dp(16); marginEnd = dp(16)
                    }
                    setBackgroundColor(0x14FFFFFF)
                })
            }
        }

        container.addView(card)
    }

    private fun buildMyDictionaryRow(icon: String, title: String, subtitle: String, danger: Boolean, onClick: () -> Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        val badgeBg = if (danger) 0x33E0685A.toInt() else 0x33A9E6C4.toInt()
        val badgeFg = if (danger) colorDanger else colorAccent
        row.addView(buildBadge(icon, badgeBg, badgeFg, 36).apply { textSize = 15f })
        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(14)
            }
        }
        textColumn.addView(TextView(this).apply {
            text = title
            setTextColor(if (danger) colorDanger else colorTextPrimary)
            textSize = 15f
        })
        textColumn.addView(TextView(this).apply {
            text = subtitle
            setTextColor(colorTextSecondary)
            textSize = 11f
        })
        row.addView(textColumn)
        row.addView(TextView(this).apply {
            text = "\u203A"
            setTextColor(colorTextSecondary)
            textSize = 18f
        })
        return row
    }

    private fun buildMyDictionaryWordList(isBangla: Boolean, editable: Boolean) {
        container.addView(TextView(this).apply {
            text = "\u2039 Back"
            setTextColor(colorAccent)
            textSize = 13f
            setPadding(dp(20), dp(4), dp(20), dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener { myDictScreen = MY_DICT_MENU; render() }
        })

        val words = UserDictionaryStore.getWords(this, isBangla)
        if (words.isEmpty()) {
            container.addView(TextView(this).apply {
                text = if (isBangla) "এখনো কোনো শব্দ যোগ করা হয়নি।" else "No words added yet."
                setTextColor(colorTextSecondary)
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(20), dp(20), dp(20))
            })
            return
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardDrawable(colorCard, 14f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(20); rightMargin = dp(20)
            }
        }

        words.forEachIndexed { index, word ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(12), dp(16), dp(12))
                if (editable) {
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { showEditWordDialog(isBangla, word) }
                }
            }
            row.addView(TextView(this).apply {
                text = word
                setTextColor(colorTextPrimary)
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            if (editable) {
                row.addView(TextView(this).apply {
                    text = "\u270E"
                    setTextColor(colorAccent)
                    textSize = 15f
                    setPadding(dp(10), 0, 0, 0)
                })
            }
            card.addView(row)
            if (index < words.size - 1) {
                card.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
                        marginStart = dp(16); marginEnd = dp(16)
                    }
                    setBackgroundColor(0x14FFFFFF)
                })
            }
        }
        container.addView(card)
    }

    private fun showAddWordDialog(isBangla: Boolean) {
        val input = EditText(this).apply {
            hint = if (isBangla) "নতুন শব্দ" else "New word"
            setTextColor(colorTextPrimary)
            setHintTextColor(colorTextSecondary)
        }
        val wrapper = FrameLayout(this).apply {
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle("Add New Word")
            .setView(wrapper)
            .setPositiveButton("Add") { _, _ ->
                val word = input.text.toString().trim()
                if (word.isNotEmpty()) {
                    UserDictionaryStore.addWord(this, isBangla, word)
                    Toast.makeText(this, "Added", Toast.LENGTH_SHORT).show()
                    render()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditWordDialog(isBangla: Boolean, word: String) {
        val input = EditText(this).apply {
            setText(word)
            setTextColor(colorTextPrimary)
            setSelection(word.length)
        }
        val wrapper = FrameLayout(this).apply {
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle("Edit Word")
            .setView(wrapper)
            .setPositiveButton("Save") { _, _ ->
                val newWord = input.text.toString().trim()
                if (newWord.isNotEmpty()) {
                    UserDictionaryStore.updateWord(this, isBangla, word, newWord)
                    render()
                }
            }
            .setNeutralButton("Delete") { _, _ ->
                UserDictionaryStore.removeWord(this, isBangla, word)
                Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pillDrawable(color: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusDp * resources.displayMetrics.density
        }

    private fun cardDrawable(color: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusDp * resources.displayMetrics.density
        }

    private fun circleDrawable(color: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            shape = GradientDrawable.OVAL
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAB_BLOCKVEIL = "blockveil"
        private const val TAB_MY = "my"
        private const val MY_DICT_MENU = "menu"
        private const val MY_DICT_VIEW = "view"
        private const val MY_DICT_EDIT = "edit"
        private val LIST_HOLDER_ID = View.generateViewId()
    }
}
