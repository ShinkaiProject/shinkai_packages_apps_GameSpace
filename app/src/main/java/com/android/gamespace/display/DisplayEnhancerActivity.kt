package com.android.gamespace.display

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.android.gamespace.ui.display.DisplayEnhancerScreen
import com.android.gamespace.ui.setGameSpaceContent

class DisplayEnhancerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val enhancer = displayEnhancer()
        setGameSpaceContent {
            DisplayEnhancerScreen(enhancer = enhancer, onBack = ::finish)
        }
    }
}
