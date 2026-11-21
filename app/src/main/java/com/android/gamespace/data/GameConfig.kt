/*
 * Copyright (C) 2021 Chaldeaprjkt
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
package com.android.gamespace.data

import android.app.GameManager

import java.util.Locale

/**
 * data class for setting up the Game Mode API Intervention
 */
data class GameConfig(
    val mode: Int,
    val downscaleFactor: Float? = null,
    val useAngle: Boolean? = null,
) {
    companion object {
        const val NO_DOWNSCALE = 1f
        const val MIN_DOWNSCALE = 0.3f
        const val MAX_DOWNSCALE = 0.99f

        fun buildModes(downscale: Float? = null, useAngle: Boolean? = null) = listOf(
            GameConfig(GameManager.GAME_MODE_PERFORMANCE, downscale, useAngle),
            GameConfig(GameManager.GAME_MODE_BATTERY, downscale, useAngle),
        )

        fun parseModes(config: String?) = config.orEmpty().split(':').mapNotNull { mode ->
            if ('=' !in mode) return@mapNotNull null
            mode.split(',').filter { '=' in it }.associateTo(linkedMapOf()) { field ->
                field.substringBefore('=').trim() to field.substringAfter('=').trim()
            }
        }

        fun Iterable<GameConfig>.mergeWith(config: String?): String {
            val modes = parseModes(config).toMutableList()
            forEach { update ->
                val fields = modes.lastOrNull { it["mode"]?.toIntOrNull() == update.mode }
                    ?: linkedMapOf("mode" to update.mode.toString()).also { modes.add(it) }
                update.downscaleFactor?.let { factor ->
                    if (factor in MIN_DOWNSCALE..MAX_DOWNSCALE) {
                        fields["downscaleFactor"] = "%.2f".format(Locale.US, factor)
                    } else {
                        fields.remove("downscaleFactor")
                    }
                }
                update.useAngle?.let { useAngle ->
                    if (useAngle) fields["useAngle"] = "true" else fields.remove("useAngle")
                }
            }
            return modes.joinToString(":") { fields ->
                fields.entries.joinToString(",") { (key, value) -> "$key=$value" }
            }
        }
    }
}
