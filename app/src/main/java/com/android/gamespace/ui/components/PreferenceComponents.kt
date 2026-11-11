package com.android.gamespace.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.gamespace.R
import kotlin.math.roundToInt

/**
 * Scaffold standar Material 3 dengan Collapsing LargeTopAppBar untuk seluruh halaman Settings GameSpace.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameSpaceSettingsScaffold(
    title: String,
    onBackClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (Modifier) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    if (onBackClick != null) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_left),
                                contentDescription = stringResource(android.R.string.cancel)
                            )
                        }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        content(Modifier.padding(innerPadding))
    }
}

/**
 * Mengembalikan bentuk sudut kartu grup Material 3 Expressive:
 * sudut luar 28.dp dan sudut antar-baris 6.dp.
 */
fun groupedItemShape(index: Int, count: Int): RoundedCornerShape {
    val outer = 28.dp
    val inner = 6.dp
    return when {
        count <= 1 -> RoundedCornerShape(outer)
        index == 0 -> RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner)
        index == count - 1 -> RoundedCornerShape(topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer)
        else -> RoundedCornerShape(inner)
    }
}

@Composable
fun PreferenceCategoryHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupedPreferenceItem(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    @DrawableRes iconRes: Int? = null,
    drawableIcon: Drawable? = null,
    index: Int = 0,
    count: Int = 1,
    enabled: Boolean = true,
    selected: Boolean = false,
    containerColor: Color = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    },
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    bottomContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    val shape = groupedItemShape(index, count)
    val contentAlpha = if (enabled) 1f else 0.45f

    Surface(
        shape = shape,
        color = containerColor,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(shape)
            .then(
                if (enabled && (onClick != null || onLongClick != null)) {
                    Modifier.combinedClickable(
                        onClick = { onClick?.invoke() },
                        onLongClick = onLongClick
                    )
                } else {
                    Modifier
                }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (leadingContent != null) {
                    leadingContent()
                    Spacer(modifier = Modifier.width(16.dp))
                } else if (drawableIcon != null) {
                    DrawableImage(
                        drawable = drawableIcon,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                } else if (iconRes != null) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                    )
                    if (!summary.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                        )
                    }
                }

                if (trailingContent != null) {
                    Spacer(modifier = Modifier.width(12.dp))
                    trailingContent()
                }
            }

            if (bottomContent != null) {
                bottomContent()
            }
        }
    }
}

@Composable
fun SwitchPreferenceItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    @DrawableRes iconRes: Int? = null,
    index: Int = 0,
    count: Int = 1,
    enabled: Boolean = true
) {
    GroupedPreferenceItem(
        title = title,
        summary = summary,
        iconRes = iconRes,
        index = index,
        count = count,
        enabled = enabled,
        modifier = modifier,
        onClick = { onCheckedChange(!checked) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = if (enabled) onCheckedChange else null,
                enabled = enabled
            )
        }
    )
}

@Composable
fun ListPreferenceItem(
    title: String,
    value: String,
    entries: List<String>,
    entryValues: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    dialogTitle: String = title,
    @DrawableRes iconRes: Int? = null,
    index: Int = 0,
    count: Int = 1,
    enabled: Boolean = true
) {
    var showDialog by remember { mutableStateOf(false) }
    val currentLabel = entries.getOrNull(entryValues.indexOf(value)) ?: value

    GroupedPreferenceItem(
        title = title,
        summary = currentLabel,
        iconRes = iconRes,
        index = index,
        count = count,
        enabled = enabled,
        modifier = modifier,
        onClick = { showDialog = true }
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(text = dialogTitle) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .selectableGroup()
                ) {
                    entries.forEachIndexed { idx, entryLabel ->
                        val entryVal = entryValues.getOrElse(idx) { "" }
                        val isSelected = entryVal == value
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .selectable(
                                    selected = isSelected,
                                    onClick = {
                                        onValueChange(entryVal)
                                        showDialog = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 8.dp, vertical = 12.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = entryLabel,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun SliderPreferenceItem(
    title: String,
    value: Int,
    valueRange: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    valueLabel: String? = null,
    index: Int = 0,
    count: Int = 1,
    enabled: Boolean = true
) {
    GroupedPreferenceItem(
        title = title,
        summary = summary,
        index = index,
        count = count,
        enabled = enabled,
        modifier = modifier,
        trailingContent = if (valueLabel != null) {
            {
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else null,
        bottomContent = {
            Spacer(modifier = Modifier.height(6.dp))
            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.roundToInt()) },
                valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}

/**
 * Slider ekspresif Material 3 dengan celah di sekitar handle (thumb) dan titik henti di ujung track,
 * mereplikasi estetika PresetSliderDrawable & GapSliderTrackDrawable secara murni di Compose Canvas.
 */
@Composable
fun ExpressiveGapSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    trackHeight: Dp = 16.dp,
    handleHeight: Dp = 44.dp,
    handleWidth: Dp = 4.dp,
    gapWidth: Dp = 4.dp,
    showEndStopDot: Boolean = true,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.secondaryContainer
) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1e-6f)
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)

    fun updateFromX(x: Float) {
        val f = (x / widthPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
        onValueChange(valueRange.start + f * span)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(handleHeight)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(valueRange) {
                detectTapGestures { offset ->
                    updateFromX(offset.x)
                    onValueChangeFinished?.invoke()
                }
            }
            .pointerInput(valueRange) {
                detectHorizontalDragGestures(
                    onDragStart = { offset -> updateFromX(offset.x) },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        updateFromX(change.position.x)
                    },
                    onDragEnd = { onValueChangeFinished?.invoke() },
                    onDragCancel = { onValueChangeFinished?.invoke() }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val tH = trackHeight.toPx()
            val top = (h - tH) / 2f
            val bottom = top + tH
            val outerR = minOf(8.dp.toPx(), tH / 2f)
            val innerR = minOf(2.dp.toPx(), outerR)
            val hW = handleWidth.toPx()
            val gap = gapWidth.toPx()

            val handleX = fraction * w
            val activeEnd = handleX - hW / 2f - gap
            val inactiveStart = handleX + hW / 2f + gap

            if (activeEnd > 0f) {
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(0f, top, activeEnd, bottom),
                            topLeft = CornerRadius(outerR, outerR),
                            bottomLeft = CornerRadius(outerR, outerR),
                            topRight = CornerRadius(innerR, innerR),
                            bottomRight = CornerRadius(innerR, innerR)
                        )
                    )
                }
                drawPath(path, activeColor)
            }

            if (inactiveStart < w) {
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(inactiveStart, top, w, bottom),
                            topLeft = CornerRadius(innerR, innerR),
                            bottomLeft = CornerRadius(innerR, innerR),
                            topRight = CornerRadius(outerR, outerR),
                            bottomRight = CornerRadius(outerR, outerR)
                        )
                    )
                }
                drawPath(path, inactiveColor)

                if (showEndStopDot) {
                    val stopRadius = 2.dp.toPx()
                    val stopX = w - 8.dp.toPx()
                    if (stopX - stopRadius >= inactiveStart) {
                        drawCircle(
                            color = activeColor,
                            radius = stopRadius,
                            center = Offset(stopX, h / 2f)
                        )
                    }
                }
            }

            // Draw the pill handle
            val thumbLeft = (handleX - hW / 2f).coerceIn(0f, (w - hW).coerceAtLeast(0f))
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(thumbLeft, 0f),
                size = Size(hW, h),
                cornerRadius = CornerRadius(hW / 2f, hW / 2f)
            )
        }
    }
}

/**
 * Mengonversi Drawable Android (seperti ikon aplikasi dari PackageManager) menjadi ImageBitmap Compose.
 */
fun Drawable.toImageBitmap(sizePx: Int = 128): ImageBitmap {
    if (this is BitmapDrawable && bitmap != null) {
        return bitmap.asImageBitmap()
    }
    val w = intrinsicWidth.takeIf { it > 0 } ?: sizePx
    val h = intrinsicHeight.takeIf { it > 0 } ?: sizePx
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bmp.asImageBitmap()
}

@Composable
fun DrawableImage(
    drawable: Drawable?,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    if (drawable == null) {
        Box(modifier = modifier)
        return
    }
    val imageBitmap = remember(drawable) { drawable.toImageBitmap() }
    Image(
        bitmap = imageBitmap,
        contentDescription = contentDescription,
        modifier = modifier
    )
}
