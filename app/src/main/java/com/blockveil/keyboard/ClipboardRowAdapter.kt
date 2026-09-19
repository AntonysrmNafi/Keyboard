package com.blockveil.keyboard

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

// Point: one Settings > Clipboard section's list (either all-pinned or
// all-unpinned items). Kept as a mutable local copy so drag-reordering can
// update it instantly for smooth visuals; ClipboardSettingsActivity is the
// one that persists the final order back to ClipboardStore once a drag ends.
class ClipboardRowAdapter(
    private val items: MutableList<ClipboardStore.ClipboardItem>,
    private val onItemClick: (ClipboardStore.ClipboardItem) -> Unit,
    private val onPinToggle: (ClipboardStore.ClipboardItem) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<ClipboardRowAdapter.RowViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

    class RowViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.row_text)
        val timestamp: TextView = view.findViewById(R.id.row_timestamp)
        val pin: android.widget.ImageView = view.findViewById(R.id.row_pin)
        val dragHandle: View = view.findViewById(R.id.row_drag_handle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.row_clipboard_item, parent, false)
        return RowViewHolder(view)
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        val item = items[position]
        holder.text.apply {
            text = when (item.type) {
                "text" -> ClipboardStore.buildPreview(item.text ?: "")
                "image" -> "\uD83D\uDCCE Image (${item.imageBase64?.length?.div(1000) ?: 0}KB)"
                else -> "(unknown)"
            }
            maxLines = ClipboardStore.PREVIEW_MAX_LINES
            ellipsize = TextUtils.TruncateAt.END
        }
        holder.timestamp.text = dateFormat.format(item.timestamp)
        holder.itemView.setOnClickListener { onItemClick(item) }
        // Point: pin icon always shows here regardless of pinned state
        // (color-coded: accent when pinned, muted when not) - this row is
        // its own always-visible unpin/pin control, on top of the same
        // toggle also being reachable from the Edit Clip sheet.
        val context = holder.pin.context
        holder.pin.apply {
            setColorFilter(
                context.resources.getColor(
                    if (item.pinned) R.color.clipboard_settings_accent else R.color.clipboard_settings_text_secondary
                )
            )
            setOnClickListener { onPinToggle(item) }
        }
        // Point: drag only starts from the handle icon (not anywhere on the
        // row), so a plain tap on the row's text always opens Edit Clip
        // instead of racing against a drag gesture.
        holder.dragHandle.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                onStartDrag(holder)
            }
            false
        }
    }

    override fun getItemCount() = items.size

    fun moveItem(fromPosition: Int, toPosition: Int) {
        val moved = items.removeAt(fromPosition)
        items.add(toPosition, moved)
        notifyItemMoved(fromPosition, toPosition)
    }

    fun currentOrder(): List<ClipboardStore.ClipboardItem> = items.toList()

    // Point: replaces this adapter's data in place (used by refresh()) -
    // the SAME adapter instance stays attached to the RecyclerView and to
    // its ItemTouchHelper for the activity's whole lifetime, so a drag
    // gesture can never land on a stale/replaced adapter (that mismatch
    // used to crash with IndexOutOfBoundsException - see moveItem above,
    // which assumes fromPosition/toPosition are valid for THIS list).
    fun updateItems(newItems: List<ClipboardStore.ClipboardItem>) {
        if (items.map { it.id } == newItems.map { it.id }) return
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
