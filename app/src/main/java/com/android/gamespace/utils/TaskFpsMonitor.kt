package com.android.gamespace.utils

import android.app.ActivityTaskManager
import android.content.Context
import android.util.Log
import android.view.WindowManager
import android.window.TaskFpsCallback
import java.util.concurrent.CopyOnWriteArraySet

/**
 * The single place that listens to the FPS the window manager reports for the game's task.
 *
 * SurfaceFlinger delivers FPS reports to only ONE listener per task: when several are registered
 * for the same task it picks the first one it finds and the others never hear anything. Having the
 * on-bar FPS counter and the FPS recorder register separately therefore made them starve each
 * other, so a recording could silently stop receiving samples while the counter kept updating.
 * Everything that wants the FPS subscribes here instead, and this class holds the one and only
 * registration, fanning every report out to all subscribers.
 *
 * Listeners are called on the thread the report arrives on (a binder thread) and must return
 * quickly; hand any real work over to another thread.
 */
class TaskFpsMonitor(private val context: Context) {

    fun interface Listener {
        fun onFps(fps: Float)
    }

    private val lock = Any()
    private val listeners = CopyOnWriteArraySet<Listener>()
    private val wm by lazy { context.getSystemService(Context.WINDOW_SERVICE) as WindowManager }

    /** Task the callback is registered for, or [NO_TASK]. Guarded by [lock]. */
    private var registeredTaskId = NO_TASK

    private val callback = object : TaskFpsCallback() {
        override fun onFpsReported(fps: Float) {
            for (listener in listeners) {
                runCatching { listener.onFps(fps) }
                    .onFailure { Log.e(TAG, "FPS listener failed", it) }
            }
        }
    }

    /**
     * Starts delivering FPS reports of the focused task to [listener]. Adding a listener that is
     * already subscribed is harmless.
     *
     * The registration is made for the task that is focused when the first listener arrives and
     * kept until the last one leaves.
     *
     * @return true if reports are being received after the call.
     */
    fun addListener(listener: Listener): Boolean {
        listeners.add(listener)
        return ensureRegistered()
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
        synchronized(lock) {
            // Checked under the lock so a listener added at the same time keeps the registration.
            if (listeners.isEmpty()) unregister()
        }
    }

    private fun ensureRegistered(): Boolean = synchronized(lock) {
        if (registeredTaskId != NO_TASK) return true

        val taskId = runCatching { ActivityTaskManager.getService()?.focusedRootTaskInfo?.taskId }
            .getOrNull() ?: return false

        try {
            wm.registerTaskFpsCallback(taskId, Runnable::run, callback)
            registeredTaskId = taskId
            true
        } catch (e: Exception) {
            Log.e(TAG, "Unable to register task FPS callback", e)
            false
        }
    }

    private fun unregister() {
        if (registeredTaskId == NO_TASK) return
        registeredTaskId = NO_TASK
        runCatching { wm.unregisterTaskFpsCallback(callback) }
    }

    private companion object {
        const val TAG = "TaskFpsMonitor"
        const val NO_TASK = -1
    }
}
