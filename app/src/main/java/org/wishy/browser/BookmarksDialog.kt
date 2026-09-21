package org.wishy.browser

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.wishy.browser.databinding.DialogBookmarksBinding
import org.wishy.browser.databinding.ItemBookmarkBinding

/**
 * Remote-friendly bookmarks list. Up/Down moves between rows, OK opens the
 * page, Right jumps to the row's delete button, Back closes.
 */
class BookmarksDialog(
    private val activity: Activity,
    private val store: BookmarkStore,
    private val onOpen: (Bookmark) -> Unit,
    private val onClosed: () -> Unit
) {

    fun show() {
        val ui = DialogBookmarksBinding.inflate(LayoutInflater.from(activity))
        val items = store.all().toMutableList()
        var dialog: AlertDialog? = null

        fun refreshEmptyState() {
            ui.emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }

        val adapter = BookmarkAdapter(
            items,
            onOpen = {
                dialog?.dismiss()
                onOpen(it)
            },
            onDeleted = { removed, position ->
                store.remove(removed.url)
                refreshEmptyState()
                // Keep the remote's focus in the list (on the neighbouring row).
                ui.list.post {
                    val target = minOf(position, items.size - 1)
                    if (target >= 0) {
                        ui.list.findViewHolderForAdapterPosition(target)?.itemView?.requestFocus()
                    } else {
                        dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.requestFocus()
                    }
                }
            }
        )

        ui.list.layoutManager = LinearLayoutManager(activity)
        ui.list.adapter = adapter

        // Put focus on the first row as soon as it exists so OK/arrow keys work immediately.
        ui.list.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            private var done = false
            override fun onChildViewAttachedToWindow(view: View) {
                if (!done) {
                    done = true
                    view.requestFocus()
                }
            }

            override fun onChildViewDetachedFromWindow(view: View) {}
        })
        refreshEmptyState()

        val d = MaterialAlertDialogBuilder(activity)
            .setView(ui.root)
            .setPositiveButton(R.string.close, null)
            .create()
        dialog = d
        d.setOnDismissListener { onClosed() }
        d.show()
        d.window?.decorView?.let { FocusAnim.attach(it) }
        d.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.6f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}

private class BookmarkAdapter(
    private val items: MutableList<Bookmark>,
    private val onOpen: (Bookmark) -> Unit,
    private val onDeleted: (Bookmark, Int) -> Unit
) : RecyclerView.Adapter<BookmarkAdapter.Holder>() {

    class Holder(val ui: ItemBookmarkBinding) : RecyclerView.ViewHolder(ui.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemBookmarkBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val bookmark = items[position]
        holder.ui.titleText.text = bookmark.title
        holder.ui.urlText.text = bookmark.url

        holder.ui.root.setOnClickListener {
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onOpen(items[p])
        }
        holder.ui.deleteButton.setOnClickListener {
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) {
                val removed = items.removeAt(p)
                notifyItemRemoved(p)
                onDeleted(removed, p)
            }
        }
    }
}
