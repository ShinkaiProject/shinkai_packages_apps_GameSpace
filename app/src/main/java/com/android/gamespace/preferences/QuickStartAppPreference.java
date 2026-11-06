/*
 * Copyright (C) 2023-2024 the risingOS Android Project           
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
package com.android.gamespace.preferences;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import androidx.preference.DialogPreference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class QuickStartAppPreference extends DialogPreference {

    private String[] appPackageNames;
    private String[] appNames;
    private Drawable[] appIcons;

    private static class AppEntry {
        final String packageName;
        final String name;
        final Drawable icon;

        AppEntry(String packageName, String name, Drawable icon) {
            this.packageName = packageName;
            this.name = name;
            this.icon = icon;
        }
    }

    public QuickStartAppPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private synchronized void ensureAppsLoaded() {
        if (appPackageNames != null) return;
        PackageManager pm = getContext().getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        List<AppEntry> entries = new ArrayList<>();
        for (ApplicationInfo app : apps) {
            if ((app.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                entries.add(new AppEntry(
                        app.packageName,
                        pm.getApplicationLabel(app).toString(),
                        pm.getApplicationIcon(app)
                ));
            }
        }
        entries.sort(Comparator.comparing(e -> e.name, String.CASE_INSENSITIVE_ORDER));
        int size = entries.size();
        appPackageNames = new String[size];
        appNames = new String[size];
        appIcons = new Drawable[size];
        for (int i = 0; i < size; i++) {
            AppEntry entry = entries.get(i);
            appPackageNames[i] = entry.packageName;
            appNames[i] = entry.name;
            appIcons[i] = entry.icon;
        }
    }

    public String[] getAppPackageNames() {
        ensureAppsLoaded();
        return appPackageNames;
    }

    public String[] getAppNames() {
        ensureAppsLoaded();
        return appNames;
    }

    public Drawable[] getAppIcons() {
        ensureAppsLoaded();
        return appIcons;
    }

    public void saveValue(String value) {
        persistString(value);
    }
}
