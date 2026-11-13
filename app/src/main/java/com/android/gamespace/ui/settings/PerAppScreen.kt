package com.android.gamespace.ui.settings

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.gamespace.R
import com.android.gamespace.data.GameConfig
import com.android.gamespace.data.SystemSettings
import com.android.gamespace.ui.components.AppIcon
import com.android.gamespace.ui.components.GameSpaceScaffold
import com.android.gamespace.ui.components.ListPreference
import com.android.gamespace.ui.components.PreferenceGroup
import com.android.gamespace.ui.components.PreferenceRow
import com.android.gamespace.ui.components.SwitchPreference
import com.android.gamespace.utils.GameModeUtils
import java.util.Locale

/**
 * The settings of one game of the library: its game mode, ANGLE, the render resolution, and
 * taking it out of the library ([onUnregister]).
 */
@Composable
fun PerAppScreen(
    packageName: String,
    settings: SystemSettings,
    gameModeUtils: GameModeUtils,
    onBack: () -> Unit,
    onUnregister: () -> Unit,
) {
    val context = LocalContext.current
    val label = remember(packageName) {
        runCatching {
            val info = context.packageManager.getApplicationInfo(
                packageName, PackageManager.ApplicationInfoFlags.of(0)
            )
            info.loadLabel(context.packageManager).toString()
        }.getOrDefault(packageName)
    }

    var mode by remember(packageName) {
        mutableStateOf(
            (settings.userGames.firstOrNull { it.packageName == packageName }?.mode ?: 1).toString()
        )
    }
    var useAngle by remember(packageName) { mutableStateOf(gameModeUtils.isAngleUsed(packageName)) }
    var downscale by remember(packageName) {
        mutableStateOf("%.2f".format(Locale.US, gameModeUtils.downscaleFactorOf(packageName)))
    }

    val angleAllowed = remember { context.resources.getBoolean(R.bool.config_allow_per_app_angle_usage) }
    val angleAvailable = remember { gameModeUtils.findAnglePackage()?.isEnabled == true }

    // The first line of the downscale dialog's text is its title, the rest explains it.
    val downscaleText = stringResource(R.string.per_app_downscale_dialog_title)
    val downscaleTitle = downscaleText.substringBefore('\n')
    val downscaleMessage = downscaleText.substringAfter('\n', "").ifEmpty { null }

    GameSpaceScaffold(title = stringResource(R.string.per_app_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppIcon(packageName, size = 54.dp)
                Text(text = label, style = MaterialTheme.typography.titleLarge)
            }

            PreferenceGroup {
                item { shape ->
                    ListPreference(
                        title = stringResource(R.string.per_app_mode_title),
                        entries = stringArrayResource(R.array.per_app_preferred_mode_entries).toList(),
                        values = stringArrayResource(R.array.per_app_preferred_mode_values).toList(),
                        value = mode,
                        onValueChange = { next ->
                            mode = next
                            gameModeUtils.setGameModeFor(packageName, settings, next.toIntOrNull() ?: 1)
                            context.forceStop(packageName)
                        },
                        shape = shape,
                    )
                }
                if (angleAllowed) {
                    item { shape ->
                        SwitchPreference(
                            title = stringResource(R.string.per_app_angle_title),
                            summary = stringResource(
                                if (angleAvailable) R.string.per_app_angle_summary
                                else R.string.cant_find_angle_pkg
                            ),
                            checked = angleAvailable && useAngle,
                            enabled = angleAvailable,
                            onCheckedChange = { next ->
                                useAngle = next
                                val modes = GameConfig.ModeBuilder.apply {
                                    this.useAngle = next
                                    this.downscale = gameModeUtils.downscaleFactorOf(packageName)
                                }.build()
                                gameModeUtils.setIntervention(packageName, modes)
                                context.forceStop(packageName)
                            },
                            shape = shape,
                        )
                    }
                }
                item { shape ->
                    ListPreference(
                        title = stringResource(R.string.per_app_downscale_title),
                        entries = stringArrayResource(R.array.per_app_downscale_entries).toList(),
                        values = stringArrayResource(R.array.per_app_downscale_values).toList(),
                        value = downscale,
                        onValueChange = { next ->
                            downscale = next
                            val modes = GameConfig.ModeBuilder.apply {
                                this.useAngle = gameModeUtils.isAngleUsed(packageName)
                                this.downscale = next.toFloatOrNull() ?: GameConfig.NO_DOWNSCALE
                            }.build()
                            gameModeUtils.setIntervention(packageName, modes)
                            context.forceStop(packageName)
                        },
                        shape = shape,
                        dialogTitle = downscaleTitle,
                        dialogMessage = downscaleMessage,
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.remove_category)) {
                item { shape ->
                    PreferenceRow(
                        title = stringResource(R.string.per_app_unregister, label),
                        shape = shape,
                        leading = {
                            Icon(
                                painter = painterResource(R.drawable.ic_remove),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = onUnregister,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun Context.forceStop(packageName: String) {
    runCatching {
        (getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
            ?.forceStopPackage(packageName)
    }
}
