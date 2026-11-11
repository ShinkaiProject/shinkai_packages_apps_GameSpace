package com.android.gamespace.stats

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Paint as AndroidPaint
import android.os.Bundle
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.android.gamespace.R
import com.android.gamespace.data.FpsSamples
import com.android.gamespace.data.FpsSessionStore
import com.android.gamespace.data.FpsStats
import com.android.gamespace.data.Range
import com.android.gamespace.ui.components.DrawableImage
import com.android.gamespace.ui.components.GameSpaceSettingsScaffold
import com.android.gamespace.ui.theme.ChartBatteryTempColor
import com.android.gamespace.ui.theme.ChartCpuTempColor
import com.android.gamespace.ui.theme.ChartCpuUsageColor
import com.android.gamespace.ui.theme.ChartFpsColor
import com.android.gamespace.ui.theme.ChartFrameTimeColor
import com.android.gamespace.ui.theme.ChartPowerColor
import com.android.gamespace.ui.theme.GameSpaceTheme
import com.android.gamespace.widget.ChartDecimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/** Menampilkan statistik dan grafik dari satu sesi rekaman FPS ([EXTRA_ID]). */
class FpsSessionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val sessionId = intent?.getStringExtra(EXTRA_ID).orEmpty()
        setContent {
            GameSpaceTheme {
                FpsSessionScreen(
                    sessionId = sessionId,
                    onBackClick = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_ID = "session_id"
    }
}

private data class ChartSectionData(
    val titleRes: Int,
    val legendRes: Int,
    val color: Color,
    val xSeconds: FloatArray,
    val values: FloatArray,
    val count: Int,
    val range: Range,
    val minAxisMax: Float,
    val formatValue: (Float) -> String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FpsSessionScreen(
    sessionId: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { FpsSessionStore(context.applicationContext) }

    var info by remember { mutableStateOf<FpsSessionStore.Info?>(null) }
    var samples by remember { mutableStateOf<FpsSamples?>(null) }
    var stats by remember { mutableStateOf<FpsStats?>(null) }
    var descriptionText by rememberSaveable { mutableStateOf("") }
    var showChartsTab by rememberSaveable { mutableStateOf(true) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(sessionId) {
        val loadedTriple = withContext(Dispatchers.IO) {
            val loadedInfo = sessionId.takeIf { it.isNotEmpty() }?.let { store.info(it) }
                ?: return@withContext null
            val loadedSamples = store.loadSamples(loadedInfo.id)
            val computedStats = FpsStats.of(loadedSamples) ?: return@withContext null
            Triple(loadedInfo, loadedSamples, computedStats)
        }
        if (loadedTriple == null) {
            Toast.makeText(context, R.string.fps_stats_not_found, Toast.LENGTH_SHORT).show()
            onBackClick()
        } else {
            info = loadedTriple.first
            samples = loadedTriple.second
            stats = loadedTriple.third
            descriptionText = loadedTriple.first.description
        }
    }

    fun saveDescription(notify: Boolean) {
        val current = info ?: return
        val trimmed = descriptionText.trim()
        if (trimmed != current.description) {
            store.setDescription(current.id, trimmed)
            info = store.info(current.id) ?: current
        }
        if (notify) {
            Toast.makeText(context, R.string.fps_stats_saved, Toast.LENGTH_SHORT).show()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                saveDescription(notify = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            saveDescription(notify = false)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val currentInfo = info
    val currentStats = stats
    val currentSamples = samples

    val appIcon = remember(currentInfo?.packageName) {
        val pkg = currentInfo?.packageName ?: return@remember null
        val pm = context.packageManager
        runCatching { pm.getApplicationIcon(pkg) }.getOrDefault(pm.defaultActivityIcon)
    }

    GameSpaceSettingsScaffold(
        title = currentInfo?.label ?: stringResource(R.string.fps_statistics_title),
        onBackClick = onBackClick,
        actions = {
            if (currentInfo != null && currentStats != null) {
                IconButton(onClick = { shareSession(context, store, currentInfo, currentStats) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_share),
                        contentDescription = stringResource(R.string.fps_stats_share)
                    )
                }
                IconButton(onClick = { saveDescription(notify = true) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_save),
                        contentDescription = stringResource(R.string.fps_stats_save)
                    )
                }
                Box {
                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_more_vert),
                            contentDescription = stringResource(R.string.more_options)
                        )
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.fps_stats_delete)) },
                            onClick = {
                                showOverflowMenu = false
                                showDeleteDialog = true
                            }
                        )
                    }
                }
            }
        }
    ) { paddingModifier ->
        if (currentInfo != null && currentStats != null && currentSamples != null) {
            val chartSections = remember(currentSamples, currentStats) {
                buildChartSections(currentSamples, currentStats)
            }

            LazyColumn(
                modifier = paddingModifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Description input
                item(key = "description_input") {
                    OutlinedTextField(
                        value = descriptionText,
                        onValueChange = { descriptionText = it },
                        label = { Text(stringResource(R.string.fps_stats_description_hint)) },
                        maxLines = 3,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Session Summary Card
                item(key = "summary_card") {
                    SessionSummaryCard(
                        info = currentInfo,
                        stats = currentStats,
                        appIcon = appIcon
                    )
                }

                // Segmented Button Tabs (Charts vs Detailed Stats)
                item(key = "tabs") {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = showChartsTab,
                            onClick = { showChartsTab = true },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) {
                            Text(stringResource(R.string.fps_stats_tab_charts))
                        }
                        SegmentedButton(
                            selected = !showChartsTab,
                            onClick = { showChartsTab = false },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) {
                            Text(stringResource(R.string.fps_stats_tab_details))
                        }
                    }
                }

                if (showChartsTab) {
                    chartSections.forEachIndexed { idx, section ->
                        item(key = "chart_$idx") {
                            ChartSectionCard(section = section)
                        }
                    }
                } else {
                    item(key = "details_card") {
                        DetailedStatsCard(info = currentInfo, stats = currentStats)
                    }
                }

                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }

    if (showDeleteDialog && currentInfo != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            text = { Text(stringResource(R.string.fps_stats_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        store.delete(currentInfo.id)
                        info = null
                        onBackClick()
                    }
                ) {
                    Text(stringResource(R.string.fps_stats_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun SessionSummaryCard(
    info: FpsSessionStore.Info,
    stats: FpsStats,
    appIcon: android.graphics.drawable.Drawable?
) {
    val context = LocalContext.current
    val fpsUnit = stringResource(R.string.fps_stats_unit_fps)
    val dateFormatted = remember(info.startMs) {
        DateUtils.formatDateTime(
            context,
            info.startMs,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or
                DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_ABBREV_MONTH
        )
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                DrawableImage(
                    drawable = appIcon,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = dateFormatted,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (info.resolution.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.fps_stats_resolution, info.resolution),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.fps_stats_session_summary),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCell(
                    label = stringResource(R.string.fps_stats_label_max),
                    value = fmt(stats.fps.max),
                    unit = fpsUnit,
                    modifier = Modifier.weight(1f)
                )
                StatCell(
                    label = stringResource(R.string.fps_stats_label_min),
                    value = fmt(stats.fps.min),
                    unit = fpsUnit,
                    modifier = Modifier.weight(1f)
                )
                StatCell(
                    label = stringResource(R.string.fps_stats_label_average),
                    value = fmt(stats.fps.avg),
                    unit = fpsUnit,
                    modifier = Modifier.weight(1f)
                )
                StatCell(
                    label = stringResource(R.string.fps_stats_label_low1),
                    value = fmt(stats.low1),
                    unit = fpsUnit,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCell(
                    label = stringResource(R.string.fps_stats_label_low01),
                    value = fmt(stats.low01),
                    unit = fpsUnit,
                    modifier = Modifier.weight(1f)
                )
                StatCell(
                    label = stringResource(R.string.fps_stats_label_low5),
                    value = fmt(stats.low5),
                    unit = fpsUnit,
                    modifier = Modifier.weight(1f)
                )
                StatCell(
                    label = stringResource(R.string.fps_stats_label_max),
                    value = stats.maxTemp?.let { fmt(it) } ?: NOT_AVAILABLE,
                    unit = stringResource(R.string.fps_stats_unit_temp),
                    modifier = Modifier.weight(1f)
                )
                StatCell(
                    label = stringResource(R.string.fps_stats_label_average),
                    value = stats.power?.let { fmt(it, 2) } ?: NOT_AVAILABLE,
                    unit = stringResource(R.string.fps_stats_unit_power),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        Text(
            text = unit,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ChartSectionCard(section: ChartSectionData) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(section.titleRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))
            LineChartComposable(
                xSeconds = section.xSeconds,
                values = section.values,
                count = section.count,
                lineColor = section.color,
                minAxisMax = section.minAxisMax,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(section.color)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(section.legendRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.fps_stats_chart_max, section.formatValue(section.range.max)),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.fps_stats_chart_min, section.formatValue(section.range.min)),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.fps_stats_chart_avg, section.formatValue(section.range.avg)),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Grafik garis terisi murni menggunakan Compose Canvas dengan desimasi titik via [ChartDecimator].
 */
@Composable
fun LineChartComposable(
    xSeconds: FloatArray,
    values: FloatArray,
    count: Int,
    lineColor: Color,
    minAxisMax: Float = 0f,
    modifier: Modifier = Modifier
) {
    val series = remember(xSeconds, values, count) {
        ChartDecimator.decimate(xSeconds, values, count, 1200)
    }
    val dataMax = remember(series) { series.y.maxOrNull() ?: 0f }
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current

    val labelPaint = remember(labelColor, density) {
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = labelColor.toArgb()
            textSize = with(density) { 10.sp.toPx() }
        }
    }

    Canvas(modifier = modifier) {
        val (yMax, yStep) = niceAxis(max(dataMax, minAxisMax))
        val duration = series.x.lastOrNull() ?: 0f
        val inMinutes = duration > 120f
        val xUnit = if (inMinutes) 60f else 1f
        val xMax = max(duration / xUnit, 1f)
        var xStep = niceAxis(xMax).second
        if (!inMinutes) xStep = max(xStep, 1f)

        val yLabels = ticks(yMax, yStep).map { it to formatTick(it, yStep) }
        val labelWidth = yLabels.maxOfOrNull { labelPaint.measureText(it.second) } ?: 0f
        val labelHeight = labelPaint.fontMetrics.let { it.descent - it.ascent }

        val left = labelWidth + 8.dp.toPx()
        val right = size.width - 12.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - labelHeight - 8.dp.toPx()
        if (right <= left || bottom <= top) return@Canvas

        fun px(sec: Float) = left + (sec / xUnit) / xMax * (right - left)
        fun py(v: Float) = bottom - v / yMax * (bottom - top)

        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f)
        val gridColor = labelColor.copy(alpha = 0.24f)

        // Y grid & labels
        for ((value, label) in yLabels) {
            val y = py(value)
            drawLine(
                color = gridColor,
                start = Offset(left, y),
                end = Offset(right, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashEffect
            )
            drawContext.canvas.nativeCanvas.drawText(
                label,
                left - 6.dp.toPx() - labelPaint.measureText(label),
                y + labelHeight / 3f,
                labelPaint
            )
        }

        // X grid & labels
        val suffix = if (inMinutes) "m" else "s"
        for (t in ticks(xMax, xStep)) {
            val x = px(t * xUnit)
            drawLine(
                color = gridColor,
                start = Offset(x, top),
                end = Offset(x, bottom),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashEffect
            )
            val label = formatTick(t, xStep) + suffix
            val w = labelPaint.measureText(label)
            drawContext.canvas.nativeCanvas.drawText(
                label,
                (x - w / 2f).coerceIn(0f, size.width - w),
                bottom + 6.dp.toPx() + labelHeight,
                labelPaint
            )
        }

        when (series.y.size) {
            0 -> return@Canvas
            1 -> {
                drawCircle(
                    color = lineColor,
                    radius = 3.dp.toPx(),
                    center = Offset(px(series.x[0]), py(series.y[0]))
                )
                return@Canvas
            }
        }

        val linePath = Path()
        val fillPath = Path()
        for (i in series.y.indices) {
            val x = px(series.x[i])
            val y = py(series.y[i])
            if (i == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, bottom)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(px(series.x.last()), bottom)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.45f), lineColor.copy(alpha = 0.04f)),
                startY = top,
                endY = bottom
            )
        )
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(
                width = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

@Composable
private fun DetailedStatsCard(
    info: FpsSessionStore.Info,
    stats: FpsStats
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            DetailSectionHeader(stringResource(R.string.fps_stats_section_session))
            DetailRow(stringResource(R.string.fps_stats_row_duration), DateUtils.formatElapsedTime(stats.durationMs / 1000))
            DetailRow(stringResource(R.string.fps_stats_row_samples), stats.sampleCount.toString())
            if (info.resolution.isNotEmpty()) {
                DetailRow(stringResource(R.string.fps_stats_row_resolution), info.resolution)
            }
            DetailRow(stringResource(R.string.fps_stats_row_package), info.packageName)

            DetailSectionHeader(stringResource(R.string.fps_stats_section_fps))
            DetailRow(stringResource(R.string.fps_stats_row_average), fmt(stats.fps.avg))
            DetailRow(stringResource(R.string.fps_stats_row_minimum), fmt(stats.fps.min))
            DetailRow(stringResource(R.string.fps_stats_row_maximum), fmt(stats.fps.max))
            DetailRow(stringResource(R.string.fps_stats_row_low1), fmt(stats.low1))
            DetailRow(stringResource(R.string.fps_stats_row_low01), fmt(stats.low01))
            DetailRow(stringResource(R.string.fps_stats_row_low5), fmt(stats.low5))
            DetailRow(stringResource(R.string.fps_stats_row_std_dev), fmt(stats.fpsStdDev))

            DetailSectionHeader(stringResource(R.string.fps_stats_section_frame_time))
            DetailRow(stringResource(R.string.fps_stats_row_average), fmt(stats.frameTime.avg) + " ms")
            DetailRow(stringResource(R.string.fps_stats_row_minimum), fmt(stats.frameTime.min) + " ms")
            DetailRow(stringResource(R.string.fps_stats_row_maximum), fmt(stats.frameTime.max) + " ms")
            DetailRow(stringResource(R.string.fps_stats_row_p95), fmt(stats.frameTimeP95) + " ms")
            DetailRow(stringResource(R.string.fps_stats_row_p99), fmt(stats.frameTimeP99) + " ms")

            RangeDetailSection(R.string.fps_stats_section_battery_temp, stats.batteryTemp, 1, " °C")
            RangeDetailSection(R.string.fps_stats_section_cpu_temp, stats.cpuTemp, 1, " °C")
            RangeDetailSection(R.string.fps_stats_section_cpu_usage, stats.cpuUsage, 1, " %")
            RangeDetailSection(R.string.fps_stats_section_power, stats.power, 2, " W")
        }
    }
}

@Composable
private fun RangeDetailSection(
    titleRes: Int,
    range: Range?,
    digits: Int,
    unit: String
) {
    if (range == null) return
    DetailSectionHeader(stringResource(titleRes))
    DetailRow(stringResource(R.string.fps_stats_row_average), fmt(range.avg, digits) + unit)
    DetailRow(stringResource(R.string.fps_stats_row_minimum), fmt(range.min, digits) + unit)
    DetailRow(stringResource(R.string.fps_stats_row_maximum), fmt(range.max, digits) + unit)
}

@Composable
private fun DetailSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun buildChartSections(
    samples: FpsSamples,
    stats: FpsStats
): List<ChartSectionData> {
    val n = samples.size
    val seconds = FloatArray(n) { samples.elapsedMs[it] / 1000f }
    val frameTimes = FloatArray(n) { if (samples.fps[it] > 0f) 1000f / samples.fps[it] else Float.NaN }
    val out = mutableListOf<ChartSectionData>()

    fun addSection(
        titleRes: Int,
        legendRes: Int,
        color: Color,
        rawValues: FloatArray,
        range: Range?,
        minAxisMax: Float,
        formatValue: (Float) -> String
    ) {
        if (range == null) return
        val xArr = FloatArray(n)
        val yArr = FloatArray(n)
        var pts = 0
        for (i in 0 until n) {
            val v = rawValues[i]
            if (v.isNaN() || v.isInfinite()) continue
            xArr[pts] = seconds[i]
            yArr[pts] = v
            pts++
        }
        if (pts == 0) return
        out.add(
            ChartSectionData(
                titleRes = titleRes,
                legendRes = legendRes,
                color = color,
                xSeconds = xArr,
                values = yArr,
                count = pts,
                range = range,
                minAxisMax = minAxisMax,
                formatValue = formatValue
            )
        )
    }

    addSection(
        R.string.fps_stats_chart_fps,
        R.string.fps_stats_legend_fps,
        ChartFpsColor,
        samples.fps,
        stats.fps,
        0f
    ) { fmt(it) }

    addSection(
        R.string.fps_stats_chart_battery_temp,
        R.string.fps_stats_legend_battery_temp,
        ChartBatteryTempColor,
        samples.batteryTempC,
        stats.batteryTemp,
        0f
    ) { fmt(it) + "°C" }

    addSection(
        R.string.fps_stats_chart_frame_time,
        R.string.fps_stats_legend_frame_time,
        ChartFrameTimeColor,
        frameTimes,
        stats.frameTime,
        0f
    ) { fmt(it) + "ms" }

    addSection(
        R.string.fps_stats_chart_cpu_usage,
        R.string.fps_stats_legend_cpu_usage,
        ChartCpuUsageColor,
        samples.cpuUsagePct,
        stats.cpuUsage,
        100f
    ) { fmt(it) + "%" }

    addSection(
        R.string.fps_stats_chart_cpu_temp,
        R.string.fps_stats_legend_cpu_temp,
        ChartCpuTempColor,
        samples.cpuTempC,
        stats.cpuTemp,
        0f
    ) { fmt(it) + "°C" }

    addSection(
        R.string.fps_stats_chart_power,
        R.string.fps_stats_legend_power,
        ChartPowerColor,
        samples.powerW,
        stats.power,
        0f
    ) { fmt(it, 2) + "W" }

    return out
}

private fun shareSession(
    context: Context,
    store: FpsSessionStore,
    info: FpsSessionStore.Info,
    stats: FpsStats
) {
    val source = store.csvFile(info.id)?.takeIf { it.exists() } ?: return
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(info.startMs))
    val file = File(dir, "fps_${info.packageName}_$stamp.csv")
    source.copyTo(file, overwrite = true)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    val text = context.getString(
        R.string.fps_stats_share_text,
        info.label,
        fmt(stats.fps.avg),
        fmt(stats.fps.min),
        fmt(stats.fps.max),
        fmt(stats.low1)
    ) + info.description.takeIf { it.isNotEmpty() }?.let { "\n$it" }.orEmpty()

    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, info.label)
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.fps_stats_share)))
}

private fun fmt(value: Float, digits: Int = 1): String =
    String.format(Locale.getDefault(), "%.${digits}f", value)

private fun ticks(axisMax: Float, step: Float): List<Float> {
    val count = (axisMax / step + 1e-4f).toInt()
    return (0..count).map { it * step }
}

private fun formatTick(value: Float, step: Float): String =
    if (step >= 1f) value.roundToInt().toString()
    else String.format(Locale.getDefault(), "%.1f", value)

private fun niceAxis(max: Float, targetTicks: Int = 5): Pair<Float, Float> {
    if (max <= 0f) return 1f to 0.25f
    val raw = max / targetTicks
    val magnitude = 10.0.pow(floor(log10(raw.toDouble()))).toFloat()
    val normalized = raw / magnitude
    val step = magnitude * when {
        normalized <= 1f -> 1f
        normalized <= 2f -> 2f
        normalized <= 5f -> 5f
        else -> 10f
    }
    return ceil(max / step - 1e-4f) * step to step
}

private const val NOT_AVAILABLE = "—"
