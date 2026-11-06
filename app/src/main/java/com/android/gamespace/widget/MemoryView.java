/*
 * Copyright (C) 2022 crDroid Android Project
 * Copyright (C) 2022 riceDroid Android Project
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
import android.view.View;
import android.widget.ProgressBar;

public class MemoryView extends ProgressBar {

    private static final long POLL_INTERVAL_MS = 3000L;

    private final ActivityManager mActivityManager;
    private final ActivityManager.MemoryInfo mMemoryInfo = new ActivityManager.MemoryInfo();
    private final Handler mHandler;
    private final MemInfoWorker mWorker;

    public MemoryView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mActivityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        mHandler = new Handler(Looper.getMainLooper());
        mWorker = new MemInfoWorker();
        setMax(100);
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        mHandler.removeCallbacks(mWorker);
        if (visibility == VISIBLE && isAttachedToWindow()) {
            mHandler.post(mWorker);
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setVisibility(VISIBLE);
    }

    @Override
    public void onDetachedFromWindow() {
        mHandler.removeCallbacks(mWorker);
        super.onDetachedFromWindow();
    }

    private class MemInfoWorker implements Runnable {
        @Override
        public void run() {
            if (!isAttachedToWindow() || getVisibility() != VISIBLE) return;
            mActivityManager.getMemoryInfo(mMemoryInfo);
            if (mMemoryInfo.totalMem > 0) {
                long usedMemory = mMemoryInfo.totalMem - mMemoryInfo.availMem;
                int usedMemoryPercentage = (int) ((usedMemory * 100L) / mMemoryInfo.totalMem);
                setProgress(usedMemoryPercentage);
            }
            mHandler.postDelayed(this, POLL_INTERVAL_MS);
        }
    }
}
