/*
 * Copyright (C) 2021 Chaldeaprjkt
 * Copyright (C) 2022-2024 crDroid Android Project
 * Copyright (C) 2023-2024 the risingOS Android Project
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
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.SystemProperties
import android.os.UserHandle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.preference.PreferenceManager
import com.android.gamespace.R
import com.android.gamespace.data.AppSettings
import com.android.gamespace.data.GameConfig
import com.android.gamespace.data.SystemSettings
import com.android.gamespace.data.UserGame
import com.android.gamespace.display.DisplayEnhancerActivity
import com.android.gamespace.preferences.appselector.AppSelectorActivity
import com.android.gamespace.stats.FpsStatsActivity
import com.android.gamespace.ui.components.DrawableImage
import com.android.gamespace.ui.components.GameSpaceSettingsScaffold
import com.android.gamespace.ui.components.GroupedPreferenceItem
import com.android.gamespace.ui.components.ListPreferenceItem
import com.android.gamespace.ui.components.PreferenceCategoryHeader
import com.android.gamespace.ui.components.SliderPreferenceItem
import com.android.gamespace.ui.components.SwitchPreferenceItem
import com.android.gamespace.ui.theme.GameSpaceTheme
import com.android.gamespace.utils.GameModeUtils
import com.android.gamespace.utils.GameModeUtils.Companion.describeGameMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint(ComponentActivity::class)
class SettingsActivity : Hilt_SettingsActivity() {

    @Inject lateinit var appSettings: AppSettings
    @Inject lateinit var systemSettings: SystemSettings
    @Inject lateinit var gameModeUtils: GameModeUtils

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GameSpaceTheme {
                SettingsScreen(
                    appSettings = appSettings,
                    systemSettings = systemSettings,
                    gameModeUtils = gameModeUtils,
                    onBackClick = { finish() }
                )
            }
        }
    }
}

private data class RegisteredGameItem(
    val packageName: String,
    val label: String,
    val modeSummary: String,
    val icon: Drawable?
)

private data class InstalledUserApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?
)

@Composable
fun SettingsScreen(
    appSettings: AppSettings,
    systemSettings: SystemSettings,
    gameModeUtils: GameModeUtils,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val resolver = context.contentResolver

    var danmakuMode by remember { mutableStateOf(appSettings.danmakuNotification) }
    var callsMode by remember { mutableStateOf(appSettings.callsMode.toString()) }
    var ringerMode by remember { mutableStateOf(appSettings.ringerMode.toString()) }
    var autoDnd by remember { mutableStateOf(appSettings.autoDnd) }
    var suppressFullscreen by remember {
        mutableStateOf(
            Settings.System.getIntForUser(
                resolver,
                KEY_SUPPRESS_FULLSCREEN_INTENT,
                0,
                UserHandle.USER_CURRENT
            ) == 1
        )
    }

    val isBypassSupported = remember {
        Build.MANUFACTURER.equals("Google", ignoreCase = true) ||
            SystemProperties.getBoolean("persist.sys.battery_bypass_supported", false)
    }
    var bypassChargeEnabled by remember {
        mutableStateOf(
            Settings.System.getIntForUser(
                resolver,
                KEY_BYPASS_CHARGE_ENABLED,
                0,
                UserHandle.USER_CURRENT
            ) == 1
        )
    }

    var autoBrightnessDisabled by remember { mutableStateOf(appSettings.noAutoBrightness) }
    var threeScreenshotDisabled by remember { mutableStateOf(appSettings.noThreeScreenshot) }
    var stayAwake by remember { mutableStateOf(appSettings.stayAwake) }
    var lockGesture by remember { mutableStateOf(appSettings.lockGesture) }
    var menuOpacity by remember { mutableIntStateOf(appSettings.menuOpacity) }
    var barAutoHideEnabled by remember { mutableStateOf(appSettings.barAutoHideEnabled) }
    var barAutoHideDelaySec by remember { mutableIntStateOf(appSettings.barAutoHideDelaySec) }

    var showQuickStartDialog by remember { mutableStateOf(false) }
    val registeredGames = remember { mutableStateListOf<RegisteredGameItem>() }

    fun refreshGameList() {
        val pm = context.packageManager
        val flags = PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
        val items = systemSettings.userGames.mapNotNull { userGame ->
            val info = runCatching { pm.getApplicationInfo(userGame.packageName, flags) }.getOrNull()
                ?: return@mapNotNull null
            RegisteredGameItem(
                packageName = userGame.packageName,
                label = info.loadLabel(pm).toString(),
                modeSummary = context.describeGameMode(userGame.mode),
                icon = runCatching { info.loadIcon(pm) }.getOrNull()
            )
        }.sortedBy { it.label.lowercase() }
        registeredGames.clear()
        registeredGames.addAll(items)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshGameList()
                danmakuMode = appSettings.danmakuNotification
                stayAwake = appSettings.stayAwake
                lockGesture = appSettings.lockGesture
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val selectorLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val selectedPkg = result.data?.getStringExtra(AppSelectorActivity.EXTRA_APP)
            if (!selectedPkg.isNullOrEmpty()) {
                val current = systemSettings.userGames.toMutableList()
                if (current.none { it.packageName == selectedPkg }) {
                    current.add(UserGame(selectedPkg))
                }
                systemSettings.userGames = current
                gameModeUtils.setIntervention(selectedPkg, GameConfig.ModeBuilder.build())
                refreshGameList()
            }
        }
    }

    val perAppLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val unregisteredPkg = result.data?.getStringExtra(PerAppSettingsActivity.PREF_UNREGISTER)
            if (!unregisteredPkg.isNullOrEmpty()) {
                val current = systemSettings.userGames.filterNot { it.packageName == unregisteredPkg }
                systemSettings.userGames = current
                gameModeUtils.setIntervention(unregisteredPkg, null)
            }
        }
        refreshGameList()
    }

    val callsEntries = stringArrayResource(R.array.in_game_calls_entries).toList()
    val callsValues = stringArrayResource(R.array.in_game_calls_values).toList()
    val ringerEntries = stringArrayResource(R.array.ringer_mode_entries).toList()
    val ringerValues = stringArrayResource(R.array.ringer_mode_values).toList()

    val inGameCount = if (isBypassSupported) 13 else 12

    GameSpaceSettingsScaffold(
        title = stringResource(R.string.settings_title),
        onBackClick = onBackClick
    ) { paddingModifier ->
        LazyColumn(
            modifier = paddingModifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Expressive Material 3 Hero Banner (replaces XML IllustrationPreference)
            item(key = "hero_banner") {
                GameSpaceHeroCard(
                    title = stringResource(R.string.settings_title),
                    subtitle = stringResource(R.string.settings_summary)
                )
            }

            // Category: In-game options
            item(key = "header_in_game") {
                PreferenceCategoryHeader(title = stringResource(R.string.in_game_preferences_title))
            }

            item(key = "in_game_group") {
                var idx = 0
                Column {
                    SwitchPreferenceItem(
                        title = stringResource(R.string.danmaku_notification_mode_title),
                        summary = stringResource(R.string.danmaku_notification_mode_summary),
                        checked = danmakuMode,
                        onCheckedChange = {
                            danmakuMode = it
                            appSettings.danmakuNotification = it
                        },
                        index = idx++,
                        count = inGameCount
                    )

                    ListPreferenceItem(
                        title = stringResource(R.string.in_game_calls_title),
                        value = callsMode,
                        entries = callsEntries,
                        entryValues = callsValues,
                        onValueChange = {
                            callsMode = it
                            appSettings.callsMode = it.toIntOrNull() ?: 0
                        },
                        index = idx++,
                        count = inGameCount
                    )

                    ListPreferenceItem(
                        title = stringResource(R.string.ringer_mode_title),
                        value = ringerMode,
                        entries = ringerEntries,
                        entryValues = ringerValues,
                        onValueChange = {
                            ringerMode = it
                            appSettings.ringerMode = it.toIntOrNull() ?: 3
                        },
                        index = idx++,
                        count = inGameCount
                    )

                    SwitchPreferenceItem(
                        title = stringResource(R.string.auto_dnd_title),
                        summary = stringResource(R.string.auto_dnd_summary),
                        checked = autoDnd,
                        onCheckedChange = {
                            autoDnd = it
                            appSettings.autoDnd = it
                        },
                        index = idx++,
                        count = inGameCount
                    )

                    SwitchPreferenceItem(
                        title = stringResource(R.string.suppress_fullscreen_intent_title),
                        summary = stringResource(R.string.suppress_fullscreen_intent_summary),
                        checked = suppressFullscreen,
                        onCheckedChange = {
                            suppressFullscreen = it
                            Settings.System.putIntForUser(
                                resolver,
                                KEY_SUPPRESS_FULLSCREEN_INTENT,
                                if (it) 1 else 0,
                                UserHandle.USER_CURRENT
                            )
                        },
                        index = idx++,
                        count = inGameCount
                    )

                    if (isBypassSupported) {
                        SwitchPreferenceItem(
                            title = stringResource(R.string.bypass_charge_enabled_summary),
                            summary = stringResource(R.string.bypass_charge_enabled_title),
                            checked = bypassChargeEnabled,
                            onCheckedChange = {
                                bypassChargeEnabled = it
                                Settings.System.putIntForUser(
                                    resolver,
                                    KEY_BYPASS_CHARGE_ENABLED,
                                    if (it) 1 else 0,
                                    UserHandle.USER_CURRENT
                                )
                            },
                            index = idx++,
                            count = inGameCount
                        )
                    }

                    SwitchPreferenceItem(
                        title = stringResource(R.string.auto_brightness_disabled_title),
                        summary = stringResource(R.string.auto_brightness_disabled_summary),
                        checked = autoBrightnessDisabled,
                        onCheckedChange = {
                            autoBrightnessDisabled = it
                            appSettings.noAutoBrightness = it
                        },
                        index = idx++,
                        count = inGameCount
                    )

                    SwitchPreferenceItem(
                        title = stringResource(R.string.three_screenshot_disabled_title),
                        summary = stringResource(R.string.three_screenshot_disabled_summary),
                        checked = threeScreenshotDisabled,
                        onCheckedChange = {
                            threeScreenshotDisabled = it
                            appSettings.noThreeScreenshot = it
                        },
                        index = idx++,
                        count = inGameCount
                    )

                    SwitchPreferenceItem(
                        title = stringResource(R.string.stay_awake_title),
                        summary = stringResource(R.string.stay_awake_summary),
                        checked = stayAwake,
                        onCheckedChange = {
                            stayAwake = it
                            appSettings.stayAwake = it
                        },
                        index = idx++,
                        count = inGameCount
                    )

                    SwitchPreferenceItem(
                        title = stringResource(R.string.lock_gesture_title),
                        checked = lockGesture,
                        onCheckedChange = {
                            lockGesture = it
                            appSettings.lockGesture = it
                        },
                        index = idx++,
                        count = inGameCount
                    )

                    SliderPreferenceItem(
                        title = stringResource(R.string.gamespace_menu_opacity_title),
                        value = menuOpacity,
                        valueRange = 0..100,
                        valueLabel = "$menuOpacity%",
                        onValueChange = {
                            menuOpacity = it
                            appSettings.menuOpacity = it
                        },
                        index = idx++,
                        count = inGameCount
                    )

                    SwitchPreferenceItem(
                        title = stringResource(R.string.gamespace_bar_auto_hide_title),
                        summary = stringResource(R.string.gamespace_bar_auto_hide_summary),
                        checked = barAutoHideEnabled,
                        onCheckedChange = {
                            barAutoHideEnabled = it
                            appSettings.barAutoHideEnabled = it
                        },
                        index = idx++,
                        count = inGameCount
                    )

                    SliderPreferenceItem(
                        title = stringResource(R.string.gamespace_bar_auto_hide_delay_value, barAutoHideDelaySec),
                        value = barAutoHideDelaySec,
                        valueRange = 1..15,
                        enabled = barAutoHideEnabled,
                        onValueChange = {
                            barAutoHideDelaySec = it
                            appSettings.barAutoHideDelaySec = it
                        },
                        index = idx,
                        count = inGameCount
                    )
                }
            }

            // Category: Other Features
            item(key = "header_other_features") {
                PreferenceCategoryHeader(title = stringResource(R.string.other_feature_category))
            }

            item(key = "other_features_group") {
                Column {
                    GroupedPreferenceItem(
                        title = stringResource(R.string.fps_statistics_title),
                        summary = stringResource(R.string.fps_statistics_summary),
                        iconRes = R.drawable.ic_fps,
                        index = 0,
                        count = 3,
                        onClick = {
                            context.startActivity(Intent(context, FpsStatsActivity::class.java))
                        }
                    )
                    GroupedPreferenceItem(
                        title = stringResource(R.string.display_enhancer_title),
                        summary = stringResource(R.string.display_enhancer_summary),
                        iconRes = R.drawable.ic_display_enhanced,
                        index = 1,
                        count = 3,
                        onClick = {
                            context.startActivity(Intent(context, DisplayEnhancerActivity::class.java))
                        }
                    )
                    GroupedPreferenceItem(
                        title = stringResource(R.string.quick_start_apps_title),
                        summary = stringResource(R.string.quick_start_apps_summary),
                        iconRes = R.drawable.ic_action_sidebar,
                        index = 2,
                        count = 3,
                        onClick = { showQuickStartDialog = true }
                    )
                }
            }

            // Category: Library
            item(key = "header_library") {
                PreferenceCategoryHeader(title = stringResource(R.string.game_list_title))
            }

            item(key = "library_group") {
                val totalLibraryItems = registeredGames.size + 1
                Column {
                    registeredGames.forEachIndexed { index, game ->
                        GroupedPreferenceItem(
                            title = game.label,
                            summary = game.modeSummary,
                            drawableIcon = game.icon,
                            index = index,
                            count = totalLibraryItems,
                            onClick = {
                                perAppLauncher.launch(
                                    Intent(context, PerAppSettingsActivity::class.java).apply {
                                        putExtra(PerAppSettingsActivity.EXTRA_PACKAGE, game.packageName)
                                    }
                                )
                            },
                            trailingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_gear),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        )
                    }

                    GroupedPreferenceItem(
                        title = stringResource(R.string.app_selector_label),
                        iconRes = R.drawable.ic_add,
                        index = registeredGames.size,
                        count = totalLibraryItems,
                        onClick = {
                            selectorLauncher.launch(Intent(context, AppSelectorActivity::class.java))
                        }
                    )
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }

    if (showQuickStartDialog) {
        QuickStartAppsDialog(
            context = context,
            onDismiss = { showQuickStartDialog = false }
        )
    }
}

@Composable
private fun GameSpaceHeroCard(
    title: String,
    subtitle: String
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 22.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_speed),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(modifier = Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun QuickStartAppsDialog(
    context: Context,
    onDismiss: () -> Unit
) {
    val allApps = remember {
        val pm = context.packageManager
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .map {
                InstalledUserApp(
                    packageName = it.packageName,
                    label = pm.getApplicationLabel(it).toString(),
                    icon = runCatching { pm.getApplicationIcon(it) }.getOrNull()
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    val selectedPackages = remember {
        val initial = Settings.System.getString(context.contentResolver, KEY_QUICK_START_APPS)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            .orEmpty()
        mutableStateListOf<String>().apply { addAll(initial) }
    }

    var searchQuery by remember { mutableStateOf("") }
    val filteredApps = remember(allApps, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) allApps else allApps.filter { it.label.lowercase().contains(q) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_start_apps_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.app_search_title)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val isChecked = app.packageName in selectedPackages
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    if (isChecked) selectedPackages.remove(app.packageName)
                                    else selectedPackages.add(app.packageName)
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = null
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            DrawableImage(
                                drawable = app.icon,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val savedValue = selectedPackages.joinToString(",")
                    Settings.System.putString(
                        context.contentResolver,
                        KEY_QUICK_START_APPS,
                        savedValue
                    )
                    PreferenceManager.getDefaultSharedPreferences(context)
                        .edit()
                        .putString(KEY_QUICK_START_APPS, savedValue)
                        .apply()
                    onDismiss()
                }
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

private const val KEY_SUPPRESS_FULLSCREEN_INTENT = "gamespace_suppress_fullscreen_intent"
private const val KEY_BYPASS_CHARGE_ENABLED = "bypass_charge_enabled"
private const val KEY_QUICK_START_APPS = "quick_start_apps"
