package com.android.gamespace.stats

import android.os.Bundle
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity

/** Shows the statistics and charts of one recorded FPS session ([EXTRA_ID]). */
class FpsSessionActivity : CollapsingToolbarBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(com.android.settingslib.collapsingtoolbar.R.id.content_frame, FpsSessionFragment())
                .commit()
        }
    }

    companion object {
        const val EXTRA_ID = "session_id"
    }
}
