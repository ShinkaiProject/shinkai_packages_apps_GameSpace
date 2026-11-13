/*
 * Copyright (C) 2021 Chaldeaprjkt
 * Copyright (C) 2022-2024 crDroid Android Project
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
package com.android.gamespace.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.android.gamespace.data.AppSettings
import com.android.gamespace.data.SystemSettings
import com.android.gamespace.ui.setGameSpaceContent
import com.android.gamespace.ui.settings.SettingsScreen
import com.android.gamespace.utils.GameModeUtils
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint(ComponentActivity::class)
class SettingsActivity : Hilt_SettingsActivity() {
    @Inject lateinit var appSettings: AppSettings
    @Inject lateinit var systemSettings: SystemSettings
    @Inject lateinit var gameModeUtils: GameModeUtils

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setGameSpaceContent {
            SettingsScreen(
                appSettings = appSettings,
                systemSettings = systemSettings,
                gameModeUtils = gameModeUtils,
                onBack = ::finish,
            )
        }
    }
}
