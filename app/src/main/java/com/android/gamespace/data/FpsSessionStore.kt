package com.android.gamespace.data

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps recorded FPS sessions in the app's private storage so they can be browsed from Settings.
 *
 * Every session is a pair of files named after its start time:
 *  - `<id>.csv`        the samples, in the same format that is exported to Download/GameSpace
 *  - `<id>.properties` metadata and the summary shown in the session list
 *
 * The summaries are remembered together with the modification time and size of the file they were
 * read from, so listing the sessions again only parses the files that actually changed.
 */
class FpsSessionStore(context: Context) {

    /** What is known about a session before its samples are loaded. */
    class Info(
        val id: String,
        val packageName: String,
        val label: String,
        val startMs: Long,
        val durationMs: Long,
        val resolution: String,
        val description: String,
        val avgFps: Float,
        val minFps: Float,
        val maxFps: Float,
        val low1: Float,
        val sampleCount: Int,
    )

    class Meta(
        val packageName: String,
        val label: String,
        val startMs: Long,
        val resolution: String,
    )

    private val dir = File(context.filesDir, DIR_NAME)

    /**
     * Stores a session. [stats] must be the statistics of [samples]; passing them in saves
     * computing them a second time.
     *
     * @return the stored session, or null if [samples] is empty or it could not be written.
     */
    fun save(meta: Meta, samples: FpsSamples, stats: FpsStats): Info? {
        if (samples.isEmpty()) return null
        val id = meta.startMs.toString()
        if (!dir.exists() && !dir.mkdirs()) return null

        val csv = File(dir, "$id.csv")
        try {
            FileOutputStream(csv).use { FpsCsv.write(samples, meta.startMs, it) }
        } catch (e: Exception) {
            csv.delete() // do not leave half a file behind
            throw e
        }
        val info = Info(
            id = id,
            packageName = meta.packageName,
            label = meta.label,
            startMs = meta.startMs,
            durationMs = stats.durationMs,
            resolution = meta.resolution,
            description = "",
            avgFps = stats.fps.avg,
            minFps = stats.fps.min,
            maxFps = stats.fps.max,
            low1 = stats.low1,
            sampleCount = stats.sampleCount,
        )
        writeInfo(info)
        prune()
        return info
    }

    /** All sessions, newest first. Unreadable entries are skipped. */
    fun list(): List<Info> {
        val files = dir.listFiles { f -> f.name.endsWith(".properties") } ?: emptyArray()
        // Forget the summaries of sessions that are gone.
        val paths = files.mapTo(HashSet()) { it.path }
        val prefix = dir.path + File.separator
        infoCache.keys.removeAll { it.startsWith(prefix) && it !in paths }
        return files.mapNotNull { readInfo(it) }.sortedByDescending { it.startMs }
    }

    fun info(id: String): Info? = metaFile(id)?.takeIf { it.exists() }?.let { readInfo(it) }

    fun loadSamples(id: String): FpsSamples {
        val file = csvFile(id)?.takeIf { it.exists() } ?: return FpsSamples(1)
        return FileInputStream(file).use { FpsCsv.read(it, file.length()) }
    }

    fun setDescription(id: String, description: String) {
        val current = info(id) ?: return
        writeInfo(
            Info(
                current.id, current.packageName, current.label, current.startMs,
                current.durationMs, current.resolution, description.trim(), current.avgFps,
                current.minFps, current.maxFps, current.low1, current.sampleCount
            )
        )
    }

    fun delete(id: String) {
        csvFile(id)?.delete()
        metaFile(id)?.let {
            it.delete()
            infoCache.remove(it.path)
        }
    }

    fun deleteAll() {
        dir.listFiles()?.forEach {
            it.delete()
            infoCache.remove(it.path)
        }
    }

    fun csvFile(id: String): File? = fileFor(id, "csv")

    private fun metaFile(id: String): File? = fileFor(id, "properties")

    /** Ids are start timestamps; anything else is rejected so an id can never escape [dir]. */
    private fun fileFor(id: String, extension: String): File? =
        if (id.isNotEmpty() && id.all { it.isDigit() }) File(dir, "$id.$extension") else null

    private fun writeInfo(info: Info) {
        val props = Properties().apply {
            setProperty(K_PACKAGE, info.packageName)
            setProperty(K_LABEL, info.label)
            setProperty(K_START, info.startMs.toString())
            setProperty(K_DURATION, info.durationMs.toString())
            setProperty(K_RESOLUTION, info.resolution)
            setProperty(K_DESCRIPTION, info.description)
            setProperty(K_AVG, info.avgFps.toString())
            setProperty(K_MIN, info.minFps.toString())
            setProperty(K_MAX, info.maxFps.toString())
            setProperty(K_LOW1, info.low1.toString())
            setProperty(K_SAMPLES, info.sampleCount.toString())
        }
        val file = metaFile(info.id) ?: return
        file.outputStream().use { props.store(it, null) }
        infoCache[file.path] = CachedInfo(file.lastModified(), file.length(), info)
    }

    private fun readInfo(file: File): Info? {
        val modified = file.lastModified()
        val length = file.length()
        infoCache[file.path]?.let {
            if (it.modified == modified && it.length == length) return it.info
        }
        val info = parseInfo(file) ?: return null
        infoCache[file.path] = CachedInfo(modified, length, info)
        return info
    }

    private fun parseInfo(file: File): Info? = runCatching {
        val props = Properties().apply { file.inputStream().use { load(it) } }
        val id = file.nameWithoutExtension
        Info(
            id = id,
            packageName = props.getProperty(K_PACKAGE) ?: return null,
            label = props.getProperty(K_LABEL) ?: props.getProperty(K_PACKAGE),
            startMs = props.getProperty(K_START)?.toLong() ?: id.toLong(),
            durationMs = props.getProperty(K_DURATION)?.toLong() ?: 0L,
            resolution = props.getProperty(K_RESOLUTION) ?: "",
            description = props.getProperty(K_DESCRIPTION) ?: "",
            avgFps = props.getProperty(K_AVG)?.toFloat() ?: 0f,
            minFps = props.getProperty(K_MIN)?.toFloat() ?: 0f,
            maxFps = props.getProperty(K_MAX)?.toFloat() ?: 0f,
            low1 = props.getProperty(K_LOW1)?.toFloat() ?: 0f,
            sampleCount = props.getProperty(K_SAMPLES)?.toInt() ?: 0,
        )
    }.getOrNull()

    /** Keeps the storage bounded: only the newest [MAX_SESSIONS] sessions survive. */
    private fun prune() {
        val count = dir.list { _, name -> name.endsWith(".properties") }?.size ?: 0
        if (count <= MAX_SESSIONS) return
        list().drop(MAX_SESSIONS).forEach { delete(it.id) }
    }

    private class CachedInfo(val modified: Long, val length: Long, val info: Info)

    companion object {
        const val DIR_NAME = "fps_sessions"
        const val MAX_SESSIONS = 100

        private const val K_PACKAGE = "package"
        private const val K_LABEL = "label"
        private const val K_START = "start_ms"
        private const val K_DURATION = "duration_ms"
        private const val K_RESOLUTION = "resolution"
        private const val K_DESCRIPTION = "description"
        private const val K_AVG = "avg_fps"
        private const val K_MIN = "min_fps"
        private const val K_MAX = "max_fps"
        private const val K_LOW1 = "low1_fps"
        private const val K_SAMPLES = "samples"

        /** Session summaries by file path, shared by every store instance in the process. */
        private val infoCache = ConcurrentHashMap<String, CachedInfo>()
    }
}
