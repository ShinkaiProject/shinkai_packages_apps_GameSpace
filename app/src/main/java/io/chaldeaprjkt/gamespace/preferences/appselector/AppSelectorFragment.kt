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
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.SearchView
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import com.google.android.material.appbar.AppBarLayout
import dagger.hilt.android.AndroidEntryPoint
import com.android.gamespace.R
import com.android.gamespace.data.SystemSettings
import com.android.gamespace.preferences.AppListPreferences
import javax.inject.Inject

@AndroidEntryPoint(SettingsBasePreferenceFragment::class)
class AppSelectorFragment : Hilt_AppSelectorFragment(), SearchView.OnQueryTextListener,
    MenuItem.OnActionExpandListener {

    @Inject
    lateinit var settings: SystemSettings

    private var appBarLayout: AppBarLayout? = null
    private val appPreferences = mutableListOf<Preference>()

    private val menuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.app_selector_menu, menu)
            val searchMenuItem = menu.findItem(R.id.app_search_menu)
            val searchView = searchMenuItem.actionView as? SearchView
            searchView?.setOnQueryTextListener(this@AppSelectorFragment)
            searchView?.queryHint = getString(R.string.app_search_title)
            searchMenuItem.setOnActionExpandListener(this@AppSelectorFragment)
        }

        override fun onMenuItemSelected(menuItem: MenuItem) = false
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val screen = preferenceManager.createPreferenceScreen(requireContext())
        preferenceScreen = screen
        loadApps(screen)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        appBarLayout = activity?.findViewById(com.android.settingslib.collapsingtoolbar.R.id.app_bar)
        activity?.addMenuProvider(menuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun loadApps(screen: PreferenceScreen) {
        val packageManager = requireContext().packageManager
        val flags = PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
        val apps = packageManager.getInstalledApplications(flags)
            .filter {
                it.packageName != requireContext().packageName &&
                        it.flags and ApplicationInfo.FLAG_SYSTEM == 0 &&
                        !settings.userGames.any { g -> g.packageName == it.packageName }
            }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }

        appPreferences.clear()
        for (appInfo in apps) {
            val pref = Preference(requireContext()).apply {
                key = appInfo.packageName
                title = appInfo.loadLabel(packageManager)
                summary = appInfo.packageName
                icon = appInfo.loadIcon(packageManager)
                isPersistent = false
                setOnPreferenceClickListener {
                    activity?.setResult(Activity.RESULT_OK, Intent().apply {
                        putExtra(AppListPreferences.EXTRA_APP, appInfo.packageName)
                    })
                    activity?.finish()
                    true
                }
            }
            appPreferences.add(pref)
            screen.addPreference(pref)
        }
    }

    override fun onQueryTextSubmit(query: String?) = false

    override fun onQueryTextChange(newText: String?): Boolean {
        val query = newText?.trim()?.lowercase().orEmpty()
        appPreferences.forEach { pref ->
            pref.isVisible = query.isEmpty() ||
                    pref.title?.toString()?.lowercase()?.contains(query) == true
        }
        return false
    }

    override fun onMenuItemActionExpand(item: MenuItem): Boolean {
        appBarLayout?.setExpanded(false, false)
        listView?.let { ViewCompat.setNestedScrollingEnabled(it, false) }
        return true
    }

    override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
        appBarLayout?.setExpanded(false, false)
        listView?.let { ViewCompat.setNestedScrollingEnabled(it, true) }
        return true
    }
}
