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
package com.android.gamespace.preferences.appselector

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.android.gamespace.data.SystemSettings
import com.android.gamespace.ui.settings.AppSelectorScreen
import com.android.gamespace.ui.setGameSpaceContent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint(ComponentActivity::class)
class AppSelectorActivity : Hilt_AppSelectorActivity() {
    @Inject lateinit var settings: SystemSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setGameSpaceContent {
            AppSelectorScreen(
                settings = settings,
                onBack = ::finish,
                onSelect = { packageName ->
                    setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_APP, packageName))
                    finish()
                },
            )
        }
    }

    companion object {
        /** In the result: the package of the app that was picked. */
        const val EXTRA_APP = "selected_app"
    }
}
