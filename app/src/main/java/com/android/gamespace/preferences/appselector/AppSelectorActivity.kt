/*
 * Copyright (C) 2021 Chaldeaprjkt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.gamespace.preferences.appselector

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.gamespace.R
import com.android.gamespace.data.SystemSettings
import com.android.gamespace.ui.components.GroupedPreferenceItem
import com.android.gamespace.ui.theme.GameSpaceTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint(ComponentActivity::class)
class AppSelectorActivity : Hilt_AppSelectorActivity() {

    @Inject lateinit var settings: SystemSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GameSpaceTheme {
                AppSelectorScreen(
                    settings = settings,
                    onBackClick = { finish() },
                    onAppSelected = { packageName ->
                        setResult(
                            Activity.RESULT_OK,
                            Intent().apply { putExtra(EXTRA_APP, packageName) }
                        )
                        finish()
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_APP = "selected_app"
    }
}

private data class SelectableAppItem(
    val packageName: String,
    val label: String,
    val icon: Drawable?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectorScreen(
    settings: SystemSettings,
    onBackClick: () -> Unit,
    onAppSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val allApps = remember {
        val pm = context.packageManager
        val flags = PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
        val registeredSet = settings.userGames.map { it.packageName }.toSet()
        pm.getInstalledApplications(flags)
            .filter {
                it.packageName != context.packageName &&
                    (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                    it.packageName !in registeredSet
            }
            .map {
                SelectableAppItem(
                    packageName = it.packageName,
                    label = it.loadLabel(pm).toString(),
                    icon = runCatching { it.loadIcon(pm) }.getOrNull()
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val filteredApps = remember(allApps, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) allApps
        else allApps.filter {
            it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_selector_label)) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_left),
                                contentDescription = stringResource(android.R.string.cancel)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            searchExpanded = !searchExpanded
                            if (!searchExpanded) searchQuery = ""
                        }) {
                            Icon(
                                painter = painterResource(
                                    if (searchExpanded) R.drawable.ic_close else R.drawable.ic_search
                                ),
                                contentDescription = stringResource(R.string.app_search_title)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )

                if (searchExpanded) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(R.string.app_search_title)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_search),
                                contentDescription = null
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            itemsIndexed(filteredApps, key = { _, app -> app.packageName }) { index, app ->
                GroupedPreferenceItem(
                    title = app.label,
                    summary = app.packageName,
                    drawableIcon = app.icon,
                    index = index,
                    count = filteredApps.size,
                    onClick = { onAppSelected(app.packageName) }
                )
            }
        }
    }
}
