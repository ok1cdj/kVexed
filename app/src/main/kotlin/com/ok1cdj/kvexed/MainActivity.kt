package com.ok1cdj.kvexed

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ok1cdj.kvexed.ui.KVexedTheme

// Single-activity host. All screens are Compose; the real navigation and game
// UI are wired up in Phase 3.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KVexedTheme {
                // Placeholder — replaced by the pack/level/game navigation.
            }
        }
    }
}
