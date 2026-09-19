package com.blockveil.keyboard

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ClipboardSettingsActivity : Activity() {

    private lateinit var pinnedHeader: TextView
    private lateinit var recentHeader: TextView
    private lateinit var sectionDivider: View
    private lateinit var emptyLabel: TextView
    private lateinit var pinnedList: RecyclerView
    private lateinit var unpinnedList: RecyclerView
    private lateinit var pinnedAdapter: ClipboardRowAdapter
    private lateinit var unpinnedAdapter: ClipboardRowAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Point: this screen has its own dedicated layout (not the shared
        // activity_settings_section.xml every other Settings screen uses),
        // because it needs two reorderable RecyclerViews and a + button
        // that don't belong on any other screen.
        setContentView(R.layout.activity_clipboard_settings)

        findViewById<ImageView>(R.id.clipboard_back_button).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.clipboard_add_button).setOnClickListener { showAddDialog() }

        pinnedHeader = findViewById(R.id.clipboard_pinned_header)
        recentHeader = findViewById(R.id.clipboard_recent_header)
        sectionDivider = findViewById(R.id.clipboard_section_divider)
        emptyLabel = findViewById(R.id.clipboard_empty_label)
        pinnedList = findViewById(R.id.clipboard_pinned_list)
        unpinnedList = findViewById(R.id.clipboard_unpinned_list)

        pinnedAdapter = setUpSection(pinnedList)
        unpinnedAdapter = setUpSection(unpinnedList)

        refresh()
    }

    override fun onResume() {
        super.onResume()
        // Point: the 1-hour auto-expiry (see ClipboardStore) is checked
        // lazily on read - refreshing here catches anything that expired
        // while this screen was in the background.
        refresh()
    }

    // Point: wires one section's RecyclerView with a LinearLayoutManager,
    // its own adapter, and its own ItemTouchHelper restricted to up/down
    // drag only (no swipe-to-dismiss - deleting is a deliberate action via
    // the Edit Clip sheet, not an accidental swipe). Persists the new order
    // the moment a drag finishes (clearView), not on every intermediate
    // step, so a mid-drag app switch can't leave things half-saved.
    private fun setUpSection(recyclerView: RecyclerView): ClipboardRowAdapter {
        val adapter = ClipboardRowAdapter(
            items = mutableListOf(),
            onItemClick = { item -> showEditClipDialog(item) },
            onPinToggle = { item ->
                ClipboardStore.togglePin(this, item.id)
                refresh()
            },
            onStartDrag = { holder -> touchHelperFor(recyclerView)?.startDrag(holder) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.isNestedScrollingEnabled = false

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                rv: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                adapter.moveItem(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Point: swipe is disabled (flags above are 0) - this is
                // required by SimpleCallback but never actually invoked.
            }

            override fun clearView(rv: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(rv, viewHolder)
                persistOrder()
            }
        }
        val helper = ItemTouchHelper(callback)
        helper.attachToRecyclerView(recyclerView)
        recyclerView.tag = helper
        return adapter
    }

    private fun touchHelperFor(recyclerView: RecyclerView): ItemTouchHelper? =
        recyclerView.tag as? ItemTouchHelper

    private fun persistOrder() {
        val order = pinnedAdapter.currentOrder().map { it.id } + unpinnedAdapter.currentOrder().map { it.id }
        ClipboardStore.reorder(this, order)
    }

    private fun refresh() {
        val items = ClipboardStore.getItems(this)
        val (pinned, unpinned) = items.partition { it.pinned }

        emptyLabel.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        if (items.isEmpty()) {
            emptyLabel.text = getString(R.string.clipboard_empty)
        }

        // Point: both section headers, and the divider between them, only
        // show when relevant - "Pinned" only if something's actually
        // pinned, "Recent" only if there's something unpinned to label, and
        // the divider only when BOTH are present (nothing to divide
        // otherwise). This is the clear Pinned/Recent separation the
        // keyboard's own clipboard panel already has.
        pinnedHeader.visibility = if (pinned.isNotEmpty()) View.VISIBLE else View.GONE
        recentHeader.visibility = if (unpinned.isNotEmpty()) View.VISIBLE else View.GONE
        sectionDivider.visibility = if (pinned.isNotEmpty() && unpinned.isNotEmpty()) View.VISIBLE else View.GONE

        pinnedAdapter.updateItems(pinned)
        unpinnedAdapter.updateItems(unpinned)
    }

    // Point: "New Clip" bottom sheet, matching the reference - a title, an
    // image-attach button top-right (picks a gallery image and adds it as
    // its own image clip immediately, no text needed), a green-bordered
    // multi-line text input, and Cancel/Save (Save greyed out and inert
    // until there's actual text, matching the reference).
    private fun showAddDialog() {
        val dialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = resources.getDrawable(R.drawable.bg_bottom_sheet)
            setPadding(dp(20), dp(16), dp(20), dp(24))
        }

        root.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(16)
            }
            setBackgroundColor(resources.getColor(R.color.clipboard_settings_text_secondary))
        })

        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            addView(TextView(this@ClipboardSettingsActivity).apply {
                text = getString(R.string.clipboard_new_clip_title)
                setTextColor(resources.getColor(R.color.clipboard_settings_text_primary))
                textSize = 20f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            addView(ImageView(this@ClipboardSettingsActivity).apply {
                setImageResource(R.drawable.ic_image_24)
                setColorFilter(resources.getColor(R.color.clipboard_settings_text_primary))
                background = resources.getDrawable(R.drawable.bg_icon_button_outline)
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setOnClickListener {
                    dialog.dismiss()
                    pickImageForNewClip()
                }
            })
        })

        val input = EditText(this).apply {
            hint = getString(R.string.clipboard_add_hint)
            setTextColor(resources.getColor(R.color.clipboard_settings_text_primary))
            textSize = 15f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            maxLines = 8
            gravity = Gravity.TOP or Gravity.START
            background = resources.getDrawable(R.drawable.bg_new_clip_input)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(16) }
        }
        root.addView(input)

        lateinit var saveButton: TextView
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(20) }

            addView(TextView(this@ClipboardSettingsActivity).apply {
                text = getString(R.string.cancel_label)
                setTextColor(resources.getColor(R.color.clipboard_settings_text_primary))
                textSize = 15f
                gravity = Gravity.CENTER
                background = resources.getDrawable(R.drawable.bg_edit_clip_cancel)
                setPadding(0, dp(14), 0, dp(14))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(8)
                }
                setOnClickListener { dialog.dismiss() }
            })

            saveButton = TextView(this@ClipboardSettingsActivity).apply {
                text = getString(R.string.save_label)
                setTextColor(resources.getColor(R.color.key_text))
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                background = resources.getDrawable(R.drawable.bg_edit_clip_save_disabled)
                isEnabled = false
                setPadding(0, dp(14), 0, dp(14))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(8)
                }
                setOnClickListener {
                    val text = input.text?.toString().orEmpty()
                    if (text.isNotBlank()) {
                        ClipboardStore.addTextItem(this@ClipboardSettingsActivity, text)
                        refresh()
                        dialog.dismiss()
                    }
                }
            }
            addView(saveButton)
        })

        // Point: Save stays visually grey and functionally inert (the click
        // listener above also no-ops on blank text) until real text is
        // typed, matching the reference exactly.
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val hasText = s?.toString()?.isNotBlank() == true
                saveButton.isEnabled = hasText
                saveButton.background = resources.getDrawable(
                    if (hasText) R.drawable.bg_edit_clip_save else R.drawable.bg_edit_clip_save_disabled
                )
            }
        })

        dialog.setContentView(root)
        dialog.show()
        dialog.window?.apply {
            setGravity(Gravity.BOTTOM)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    // Point: launches the system image picker for "New Clip"'s image
    // button. Uses the classic startActivityForResult/onActivityResult pair
    // (not the newer Activity Result API) because this project's Activity
    // base class is plain android.app.Activity, not ComponentActivity.
    private fun pickImageForNewClip() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
        startActivityForResult(intent, PICK_IMAGE_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_IMAGE_REQUEST_CODE || resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        val base64 = ClipboardStore.encodeImageUriToBase64(this, uri) ?: return
        ClipboardStore.addImageItem(this, base64)
        refresh()
    }

    // Point: "Edit Clip" bottom sheet - full (untruncated) editable text,
    // a Pin/Unpin toggle and a Delete button up top (both apply
    // immediately, no Save needed for those), and Cancel/Save for text
    // edits. Built as a plain Dialog anchored to the bottom of the screen
    // rather than a Material BottomSheetDialog, since this project doesn't
    // otherwise depend on AndroidX Material - no need to add that whole
    // library just for this one sheet's shape.
    private fun showEditClipDialog(item: ClipboardStore.ClipboardItem) {
        val dialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        }

        var isPinned = item.pinned
        lateinit var pinIcon: ImageView

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = resources.getDrawable(R.drawable.bg_bottom_sheet)
            setPadding(dp(20), dp(16), dp(20), dp(24))
        }

        // Drag-handle-style bar at the very top, purely decorative (matches
        // the standard bottom-sheet affordance look).
        root.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(16)
            }
            setBackgroundColor(resources.getColor(R.color.clipboard_settings_text_secondary))
        })

        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            addView(TextView(this@ClipboardSettingsActivity).apply {
                text = getString(R.string.clipboard_edit_title)
                setTextColor(resources.getColor(R.color.clipboard_settings_text_primary))
                textSize = 20f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            pinIcon = ImageView(this@ClipboardSettingsActivity).apply {
                setImageResource(R.drawable.ic_pin_24)
                setColorFilter(
                    resources.getColor(
                        if (isPinned) R.color.clipboard_settings_accent else R.color.clipboard_settings_text_secondary
                    )
                )
                background = resources.getDrawable(
                    if (isPinned) R.drawable.bg_icon_button_outline_active else R.drawable.bg_icon_button_outline
                )
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(10) }
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setOnClickListener {
                    isPinned = !isPinned
                    ClipboardStore.togglePin(this@ClipboardSettingsActivity, item.id)
                    setColorFilter(
                        resources.getColor(
                            if (isPinned) R.color.clipboard_settings_accent else R.color.clipboard_settings_text_secondary
                        )
                    )
                    background = resources.getDrawable(
                        if (isPinned) R.drawable.bg_icon_button_outline_active else R.drawable.bg_icon_button_outline
                    )
                    refresh()
                }
            }
            addView(pinIcon)

            addView(ImageView(this@ClipboardSettingsActivity).apply {
                setImageResource(R.drawable.ic_delete_24)
                setColorFilter(resources.getColor(R.color.clipboard_settings_delete))
                background = resources.getDrawable(R.drawable.bg_icon_button_outline_danger)
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setOnClickListener {
                    ClipboardStore.removeItem(this@ClipboardSettingsActivity, item.id)
                    refresh()
                    dialog.dismiss()
                }
            })
        })

        val input = EditText(this).apply {
            setText(item.text ?: "")
            setTextColor(resources.getColor(R.color.clipboard_settings_text_primary))
            textSize = 15f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            maxLines = 8
            gravity = Gravity.TOP or Gravity.START
            background = resources.getDrawable(R.drawable.bg_edit_clip_input)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(16) }
        }
        root.addView(input)

        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(20) }

            addView(TextView(this@ClipboardSettingsActivity).apply {
                text = getString(R.string.cancel_label)
                setTextColor(resources.getColor(R.color.clipboard_settings_text_primary))
                textSize = 15f
                gravity = Gravity.CENTER
                background = resources.getDrawable(R.drawable.bg_edit_clip_cancel)
                setPadding(0, dp(14), 0, dp(14))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(8)
                }
                setOnClickListener { dialog.dismiss() }
            })

            addView(TextView(this@ClipboardSettingsActivity).apply {
                text = getString(R.string.save_label)
                setTextColor(resources.getColor(R.color.key_text))
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                background = resources.getDrawable(R.drawable.bg_edit_clip_save)
                setPadding(0, dp(14), 0, dp(14))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(8)
                }
                setOnClickListener {
                    val newText = input.text?.toString().orEmpty()
                    if (newText.isNotBlank()) {
                        ClipboardStore.updateText(this@ClipboardSettingsActivity, item.id, newText)
                    }
                    refresh()
                    dialog.dismiss()
                }
            })
        })

        dialog.setContentView(root)
        dialog.show()
        // Point: setLayout must happen AFTER show() - calling it before the
        // window's content is attached doesn't reliably take effect, and
        // the dialog was collapsing to a sliver (text wrapping one
        // character per line) as a result.
        dialog.window?.apply {
            setGravity(Gravity.BOTTOM)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val PICK_IMAGE_REQUEST_CODE = 4201
    }
}
