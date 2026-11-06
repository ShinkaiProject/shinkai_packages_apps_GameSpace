package com.android.gamespace.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;

/**
 * Reads how much of the RAM is in use, once a second, for as long as something is listening.
 *
 * The panel shows the usage twice (as a text and as a bar). Each of them used to poll the
 * system on its own, allocating a new {@link ActivityManager.MemoryInfo} every time. They now
 * share one poll, which does not run at all while the panel is closed, and which tells the
 * listeners only when the percentage has changed.
 *
 * Main thread only.
 */
public final class MemoryUsageMonitor {

    public interface Listener {
        void onUsageChanged(int percent);
    }

    private static final long INTERVAL_MS = 1000;

    private static MemoryUsageMonitor sInstance;

    public static synchronized MemoryUsageMonitor get(Context context) {
        if (sInstance == null) {
            Context appContext = context.getApplicationContext();
            sInstance = new MemoryUsageMonitor(appContext != null ? appContext : context);
        }
        return sInstance;
    }

    private final ActivityManager mActivityManager;
    private final ActivityManager.MemoryInfo mMemoryInfo = new ActivityManager.MemoryInfo();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final ArrayList<Listener> mListeners = new ArrayList<>();

    private final Runnable mTick = new Runnable() {
        @Override
        public void run() {
            sample();
            mHandler.postDelayed(this, INTERVAL_MS);
        }
    };

    /** Last percentage that was handed out, or -1 when nothing has been read since the start. */
    private int mPercent = -1;

    private MemoryUsageMonitor(Context context) {
        mActivityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
    }

    public void addListener(Listener listener) {
        if (mListeners.contains(listener)) return;
        mListeners.add(listener);
        if (mListeners.size() == 1) {
            mPercent = -1;
            mHandler.post(mTick);
        } else if (mPercent >= 0) {
            listener.onUsageChanged(mPercent);
        }
    }

    public void removeListener(Listener listener) {
        if (!mListeners.remove(listener)) return;
        if (mListeners.isEmpty()) {
            mHandler.removeCallbacks(mTick);
        }
    }

    private void sample() {
        mActivityManager.getMemoryInfo(mMemoryInfo);
        if (mMemoryInfo.totalMem <= 0) return;
        long used = mMemoryInfo.totalMem - mMemoryInfo.availMem;
        int percent = (int) ((used * 100) / mMemoryInfo.totalMem);
        if (percent == mPercent) return;
        mPercent = percent;
        // Backwards, so that a listener may remove itself from its callback.
        for (int i = mListeners.size() - 1; i >= 0; i--) {
            mListeners.get(i).onUsageChanged(percent);
        }
    }
}
