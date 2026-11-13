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

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.android.gamespace.data.SystemSettings
import com.android.gamespace.ui.settings.PerAppScreen
import com.android.gamespace.ui.setGameSpaceContent
import com.android.gamespace.utils.GameModeUtils
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint(ComponentActivity::class)
class PerAppSettingsActivity : Hilt_PerAppSettingsActivity() {
    @Inject lateinit var settings: SystemSettings
    @Inject lateinit var gameModeUtils: GameModeUtils

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val packageName = intent?.getStringExtra(EXTRA_PACKAGE)
        if (packageName == null) {
            finish()
            return
        }
        setGameSpaceContent {
            PerAppScreen(
                packageName = packageName,
                settings = settings,
                gameModeUtils = gameModeUtils,
                onBack = ::finish,
                onUnregister = {
                    setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_UNREGISTER, packageName))
                    finish()
                },
            )
        }
    }

    companion object {
        /** The package of the game to configure. */
        const val EXTRA_PACKAGE = "package_name"

        /** In the result: the package of the game that is to be taken out of the library. */
        const val EXTRA_UNREGISTER = "per_app_unregister"
    }
}
