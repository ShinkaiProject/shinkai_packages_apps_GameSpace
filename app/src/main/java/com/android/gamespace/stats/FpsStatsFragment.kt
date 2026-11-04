package com.android.gamespace.stats

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.gamespace.R
import com.android.gamespace.data.FpsSessionStore
import com.android.gamespace.widget.showMaterial3PopupMenu
import java.util.Locale

/**
 * The list of recorded FPS sessions, newest first.
 *
 * Long-pressing a session (or choosing "Select" in the menu) switches to selection mode, where
 * any number of sessions can be ticked and deleted together.
 */
class FpsStatsFragment : Fragment(R.layout.fragment_fps_stats) {

    private val store by lazy { FpsSessionStore(requireContext().applicationContext) }
    private val adapter = SessionAdapter(
        onClick = { onItemClick(it) },
        onLongClick = { onItemLongClick(it) },
    )
    private var emptyView: View? = null
    private var sessions: List<FpsSessionStore.Info> = emptyList()

    private var selecting = false
    private val selected = linkedSetOf<String>()

    /** While selecting, Back leaves selection mode instead of closing the screen. */
    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = exitSelection()
    }

    private val menuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.fps_stats_menu, menu)
            menu.findItem(R.id.menu_delete_selected)?.apply {
                isVisible = selecting
                isEnabled = selected.isNotEmpty()
            }
            menu.findItem(R.id.menu_more)?.apply {
                isVisible = sessions.isNotEmpty()
                actionView?.setOnClickListener { showMoreMenu(it) }
            }
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
            R.id.menu_delete_selected -> { confirmDeleteSelected(); true }
            else -> false
        }
    }

    /** The Material 3 popup behind the toolbar's "more" button; its entries depend on the mode. */
    private fun showMoreMenu(anchor: View) {
        showMaterial3PopupMenu(
            anchor = anchor,
            menuRes = R.menu.fps_stats_popup_menu,
            prepare = { menu ->
                menu.findItem(R.id.menu_select_all)?.isVisible = selecting
                menu.findItem(R.id.menu_select)?.isVisible = !selecting
                menu.findItem(R.id.menu_delete_all)?.isVisible = !selecting
            },
            onItemClick = { item ->
                when (item.itemId) {
                    R.id.menu_select -> { enterSelection(null); true }
                    R.id.menu_select_all -> { selectAll(); true }
                    R.id.menu_delete_all -> { confirmDeleteAll(); true }
                    else -> false
                }
            },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let {
            selecting = it.getBoolean(KEY_SELECTING)
            selected.addAll(it.getStringArrayList(KEY_SELECTED).orEmpty())
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_SELECTING, selecting)
        outState.putStringArrayList(KEY_SELECTED, ArrayList(selected))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        emptyView = view.findViewById(R.id.empty_view)
        view.findViewById<RecyclerView>(R.id.session_list).also {
            it.layoutManager = LinearLayoutManager(requireContext())
            it.adapter = adapter
        }
        val activity = requireActivity()
        activity.addMenuProvider(menuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)
        activity.onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
        applySelectionState()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun onDestroyView() {
        emptyView = null
        super.onDestroyView()
    }

    private fun reload() {
        val activity = requireActivity()
        val store = store
        Thread {
            val loaded = store.list()
            activity.runOnUiThread {
                if (!isAdded || view == null) return@runOnUiThread
                sessions = loaded
                // Forget selected sessions that no longer exist; nothing to select in an empty list.
                selected.retainAll(loaded.map { it.id }.toSet())
                if (loaded.isEmpty()) selecting = false
                adapter.submit(loaded)
                emptyView?.visibility = if (loaded.isEmpty()) View.VISIBLE else View.GONE
                applySelectionState()
            }
        }.start()
    }

    private fun onItemClick(info: FpsSessionStore.Info) {
        if (selecting) toggle(info.id) else openSession(info)
    }

    private fun onItemLongClick(info: FpsSessionStore.Info) {
        if (selecting) toggle(info.id) else enterSelection(info.id)
    }

    private fun openSession(info: FpsSessionStore.Info) {
        startActivity(
            Intent(requireContext(), FpsSessionActivity::class.java)
                .putExtra(FpsSessionActivity.EXTRA_ID, info.id)
        )
    }

    private fun enterSelection(firstId: String?) {
        if (sessions.isEmpty()) return
        selecting = true
        firstId?.let { selected.add(it) }
        applySelectionState()
    }

    private fun exitSelection() {
        selecting = false
        selected.clear()
        applySelectionState()
    }

    private fun toggle(id: String) {
        if (!selected.add(id)) selected.remove(id)
        applySelectionState()
    }

    private fun selectAll() {
        selected.addAll(sessions.map { it.id })
        applySelectionState()
    }

    /** Brings the adapter, title, menu and Back handling in line with the selection state. */
    private fun applySelectionState() {
        backCallback.isEnabled = selecting
        adapter.setSelection(selecting, selected)
        activity?.title = if (selecting) {
            getString(R.string.fps_stats_selected_count, selected.size)
        } else {
            getString(R.string.fps_statistics_title)
        }
        activity?.invalidateOptionsMenu()
    }

    private fun confirmDeleteSelected() {
        val ids = selected.toList()
        if (ids.isEmpty()) return
        AlertDialog.Builder(requireContext())
            .setMessage(
                resources.getQuantityString(R.plurals.fps_stats_delete_selected_message, ids.size, ids.size)
            )
            .setPositiveButton(R.string.fps_stats_delete) { _, _ -> deleteSessions(ids) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteSessions(ids: List<String>) {
        val activity = requireActivity()
        val store = store
        Thread {
            ids.forEach { store.delete(it) }
            activity.runOnUiThread {
                if (!isAdded || view == null) return@runOnUiThread
                exitSelection()
                reload()
            }
        }.start()
    }

    private fun confirmDeleteAll() {
        if (sessions.isEmpty()) return
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.fps_stats_delete_all_message)
            .setPositiveButton(R.string.fps_stats_delete_all) { _, _ ->
                store.deleteAll()
                reload()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private class SessionAdapter(
        private val onClick: (FpsSessionStore.Info) -> Unit,
        private val onLongClick: (FpsSessionStore.Info) -> Unit,
    ) : RecyclerView.Adapter<SessionAdapter.Holder>() {

        private var items: List<FpsSessionStore.Info> = emptyList()
        private var selecting = false
        private var selectedIds: Set<String> = emptySet()

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val check: CheckBox = view.findViewById(R.id.session_check)
            val icon: ImageView = view.findViewById(android.R.id.icon)
            val title: TextView = view.findViewById(android.R.id.title)
            val summary: TextView = view.findViewById(android.R.id.summary)
            val average: TextView = view.findViewById(R.id.session_avg)
        }

        @Suppress("NotifyDataSetChanged") // the list is replaced as a whole
        fun submit(sessions: List<FpsSessionStore.Info>) {
            items = sessions
            notifyDataSetChanged()
        }

        @Suppress("NotifyDataSetChanged") // every row shows or hides its checkbox
        fun setSelection(selecting: Boolean, selectedIds: Set<String>) {
            this.selecting = selecting
            this.selectedIds = selectedIds.toSet()
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_fps_session, parent, false)
        )

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val info = items[position]
            val context = holder.itemView.context
            val pm = context.packageManager

            holder.icon.setImageDrawable(
                runCatching { pm.getApplicationIcon(info.packageName) }
                    .getOrDefault(pm.defaultActivityIcon)
            )
            holder.title.text = info.label

            val date = DateUtils.formatDateTime(
                context, info.startMs,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
            )
            val duration = DateUtils.formatElapsedTime(info.durationMs / 1000)
            holder.summary.text = context.getString(R.string.fps_stats_item_summary, date, duration)
            holder.average.text = String.format(Locale.getDefault(), "%.1f", info.avgFps)

            // Rows form one grouped list: big corners on the outside, small ones between rows.
            holder.itemView.setBackgroundResource(
                when {
                    items.size == 1 -> R.drawable.bg_session_item_single
                    position == 0 -> R.drawable.bg_session_item_top
                    position == items.lastIndex -> R.drawable.bg_session_item_bottom
                    else -> R.drawable.bg_session_item_middle
                }
            )

            val isSelected = selecting && info.id in selectedIds
            holder.check.visibility = if (selecting) View.VISIBLE else View.GONE
            holder.check.isChecked = isSelected
            holder.itemView.isActivated = isSelected

            holder.itemView.setOnClickListener { onClick(info) }
            holder.itemView.setOnLongClickListener { onLongClick(info); true }
        }
    }

    companion object {
        private const val KEY_SELECTING = "selecting"
        private const val KEY_SELECTED = "selected_ids"
    }
}
