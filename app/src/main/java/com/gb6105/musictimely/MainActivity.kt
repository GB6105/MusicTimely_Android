package com.gb6105.musictimely

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.gb6105.musictimely.ui.app.MusicTimelyApp
import com.gb6105.musictimely.ui.theme.MusicTimelyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MusicTimelyTheme {
                // MusicTimelyApp draws the background edge-to-edge and pads its content by WindowInsets.safeDrawing.
                MusicTimelyApp(
                    appViewModel = hiltViewModel(),
                    allocationViewModel = { hiltViewModel() },
                    sessionViewModel = { hiltViewModel() },
                    settingsViewModel = { hiltViewModel() },
                    musicSheetViewModel = { hiltViewModel() },
                    seenViewModel = { hiltViewModel() },
                    memoViewModel = { hiltViewModel() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
