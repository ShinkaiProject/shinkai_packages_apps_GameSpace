package com.android.gamespace.ui.stats

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.android.gamespace.R
import com.android.gamespace.data.FpsSessionStore
import com.android.gamespace.data.FpsStats
import com.android.gamespace.data.Range
import com.android.gamespace.ui.components.AppIcon
import com.android.gamespace.ui.components.GameSpaceScaffold
import com.android.gamespace.ui.components.LifecycleEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val NOT_AVAILABLE = "—"

private val ColorFps = Color(0xFF4CAF50)
private val ColorBatteryTemp = Color(0xFFFF9800)
private val ColorFrameTime = Color(0xFF42A5F5)
private val ColorCpuUsage = Color(0xFF7986CB)
private val ColorCpuTemp = Color(0xFF26C6DA)
private val ColorPower = Color(0xFFBA68C8)

/** One chart section: the thinned-out points to draw and the figures printed below them. */
private class ChartSpec(
    @StringRes val title: Int,
    @StringRes val legend: Int,
    val color: Color,
    val series: ChartDecimator.Series,
    val range: Range,
    val minAxisMax: Float,
    val unit: String,
    val digits: Int,
)

/** A session as it is shown: what is known of it, its figures and its charts. */
private class LoadedSession(
    val info: FpsSessionStore.Info,
    val stats: FpsStats,
    val charts: List<ChartSpec>,
)

/**
 * The statistics of one recorded session: a description to edit, a summary card, and either the
 * charts or a table of detailed figures.
 */
@Composable
fun FpsSessionScreen(
    store: FpsSessionStore,
    sessionId: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf<LoadedSession?>(null) }
    // The text field's content; null until the session is there to fill it.
    var description by rememberSaveable { mutableStateOf<String?>(null) }
    // What the store has, so that unchanged text is not written again.
    var storedDescription by remember { mutableStateOf("") }
    var showCharts by rememberSaveable { mutableStateOf(true) }
    var confirmDelete by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(sessionId) {
        val loaded = withContext(Dispatchers.IO) { loadSession(context, store, sessionId) }
        if (loaded == null) {
            Toast.makeText(context, R.string.fps_stats_not_found, Toast.LENGTH_SHORT).show()
            onBack()
        } else {
            session = loaded
            storedDescription = loaded.info.description
            if (description == null) description = loaded.info.description
        }
    }

    fun saveDescription(notify: Boolean) {
        val current = session ?: return
        val text = description?.trim() ?: return
        if (text != storedDescription) {
            store.setDescription(current.info.id, text)
            storedDescription = text
        }
        if (notify) Toast.makeText(context, R.string.fps_stats_saved, Toast.LENGTH_SHORT).show()
    }

    // Keep whatever was typed even if the user leaves without pressing save.
    LifecycleEffect(onPause = { saveDescription(notify = false) })

    GameSpaceScaffold(
        title = session?.info?.label.orEmpty(),
        onBack = onBack,
        actions = {
            val loaded = session
            IconButton(
                enabled = loaded != null,
                onClick = {
                    if (loaded != null) {
                        scope.launch {
                            val send = withContext(Dispatchers.IO) {
                                buildShareIntent(context, store, loaded, description?.trim().orEmpty())
                            }
                            if (send != null) {
                                context.startActivity(
                                    Intent.createChooser(send, context.getString(R.string.fps_stats_share))
                                )
                            }
                        }
                    }
                },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_share),
                    contentDescription = stringResource(R.string.fps_stats_share),
                )
            }
            IconButton(enabled = loaded != null, onClick = { saveDescription(notify = true) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_save),
                    contentDescription = stringResource(R.string.fps_stats_save),
                )
            }
            Box {
                IconButton(enabled = loaded != null, onClick = { menuOpen = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert),
                        contentDescription = stringResource(R.string.more_options),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.fps_stats_delete)) },
                        onClick = {
                            menuOpen = false
                            confirmDelete = true
                        },
                    )
                }
            }
        },
    ) { padding ->
        val loaded = session
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = description.orEmpty(),
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.fps_stats_description_hint)) },
                maxLines = 3,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
            )

            if (loaded != null) {
                Spacer(Modifier.height(16.dp))
                SummaryCard(loaded)

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SessionTab(R.string.fps_stats_tab_charts, showCharts, Modifier.weight(1f)) {
                        showCharts = true
                    }
                    SessionTab(R.string.fps_stats_tab_details, !showCharts, Modifier.weight(1f)) {
                        showCharts = false
                    }
                }

                if (showCharts) {
                    loaded.charts.forEach { ChartSection(it) }
                } else {
                    Details(loaded)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.fps_stats_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    session?.let { store.delete(it.info.id) }
                    session = null
                    onBack()
                }) { Text(stringResource(R.string.fps_stats_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun SummaryCard(session: LoadedSession) {
    val context = LocalContext.current
    val info = session.info
    val stats = session.stats
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val fpsUnit = stringResource(R.string.fps_stats_unit_fps)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(info.packageName, size = 32.dp)
                Text(
                    text = remember(info.startMs) {
                        DateUtils.formatDateTime(
                            context, info.startMs,
                            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or
                                DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_ABBREV_MONTH
                        )
                    },
                    color = secondary,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                )
                if (info.resolution.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.fps_stats_resolution, info.resolution),
                        color = secondary,
                        fontSize = 12.sp,
                    )
                }
            }

            Text(
                text = stringResource(R.string.fps_stats_session_summary),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp),
            )

            Row(Modifier.padding(top = 12.dp)) {
                StatCell(R.string.fps_stats_label_max, fmt(stats.fps.max), fpsUnit)
                StatCell(R.string.fps_stats_label_min, fmt(stats.fps.min), fpsUnit)
                StatCell(R.string.fps_stats_label_average, fmt(stats.fps.avg), fpsUnit)
                StatCell(R.string.fps_stats_label_low1, fmt(stats.low1), fpsUnit)
            }
            Row(Modifier.padding(top = 8.dp)) {
                StatCell(R.string.fps_stats_label_low01, fmt(stats.low01), fpsUnit)
                StatCell(R.string.fps_stats_label_low5, fmt(stats.low5), fpsUnit)
                StatCell(
                    R.string.fps_stats_label_max,
                    stats.maxTemp?.let { fmt(it) } ?: NOT_AVAILABLE,
                    stringResource(R.string.fps_stats_unit_temp),
                )
                StatCell(
                    R.string.fps_stats_label_average,
                    stats.power?.let { fmt(it.avg, 2) } ?: NOT_AVAILABLE,
                    stringResource(R.string.fps_stats_unit_power),
                )
            }
        }
    }
}

@Composable
private fun RowScope.StatCell(@StringRes label: Int, value: String, unit: String) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .weight(1f)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(label),
            color = secondary,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        Text(
            text = unit,
            color = secondary,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SessionTab(
    @StringRes title: Int,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) colors.primaryContainer else colors.surfaceColorAtElevation(3.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(title),
            color = if (selected) colors.onPrimaryContainer else colors.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ChartSection(chart: ChartSpec) {
    val range = chart.range
    fun format(value: Float) = fmt(value, chart.digits) + chart.unit

    Column(Modifier.padding(top = 24.dp)) {
        Text(
            text = stringResource(chart.title),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        LineChart(
            series = chart.series,
            color = chart.color,
            minAxisMax = chart.minAxisMax,
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .height(190.dp),
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(chart.color)
            )
            Text(
                text = stringResource(chart.legend),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        Row(Modifier.padding(top = 12.dp)) {
            for (text in listOf(
                stringResource(R.string.fps_stats_chart_max, format(range.max)),
                stringResource(R.string.fps_stats_chart_min, format(range.min)),
                stringResource(R.string.fps_stats_chart_avg, format(range.avg)),
            )) {
                Text(
                    text = text,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Details(session: LoadedSession) {
    val info = session.info
    val stats = session.stats
    Column {
        DetailHeader(R.string.fps_stats_section_session)
        DetailRow(R.string.fps_stats_row_duration, DateUtils.formatElapsedTime(stats.durationMs / 1000))
        DetailRow(R.string.fps_stats_row_samples, stats.sampleCount.toString())
        if (info.resolution.isNotEmpty()) DetailRow(R.string.fps_stats_row_resolution, info.resolution)
        DetailRow(R.string.fps_stats_row_package, info.packageName)

        DetailHeader(R.string.fps_stats_section_fps)
        DetailRow(R.string.fps_stats_row_average, fmt(stats.fps.avg))
        DetailRow(R.string.fps_stats_row_minimum, fmt(stats.fps.min))
        DetailRow(R.string.fps_stats_row_maximum, fmt(stats.fps.max))
        DetailRow(R.string.fps_stats_row_low1, fmt(stats.low1))
        DetailRow(R.string.fps_stats_row_low01, fmt(stats.low01))
        DetailRow(R.string.fps_stats_row_low5, fmt(stats.low5))
        DetailRow(R.string.fps_stats_row_std_dev, fmt(stats.fpsStdDev))

        DetailHeader(R.string.fps_stats_section_frame_time)
        DetailRow(R.string.fps_stats_row_average, fmt(stats.frameTime.avg) + " ms")
        DetailRow(R.string.fps_stats_row_minimum, fmt(stats.frameTime.min) + " ms")
        DetailRow(R.string.fps_stats_row_maximum, fmt(stats.frameTime.max) + " ms")
        DetailRow(R.string.fps_stats_row_p95, fmt(stats.frameTimeP95) + " ms")
        DetailRow(R.string.fps_stats_row_p99, fmt(stats.frameTimeP99) + " ms")

        RangeRows(R.string.fps_stats_section_battery_temp, stats.batteryTemp, 1, " °C")
        RangeRows(R.string.fps_stats_section_cpu_temp, stats.cpuTemp, 1, " °C")
        RangeRows(R.string.fps_stats_section_cpu_usage, stats.cpuUsage, 1, " %")
        RangeRows(R.string.fps_stats_section_power, stats.power, 2, " W")
    }
}

@Composable
private fun RangeRows(@StringRes title: Int, range: Range?, digits: Int, unit: String) {
    if (range == null) return
    DetailHeader(title)
    DetailRow(R.string.fps_stats_row_average, fmt(range.avg, digits) + unit)
    DetailRow(R.string.fps_stats_row_minimum, fmt(range.min, digits) + unit)
    DetailRow(R.string.fps_stats_row_maximum, fmt(range.max, digits) + unit)
}

@Composable
private fun DetailHeader(@StringRes title: Int) {
    Text(
        text = stringResource(title),
        color = MaterialTheme.colorScheme.primary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun DetailRow(@StringRes label: Int, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(label),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Reads the session [id] and prepares everything its screen shows; null when it is gone. */
private fun loadSession(context: Context, store: FpsSessionStore, id: String?): LoadedSession? {
    val info = id?.let { store.info(it) } ?: return null
    val samples = store.loadSamples(info.id)
    val stats = FpsStats.of(samples) ?: return null

    // The charts share their working arrays: the time of every sample and the points of the chart
    // being built. Nothing is allocated per point beyond the arrays of the thinned-out series.
    val count = samples.size
    val seconds = FloatArray(count) { samples.elapsedMs[it] / 1000f }
    val scratchX = FloatArray(count)
    val scratchY = FloatArray(count)
    val fps = samples.fps
    val frameTimes = FloatArray(count) { if (fps[it] > 0f) 1000f / fps[it] else Float.NaN }

    val charts = ArrayList<ChartSpec>(6)
    fun add(
        @StringRes title: Int,
        @StringRes legend: Int,
        color: Color,
        values: FloatArray,
        range: Range?,
        minAxisMax: Float,
        unit: String,
        digits: Int = 1,
    ) {
        // Collect the points that have a value: a sensor that could not be read leaves NaN.
        var points = 0
        for (i in 0 until count) {
            val v = values[i]
            if (v.isNaN() || v.isInfinite()) continue
            scratchX[points] = seconds[i]
            scratchY[points] = v
            points++
        }
        if (points == 0 || range == null) return
        charts += ChartSpec(
            title, legend, color,
            ChartDecimator.decimate(scratchX, scratchY, points, CHART_MAX_POINTS),
            range, minAxisMax, unit, digits,
        )
    }

    add(R.string.fps_stats_chart_fps, R.string.fps_stats_legend_fps, ColorFps, fps, stats.fps, 0f, "")
    add(
        R.string.fps_stats_chart_battery_temp, R.string.fps_stats_legend_battery_temp,
        ColorBatteryTemp, samples.batteryTempC, stats.batteryTemp, 0f, "°C"
    )
    add(
        R.string.fps_stats_chart_frame_time, R.string.fps_stats_legend_frame_time,
        ColorFrameTime, frameTimes, stats.frameTime, 0f, "ms"
    )
    add(
        R.string.fps_stats_chart_cpu_usage, R.string.fps_stats_legend_cpu_usage,
        ColorCpuUsage, samples.cpuUsagePct, stats.cpuUsage, 100f, "%"
    )
    add(
        R.string.fps_stats_chart_cpu_temp, R.string.fps_stats_legend_cpu_temp,
        ColorCpuTemp, samples.cpuTempC, stats.cpuTemp, 0f, "°C"
    )
    add(
        R.string.fps_stats_chart_power, R.string.fps_stats_legend_power,
        ColorPower, samples.powerW, stats.power, 0f, "W", digits = 2
    )
    return LoadedSession(info, stats, charts)
}

/** The intent that shares the session's CSV together with a one-line summary; null without CSV. */
private fun buildShareIntent(
    context: Context,
    store: FpsSessionStore,
    session: LoadedSession,
    description: String,
): Intent? {
    val info = session.info
    val stats = session.stats
    val source = store.csvFile(info.id)?.takeIf { it.exists() } ?: return null

    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(info.startMs))
    val file = File(dir, "fps_${info.packageName}_$stamp.csv")
    source.copyTo(file, overwrite = true)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    val text = context.getString(
        R.string.fps_stats_share_text, info.label,
        fmt(stats.fps.avg), fmt(stats.fps.min), fmt(stats.fps.max), fmt(stats.low1)
    ) + description.takeIf { it.isNotEmpty() }?.let { "\n$it" }.orEmpty()

    return Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, info.label)
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private fun fmt(value: Float, digits: Int = 1): String =
    String.format(Locale.getDefault(), "%.${digits}f", value)
