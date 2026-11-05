package com.android.gamespace.utils

import android.app.ActivityTaskManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import com.android.gamespace.R
import com.android.gamespace.data.FpsCsv
import com.android.gamespace.data.FpsSamples
import com.android.gamespace.data.FpsSessionStore
import com.android.gamespace.data.FpsStats
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Records the FPS reported by the window manager for the game's task together with battery, power
 * and CPU readings taken at the same moments.
 *
 * When stopped, the session is stored for the FPS statistics screen in Settings and a CSV copy is
 * exported to Download/GameSpace.
 *
 * Recording is independent from the on-bar FPS counter, so it keeps running while the bar is
 * collapsed or the panel is closed. It is stopped automatically when the game session ends.
 *
 * It is built to stay out of the game's way: samples go into primitive arrays, the sensors are
 * read on a background-priority thread that only exists while there is something to do, nothing
 * is posted to the main thread unless a listener asked for samples, and saving streams the CSV
 * to disk instead of building it in memory.
 */
class FpsRecorder @Inject constructor(
    private val context: Context,
    private val fpsMonitor: TaskFpsMonitor,
) {

    interface Listener {
        /** Called on the main thread when recording starts or stops. */
        fun onStateChanged(recording: Boolean) {}

        /**
         * Called on the main thread for every FPS sample received while recording. Only listeners
         * added with `wantsSamples = true` get these.
         */
        fun onSample(elapsedMs: Long, fps: Float) {}
    }

    private class Session(
        val packageName: String,
        val label: String,
        val resolution: String,
        val startWallMs: Long,
        val startElapsedMs: Long,
    ) {
        val samples = FpsSamples()
        var fpsListener: TaskFpsMonitor.Listener? = null
    }

    private val lock = Any()
    private val stateListeners = CopyOnWriteArrayList<Listener>()
    private val sampleListeners = CopyOnWriteArrayList<Listener>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val wm by lazy { context.getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private val store by lazy { FpsSessionStore(context) }
    private val sampler by lazy { SystemStatsSampler(context) }

    // FPS reports are handled on their own thread so reading sysfs never blocks a binder thread.
    // Being single threaded it also keeps the sampler's state consistent.
    private val sampleExecutor by lazy { backgroundExecutor("FpsRecorder-sample") }
    private val saver by lazy { backgroundExecutor("FpsRecorder-save") }

    @Volatile
    private var session: Session? = null

    val isRecording: Boolean
        get() = session != null

    /** Time since recording started, or 0 when idle. */
    val elapsedMs: Long
        get() = session?.let { SystemClock.elapsedRealtime() - it.startElapsedMs } ?: 0L

    /**
     * @param wantsSamples also call [Listener.onSample]. Leave it off when only the state matters:
     * every sample listener costs a main thread wake-up for each FPS report while recording.
     */
    fun addListener(listener: Listener, wantsSamples: Boolean = false) {
        stateListeners.addIfAbsent(listener)
        if (wantsSamples) sampleListeners.addIfAbsent(listener)
    }

    fun removeListener(listener: Listener) {
        stateListeners.remove(listener)
        sampleListeners.remove(listener)
    }

    /**
     * Starts recording the FPS of the currently focused task (the game).
     * @return true if a recording is running after the call.
     */
    fun start(): Boolean {
        if (isRecording) return true

        val task = runCatching { ActivityTaskManager.getService()?.focusedRootTaskInfo }
            .getOrNull() ?: run {
            toast(R.string.fps_record_failed)
            return false
        }

        val packageName = task.topActivity?.packageName ?: "game"
        val newSession = Session(
            packageName = packageName,
            label = appLabel(packageName),
            resolution = displayResolution(),
            startWallMs = System.currentTimeMillis(),
            startElapsedMs = SystemClock.elapsedRealtime(),
        )
        // Reports arrive on a binder thread: note when, then do the sysfs reads on our own thread.
        val listener = TaskFpsMonitor.Listener { fps ->
            val receivedAt = SystemClock.elapsedRealtime()
            sampleExecutor.execute { onSample(newSession, fps, receivedAt) }
        }
        newSession.fpsListener = listener

        // Publish the session first so the very first report is not dropped.
        synchronized(lock) { session = newSession }

        // Queued before subscribing, so it always runs before the first report.
        sampleExecutor.execute { runCatching { sampler.reset() } }

        // Shares the single FPS registration with the on-bar counter; see TaskFpsMonitor.
        if (!fpsMonitor.addListener(listener)) {
            Log.e(TAG, "Unable to receive task FPS reports")
            synchronized(lock) { session = null }
            toast(R.string.fps_record_failed)
            return false
        }

        dispatchState(true)
        return true
    }

    /**
     * Stops recording and saves the session.
     *
     * @param blocking write the files on the calling thread. Use it when the process may go away
     * right after the call (e.g. the game session is being torn down).
     */
    fun stop(blocking: Boolean = false) {
        val finished = synchronized(lock) { session.also { session = null } } ?: return

        finished.fpsListener?.let { fpsMonitor.removeListener(it) }
        dispatchState(false)

        if (finished.samples.isEmpty()) {
            toast(R.string.fps_record_empty)
            return
        }

        val work = Runnable { save(finished) }
        if (blocking) work.run() else saver.execute(work)
    }

    /** @param receivedAt when the report arrived; sampling the sensors may lag behind it a little. */
    private fun onSample(s: Session, fps: Float, receivedAt: Long) {
        if (session !== s) return
        val reading = runCatching { sampler.read(receivedAt) }.getOrNull()

        val elapsed: Long
        val limitReached: Boolean
        synchronized(lock) {
            if (session !== s) return
            elapsed = (receivedAt - s.startElapsedMs).coerceAtLeast(0L)
            s.samples.add(
                elapsedMs = elapsed,
                fps = fps,
                batteryTempC = reading?.batteryTempC ?: Float.NaN,
                powerW = reading?.powerW ?: Float.NaN,
                cpuUsagePct = reading?.cpuUsagePct ?: Float.NaN,
                cpuTempC = reading?.cpuTempC ?: Float.NaN,
            )
            limitReached = s.samples.size >= MAX_SAMPLES
        }
        // Waking the main thread twice a second for nobody would be pure waste.
        if (sampleListeners.isNotEmpty()) {
            mainHandler.post { sampleListeners.forEach { it.onSample(elapsed, fps) } }
        }
        if (limitReached) mainHandler.post { stop() }
    }

    private fun dispatchState(recording: Boolean) {
        mainHandler.post { stateListeners.forEach { it.onStateChanged(recording) } }
    }

    private fun appLabel(packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            .loadLabel(pm).toString()
    }.getOrDefault(packageName)

    private fun displayResolution(): String = runCatching {
        val bounds = wm.maximumWindowMetrics.bounds
        "${bounds.width()}x${bounds.height()}"
    }.getOrDefault("")

    private fun save(finished: Session) {
        val samples = finished.samples
        val stats = FpsStats.of(samples) ?: return

        val stored = runCatching {
            store.save(
                FpsSessionStore.Meta(
                    finished.packageName, finished.label, finished.startWallMs, finished.resolution
                ),
                samples,
                stats
            )
        }.onFailure { Log.e(TAG, "Unable to store FPS session", it) }.getOrNull()

        val name = "fps_${finished.packageName}_" +
            "${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(finished.startWallMs))}.csv"

        // The stored copy is exactly the file to export, so copy it rather than encode it again.
        val storedCsv = stored?.let { store.csvFile(it.id) }?.takeIf { it.exists() }
        val writeCsv: (OutputStream) -> Unit = if (storedCsv != null) {
            { out -> FileInputStream(storedCsv).use { it.copyTo(out, COPY_BUFFER) } }
        } else {
            { out -> FpsCsv.write(samples, finished.startWallMs, out) }
        }

        val exported = saveToDownloads(name, writeCsv)
            // Only fall back to app storage when the session is not kept by the store either.
            ?: if (stored == null) saveToAppStorage(name, writeCsv) else null

        val location = exported
            ?: if (stored != null) context.getString(R.string.fps_statistics_title) else null

        if (location == null) {
            toast(R.string.fps_record_save_failed)
        } else {
            toast(
                context.getString(
                    R.string.fps_record_saved, location,
                    stats.fps.avg.roundToInt(), stats.fps.min.roundToInt(),
                    stats.fps.max.roundToInt(), stats.low1.roundToInt()
                )
            )
        }
    }

    /** @return the folder the file was written to, or null on failure. */
    private fun saveToDownloads(name: String, writeCsv: (OutputStream) -> Unit): String? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_DIR)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return null

        return try {
            val stream = resolver.openOutputStream(uri) ?: throw IOException("No output stream")
            stream.use(writeCsv)
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            RELATIVE_DIR
        } catch (e: Exception) {
            Log.e(TAG, "Unable to write FPS log to $RELATIVE_DIR", e)
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    /** Fallback when MediaStore is unavailable. */
    private fun saveToAppStorage(name: String, writeCsv: (OutputStream) -> Unit): String? = runCatching {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "fps")
        dir.mkdirs()
        FileOutputStream(File(dir, name)).use(writeCsv)
        dir.absolutePath
    }.onFailure { Log.e(TAG, "Unable to write FPS log to app storage", it) }.getOrNull()

    private fun toast(resId: Int) {
        toast(context.getString(resId))
    }

    private fun toast(text: CharSequence) {
        mainHandler.post { Toast.makeText(context, text, Toast.LENGTH_LONG).show() }
    }

    companion object {
        private const val TAG = "FpsRecorder"
        private const val COPY_BUFFER = 16 * 1024

        /** How long a worker thread stays around after its last job. */
        private const val IDLE_THREAD_SECONDS = 10L

        /**
         * One worker that runs jobs in order on a background-priority thread (so the game keeps the
         * big cores) and gives the thread up when it has been idle for a while: nothing stays
         * alive between recordings.
         */
        private fun backgroundExecutor(name: String) = ThreadPoolExecutor(
            1, 1, IDLE_THREAD_SECONDS, TimeUnit.SECONDS, LinkedBlockingQueue<Runnable>(),
            ThreadFactory { job ->
                Thread({
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                    job.run()
                }, name).apply { isDaemon = true }
            }
        ).apply { allowCoreThreadTimeOut(true) }

        private val RELATIVE_DIR = "${Environment.DIRECTORY_DOWNLOADS}/GameSpace"

        /** Safety cap so a forgotten recording cannot grow without bound. */
        private const val MAX_SAMPLES = 100_000
    }
}
