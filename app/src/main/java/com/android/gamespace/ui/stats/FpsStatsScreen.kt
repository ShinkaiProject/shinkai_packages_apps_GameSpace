package com.android.gamespace.ui.stats

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.gamespace.R
import com.android.gamespace.data.FpsSessionStore
import com.android.gamespace.ui.components.AppIcon
import com.android.gamespace.ui.components.BackButton
import com.android.gamespace.ui.components.rememberResumeCount
import com.android.gamespace.ui.theme.groupItemShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** What a confirmation dialog of this screen is about to delete. */
private enum class Deleting { Selected, All }

/**
 * The recorded FPS sessions, newest first. A long press (or "Select" in the menu) starts a
 * selection, in which sessions are ticked and deleted together.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FpsStatsScreen(
    store: FpsSessionStore,
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val resume by rememberResumeCount()

    var sessions by remember { mutableStateOf<List<FpsSessionStore.Info>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    val selected = rememberSaveable(
        saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() })
    ) { mutableStateListOf<String>() }
    var menuOpen by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Deleting?>(null) }

    fun exitSelection() {
        selecting = false
        selected.clear()
    }

    fun reload() {
        scope.launch {
            val list = withContext(Dispatchers.IO) { store.list() }
            // What a row shows never changes for an id, so the same ids need no new list.
            if (!loaded || list.map { it.id } != sessions.map { it.id }) {
                sessions = list
                selected.retainAll(list.map { it.id }.toSet())
                if (list.isEmpty()) selecting = false
            }
            loaded = true
        }
    }

    // Each time the screen comes back: sessions can have been recorded or deleted meanwhile.
    LaunchedEffect(resume) { reload() }

    // While selecting, Back leaves the selection instead of closing the screen.
    BackHandler(enabled = selecting) { exitSelection() }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = if (selecting) {
                            stringResource(R.string.fps_stats_selected_count, selected.size)
                        } else {
                            stringResource(R.string.fps_statistics_title)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (selecting) {
                        IconButton(onClick = ::exitSelection) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(android.R.string.cancel),
                            )
                        }
                    } else {
                        BackButton(onBack)
                    }
                },
                actions = {
                    if (selecting) {
                        IconButton(
                            onClick = { deleting = Deleting.Selected },
                            enabled = selected.isNotEmpty(),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription = stringResource(R.string.fps_stats_delete),
                            )
                        }
                    }
                    if (sessions.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_more_vert),
                                    contentDescription = stringResource(R.string.more_options),
                                )
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                if (selecting) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.fps_stats_select_all)) },
                                        onClick = {
                                            menuOpen = false
                                            selected.clear()
                                            selected.addAll(sessions.map { it.id })
                                        },
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.fps_stats_select)) },
                                        onClick = {
                                            menuOpen = false
                                            selecting = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.fps_stats_delete_all)) },
                                        onClick = {
                                            menuOpen = false
                                            deleting = Deleting.All
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        if (loaded && sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.fps_stats_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(sessions, key = { _, info -> info.id }) { index, info ->
                    SessionRow(
                        info = info,
                        shape = groupItemShape(index, sessions.size),
                        selecting = selecting,
                        isSelected = info.id in selected,
                        onClick = {
                            if (selecting) {
                                if (!selected.remove(info.id)) selected.add(info.id)
                            } else {
                                onOpenSession(info.id)
                            }
                        },
                        onLongClick = {
                            if (selecting) {
                                if (!selected.remove(info.id)) selected.add(info.id)
                            } else {
                                selecting = true
                                selected.add(info.id)
                            }
                        },
                    )
                }
            }
        }
    }

    deleting?.let { what ->
        val ids = selected.toList()
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = {
                Text(
                    when (what) {
                        Deleting.Selected -> context.resources.getQuantityString(
                            R.plurals.fps_stats_delete_selected_message, ids.size, ids.size
                        )
                        Deleting.All -> stringResource(R.string.fps_stats_delete_all_message)
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                when (what) {
                                    Deleting.Selected -> ids.forEach { store.delete(it) }
                                    Deleting.All -> store.deleteAll()
                                }
                            }
                            if (what == Deleting.Selected) exitSelection()
                            reload()
                        }
                    }
                ) {
                    Text(
                        stringResource(
                            if (what == Deleting.All) R.string.fps_stats_delete_all
                            else R.string.fps_stats_delete
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(
    info: FpsSessionStore.Info,
    shape: androidx.compose.ui.graphics.Shape,
    selecting: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val date = DateUtils.formatDateTime(
        context, info.startMs,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
    )
    val duration = DateUtils.formatElapsedTime(info.durationMs / 1000)

    Surface(
        shape = shape,
        color = if (isSelected && selecting) colors.secondaryContainer
        else colors.surfaceColorAtElevation(3.dp),
        contentColor = if (isSelected && selecting) colors.onSecondaryContainer else colors.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .heightIn(min = 80.dp)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selecting) {
                // The row handles the touch; the box only shows the state.
                Checkbox(checked = isSelected, onCheckedChange = null)
                Spacer(Modifier.width(8.dp))
            }
            AppIcon(info.packageName, size = 48.dp, modifier = Modifier.padding(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp),
            ) {
                Text(
                    text = info.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.fps_stats_item_summary, date, duration),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = String.format(Locale.getDefault(), "%.1f", info.avgFps),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.fps_stats_avg_fps_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}
