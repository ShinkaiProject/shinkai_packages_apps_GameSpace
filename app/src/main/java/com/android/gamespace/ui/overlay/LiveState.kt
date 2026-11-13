package com.android.gamespace.ui.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * Something that says when a value of the system has changed. [subscribe] starts to listen,
 * calling its argument on every change, and returns what stops it again.
 */
fun interface ChangeSource {
    fun subscribe(onChange: () -> Unit): () -> Unit
}

/** A change of one of the broadcasts [actions]. */
fun Context.broadcasts(vararg actions: String) = ChangeSource { signal ->
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = signal()
    }
    val filter = IntentFilter().apply { actions.forEach { addAction(it) } }
    registerReceiver(receiver, filter)
    val stop: () -> Unit = { runCatching { unregisterReceiver(receiver) } }
    stop
}

/** A change of what [uri] points to. */
fun Context.observing(uri: Uri) = ChangeSource { signal ->
    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = signal()
    }
    contentResolver.registerContentObserver(uri, false, observer)
    val stop: () -> Unit = { runCatching { contentResolver.unregisterContentObserver(observer) } }
    stop
}

/**
 * A value of the system, read now and read again on each change that [source] reports, for as
 * long as this is on the screen. Without a source it is read once. The result is a state that may
 * also be set, to show what is about to happen before the system has said so.
 */
@Composable
fun <T> rememberLive(source: ChangeSource?, read: () -> T): MutableState<T> {
    val state = remember { mutableStateOf(read()) }
    val latestRead by rememberUpdatedState(read)
    DisposableEffect(source == null) {
        val stop = source?.subscribe { state.value = latestRead() }
        // It may have changed between reading it and starting to listen.
        if (source != null) state.value = latestRead()
        onDispose { stop?.invoke() }
    }
    return state
}
