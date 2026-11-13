package com.android.gamespace.ui.components

import android.content.Context
import android.os.UserHandle
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** A number that goes up each time the screen comes back to the front. */
@Composable
fun rememberResumeCount(): State<Int> {
    val count = remember { mutableStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) count.value = count.value + 1
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return count
}

/**
 * A setting that is shown and changed on a screen: it is read when it is made, and again each
 * time [resume] changes (so a change made elsewhere while the screen was away shows up), and
 * setting it writes it right away.
 */
class PrefState<T>(private val read: () -> T, private val write: (T) -> Unit) {
    private val state = mutableStateOf(read())

    var value: T
        get() = state.value
        set(next) {
            state.value = next
            write(next)
        }
}

@Composable
fun <T> rememberPref(resume: Int, read: () -> T, write: (T) -> Unit): PrefState<T> =
    remember(resume) { PrefState(read, write) }

/** A yes / no setting that lives in Settings.System of the current user. */
fun Context.getSystemFlag(key: String, default: Boolean): Boolean =
    Settings.System.getIntForUser(
        contentResolver, key, if (default) 1 else 0, UserHandle.USER_CURRENT
    ) != 0

fun Context.putSystemFlag(key: String, value: Boolean) {
    Settings.System.putIntForUser(
        contentResolver, key, if (value) 1 else 0, UserHandle.USER_CURRENT
    )
}

@Composable
fun rememberSystemFlag(resume: Int, key: String, default: Boolean): PrefState<Boolean> {
    val context = LocalContext.current
    return rememberPref(
        resume = resume,
        read = { context.getSystemFlag(key, default) },
        write = { context.putSystemFlag(key, it) },
    )
}
