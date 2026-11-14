package com.android.gamespace.widget.compose.panel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import com.android.gamespace.R
import com.android.gamespace.widget.compose.components.GameSpaceIconButton
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Header layout tuning.
private val ClockSize = 40.dp
private val ClockToDateSpacing = 10.dp
private val TemperatureToBatterySpacing = 12.dp
private val BatteryToEditSpacing = 8.dp

// Analog clock face: a smooth "cookie" with ClockScallopCount lobes.
// ClockScallopDepth is the valley depth as a fraction of the radius.
private const val ClockScallopCount = 12
private const val ClockScallopDepth = 0.08f

@Composable
fun PanelHeader(
    editing: Boolean,
    onToggleEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val now = rememberCurrentTimeMillis(context)
    val locale = Locale.getDefault()
    val dayFormatter = remember(locale) { DateTimeFormatter.ofPattern("EEEE", locale) }
    val dateFormatter = remember(locale) { DateTimeFormatter.ofPattern("d MMM", locale) }
    val dateTime = remember(now) {
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault())
    }
    val battery = rememberBatteryInfo(context)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnalogClock(
            hour = dateTime.hour,
            minute = dateTime.minute,
            clockSize = ClockSize,
        )
        Spacer(modifier = Modifier.width(ClockToDateSpacing))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = dateTime.format(dayFormatter),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = dateTime.format(dateFormatter),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HeaderValue(
            iconRes = R.drawable.thermometer,
            text = "${battery.temperatureC}°C",
        )
        Spacer(modifier = Modifier.width(TemperatureToBatterySpacing))
        HeaderValue(
            iconRes = R.drawable.battery_outline,
            text = stringResource(R.string.battery_format, battery.percent),
        )
        Spacer(modifier = Modifier.width(BatteryToEditSpacing))
        CompositionLocalProvider(LocalRippleConfiguration provides null) {
            GameSpaceIconButton(
                iconRes = if (editing) R.drawable.ic_check else R.drawable.ic_edit_tiles,
                onClick = onToggleEdit,
                contentDescription = stringResource(
                    if (editing) R.string.panel_edit_done else R.string.panel_edit_enter
                ),
            )
        }
    }
}

@Composable
private fun AnalogClock(
    hour: Int,
    minute: Int,
    clockSize: Dp,
    modifier: Modifier = Modifier,
) {
    val faceColor = MaterialTheme.colorScheme.primary
    val handColor = MaterialTheme.colorScheme.onPrimary

    Canvas(modifier = modifier.size(clockSize)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f

        drawPath(
            path = scallopedCirclePath(center, radius),
            color = faceColor,
        )

        // 0 degrees = 12 o'clock, clockwise.
        val hourAngle = ((hour % 12) + minute / 60f) * 30f
        val minuteAngle = minute * 6f
        drawClockHand(
            center = center,
            angleDegrees = hourAngle,
            length = radius * 0.42f,
            strokeWidth = 3.dp.toPx(),
            color = handColor,
        )
        drawClockHand(
            center = center,
            angleDegrees = minuteAngle,
            length = radius * 0.64f,
            strokeWidth = 2.5f.dp.toPx(),
            color = handColor,
        )
    }
}

private fun DrawScope.drawClockHand(
    center: Offset,
    angleDegrees: Float,
    length: Float,
    strokeWidth: Float,
    color: Color,
) {
    val radians = angleDegrees * (PI.toFloat() / 180f)
    drawLine(
        color = color,
        start = center,
        end = Offset(
            x = center.x + length * sin(radians),
            y = center.y - length * cos(radians),
        ),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
    )
}

private fun scallopedCirclePath(center: Offset, radius: Float): Path {
    val steps = ClockScallopCount * 24
    val path = Path()
    for (i in 0 until steps) {
        val theta = 2f * PI.toFloat() * i / steps
        // Peaks at multiples of 360/ClockScallopCount degrees, valleys in between.
        val r = radius * (1f - ClockScallopDepth * (1f - cos(ClockScallopCount * theta)) / 2f)
        val x = center.x + r * cos(theta)
        val y = center.y + r * sin(theta)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

@Composable
private fun rememberCurrentTimeMillis(context: Context): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LifecycleStartEffect(context) {
        now = System.currentTimeMillis()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                now = System.currentTimeMillis()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onStopOrDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return now
}

@Composable
private fun HeaderValue(
    iconRes: Int,
    text: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

private data class BatteryInfo(
    val temperatureC: Int,
    val percent: Int,
)

@Composable
private fun rememberBatteryInfo(context: Context): BatteryInfo {
    var battery by remember { mutableStateOf(readBatteryInfo(context, null)) }
    LifecycleStartEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                battery = readBatteryInfo(context ?: return, intent)
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onStopOrDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return battery
}

private fun readBatteryInfo(context: Context, intent: Intent?): BatteryInfo {
    val source = intent ?: context.registerReceiver(
        null,
        IntentFilter(Intent.ACTION_BATTERY_CHANGED),
    )
    val temperature = (source?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10
    val level = source?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
    val scale = source?.getIntExtra(BatteryManager.EXTRA_SCALE, 0) ?: 0
    val percent = if (scale > 0) (level.toFloat() / scale * 100).toInt() else 0
    return BatteryInfo(temperature, percent)
}
