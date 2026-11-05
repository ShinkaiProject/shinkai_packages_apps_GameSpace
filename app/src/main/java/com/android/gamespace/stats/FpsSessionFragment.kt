package com.android.gamespace.stats

import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.format.DateUtils
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.android.gamespace.R
import com.android.gamespace.data.FpsSamples
import com.android.gamespace.data.FpsSessionStore
import com.android.gamespace.data.FpsStats
import com.android.gamespace.data.Range
import com.android.gamespace.widget.LineChartView
import com.android.gamespace.widget.showMaterial3PopupMenu
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Statistics of one recorded session: a summary card, charts and a table of detailed figures.
 */
class FpsSessionFragment : Fragment(R.layout.fragment_fps_session) {

    private val store by lazy { FpsSessionStore(requireContext().applicationContext) }
    private var info: FpsSessionStore.Info? = null
    private var stats: FpsStats? = null
    private var descriptionInput: EditText? = null

    private val menuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.fps_session_menu, menu)
            menu.findItem(R.id.menu_more)?.actionView?.setOnClickListener { showMoreMenu(it) }
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
            R.id.menu_share -> { share(); true }
            R.id.menu_save -> { saveDescription(notify = true); true }
            else -> false
        }
    }

    /** The Material 3 popup behind the toolbar's "more" button. */
    private fun showMoreMenu(anchor: View) {
        showMaterial3PopupMenu(anchor, R.menu.fps_session_popup_menu) { item ->
            when (item.itemId) {
                R.id.menu_delete -> { confirmDelete(); true }
                else -> false
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        descriptionInput = view.findViewById(R.id.description_input)
        requireActivity().addMenuProvider(menuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)

        val charts = view.findViewById<View>(R.id.charts_container)
        val details = view.findViewById<View>(R.id.details_container)
        val tabCharts = view.findViewById<View>(R.id.tab_charts)
        val tabDetails = view.findViewById<View>(R.id.tab_details)
        fun select(showCharts: Boolean) {
            charts.visibility = if (showCharts) View.VISIBLE else View.GONE
            details.visibility = if (showCharts) View.GONE else View.VISIBLE
            tabCharts.isSelected = showCharts
            tabDetails.isSelected = !showCharts
        }
        tabCharts.setOnClickListener { select(true) }
        tabDetails.setOnClickListener { select(false) }
        select(true)

        load()
    }

    override fun onPause() {
        super.onPause()
        // Keep whatever was typed even if the user leaves without pressing save.
        saveDescription(notify = false)
    }

    override fun onDestroyView() {
        descriptionInput = null
        super.onDestroyView()
    }

    private fun load() {
        val activity = requireActivity()
        val id = activity.intent.getStringExtra(FpsSessionActivity.EXTRA_ID)
        Thread {
            val loaded = id?.let { store.info(it) }
            val samples = loaded?.let { store.loadSamples(it.id) }
            val computed = samples?.let { FpsStats.of(it) }
            activity.runOnUiThread {
                if (!isAdded || view == null) return@runOnUiThread
                if (loaded == null || samples == null || computed == null) {
                    Toast.makeText(activity, R.string.fps_stats_not_found, Toast.LENGTH_SHORT).show()
                    activity.finish()
                } else {
                    info = loaded
                    stats = computed
                    bind(loaded, samples, computed)
                }
            }
        }.start()
    }

    private fun bind(info: FpsSessionStore.Info, samples: FpsSamples, stats: FpsStats) {
        val view = requireView()
        val context = requireContext()
        requireActivity().title = info.label
        descriptionInput?.setText(info.description)

        val pm = context.packageManager
        view.findViewById<ImageView>(R.id.app_icon).setImageDrawable(
            runCatching { pm.getApplicationIcon(info.packageName) }
                .getOrDefault(pm.defaultActivityIcon)
        )
        view.findViewById<TextView>(R.id.summary_date).text = DateUtils.formatDateTime(
            context, info.startMs,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or
                DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_ABBREV_MONTH
        )
        view.findViewById<TextView>(R.id.summary_resolution).apply {
            text = getString(R.string.fps_stats_resolution, info.resolution)
            visibility = if (info.resolution.isEmpty()) View.GONE else View.VISIBLE
        }

        bindSummary(view, stats)
        bindCharts(view.findViewById(R.id.charts_container), samples, stats)
        bindDetails(view.findViewById(R.id.details_container), info, stats)
    }

    private fun bindSummary(view: View, stats: FpsStats) {
        val fpsUnit = getString(R.string.fps_stats_unit_fps)
        val row1 = view.findViewById<LinearLayout>(R.id.summary_row1)
        addCell(row1, R.string.fps_stats_label_max, fmt(stats.fps.max), fpsUnit)
        addCell(row1, R.string.fps_stats_label_min, fmt(stats.fps.min), fpsUnit)
        addCell(row1, R.string.fps_stats_label_average, fmt(stats.fps.avg), fpsUnit)
        addCell(row1, R.string.fps_stats_label_low1, fmt(stats.low1), fpsUnit)

        val row2 = view.findViewById<LinearLayout>(R.id.summary_row2)
        addCell(row2, R.string.fps_stats_label_low01, fmt(stats.low01), fpsUnit)
        addCell(row2, R.string.fps_stats_label_low5, fmt(stats.low5), fpsUnit)
        addCell(
            row2, R.string.fps_stats_label_max,
            stats.maxTemp?.let { fmt(it) } ?: NOT_AVAILABLE,
            getString(R.string.fps_stats_unit_temp)
        )
        addCell(
            row2, R.string.fps_stats_label_average,
            stats.power?.let { fmt(it.avg, 2) } ?: NOT_AVAILABLE,
            getString(R.string.fps_stats_unit_power)
        )
    }

    private fun addCell(row: LinearLayout, @StringRes label: Int, value: String, unit: String) {
        val cell = layoutInflater.inflate(R.layout.item_stat_cell, row, false)
        cell.findViewById<TextView>(R.id.cell_label).setText(label)
        cell.findViewById<TextView>(R.id.cell_value).text = value
        cell.findViewById<TextView>(R.id.cell_unit).text = unit
        row.addView(cell)
    }

    /**
     * The charts share their working arrays: one with the time of every sample and two to collect
     * the points of the chart being built. Nothing is allocated per point.
     */
    private class ChartInput(samples: FpsSamples) {
        val count = samples.size
        val seconds = FloatArray(count) { samples.elapsedMs[it] / 1000f }
        val scratchX = FloatArray(count)
        val scratchY = FloatArray(count)
    }

    private fun bindCharts(
        container: LinearLayout,
        samples: FpsSamples,
        stats: FpsStats,
    ) {
        val input = ChartInput(samples)
        val fps = samples.fps
        val frameTimes = FloatArray(input.count) {
            if (fps[it] > 0f) 1000f / fps[it] else Float.NaN
        }

        addChart(
            container, input, R.string.fps_stats_chart_fps, R.string.fps_stats_legend_fps, COLOR_FPS,
            fps, stats.fps, 0f
        ) { fmt(it) }
        addChart(
            container, input, R.string.fps_stats_chart_battery_temp,
            R.string.fps_stats_legend_battery_temp, COLOR_BATTERY_TEMP,
            samples.batteryTempC, stats.batteryTemp, 0f
        ) { fmt(it) + "°C" }
        addChart(
            container, input, R.string.fps_stats_chart_frame_time,
            R.string.fps_stats_legend_frame_time, COLOR_FRAME_TIME,
            frameTimes, stats.frameTime, 0f
        ) { fmt(it) + "ms" }
        addChart(
            container, input, R.string.fps_stats_chart_cpu_usage,
            R.string.fps_stats_legend_cpu_usage, COLOR_CPU_USAGE,
            samples.cpuUsagePct, stats.cpuUsage, 100f
        ) { fmt(it) + "%" }
        addChart(
            container, input, R.string.fps_stats_chart_cpu_temp,
            R.string.fps_stats_legend_cpu_temp, COLOR_CPU_TEMP,
            samples.cpuTempC, stats.cpuTemp, 0f
        ) { fmt(it) + "°C" }
        addChart(
            container, input, R.string.fps_stats_chart_power, R.string.fps_stats_legend_power,
            COLOR_POWER, samples.powerW, stats.power, 0f
        ) { fmt(it, 2) + "W" }
    }

    /** Adds a chart section; sections without data (sensor not readable) are left out. */
    private fun addChart(
        container: LinearLayout,
        input: ChartInput,
        @StringRes title: Int,
        @StringRes legend: Int,
        color: Int,
        values: FloatArray,
        range: Range?,
        minAxisMax: Float,
        format: (Float) -> String,
    ) {
        // Collect the points that have a value: a sensor that could not be read leaves NaN.
        var points = 0
        for (i in 0 until input.count) {
            val v = values[i]
            if (v.isNaN() || v.isInfinite()) continue
            input.scratchX[points] = input.seconds[i]
            input.scratchY[points] = v
            points++
        }
        if (points == 0 || range == null) return

        val section = layoutInflater.inflate(R.layout.item_chart_section, container, false)
        section.findViewById<TextView>(R.id.chart_title).setText(title)
        section.findViewById<LineChartView>(R.id.chart)
            .setData(input.scratchX, input.scratchY, color, minAxisMax, points)
        section.findViewById<View>(R.id.legend_dot).backgroundTintList = ColorStateList.valueOf(color)
        section.findViewById<TextView>(R.id.legend_name).setText(legend)
        section.findViewById<TextView>(R.id.stat_max).text =
            getString(R.string.fps_stats_chart_max, format(range.max))
        section.findViewById<TextView>(R.id.stat_min).text =
            getString(R.string.fps_stats_chart_min, format(range.min))
        section.findViewById<TextView>(R.id.stat_avg).text =
            getString(R.string.fps_stats_chart_avg, format(range.avg))
        container.addView(section)
    }

    private fun bindDetails(container: LinearLayout, info: FpsSessionStore.Info, stats: FpsStats) {
        header(container, R.string.fps_stats_section_session)
        row(container, R.string.fps_stats_row_duration, DateUtils.formatElapsedTime(stats.durationMs / 1000))
        row(container, R.string.fps_stats_row_samples, stats.sampleCount.toString())
        if (info.resolution.isNotEmpty()) row(container, R.string.fps_stats_row_resolution, info.resolution)
        row(container, R.string.fps_stats_row_package, info.packageName)

        header(container, R.string.fps_stats_section_fps)
        row(container, R.string.fps_stats_row_average, fmt(stats.fps.avg))
        row(container, R.string.fps_stats_row_minimum, fmt(stats.fps.min))
        row(container, R.string.fps_stats_row_maximum, fmt(stats.fps.max))
        row(container, R.string.fps_stats_row_low1, fmt(stats.low1))
        row(container, R.string.fps_stats_row_low01, fmt(stats.low01))
        row(container, R.string.fps_stats_row_low5, fmt(stats.low5))
        row(container, R.string.fps_stats_row_std_dev, fmt(stats.fpsStdDev))

        header(container, R.string.fps_stats_section_frame_time)
        row(container, R.string.fps_stats_row_average, fmt(stats.frameTime.avg) + " ms")
        row(container, R.string.fps_stats_row_minimum, fmt(stats.frameTime.min) + " ms")
        row(container, R.string.fps_stats_row_maximum, fmt(stats.frameTime.max) + " ms")
        row(container, R.string.fps_stats_row_p95, fmt(stats.frameTimeP95) + " ms")
        row(container, R.string.fps_stats_row_p99, fmt(stats.frameTimeP99) + " ms")

        rangeRows(container, R.string.fps_stats_section_battery_temp, stats.batteryTemp, 1, " °C")
        rangeRows(container, R.string.fps_stats_section_cpu_temp, stats.cpuTemp, 1, " °C")
        rangeRows(container, R.string.fps_stats_section_cpu_usage, stats.cpuUsage, 1, " %")
        rangeRows(container, R.string.fps_stats_section_power, stats.power, 2, " W")
    }

    private fun rangeRows(
        container: LinearLayout, @StringRes title: Int, range: Range?, digits: Int, unit: String
    ) {
        if (range == null) return
        header(container, title)
        row(container, R.string.fps_stats_row_average, fmt(range.avg, digits) + unit)
        row(container, R.string.fps_stats_row_minimum, fmt(range.min, digits) + unit)
        row(container, R.string.fps_stats_row_maximum, fmt(range.max, digits) + unit)
    }

    private fun header(container: LinearLayout, @StringRes title: Int) {
        val header = layoutInflater.inflate(R.layout.item_detail_header, container, false) as TextView
        header.setText(title)
        container.addView(header)
    }

    private fun row(container: LinearLayout, @StringRes label: Int, value: String) {
        val row = layoutInflater.inflate(R.layout.item_detail_row, container, false)
        row.findViewById<TextView>(R.id.row_label).setText(label)
        row.findViewById<TextView>(R.id.row_value).text = value
        container.addView(row)
    }

    private fun saveDescription(notify: Boolean) {
        val current = info ?: return
        val text = descriptionInput?.text?.toString()?.trim() ?: return
        if (text != current.description) {
            store.setDescription(current.id, text)
            info = store.info(current.id) ?: current
        }
        if (notify) {
            Toast.makeText(requireContext(), R.string.fps_stats_saved, Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDelete() {
        val current = info ?: return
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.fps_stats_delete_message)
            .setPositiveButton(R.string.fps_stats_delete) { _, _ ->
                store.delete(current.id)
                info = null
                requireActivity().finish()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Shares the session's CSV together with a one-line summary. */
    private fun share() {
        val current = info ?: return
        val currentStats = stats ?: return
        val context = requireContext()
        val source = store.csvFile(current.id)?.takeIf { it.exists() } ?: return

        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(current.startMs))
        val file = File(dir, "fps_${current.packageName}_$stamp.csv")
        source.copyTo(file, overwrite = true)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        val text = getString(
            R.string.fps_stats_share_text, current.label,
            fmt(currentStats.fps.avg), fmt(currentStats.fps.min),
            fmt(currentStats.fps.max), fmt(currentStats.low1)
        ) + current.description.takeIf { it.isNotEmpty() }?.let { "\n$it" }.orEmpty()

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, current.label)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, getString(R.string.fps_stats_share)))
    }

    private fun fmt(value: Float, digits: Int = 1): String =
        String.format(Locale.getDefault(), "%.${digits}f", value)

    companion object {
        private const val NOT_AVAILABLE = "—"

        private const val COLOR_FPS = 0xFF4CAF50.toInt()
        private const val COLOR_BATTERY_TEMP = 0xFFFF9800.toInt()
        private const val COLOR_FRAME_TIME = 0xFF42A5F5.toInt()
        private const val COLOR_CPU_USAGE = 0xFF7986CB.toInt()
        private const val COLOR_CPU_TEMP = 0xFF26C6DA.toInt()
        private const val COLOR_POWER = 0xFFBA68C8.toInt()
    }
}
