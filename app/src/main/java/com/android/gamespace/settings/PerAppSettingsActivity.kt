/*
 * Copyright (C) 2021 Chaldeaprjkt
 * Copyright (C) 2022-2024 crDroid Android Project
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
package com.android.gamespace.settings

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.gamespace.R
import com.android.gamespace.data.GameConfig
import com.android.gamespace.data.SystemSettings
import com.android.gamespace.ui.components.DrawableImage
import com.android.gamespace.ui.components.GameSpaceSettingsScaffold
import com.android.gamespace.ui.components.GroupedPreferenceItem
import com.android.gamespace.ui.components.ListPreferenceItem
import com.android.gamespace.ui.components.PreferenceCategoryHeader
import com.android.gamespace.ui.components.SwitchPreferenceItem
import com.android.gamespace.ui.theme.GameSpaceTheme
import com.android.gamespace.utils.GameModeUtils
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint(ComponentActivity::class)
class PerAppSettingsActivity : Hilt_PerAppSettingsActivity() {

    @Inject lateinit var settings: SystemSettings
    @Inject lateinit var gameModeUtils: GameModeUtils

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val pkgName = intent?.getStringExtra(EXTRA_PACKAGE).orEmpty()
        setContent {
            GameSpaceTheme {
                PerAppSettingsScreen(
                    packageName = pkgName,
                    settings = settings,
                    gameModeUtils = gameModeUtils,
                    onBackClick = { finish() },
                    onUnregister = { pkg ->
                        setResult(
                            Activity.RESULT_OK,
                            Intent().apply { putExtra(PREF_UNREGISTER, pkg) }
                        )
                        finish()
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_PACKAGE = "package_name"
        const val PREF_PREFERRED_MODE = "per_app_preferred_mode"
        const val PREF_USE_ANGLE = "per_app_use_angle"
        const val PREF_DOWNSCALE = "per_app_downscale"
        const val PREF_UNREGISTER = "per_app_unregister"
    }
}

@Composable
fun PerAppSettingsScreen(
    packageName: String,
    settings: SystemSettings,
    gameModeUtils: GameModeUtils,
    onBackClick: () -> Unit,
    onUnregister: (String) -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager

    val appInfo = remember(packageName) {
        runCatching {
            pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        }.getOrNull()
    }
    val appLabel = remember(appInfo) {
        appInfo?.loadLabel(pm)?.toString() ?: packageName
    }
    val appIcon = remember(appInfo) {
        runCatching { appInfo?.loadIcon(pm) }.getOrNull()
    }

    val currentConfig = remember(packageName) {
        settings.userGames.firstOrNull { it.packageName == packageName }
    }

    var preferredMode by remember {
        mutableStateOf((currentConfig?.mode ?: 1).toString())
    }

    val allowAngle = booleanResource(R.bool.config_allow_per_app_angle_usage)
    val anglePackageAvailable = remember {
        gameModeUtils.findAnglePackage()?.isEnabled == true
    }
    var useAngle by remember {
        mutableStateOf(gameModeUtils.isAngleUsed(packageName))
    }

    var downscale by remember {
        mutableStateOf(
            "%.2f".format(Locale.US, gameModeUtils.downscaleFactorOf(packageName))
        )
    }

    fun forceStop(pkg: String) {
        runCatching {
            (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
                ?.forceStopPackage(pkg)
        }
    }

    val modeEntries = stringArrayResource(R.array.per_app_preferred_mode_entries).toList()
    val modeValues = stringArrayResource(R.array.per_app_preferred_mode_values).toList()
    val downscaleEntries = stringArrayResource(R.array.per_app_downscale_entries).toList()
    val downscaleValues = stringArrayResource(R.array.per_app_downscale_values).toList()

    val optionsCount = if (allowAngle) 3 else 2

    GameSpaceSettingsScaffold(
        title = stringResource(R.string.per_app_title),
        onBackClick = onBackClick
    ) { paddingModifier ->
        LazyColumn(
            modifier = paddingModifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // App Header
            item(key = "app_header") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp, bottom = 24.dp)
                ) {
                    DrawableImage(
                        drawable = appIcon,
                        contentDescription = appLabel,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = appLabel,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = packageName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Per-App Options
            item(key = "per_app_options") {
                var idx = 0
                Column {
                    ListPreferenceItem(
                        title = stringResource(R.string.per_app_mode_title),
                        value = preferredMode,
                        entries = modeEntries,
                        entryValues = modeValues,
                        onValueChange = { newValue ->
                            preferredMode = newValue
                            val newMode = newValue.toIntOrNull() ?: 1
                            gameModeUtils.setGameModeFor(packageName, settings, newMode)
                            forceStop(packageName)
                        },
                        index = idx++,
                        count = optionsCount
                    )

                    if (allowAngle) {
                        SwitchPreferenceItem(
                            title = stringResource(R.string.per_app_angle_title),
                            summary = if (anglePackageAvailable) {
                                stringResource(R.string.per_app_angle_summary)
                            } else {
                                stringResource(R.string.cant_find_angle_pkg)
                            },
                            checked = useAngle,
                            enabled = anglePackageAvailable,
                            onCheckedChange = { checked ->
                                useAngle = checked
                                val newModes = GameConfig.ModeBuilder.apply {
                                    this.useAngle = checked
                                    this.downscale = gameModeUtils.downscaleFactorOf(packageName)
                                }.build()
                                gameModeUtils.setIntervention(packageName, newModes)
                                forceStop(packageName)
                            },
                            index = idx++,
                            count = optionsCount
                        )
                    }

                    ListPreferenceItem(
                        title = stringResource(R.string.per_app_downscale_title),
                        dialogTitle = stringResource(R.string.per_app_downscale_dialog_title),
                        value = downscale,
                        entries = downscaleEntries,
                        entryValues = downscaleValues,
                        onValueChange = { newValue ->
                            downscale = newValue
                            val newModes = GameConfig.ModeBuilder.apply {
                                this.useAngle = gameModeUtils.isAngleUsed(packageName)
                                this.downscale = newValue.toFloatOrNull() ?: GameConfig.NO_DOWNSCALE
                            }.build()
                            gameModeUtils.setIntervention(packageName, newModes)
                            forceStop(packageName)
                        },
                        index = idx,
                        count = optionsCount
                    )
                }
            }

            // Remove Category
            item(key = "remove_header") {
                PreferenceCategoryHeader(title = stringResource(R.string.remove_category))
            }

            item(key = "remove_item") {
                GroupedPreferenceItem(
                    title = stringResource(R.string.per_app_unregister, appLabel),
                    iconRes = R.drawable.ic_remove,
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                    index = 0,
                    count = 1,
                    onClick = { onUnregister(packageName) }
                )
            }
        }
    }
}
