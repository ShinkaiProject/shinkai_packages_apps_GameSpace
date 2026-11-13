package com.android.gamespace.ui.settings

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.gamespace.R
import com.android.gamespace.ui.components.AppIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Where the apps of the quick start list are kept: the names of their packages, comma apart. */
const val KEY_QUICK_START_APPS = "quick_start_apps"

private class InstalledApp(val packageName: String, val label: String)

/** The apps that were installed by the user, in alphabetical order. */
private fun Context.installedUserApps(): List<InstalledApp> {
    val flags = PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
    return packageManager.getInstalledApplications(flags)
        .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
        .map { InstalledApp(it.packageName, packageManager.getApplicationLabel(it).toString()) }
        .sortedBy { it.label.lowercase() }
}

fun Context.savedQuickStartApps(): List<String> =
    Settings.System.getString(contentResolver, KEY_QUICK_START_APPS)
        ?.split(",")
        ?.filter { it.isNotEmpty() }
        .orEmpty()

/** Chooses the apps that can be started from the game panel. */
@Composable
fun QuickStartAppsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val apps by produceState<List<InstalledApp>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { context.installedUserApps() }
    }
    val selected = remember { mutableStateListOf<String>().apply { addAll(context.savedQuickStartApps()) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_start_apps_title)) },
        text = {
            val list = apps
            if (list != null) {
                LazyColumn {
                    items(list, key = { it.packageName }) { app ->
                        val checked = app.packageName in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) selected.remove(app.packageName)
                                    else selected.add(app.packageName)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Spacer(Modifier.width(12.dp))
                            AppIcon(app.packageName, size = 32.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(app.label, maxLines = 1)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    Settings.System.putString(
                        context.contentResolver,
                        KEY_QUICK_START_APPS,
                        selected.joinToString(",")
                    )
                    onDismiss()
                }
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
