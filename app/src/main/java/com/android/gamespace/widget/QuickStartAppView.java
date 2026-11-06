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
package com.android.gamespace.widget;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.android.gamespace.R;

public class QuickStartAppView extends LinearLayout {
    private static final LruCache<String, Drawable> sIconCache = new LruCache<>(24);

    private RecyclerView recyclerView;
    private MyRecyclerViewAdapter mAdapter;
    private Context mContext;
    private SettingsContentObserver mObserver;
    private boolean mObserverRegistered = false;

    public QuickStartAppView(Context context) {
        super(context);
        init(context);
    }

    public QuickStartAppView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public QuickStartAppView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        recyclerView = findViewById(R.id.quick_start_app_list);
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemAnimator(null);
        LinearLayoutManager layoutManager = new LinearLayoutManager(mContext, RecyclerView.HORIZONTAL, false);
        recyclerView.setLayoutManager(layoutManager);
        mAdapter = new MyRecyclerViewAdapter(mContext);
        recyclerView.setAdapter(mAdapter);
        updateAppIcons();
    }

    private void init(Context context) {
        mContext = context;
        mObserver = new SettingsContentObserver(new Handler(Looper.getMainLooper()));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!mObserverRegistered) {
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor("quick_start_apps"), true, mObserver);
            mObserverRegistered = true;
        }
        updateAppIcons();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mObserverRegistered) {
            mContext.getContentResolver().unregisterContentObserver(mObserver);
            mObserverRegistered = false;
        }
        super.onDetachedFromWindow();
    }

    private void updateAppIcons() {
        if (mAdapter == null) return;
        String appPackageNames = Settings.System.getString(mContext.getContentResolver(), "quick_start_apps");
        if (appPackageNames != null && !appPackageNames.isEmpty()) {
            String[] packages = appPackageNames.split(",");
            if (packages.length > 0) {
                mAdapter.setData(Arrays.asList(packages));
                setVisibility(View.VISIBLE);
                return;
            }
        }
        setVisibility(View.GONE);
    }

    private Drawable getCachedAppIcon(String packageName) {
        Drawable cached = sIconCache.get(packageName);
        if (cached != null) return cached;
        try {
            PackageManager pm = mContext.getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            Drawable icon = pm.getApplicationIcon(appInfo);
            if (icon != null) {
                sIconCache.put(packageName, icon);
            }
            return icon;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private void setupAppIcon(ImageView imageView, @Nullable String packageName) {
        if (imageView == null) return;
        if (packageName == null || packageName.isEmpty()) {
            imageView.setVisibility(GONE);
        } else {
            Drawable icon = getCachedAppIcon(packageName);
            if (icon != null) {
                imageView.setImageDrawable(icon);
                imageView.setOnClickListener(v -> launchAppInFreeformMode(packageName));
                imageView.setVisibility(VISIBLE);
            } else {
                imageView.setVisibility(GONE);
            }
        }
    }

    private void launchAppInFreeformMode(String packageName) {
        try {
            PackageManager packageManager = mContext.getPackageManager();
            Intent launchIntent = packageManager.getLaunchIntentForPackage(packageName);
            if (launchIntent != null && launchIntent.getComponent() != null) {
                String activityName = launchIntent.getComponent().getClassName();
                Intent freeformIntent = new Intent("com.libremobileos.freeform.START_FREEFORM")
                        .setPackage("com.libremobileos.freeform")
                        .putExtra("packageName", packageName)
                        .putExtra("activityName", activityName)
                        .putExtra("userId", UserHandle.myUserId());
                mContext.sendBroadcast(freeformIntent);
            }
        } catch (Exception ignored) {}
    }

    private class SettingsContentObserver extends ContentObserver {
        SettingsContentObserver(Handler handler) {
            super(handler);
        }
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            updateAppIcons();
        }
    }

    public class MyRecyclerViewAdapter extends RecyclerView.Adapter<MyRecyclerViewAdapter.RecyclerHolder> {
        private final Context mContext;
        private final List<String> dataList = new ArrayList<>();

        public MyRecyclerViewAdapter(Context context) {
            this.mContext = context;
        }

        public void setData(List<String> newData) {
            this.dataList.clear();
            if (newData != null) {
                this.dataList.addAll(newData);
            }
            notifyDataSetChanged();
        }

        @Override
        public RecyclerHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(mContext).inflate(R.layout.quick_start_app_item, parent, false);
            return new RecyclerHolder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerHolder holder, int position) {
            setupAppIcon(holder.imageView, dataList.get(position));
            holder.itemView.setVisibility(holder.imageView.getVisibility());
        }

        @Override
        public int getItemCount() {
            return dataList.size();
        }

        class RecyclerHolder extends RecyclerView.ViewHolder {
            final ImageView imageView;

            private RecyclerHolder(View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.app_icon);
                imageView.setVisibility(GONE);
            }
        }
    }
}
