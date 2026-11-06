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

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import com.android.gamespace.R;
import com.android.gamespace.utils.MemoryUsageMonitor;

public class TextMemoryView extends TextView {

    private final MemoryUsageMonitor mMonitor;

    private final MemoryUsageMonitor.Listener mListener = percent ->
            setText(getContext().getString(R.string.ram_usage) + " " + percent + "%");

    public TextMemoryView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mMonitor = MemoryUsageMonitor.get(context);
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);

        if (visibility == VISIBLE) {
            mMonitor.addListener(mListener);
        } else {
            mMonitor.removeListener(mListener);
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
}
