package com.android.gamespace.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import kotlin.math.roundToLong

/**
 * Keeps recorded FPS sessions in the app's private storage so they can be browsed from Settings.
 *
 * Every session is a pair of files named after its start time:
 *  - `<id>.csv`        the samples, in the same format that is exported to Download/GameSpace
 *  - `<id>.properties` metadata and the summary shown in the session list
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

    /** @return the stored session, or null if [samples] is empty or it could not be written. */
    fun save(meta: Meta, samples: List<FpsSample>): Info? {
        val stats = FpsStats.of(samples) ?: return null
        val id = meta.startMs.toString()
        if (!dir.exists() && !dir.mkdirs()) return null

        File(dir, "$id.csv").writeText(encodeCsv(samples, meta.startMs))
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
    fun list(): List<Info> =
        (dir.listFiles { f -> f.name.endsWith(".properties") } ?: emptyArray())
            .mapNotNull { readInfo(it) }
            .sortedByDescending { it.startMs }

    fun info(id: String): Info? = metaFile(id)?.takeIf { it.exists() }?.let { readInfo(it) }

    fun loadSamples(id: String): List<FpsSample> {
        val file = csvFile(id)?.takeIf { it.exists() } ?: return emptyList()
        return decodeCsv(file.readText())
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
        metaFile(id)?.delete()
    }

    fun deleteAll() {
        dir.listFiles()?.forEach { it.delete() }
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
    }

    private fun readInfo(file: File): Info? = runCatching {
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
        list().drop(MAX_SESSIONS).forEach { delete(it.id) }
    }

    companion object {
        const val DIR_NAME = "fps_sessions"
        const val MAX_SESSIONS = 100
        const val CSV_HEADER =
            "timestamp,elapsed_s,fps,battery_temp_c,power_w,cpu_usage_pct,cpu_temp_c"

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

        /** Metrics the device could not provide are left empty. */
        fun encodeCsv(samples: List<FpsSample>, startWallMs: Long): String {
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
            fun num(v: Float, digits: Int) =
                if (v.isNaN()) "" else String.format(Locale.US, "%.${digits}f", v)
            return buildString {
                append(CSV_HEADER).append('\n')
                for (s in samples) {
                    append(time.format(Date(startWallMs + s.elapsedMs))).append(',')
                    append(String.format(Locale.US, "%.1f", s.elapsedMs / 1000f)).append(',')
                    append(num(s.fps, 1)).append(',')
                    append(num(s.batteryTempC, 1)).append(',')
                    append(num(s.powerW, 2)).append(',')
                    append(num(s.cpuUsagePct, 1)).append(',')
                    append(num(s.cpuTempC, 1)).append('\n')
                }
            }
        }

        /** Inverse of [encodeCsv]. Malformed lines are skipped. */
        fun decodeCsv(text: String): List<FpsSample> =
            text.lineSequence().drop(1).mapNotNull { line ->
                val c = line.split(',')
                if (c.size < 3) return@mapNotNull null
                val elapsed = c[1].toFloatOrNull() ?: return@mapNotNull null
                val fps = c[2].toFloatOrNull() ?: return@mapNotNull null
                fun at(i: Int) = c.getOrNull(i)?.toFloatOrNull() ?: Float.NaN
                FpsSample(
                    elapsedMs = (elapsed * 1000f).roundToLong(),
                    fps = fps,
                    batteryTempC = at(3),
                    powerW = at(4),
                    cpuUsagePct = at(5),
                    cpuTempC = at(6),
                )
            }.toList()
    }
}
