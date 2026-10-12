package com.gb6105.musictimely.data.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.HandlerThread
import com.gb6105.musictimely.domain.model.control.MusicCommand
import com.gb6105.musictimely.domain.model.observation.TrackMetadata
import com.gb6105.musictimely.service.MediaAccessListener

/**
 * MediaSessionManager access through this app's notification listener (SRS Android §7.2).
 * Callbacks arrive on a dedicated handler thread, never on main (FR-A033).
 */
class AndroidMediaSessionGateway(
    private val context: Context,
    private val handler: Handler = callbackHandler,
) : MediaSessionGateway {

    private val component = ComponentName(context, MediaAccessListener::class.java)
    private val manager get() = context.getSystemService(MediaSessionManager::class.java)

    override fun isAccessGranted(): Boolean = isListenerEnabled(context)

    override fun activeSessions(): List<MediaSessionHandle> = manager.getActiveSessions(component).map(::AndroidSession)

    override fun watchSessions(onChanged: () -> Unit): AutoCloseable {
        val listener = MediaSessionManager.OnActiveSessionsChangedListener { onChanged() }
        manager.addOnActiveSessionsChangedListener(listener, component, handler)
        return AutoCloseable { manager.removeOnActiveSessionsChangedListener(listener) }
    }

    private inner class AndroidSession(private val controller: MediaController) : MediaSessionHandle {
        override val packageName: String get() = controller.packageName

        /** Transitional states (buffering, skipping …) count as playing so they do not read as a user pause. */
        override fun isPlaying(): Boolean = controller.playbackState?.state in PLAYING_STATES

        override fun metadata(): TrackMetadata? = controller.metadata?.let { m ->
            TrackMetadata(
                packageName = controller.packageName,
                title = m.getString(MediaMetadata.METADATA_KEY_TITLE) ?: m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
                artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST),
                durationMs = m.getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf { it > 0 },
            )
        }

        override fun positionMs(): Long? = controller.playbackState?.position?.takeIf { it >= 0 }

        override fun watch(listener: MediaSessionListener): AutoCloseable {
            val callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) = listener.onMetadataChanged()
                override fun onPlaybackStateChanged(state: PlaybackState?) = listener.onPlaybackChanged()
                override fun onSessionDestroyed() = listener.onDestroyed()
            }
            controller.registerCallback(callback, handler)
            return AutoCloseable { controller.unregisterCallback(callback) }
        }

        override fun send(command: MusicCommand) {
            when (command) {
                MusicCommand.PAUSE -> controller.transportControls.pause()
                MusicCommand.PLAY -> controller.transportControls.play()
            }
        }
    }

    companion object {
        /** The single observation thread: media callbacks, coordinator work and its delayed tasks. */
        val callbackHandler: Handler by lazy {
            Handler(HandlerThread("media-observation").apply { start() }.looper)
        }

        private val PLAYING_STATES = setOf(
            PlaybackState.STATE_PLAYING,
            PlaybackState.STATE_BUFFERING,
            PlaybackState.STATE_CONNECTING,
            PlaybackState.STATE_FAST_FORWARDING,
            PlaybackState.STATE_REWINDING,
            PlaybackState.STATE_SKIPPING_TO_NEXT,
            PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
            PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM,
        )
    }
}
