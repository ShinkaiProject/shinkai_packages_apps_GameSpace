package com.android.gamespace.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemProperties
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.gamespace.R
import com.android.gamespace.data.AppSettings
import com.android.gamespace.data.GameConfig
import com.android.gamespace.data.SystemSettings
import com.android.gamespace.data.UserGame
import com.android.gamespace.display.DisplayEnhancerActivity
import com.android.gamespace.preferences.appselector.AppSelectorActivity
import com.android.gamespace.settings.PerAppSettingsActivity
import com.android.gamespace.stats.FpsStatsActivity
import com.android.gamespace.ui.components.AppIcon
import com.android.gamespace.ui.components.GameSpaceScaffold
import com.android.gamespace.ui.components.ListPreference
import com.android.gamespace.ui.components.LottieIllustration
import com.android.gamespace.ui.components.PreferenceGroup
import com.android.gamespace.ui.components.PreferenceRow
import com.android.gamespace.ui.components.SliderPreference
import com.android.gamespace.ui.components.SwitchPreference
import com.android.gamespace.ui.components.rememberPref
import com.android.gamespace.ui.components.rememberResumeCount
import com.android.gamespace.ui.components.rememberSystemFlag
import com.android.gamespace.utils.GameModeUtils
import com.android.gamespace.utils.GameModeUtils.Companion.describeGameMode

/** An app of the library: a game that is set up for Game Space. */
private class LibraryEntry(val packageName: String, val label: String, val mode: Int)

private fun Context.libraryOf(games: List<UserGame>): List<LibraryEntry> =
    games.mapNotNull { game ->
        val info = try {
            packageManager.getApplicationInfo(
                game.packageName,
                PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
            )
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
        info?.let { LibraryEntry(game.packageName, it.loadLabel(packageManager).toString(), game.mode) }
    }.sortedBy { it.label.lowercase() }

/** The main screen: what Game Space does while a game runs, and the library of games. */
@Composable
fun SettingsScreen(
    appSettings: AppSettings,
    systemSettings: SystemSettings,
    gameModeUtils: GameModeUtils,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val resume by rememberResumeCount()

    // The library is read again after a game is added or removed, and when the screen comes back.
    var libraryVersion by remember { mutableStateOf(0) }
    val library = remember(resume, libraryVersion) { context.libraryOf(systemSettings.userGames) }

    fun registerApp(packageName: String) {
        val games = systemSettings.userGames.toMutableList()
        if (games.none { it.packageName == packageName }) games.add(UserGame(packageName))
        systemSettings.userGames = games
        gameModeUtils.setIntervention(packageName, GameConfig.ModeBuilder.build())
        libraryVersion++
    }

    fun unregisterApp(packageName: String) {
        val games = systemSettings.userGames.filter { it.packageName != packageName }
        systemSettings.userGames = games
        gameModeUtils.setIntervention(packageName, null)
        libraryVersion++
    }

    val selectorLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra(AppSelectorActivity.EXTRA_APP)?.let { registerApp(it) }
        }
    }
    val perAppLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra(PerAppSettingsActivity.EXTRA_UNREGISTER)
                ?.let { unregisterApp(it) }
        }
    }

    val danmaku = rememberPref(resume, { appSettings.danmakuNotification }, { appSettings.danmakuNotification = it })
    val callsMode = rememberPref(resume, { appSettings.callsMode.toString() }, { appSettings.callsMode = it.toIntOrNull() ?: 0 })
    val ringerMode = rememberPref(resume, { appSettings.ringerMode.toString() }, { appSettings.ringerMode = it.toIntOrNull() ?: 3 })
    val autoDnd = rememberPref(resume, { appSettings.autoDnd }, { appSettings.autoDnd = it })
    val suppressFullscreen = rememberSystemFlag(resume, "gamespace_suppress_fullscreen_intent", false)
    val bypassCharge = rememberSystemFlag(resume, "bypass_charge_enabled", false)
    val autoBrightness = rememberPref(resume, { appSettings.noAutoBrightness }, { appSettings.noAutoBrightness = it })
    val threeScreenshot = rememberPref(resume, { appSettings.noThreeScreenshot }, { appSettings.noThreeScreenshot = it })
    val stayAwake = rememberPref(resume, { appSettings.stayAwake }, { appSettings.stayAwake = it })
    val lockGesture = rememberPref(resume, { appSettings.lockGesture }, { appSettings.lockGesture = it })
    val opacity = rememberPref(resume, { appSettings.menuOpacity }, { appSettings.menuOpacity = it })
    val autoHide = rememberPref(resume, { appSettings.barAutoHideEnabled }, { appSettings.barAutoHideEnabled = it })
    val autoHideDelay = rememberPref(resume, { appSettings.barAutoHideDelaySec }, { appSettings.barAutoHideDelaySec = it })

    // Only devices that can do it show the bypass charging switch.
    val bypassSupported = remember {
        Build.MANUFACTURER.equals("Google", ignoreCase = true) ||
            SystemProperties.getBoolean("persist.sys.battery_bypass_supported", false)
    }

    var quickStartOpen by remember { mutableStateOf(false) }

    GameSpaceScaffold(title = stringResource(R.string.settings_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(padding),
        ) {
            LottieIllustration(
                rawRes = R.raw.game_settings_illustration,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
            )

            PreferenceGroup(title = stringResource(R.string.in_game_preferences_title)) {
                item { shape ->
                    SwitchPreference(
                        title = stringResource(R.string.danmaku_notification_mode_title),
                        summary = stringResource(R.string.danmaku_notification_mode_summary),
                        checked = danmaku.value,
                        onCheckedChange = { danmaku.value = it },
                        shape = shape,
                    )
                }
                item { shape ->
                    ListPreference(
                        title = stringResource(R.string.in_game_calls_title),
                        entries = stringArrayResource(R.array.in_game_calls_entries).toList(),
                        values = stringArrayResource(R.array.in_game_calls_values).toList(),
                        value = callsMode.value,
                        onValueChange = { callsMode.value = it },
                        shape = shape,
                    )
                }
                item { shape ->
                    ListPreference(
                        title = stringResource(R.string.ringer_mode_title),
                        entries = stringArrayResource(R.array.ringer_mode_entries).toList(),
                        values = stringArrayResource(R.array.ringer_mode_values).toList(),
                        value = ringerMode.value,
                        onValueChange = { ringerMode.value = it },
                        shape = shape,
                    )
                }
                item { shape ->
                    SwitchPreference(
                        title = stringResource(R.string.auto_dnd_title),
                        summary = stringResource(R.string.auto_dnd_summary),
                        checked = autoDnd.value,
                        onCheckedChange = { autoDnd.value = it },
                        shape = shape,
                    )
                }
                item { shape ->
                    SwitchPreference(
                        title = stringResource(R.string.suppress_fullscreen_intent_title),
                        summary = stringResource(R.string.suppress_fullscreen_intent_summary),
                        checked = suppressFullscreen.value,
                        onCheckedChange = { suppressFullscreen.value = it },
                        shape = shape,
                    )
                }
                if (bypassSupported) {
                    item { shape ->
                        SwitchPreference(
                            title = stringResource(R.string.bypass_charge_enabled_summary),
                            summary = stringResource(R.string.bypass_charge_enabled_title),
                            checked = bypassCharge.value,
                            onCheckedChange = { bypassCharge.value = it },
                            shape = shape,
                        )
                    }
                }
                item { shape ->
                    SwitchPreference(
                        title = stringResource(R.string.auto_brightness_disabled_title),
                        summary = stringResource(R.string.auto_brightness_disabled_summary),
                        checked = autoBrightness.value,
                        onCheckedChange = { autoBrightness.value = it },
                        shape = shape,
                    )
                }
                item { shape ->
                    SwitchPreference(
                        title = stringResource(R.string.three_screenshot_disabled_title),
                        summary = stringResource(R.string.three_screenshot_disabled_summary),
                        checked = threeScreenshot.value,
                        onCheckedChange = { threeScreenshot.value = it },
                        shape = shape,
                    )
                }
                item { shape ->
                    SwitchPreference(
                        title = stringResource(R.string.stay_awake_title),
                        summary = stringResource(R.string.stay_awake_summary),
                        checked = stayAwake.value,
                        onCheckedChange = { stayAwake.value = it },
                        shape = shape,
                    )
                }
                item { shape ->
                    SwitchPreference(
                        title = stringResource(R.string.lock_gesture_title),
                        checked = lockGesture.value,
                        onCheckedChange = { lockGesture.value = it },
                        shape = shape,
                    )
                }
                item { shape ->
                    SliderPreference(
                        title = stringResource(R.string.gamespace_menu_opacity_title),
                        value = opacity.value,
                        min = 0,
                        max = 100,
                        onValueChange = { opacity.value = it },
                        shape = shape,
                    )
                }
                item { shape ->
                    SwitchPreference(
                        title = stringResource(R.string.gamespace_bar_auto_hide_title),
                        summary = stringResource(R.string.gamespace_bar_auto_hide_summary),
                        checked = autoHide.value,
                        onCheckedChange = { autoHide.value = it },
                        shape = shape,
                    )
                }
                item { shape ->
                    SliderPreference(
                        title = stringResource(
                            R.string.gamespace_bar_auto_hide_delay_value, autoHideDelay.value
                        ),
                        value = autoHideDelay.value,
                        min = 1,
                        max = 15,
                        onValueChange = { autoHideDelay.value = it },
                        shape = shape,
                        enabled = autoHide.value,
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.other_feature_category)) {
                item { shape ->
                    PreferenceRow(
                        title = stringResource(R.string.fps_statistics_title),
                        summary = stringResource(R.string.fps_statistics_summary),
                        shape = shape,
                        onClick = { context.startActivity(Intent(context, FpsStatsActivity::class.java)) },
                    )
                }
                item { shape ->
                    PreferenceRow(
                        title = stringResource(R.string.display_enhancer_title),
                        summary = stringResource(R.string.display_enhancer_summary),
                        shape = shape,
                        onClick = {
                            context.startActivity(Intent(context, DisplayEnhancerActivity::class.java))
                        },
                    )
                }
                item { shape ->
                    PreferenceRow(
                        title = stringResource(R.string.quick_start_apps_title),
                        summary = stringResource(R.string.quick_start_apps_summary),
                        shape = shape,
                        onClick = { quickStartOpen = true },
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.game_list_title)) {
                item { shape ->
                    PreferenceRow(
                        title = stringResource(R.string.game_add),
                        shape = shape,
                        leading = {
                            Icon(
                                painter = painterResource(R.drawable.ic_add),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = {
                            selectorLauncher.launch(Intent(context, AppSelectorActivity::class.java))
                        },
                    )
                }
                library.forEach { entry ->
                    item { shape ->
                        PreferenceRow(
                            title = entry.label,
                            summary = context.describeGameMode(entry.mode),
                            shape = shape,
                            leading = { AppIcon(entry.packageName, size = 48.dp) },
                            trailing = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_gear),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            onClick = {
                                perAppLauncher.launch(
                                    Intent(context, PerAppSettingsActivity::class.java).apply {
                                        putExtra(PerAppSettingsActivity.EXTRA_PACKAGE, entry.packageName)
                                    }
                                )
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (quickStartOpen) {
        QuickStartAppsDialog(onDismiss = { quickStartOpen = false })
    }
}
