package com.gb6105.musictimely.ui.share

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.gb6105.musictimely.ui.theme.MusicTimelyTheme
import dagger.hilt.android.AndroidEntryPoint

/** Receives `ACTION_SEND` text/plain (FR-A023). The URL text stays in device settings only. */
@AndroidEntryPoint
class ShareReceiveActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        setContent {
            MusicTimelyTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
                        ShareRoute(hiltViewModel(), sharedText = text, onClose = ::finish)
                    }
                }
            }
        }
    }
}
