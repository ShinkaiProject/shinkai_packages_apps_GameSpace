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

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ProgressBar;

import com.android.gamespace.utils.MemoryUsageMonitor;

public class MemoryView extends ProgressBar {

    private final MemoryUsageMonitor mMonitor;

    private final MemoryUsageMonitor.Listener mListener = this::setProgress;

    public MemoryView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mMonitor = MemoryUsageMonitor.get(context);
        setMax(100);
    }

    /* Hijack this method to detect visibility rather than
     * onVisibilityChanged() because the the latter one can be
     * influenced by more factors, leading to unstable behavior. */
    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);

        if (visibility == VISIBLE)
            mMonitor.addListener(mListener);
        else
            mMonitor.removeListener(mListener);
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
}
