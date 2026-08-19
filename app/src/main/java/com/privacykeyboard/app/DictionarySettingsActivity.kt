package com.privacykeyboard.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

// Two tabs:
// - BlockVeil Dictionary: browse the bundled offline word list per language
//   (search + alphabet jump bar), the same words used for typing suggestions.
// - My Dictionary: personal dictionary management. Add/Edit/View/Backup and the
//   PIN lock are UI-only placeholders for now (explicitly deferred to a future
//   update); Clear All is fully wired to UserDictionaryStore already.
class DictionarySettingsActivity : Activity() {

    private lateinit var container: LinearLayout

    private var currentTab: String = TAB_BLOCKVEIL
    private var currentLanguageIsBangla: Boolean? = null
    private var searchQuery: String = ""
    private var selectedLetter: String? = null

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
        container.addView(buildTabBar())

        val language = currentLanguageIsBangla
        if (language == null) {
            container.addView(buildLanguagePicker())
            return
        }

        container.addView(buildLanguageSwitchRow(language))
        if (currentTab == TAB_BLOCKVEIL) {
            buildBlockVeilDictionaryContent(language)
        } else {
            buildMyDictionaryContent(language)
        }
    }

    private fun buildTabBar(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(16), dp(16), dp(8))
        }
        row.addView(buildTabButton("BlockVeil Dictionary", TAB_BLOCKVEIL))
        row.addView(buildTabButton("My Dictionary", TAB_MY))
        return row
    }

    private fun buildTabButton(title: String, tab: String): TextView {
        val selected = currentTab == tab
        return TextView(this).apply {
            text = title
            textSize = 13f
            setTextColor(resources.getColor(if (selected) R.color.ime_bg else R.color.text_primary))
            setBackgroundColor(if (selected) resources.getColor(R.color.accent_mint) else 0x00000000)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            }
            setOnClickListener {
                if (currentTab != tab) {
                    currentTab = tab
                    searchQuery = ""
                    selectedLetter = null
                    render()
                }
            }
        }
    }

    private fun buildLanguagePicker(): LinearLayout {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(16))
        }
        column.addView(TextView(this).apply {
            text = "Choose a language"
            setTextColor(resources.getColor(R.color.text_secondary))
            textSize = 13f
            setPadding(0, 0, 0, dp(12))
        })
        column.addView(buildLanguageChoiceButton("English", false))
        column.addView(buildLanguageChoiceButton("বাংলা", true))
        return column
    }

    private fun buildLanguageChoiceButton(title: String, isBangla: Boolean): TextView {
        return TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(resources.getColor(R.color.text_primary))
            setBackgroundColor(resources.getColor(R.color.keyboard_bg))
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(10)
            }
            setOnClickListener {
                currentLanguageIsBangla = isBangla
                searchQuery = ""
                selectedLetter = null
                render()
            }
        }
    }

    private fun buildLanguageSwitchRow(currentIsBangla: Boolean): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(4), dp(20), dp(8))
        }
        row.addView(TextView(this).apply {
            text = if (currentIsBangla) "বাংলা" else "English"
            setTextColor(resources.getColor(R.color.text_primary))
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        row.addView(TextView(this).apply {
            text = "Change language"
            setTextColor(resources.getColor(R.color.accent_mint))
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            gravity = Gravity.END
            setOnClickListener {
                currentLanguageIsBangla = null
                render()
            }
        })
        return row
    }

    private fun buildBlockVeilDictionaryContent(isBangla: Boolean) {
        val total = DictionaryProvider.wordCount(isBangla)

        container.addView(TextView(this).apply {
            text = "$total words, offline, bundled with the app"
            setTextColor(resources.getColor(R.color.text_secondary))
            textSize = 12f
            setPadding(dp(20), 0, dp(20), dp(8))
        })

        val searchBox = EditText(this).apply {
            hint = "Search words"
            setHintTextColor(resources.getColor(R.color.text_secondary))
            setTextColor(resources.getColor(R.color.text_primary))
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setBackgroundColor(resources.getColor(R.color.keyboard_bg))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(20); rightMargin = dp(20); bottomMargin = dp(10)
            }
            setText(searchQuery)
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
        container.addView(searchBox)

        container.addView(buildAlphabetBar(isBangla))

        val listHolder = LinearLayout(this).apply {
            id = LIST_HOLDER_ID
            orientation = LinearLayout.VERTICAL
        }
        container.addView(listHolder)
        populateWordList(listHolder, isBangla)
    }

    private fun buildAlphabetBar(isBangla: Boolean): HorizontalScrollView {
        val letters = DictionaryProvider.allWords(isBangla)
            .mapNotNull { it.firstOrNull() }
            .map { if (isBangla) it.toString() else it.uppercaseChar().toString() }
            .distinct()
            .sorted()

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        letters.forEach { letter ->
            row.addView(TextView(this).apply {
                text = letter
                textSize = 14f
                setTextColor(resources.getColor(if (selectedLetter == letter) R.color.ime_bg else R.color.text_primary))
                setBackgroundColor(if (selectedLetter == letter) resources.getColor(R.color.accent_mint) else 0x00000000)
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(8), dp(10), dp(8))
                setOnClickListener {
                    selectedLetter = if (selectedLetter == letter) null else letter
                    searchQuery = ""
                    render()
                }
            })
        }
        return HorizontalScrollView(this).apply {
            addView(row)
            setPadding(dp(12), dp(4), dp(12), dp(8))
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
                text = "No matching words."
                setTextColor(resources.getColor(R.color.text_secondary))
                textSize = 13f
                setPadding(dp(20), dp(12), dp(20), dp(12))
            })
            return
        }

        filtered.take(500).forEach { word ->
            holder.addView(TextView(this).apply {
                text = word
                setTextColor(resources.getColor(R.color.text_primary))
                textSize = 15f
                setPadding(dp(20), dp(10), dp(20), dp(10))
            })
        }
        if (filtered.size > 500) {
            holder.addView(TextView(this).apply {
                text = "+ ${filtered.size - 500} more - narrow your search to see them"
                setTextColor(resources.getColor(R.color.text_secondary))
                textSize = 12f
                setPadding(dp(20), dp(8), dp(20), dp(16))
            })
        }
    }

    private fun buildMyDictionaryContent(isBangla: Boolean) {
        val count = UserDictionaryStore.getWords(this, isBangla).size
        container.addView(TextView(this).apply {
            text = "$count personal word(s) saved for ${if (isBangla) "বাংলা" else "English"}"
            setTextColor(resources.getColor(R.color.text_secondary))
            textSize = 12f
            setPadding(dp(20), 0, dp(20), dp(8))
        })

        container.addView(buildMyDictionaryRow("View My Words") {
            Toast.makeText(this, "Coming in a future update", Toast.LENGTH_SHORT).show()
        })
        container.addView(buildMyDictionaryRow("Add New Word") {
            Toast.makeText(this, "Coming in a future update", Toast.LENGTH_SHORT).show()
        })
        container.addView(buildMyDictionaryRow("Edit / Delete Word") {
            Toast.makeText(this, "Coming in a future update", Toast.LENGTH_SHORT).show()
        })
        container.addView(buildMyDictionaryRow("Backup & Restore Dictionary") {
            Toast.makeText(this, "Coming in a future update", Toast.LENGTH_SHORT).show()
        })
        container.addView(buildMyDictionaryRow("Clear All Words") {
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
        })
        container.addView(buildMyDictionaryRow("Dictionary Lock (PIN)") {
            Toast.makeText(this, "Coming in a future update", Toast.LENGTH_SHORT).show()
        })
    }

    private fun buildMyDictionaryRow(title: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = title
            setTextColor(resources.getColor(R.color.text_primary))
            textSize = 15f
            setPadding(dp(20), dp(14), dp(20), dp(14))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAB_BLOCKVEIL = "blockveil"
        private const val TAB_MY = "my"
        private val LIST_HOLDER_ID = android.view.View.generateViewId()
    }
}
