/*
 * Copyright (C) 2021 Chaldeaprjkt
 *               2022 crDroid Android Project
 *               2023-2024 the risingOS Android Project
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

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemProperties
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import dagger.hilt.android.AndroidEntryPoint
import com.android.gamespace.R
import com.android.gamespace.preferences.AppListPreferences
import com.android.gamespace.preferences.appselector.AppSelectorActivity
import com.android.gamespace.preferences.QuickStartAppPreference
import com.android.gamespace.preferences.QuickStartAppPreferenceDialogFragment

@AndroidEntryPoint(PreferenceFragmentCompat::class)
class SettingsFragment : Hilt_SettingsFragment() {

    private var apps: AppListPreferences? = null

    private val selectorResult =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            apps?.useSelectorResult(it)
        }

    private val perAppResult =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            apps?.usePerAppResult(it)
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val isGoogleDevice = Build.MANUFACTURER.equals("Google", ignoreCase = true)
        val bypassSupported = SystemProperties.getBoolean("persist.sys.battery_bypass_supported", false)
        val bypassChargePref = findPreference<Preference>("bypass_charge_enabled")

        if (!isGoogleDevice && !bypassSupported && bypassChargePref != null) {
            val category = findPreference<Preference>("in_game_preferences") as? PreferenceCategory
            category?.removePreference(bypassChargePref)
        }

        apps = findPreference(Settings.System.GAMESPACE_GAME_LIST)
        apps?.onRegisteredAppClick {
            perAppResult.launch(Intent(context, PerAppSettingsActivity::class.java).apply {
                putExtra(PerAppSettingsActivity.EXTRA_PACKAGE, it)
            })
        }

        findPreference<Preference>(AppListPreferences.KEY_ADD_GAME)
            ?.setOnPreferenceClickListener {
                selectorResult.launch(Intent(context, AppSelectorActivity::class.java))
                return@setOnPreferenceClickListener true
            }
    }

    override fun onResume() {
        super.onResume()
        apps?.updateAppList()
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference is QuickStartAppPreference) {
            val dialogFragment = QuickStartAppPreferenceDialogFragment.newInstance(preference.key)
            dialogFragment.setTargetFragment(this, 0)
            dialogFragment.show(parentFragmentManager, "QuickStartAppPreferenceDialogFragment")
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }
}
