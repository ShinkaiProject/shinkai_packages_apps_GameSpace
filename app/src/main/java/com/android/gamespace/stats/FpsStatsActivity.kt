package com.android.gamespace.stats

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.android.gamespace.R
import com.android.gamespace.data.FpsSessionStore
import com.android.gamespace.ui.components.GameSpaceSettingsScaffold
import com.android.gamespace.ui.components.GroupedPreferenceItem
import com.android.gamespace.ui.theme.GameSpaceTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class FpsStatsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GameSpaceTheme {
                FpsStatsScreen(
                    onBackClick = { finish() }
                )
            }
        }
    }
}

@Composable
fun FpsStatsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { FpsSessionStore(context.applicationContext) }

    var sessions by remember { mutableStateOf<List<FpsSessionStore.Info>>(emptyList()) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    val iconCache = remember { HashMap<String, Drawable>() }

    fun getAppIcon(packageName: String): Drawable {
        return iconCache.getOrPut(packageName) {
            val pm = context.packageManager
            runCatching { pm.getApplicationIcon(packageName) }.getOrNull()
                ?: pm.defaultActivityIcon
        }
    }

    fun reload() {
        scope.launch {
            val loaded = withContext(Dispatchers.IO) { store.list() }
            sessions = loaded
            val validIds = loaded.map { it.id }.toSet()
            selectedIds.removeAll { it !in validIds }
            if (loaded.isEmpty()) selecting = false
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                reload()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = selecting) {
        selecting = false
        selectedIds.clear()
    }

    val titleText = if (selecting) {
        stringResource(R.string.fps_stats_selected_count, selectedIds.size)
    } else {
        stringResource(R.string.fps_statistics_title)
    }

    GameSpaceSettingsScaffold(
        title = titleText,
        onBackClick = {
            if (selecting) {
                selecting = false
                selectedIds.clear()
            } else {
                onBackClick()
            }
        },
        actions = {
            if (selecting) {
                IconButton(
                    onClick = { showDeleteSelectedDialog = true },
                    enabled = selectedIds.isNotEmpty()
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.fps_stats_delete)
                    )
                }
            }
            if (sessions.isNotEmpty()) {
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
                        if (selecting) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.fps_stats_select_all)) },
                                onClick = {
                                    showOverflowMenu = false
                                    selectedIds.clear()
                                    selectedIds.addAll(sessions.map { it.id })
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.fps_stats_select)) },
                                onClick = {
                                    showOverflowMenu = false
                                    selecting = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.fps_stats_delete_all)) },
                                onClick = {
                                    showOverflowMenu = false
                                    showDeleteAllDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { paddingModifier ->
        if (sessions.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = paddingModifier
                    .fillMaxSize()
                    .padding(32.dp)
            ) {
                Text(
                    text = stringResource(R.string.fps_stats_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = paddingModifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                itemsIndexed(sessions, key = { _, item -> item.id }) { index, info ->
                    val isSelected = selecting && info.id in selectedIds
                    val dateText = DateUtils.formatDateTime(
                        context,
                        info.startMs,
                        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
                    )
                    val durationText = DateUtils.formatElapsedTime(info.durationMs / 1000)

                    GroupedPreferenceItem(
                        title = info.label,
                        summary = stringResource(R.string.fps_stats_item_summary, dateText, durationText),
                        drawableIcon = getAppIcon(info.packageName),
                        index = index,
                        count = sessions.size,
                        selected = isSelected,
                        leadingContent = if (selecting) {
                            {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = null
                                )
                            }
                        } else null,
                        onClick = {
                            if (selecting) {
                                if (info.id in selectedIds) selectedIds.remove(info.id)
                                else selectedIds.add(info.id)
                            } else {
                                context.startActivity(
                                    Intent(context, FpsSessionActivity::class.java)
                                        .putExtra(FpsSessionActivity.EXTRA_ID, info.id)
                                )
                            }
                        },
                        onLongClick = {
                            if (!selecting) {
                                selecting = true
                                selectedIds.add(info.id)
                            } else {
                                if (info.id in selectedIds) selectedIds.remove(info.id)
                                else selectedIds.add(info.id)
                            }
                        },
                        trailingContent = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = String.format(Locale.getDefault(), "%.1f", info.avgFps),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = stringResource(R.string.fps_stats_avg_fps_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    if (showDeleteSelectedDialog) {
        val idsToDelete = selectedIds.toList()
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.fps_stats_delete_selected_message,
                        idsToDelete.size,
                        idsToDelete.size
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteSelectedDialog = false
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                idsToDelete.forEach { store.delete(it) }
                            }
                            selecting = false
                            selectedIds.clear()
                            reload()
                        }
                    }
                ) {
                    Text(stringResource(R.string.fps_stats_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            text = { Text(stringResource(R.string.fps_stats_delete_all_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAllDialog = false
                        scope.launch {
                            withContext(Dispatchers.IO) { store.deleteAll() }
                            selecting = false
                            selectedIds.clear()
                            reload()
                        }
                    }
                ) {
                    Text(stringResource(R.string.fps_stats_delete_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}
