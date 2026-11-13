package com.android.gamespace.ui.settings

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.gamespace.R
import com.android.gamespace.data.SystemSettings
import com.android.gamespace.ui.components.AppIcon
import com.android.gamespace.ui.components.BackButton
import com.android.gamespace.ui.components.PreferenceRow
import com.android.gamespace.ui.theme.groupItemShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class Candidate(val packageName: String, val label: String)

/** Lists the apps that are not in the library yet and lets one be picked, with a search. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectorScreen(
    settings: SystemSettings,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    val apps by produceState<List<Candidate>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val flags = PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
            val inLibrary = settings.userGames.map { it.packageName }.toSet()
            pm.getInstalledApplications(flags)
                .filter {
                    it.packageName != context.packageName &&
                        it.flags and ApplicationInfo.FLAG_SYSTEM == 0 &&
                        it.packageName !in inLibrary
                }
                .map { Candidate(it.packageName, it.loadLabel(pm).toString()) }
                .sortedBy { it.label.lowercase() }
        }
    }

    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val shown = remember(apps, query) {
        val text = query.trim().lowercase()
        apps.orEmpty().filter { text.isEmpty() || it.label.lowercase().contains(text) }
    }

    fun closeSearch() {
        searching = false
        query = ""
    }
    BackHandler(enabled = searching) { closeSearch() }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val focus = remember { FocusRequester() }
    LaunchedEffect(searching) {
        if (searching) focus.requestFocus()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (searching) {
                TopAppBar(
                    title = {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.app_search_title)) },
                            colors = TextFieldDefaults.textFieldColors(
                                containerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focus),
                        )
                    },
                    navigationIcon = { BackButton(::closeSearch) },
                )
            } else {
                LargeTopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.app_selector_label),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = { BackButton(onBack) },
                    actions = {
                        IconButton(onClick = { searching = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_search),
                                contentDescription = stringResource(R.string.app_search_title),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(shown, key = { _, app -> app.packageName }) { index, app ->
                PreferenceRow(
                    title = app.label,
                    summary = app.packageName,
                    shape = groupItemShape(index, shown.size),
                    leading = { AppIcon(app.packageName, size = 40.dp) },
                    onClick = { onSelect(app.packageName) },
                )
            }
        }
    }
}
