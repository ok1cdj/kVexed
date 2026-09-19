package com.ok1cdj.kvexed

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ok1cdj.kvexed.ui.AboutDialog
import com.ok1cdj.kvexed.ui.GameScreen
import com.ok1cdj.kvexed.ui.GameViewModel
import com.ok1cdj.kvexed.ui.KVexedTheme
import com.ok1cdj.kvexed.ui.LevelListScreen
import com.ok1cdj.kvexed.ui.PackListScreen
import com.ok1cdj.kvexed.ui.Screen
import com.ok1cdj.kvexed.ui.SettingsDialog

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KVexedTheme { App() } }
    }
}

@Composable
private fun App() {
    val vm: GameViewModel = viewModel()
    var showAbout by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    // Persist progress (and the resumable board) whenever the app goes to the
    // background — per the spec, on pause rather than continuously.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) vm.persist()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // System back mirrors the in-screen back navigation.
    when (val s = vm.screen) {
        is Screen.LevelList -> BackHandler { vm.backToPacks() }
        is Screen.Game -> BackHandler { vm.backToLevels() }
        Screen.PackList -> {} // default: exit the app
    }

    // targetSdk 37 forces edge-to-edge, so inset the whole app below the status
    // and navigation bars — otherwise the header (ⓘ, ‹ back) sits under the
    // status bar, which swallows those taps.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding(),
    ) {
        when (val s = vm.screen) {
            Screen.PackList -> PackListScreen(vm, onAbout = { showAbout = true }, onSettings = { showSettings = true })
            is Screen.LevelList -> LevelListScreen(vm, s.packId)
            is Screen.Game -> GameScreen(vm, onAbout = { showAbout = true })
        }
    }

    if (showAbout) AboutDialog(onDismiss = { showAbout = false })
    if (showSettings) {
        SettingsDialog(
            settings = vm.settings,
            onShowSolve = { vm.setHideSolve(!it) },
            onHaptics = vm::setHaptics,
            onDismiss = { showSettings = false },
        )
    }
}
