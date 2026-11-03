package com.android.gamespace.stats

import android.os.Bundle
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity

/** Lists the recorded FPS sessions. */
class FpsStatsActivity : CollapsingToolbarBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(com.android.settingslib.collapsingtoolbar.R.id.content_frame, FpsStatsFragment())
                .commit()
        }
    }
}
