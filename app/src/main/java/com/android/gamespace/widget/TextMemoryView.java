/*
 * Copyright (C) 2022 riceDroid Android Project
 *               2023-2024 the risingOS Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.gamespace.widget;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.widget.TextView;

import com.android.gamespace.R;

public class TextMemoryView extends TextView {

    private ActivityManager mActivityManager;
    private Handler mHandler;
    private MemInfoWorker mWorker;

    public TextMemoryView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mActivityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        mHandler = new Handler(Looper.getMainLooper());
        mWorker = new MemInfoWorker();
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);

        if (visibility == VISIBLE) {
            mHandler.post(mWorker);
        } else {
            mHandler.removeCallbacks(mWorker);
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setVisibility(VISIBLE);
    }

    @Override
    public void onDetachedFromWindow() {
        setVisibility(GONE);
        super.onDetachedFromWindow();
    }

    private class MemInfoWorker implements Runnable {
        @Override
        public void run() {
            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            mActivityManager.getMemoryInfo(memoryInfo);
            long usedMemory = memoryInfo.totalMem - memoryInfo.availMem;
            int usedMemoryPercentage = (int) ((usedMemory * 100) / memoryInfo.totalMem);
            String ramUsage = getContext().getString(R.string.ram_usage) + " " + usedMemoryPercentage + "%";
            setText(ramUsage);
            mHandler.postDelayed(this, 1000);
        }
    }
}

