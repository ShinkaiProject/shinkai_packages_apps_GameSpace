package com.android.gamespace.stats

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.android.gamespace.data.FpsSessionStore
import com.android.gamespace.ui.setGameSpaceContent
import com.android.gamespace.ui.stats.FpsSessionScreen

/** Shows the statistics and charts of one recorded FPS session ([EXTRA_ID]). */
class FpsSessionActivity : ComponentActivity() {

    private val store by lazy { FpsSessionStore(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.getStringExtra(EXTRA_ID)
        setGameSpaceContent {
            FpsSessionScreen(store = store, sessionId = id, onBack = ::finish)
        }
    }

    companion object {
        const val EXTRA_ID = "session_id"
    }
}
