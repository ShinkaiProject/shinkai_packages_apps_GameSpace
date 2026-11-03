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
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.gamespace.R
import com.android.gamespace.data.FpsSessionStore
import java.util.Locale

/** The list of recorded FPS sessions, newest first. */
class FpsStatsFragment : Fragment(R.layout.fragment_fps_stats) {

    private val store by lazy { FpsSessionStore(requireContext().applicationContext) }
    private val adapter = SessionAdapter { openSession(it) }
    private var emptyView: View? = null
    private var hasSessions = false

    private val menuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.fps_stats_menu, menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            if (menuItem.itemId != R.id.menu_delete_all) return false
            confirmDeleteAll()
            return true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity?.title = getString(R.string.fps_statistics_title)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        emptyView = view.findViewById(R.id.empty_view)
        view.findViewById<RecyclerView>(R.id.session_list).also {
            it.layoutManager = LinearLayoutManager(requireContext())
            it.adapter = adapter
        }
        requireActivity().addMenuProvider(menuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)
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
        Thread {
            val sessions = store.list()
            activity.runOnUiThread {
                if (!isAdded || view == null) return@runOnUiThread
                hasSessions = sessions.isNotEmpty()
                adapter.submit(sessions)
                emptyView?.visibility = if (hasSessions) View.GONE else View.VISIBLE
            }
        }.start()
    }

    private fun openSession(info: FpsSessionStore.Info) {
        startActivity(
            Intent(requireContext(), FpsSessionActivity::class.java)
                .putExtra(FpsSessionActivity.EXTRA_ID, info.id)
        )
    }

    private fun confirmDeleteAll() {
        if (!hasSessions) return
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
        private val onClick: (FpsSessionStore.Info) -> Unit
    ) : RecyclerView.Adapter<SessionAdapter.Holder>() {

        private var items: List<FpsSessionStore.Info> = emptyList()

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
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
            holder.itemView.setOnClickListener { onClick(info) }
        }
    }
}
