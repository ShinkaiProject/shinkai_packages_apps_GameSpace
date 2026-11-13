package com.android.gamespace.stats

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.android.gamespace.data.FpsSessionStore
import com.android.gamespace.ui.setGameSpaceContent
import com.android.gamespace.ui.stats.FpsStatsScreen

/** Lists the recorded FPS sessions. */
class FpsStatsActivity : ComponentActivity() {

    private val store by lazy { FpsSessionStore(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setGameSpaceContent {
            FpsStatsScreen(
                store = store,
                onBack = ::finish,
                onOpenSession = { id ->
                    startActivity(
                        Intent(this, FpsSessionActivity::class.java)
                            .putExtra(FpsSessionActivity.EXTRA_ID, id)
                    )
                },
            )
        }
    }
}
