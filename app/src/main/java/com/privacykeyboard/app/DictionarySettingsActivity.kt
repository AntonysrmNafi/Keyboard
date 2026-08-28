package com.privacykeyboard.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
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

// Navigation is a strict 3-step flow, each step its own screen (nothing appears
// side by side that shouldn't):
//   1. Section picker  - "BlockVeil Dictionary" vs "My Dictionary" (two buttons only)
//   2. Language picker - English vs বাংলা for whichever section was picked
//   3. Content         - bundled word browser, or personal dictionary management
//
// BlockVeil Dictionary is the bundled, read-only word list (also used for typing
// suggestions). My Dictionary is the user's own word list, stored separately via
// UserDictionaryStore and never mixed with the bundled list. My Dictionary can
// optionally be protected by a single "Dictionary Security Lock" PIN that covers
// both languages at once (DictionaryLockStore) - there is no per-language lock.
class DictionarySettingsActivity : Activity() {

    private lateinit var container: LinearLayout

    private var currentSection: String? = null
    private var currentLanguageIsBangla: Boolean? = null
    private var searchQuery: String = ""
    private var selectedLetter: String? = null
    private var myDictScreen: String = MY_DICT_MENU
    private var pendingUnlockLanguage: Boolean? = null

    private val colorBg by lazy { resources.getColor(R.color.bg_dark) }
    private val colorCard by lazy { resources.getColor(R.color.keyboard_bg) }
    private val colorAccent by lazy { resources.getColor(R.color.accent_mint) }
    private val colorTextPrimary by lazy { resources.getColor(R.color.text_primary) }
    private val colorTextSecondary by lazy { resources.getColor(R.color.text_secondary) }
    private val colorDanger = 0xFFE0685A.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Point 3: prevent forced IME hide+reshow flash during transitions
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED)
        setContentView(R.layout.activity_settings_section)

        DictionaryProvider.load(this)

        findViewById<TextView>(R.id.section_title).text = "Dictionary"
        findViewById<android.widget.ImageView>(R.id.back_button).setOnClickListener {
            if (!stepBackOrFinish()) finish()
        }

        container = findViewById(R.id.row_container)
        render()
    }

    // Point 3: the phone's own Back button used to close this whole screen
    // immediately no matter how deep the user was (section -> language -> My
    // Dictionary sub-screen). It now steps back one level at a time instead, the
    // same as the on-screen back arrow above.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!stepBackOrFinish()) super.onBackPressed()
    }

    // Returns true if it just stepped back one level (and re-rendered). Returns
    // false if there was nowhere left to step back to - the caller is then
    // responsible for actually leaving the screen.
    private fun stepBackOrFinish(): Boolean {
        return when {
            currentLanguageIsBangla != null && myDictScreen != MY_DICT_MENU -> {
                myDictScreen = MY_DICT_MENU
                render()
                true
            }
            currentLanguageIsBangla != null -> {
                currentLanguageIsBangla = null
                render()
                true
            }
            currentSection != null -> {
                currentSection = null
                render()
                true
            }
            else -> false
        }
    }

    private fun render() {
        // Point 2: re-check every time the screen changes (not just onResume),
        // since navigating in/out of My Dictionary happens via internal state
        // changes, not activity pause/resume.
        updateScreenshotProtection()
        container.removeAllViews()
        container.setPadding(0, dp(4), 0, dp(24))

        val section = currentSection
        if (section == null) {
            container.addView(buildSectionPicker())
            return
        }

        container.addView(buildSectionHeader(section))

        val language = currentLanguageIsBangla
        if (language == null) {
            container.addView(buildLanguagePicker(section))
            return
        }

        container.addView(buildBreadcrumb(language))
        if (section == SECTION_BLOCKVEIL) {
            buildBlockVeilDictionaryContent(language)
        } else {
            buildMyDictionaryContent(language)
        }
    }

    // --- Step 1: section picker -------------------------------------------------------

    private fun buildSectionPicker(): LinearLayout {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(8))
        }
        val bundledTotal = DictionaryProvider.wordCount(false) + DictionaryProvider.wordCount(true)
        column.addView(buildSectionCard(
            "BlockVeil Dictionary",
            "$bundledTotal bundled words \u00B7 read-only",
            "\uD83D\uDCD6",
            SECTION_BLOCKVEIL
        ))
        column.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, dp(12)) })
        column.addView(buildSectionCard(
            "My Dictionary",
            "Your own words, optionally PIN-locked",
            "\uD83D\uDC64",
            SECTION_MY
        ))
        return column
    }

    private fun buildSectionCard(title: String, subtitle: String, icon: String, section: String): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cardDrawable(colorCard, 16f)
            setPadding(dp(16), dp(18), dp(16), dp(18))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                currentSection = section
                currentLanguageIsBangla = null
                searchQuery = ""
                selectedLetter = null
                myDictScreen = MY_DICT_MENU
                render()
            }
        }
        row.addView(buildBadge(icon, colorAccent, colorBg, 44).apply { textSize = 17f })
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

    private fun buildSectionHeader(section: String): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(4))
        }
        row.addView(TextView(this).apply {
            text = "\u2039"
            setTextColor(colorAccent)
            textSize = 20f
            setPadding(dp(2), dp(2), dp(12), dp(2))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                currentSection = null
                currentLanguageIsBangla = null
                render()
            }
        })
        row.addView(TextView(this).apply {
            text = if (section == SECTION_BLOCKVEIL) "BlockVeil Dictionary" else "My Dictionary"
            setTextColor(colorTextPrimary)
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        return row
    }

    // --- Step 2: language picker -------------------------------------------------------

    private fun buildLanguagePicker(section: String): LinearLayout {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(8))
        }
        column.addView(TextView(this).apply {
            text = "Choose a language"
            setTextColor(colorTextSecondary)
            textSize = 13f
            setPadding(dp(4), 0, 0, dp(12))
        })

        // Point 1.2 fix: My Dictionary shows the user's own personal word counts here,
        // never the bundled BlockVeil Dictionary totals.
        val englishSubtitle = if (section == SECTION_MY)
            UserDictionaryStore.getWords(this, false).size.toString() + " personal word(s)"
        else
            DictionaryProvider.wordCount(false).toString() + " words"
        val banglaSubtitle = if (section == SECTION_MY)
            UserDictionaryStore.getWords(this, true).size.toString() + " personal word(s)"
        else
            DictionaryProvider.wordCount(true).toString() + " words"

        column.addView(buildLanguageCard("English", englishSubtitle, "EN", false, section))
        column.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, dp(12)) })
        column.addView(buildLanguageCard("বাংলা", banglaSubtitle, "বা", true, section))

        // Point 2: one combined lock for the whole My Dictionary section, shown once
        // below both language buttons - not a separate lock per language.
        if (section == SECTION_MY) {
            column.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, dp(14)) })
            column.addView(buildDictionarySecurityLockRow())
        }
        return column
    }

    private fun buildLanguageCard(title: String, subtitle: String, badge: String, isBangla: Boolean, section: String): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cardDrawable(colorCard, 16f)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            isClickable = true
            isFocusable = true
            setOnClickListener { openLanguage(section, isBangla) }
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

    // Only My Dictionary can be locked, and one PIN gates both languages equally.
    // The PIN check itself always happens on its own full page (DictionaryUnlockActivity),
    // never in a popup (Point 1).
    private fun openLanguage(section: String, isBangla: Boolean) {
        if (section == SECTION_MY && DictionaryLockStore.isLocked(this)) {
            pendingUnlockLanguage = isBangla
            val intent = Intent(this, DictionaryUnlockActivity::class.java)
            intent.putExtra("isForLockManagement", false)  // Point 1: not for lock management
            startActivityForResult(intent, REQUEST_UNLOCK_FOR_LANGUAGE)
        } else {
            selectLanguage(isBangla)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            REQUEST_UNLOCK_FOR_LANGUAGE -> {
                val isBangla = pendingUnlockLanguage
                pendingUnlockLanguage = null
                if (isBangla != null) selectLanguage(isBangla)
            }
            REQUEST_UNLOCK_FOR_MANAGE -> {
                startActivity(Intent(this, DictionaryLockManageActivity::class.java))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Lock state / word counts may have changed in DictionaryLockManageActivity,
        // UserDictionaryStore, etc. while this screen was in the background.
        // Point 7: Apply screenshot protection flag if in My Dictionary view
        updateScreenshotProtection()
        render()
    }

    override fun onPause() {
        super.onPause()
        // Point 4: if the phone was locked/turned off while the user was viewing
        // or editing My Dictionary (language chosen, not at the section/language
        // picker), auto-reset to the top level so they have to unlock again on
        // onResume.
        if (currentSection == SECTION_MY && currentLanguageIsBangla != null) {
            currentLanguageIsBangla = null
            myDictScreen = MY_DICT_MENU
        }
        // Point 7: Remove screenshot protection when leaving
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun updateScreenshotProtection() {
        // Point 7: Enable screenshot/recording protection only when viewing or
        // editing My Dictionary (English or বাংলা), if user has disabled screenshots.
        val screenshotsAllowed = android.preference.PreferenceManager
            .getDefaultSharedPreferences(this)
            .getBoolean("allow_screenshots_my_dict", true)

        if (currentSection == SECTION_MY && currentLanguageIsBangla != null && !screenshotsAllowed) {
            window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
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

    // --- Dictionary Security Lock (Point 2) --------------------------------------------

    private fun buildDictionarySecurityLockRow(): LinearLayout {
        val locked = DictionaryLockStore.isLocked(this)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cardDrawable(colorCard, 16f)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            isClickable = true
            isFocusable = true
            setOnClickListener { openDictionarySecurityLock() }
        }
        // Point 1: icon changed to encrypted_24
        row.addView(iconBadge("\uD83D\uDD10", 40))
        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(14)
            }
        }
        textColumn.addView(TextView(this).apply {
            text = "Dictionary Security Lock"
            setTextColor(colorTextPrimary)
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        textColumn.addView(TextView(this).apply {
            text = if (locked) "On \u00B7 Your words, locked away" else "Off \u00B7 tap to secure My Dictionary"
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

    private fun iconBadge(icon: String, sizeDp: Int): TextView {
        return TextView(this).apply {
            text = icon
            textSize = (sizeDp / 2.4f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        }
    }

    // Not locked yet -> straight to the full-page "Set PIN" screen.
    // Already locked -> the full-page PIN screen must succeed first, then the
    // management page (Change PIN/Pass, Remove PIN/Pass, Set/Change Email) opens.
    // Neither step is ever a popup (Point 1 / Point 1.2).
    private fun openDictionarySecurityLock() {
        if (!DictionaryLockStore.isLocked(this)) {
            startActivity(Intent(this, DictionaryLockManageActivity::class.java))
            return
        }
        val cooldown = DictionaryLockStore.cooldownSecondsRemaining(this)
        if (cooldown > 0) {
            Toast.makeText(this, "Too many attempts. Try again in ${cooldown}s.", Toast.LENGTH_SHORT).show()
            return
        }
        startActivityForResult(Intent(this, DictionaryUnlockActivity::class.java).apply {
            putExtra("isForLockManagement", true)  // Point 1: for lock management - forgot PIN available
        }, REQUEST_UNLOCK_FOR_MANAGE)
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
                // Point 3: Enable text selection for long press + copy
                setTextIsSelectable(true)
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
        private const val SECTION_BLOCKVEIL = "blockveil"
        private const val SECTION_MY = "my"
        private const val MY_DICT_MENU = "menu"
        private const val MY_DICT_VIEW = "view"
        private const val MY_DICT_EDIT = "edit"
        private val LIST_HOLDER_ID = View.generateViewId()
        private const val REQUEST_UNLOCK_FOR_LANGUAGE = 1001
        private const val REQUEST_UNLOCK_FOR_MANAGE = 1002
    }
}
