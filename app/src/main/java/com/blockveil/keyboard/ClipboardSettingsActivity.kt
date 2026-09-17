package com.blockveil.keyboard

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.TextUtils
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Locale

class ClipboardSettingsActivity : Activity() {

    private lateinit var rowContainer: LinearLayout
    private lateinit var normalTopBar: LinearLayout
    private lateinit var selectionTopBar: LinearLayout
    private lateinit var selectionCountText: TextView

    // Point: which item ids are checked, only meaningful while selection
    // mode is active (selectionTopBar visible).
    private val selectedIds = mutableSetOf<String>()
    private val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Point: this screen has its own dedicated layout (not the shared
        // activity_settings_section.xml every other Settings screen uses),
        // because it needs a second "N selected" top bar and a +
        // button that don't belong on any other screen.
        setContentView(R.layout.activity_clipboard_settings)

        normalTopBar = findViewById(R.id.clipboard_normal_topbar)
        selectionTopBar = findViewById(R.id.clipboard_selection_topbar)
        selectionCountText = findViewById(R.id.clipboard_selection_count)
        rowContainer = findViewById(R.id.clipboard_row_container)

        findViewById<ImageView>(R.id.clipboard_back_button).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.clipboard_add_button).setOnClickListener { showAddDialog() }
        findViewById<ImageView>(R.id.clipboard_selection_cancel).setOnClickListener { exitSelectionMode() }
        findViewById<ImageView>(R.id.clipboard_selection_pin).setOnClickListener {
            ClipboardStore.setPinned(this, selectedIds.toSet(), true)
            exitSelectionMode()
        }
        findViewById<ImageView>(R.id.clipboard_selection_delete).setOnClickListener {
            ClipboardStore.removeItems(this, selectedIds.toSet())
            exitSelectionMode()
        }

        refresh()
    }

    private fun showAddDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.clipboard_add_hint)
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.clipboard_add_entry)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val text = input.text?.toString().orEmpty()
                if (text.isNotBlank()) {
                    ClipboardStore.addTextItem(this, text)
                    refresh()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun enterSelectionMode(firstItemId: String) {
        selectedIds.clear()
        selectedIds.add(firstItemId)
        normalTopBar.visibility = android.view.View.GONE
        selectionTopBar.visibility = android.view.View.VISIBLE
        refresh()
    }

    private fun exitSelectionMode() {
        selectedIds.clear()
        normalTopBar.visibility = android.view.View.VISIBLE
        selectionTopBar.visibility = android.view.View.GONE
        refresh()
    }

    private fun toggleSelected(itemId: String) {
        if (!selectedIds.remove(itemId)) selectedIds.add(itemId)
        if (selectedIds.isEmpty()) {
            exitSelectionMode()
        } else {
            refresh()
        }
    }

    private val isSelecting: Boolean get() = selectionTopBar.visibility == android.view.View.VISIBLE

    private fun refresh() {
        rowContainer.removeAllViews()
        if (isSelecting) {
            selectionCountText.text = getString(R.string.clipboard_selected_count, selectedIds.size)
        }

        val items = ClipboardStore.getItems(this)

        if (items.isEmpty()) {
            rowContainer.addView(TextView(this).apply {
                text = getString(R.string.clipboard_empty)
                setTextColor(resources.getColor(R.color.clipboard_settings_text_secondary))
                textSize = 14f
                setPadding(dp(20), dp(20), dp(20), dp(20))
            })
            return
        }

        items.forEach { item -> rowContainer.addView(buildRow(item)) }
    }

    // Point: one row - checkbox (only visible/interactive in selection
    // mode), preview text (same shared 120-char/25-per-line truncation as
    // everywhere else) + a formatted timestamp underneath, and a drag-handle
    // icon on the right matching the reference (decorative here - this list
    // isn't reorderable, it's always newest-first like the keyboard's own
    // clipboard panel).
    private fun buildRow(item: ClipboardStore.ClipboardItem): LinearLayout {
        val selected = item.id in selectedIds
        val displayText = when (item.type) {
            "text" -> ClipboardStore.buildPreview(item.text ?: "")
            "image" -> "\uD83D\uDCCE Image (${item.imageBase64?.length?.div(1000) ?: 0}KB)"
            else -> "(unknown)"
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            background = resources.getDrawable(
                if (selected) R.drawable.bg_clipboard_settings_row_selected
                else R.drawable.bg_clipboard_settings_row
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
                bottomMargin = dp(4)
            }
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setOnClickListener {
                if (isSelecting) toggleSelected(item.id)
            }
            setOnLongClickListener {
                if (!isSelecting) enterSelectionMode(item.id)
                true
            }
        }

        row.addView(CheckBox(this).apply {
            isChecked = selected
            isClickable = false // the row itself handles the tap
            visibility = if (isSelecting) android.view.View.VISIBLE else android.view.View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(12) }
        })

        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            addView(TextView(this@ClipboardSettingsActivity).apply {
                text = displayText
                setTextColor(resources.getColor(R.color.clipboard_settings_text_primary))
                textSize = 15f
                maxLines = ClipboardStore.PREVIEW_MAX_LINES
                ellipsize = TextUtils.TruncateAt.END
            })
            addView(TextView(this@ClipboardSettingsActivity).apply {
                text = dateFormat.format(item.timestamp)
                setTextColor(resources.getColor(R.color.clipboard_settings_text_secondary))
                textSize = 12f
                setPadding(0, dp(6), 0, 0)
            })
        })

        row.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_drag_handle_24)
            setColorFilter(resources.getColor(R.color.clipboard_settings_text_secondary))
            layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginStart = dp(12) }
        })

        return row
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
