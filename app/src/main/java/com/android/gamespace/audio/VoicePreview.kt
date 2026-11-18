/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.gamespace.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log

/**
 * Records up to three seconds from the current voice communication source and plays it
 * back. The Voice Changer HAL effect is applied by the capture path, so the playback
 * already contains the processed voice.
 */
class VoicePreview(private val context: Context, private val onStateChange: (Boolean) -> Unit) {

    @Volatile
    private var cancelled = false

    @Volatile
    var isRunning = false
        private set

    private var thread: Thread? = null

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRunning) return
        cancelled = false
        thread = Thread({ run() }, "gamespace-voice-preview").apply { start() }
    }

    fun stop() {
        cancelled = true
        thread?.join(2000)
        thread = null
    }

    private fun run() {
        isRunning = true
        onStateChange(true)
        try {
            val rate = 48000
            val minBuffer = AudioRecord.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(rate / 2)
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer,
            )
            val maxSamples = rate * PREVIEW_SECONDS
            val buffer = ShortArray(maxSamples)
            var filled = 0
            record.startRecording()
            while (!cancelled && filled < maxSamples) {
                val want = minBuffer / 4
                val read = record.read(buffer, filled, want)
                if (read <= 0) break
                filled += read
            }
            record.stop()
            record.release()

            if (filled > 0 && !cancelled) {
                val track = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    rate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    filled * 2,
                    AudioTrack.MODE_STATIC,
                )
                track.write(buffer, 0, filled)
                track.play()
                val durationMs = filled * 1000L / rate
                val waited = track.playbackHeadPosition * 1000L / rate
                val remaining = durationMs - waited
                if (remaining > 0) Thread.sleep(remaining)
                track.stop()
                track.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Voice preview failed", e)
        } finally {
            isRunning = false
            onStateChange(false)
        }
    }

    private companion object {
        const val TAG = "GameSpaceVoicePreview"
        const val PREVIEW_SECONDS = 3
    }
}
