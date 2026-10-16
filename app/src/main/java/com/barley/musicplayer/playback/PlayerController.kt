package com.barley.musicplayer.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.barley.musicplayer.data.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var controller: MediaController? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = refreshState()
        override fun onPlaybackStateChanged(playbackState: Int) = refreshState()
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = refreshState()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = refreshState()
        override fun onPlayerError(error: PlaybackException) {
            Log.w("PlayerController", "playback error: ${error.message}", error)
            refreshState()
        }
    }

    fun connect(onReady: () -> Unit = {}) {
        if (controller != null) {
            onReady()
            return
        }
        val sessionToken = SessionToken(context, ComponentName(context, MusicPlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener(
            {
                controller = future.get().also { it.addListener(playerListener) }
                refreshState()
                onReady()
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun playQueue(tracks: List<Track>, startIndex: Int) {
        val mediaItems = tracks.map { it.toMediaItem() }
        controller?.apply {
            setMediaItems(mediaItems, startIndex, 0L)
            prepare()
            play()
        }
    }

    fun togglePlayPause() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun skipNext() {
        controller?.takeIf { it.hasNextMediaItem() }?.seekToNextMediaItem()
    }

    fun skipPrevious() {
        controller?.takeIf { it.hasPreviousMediaItem() }?.seekToPreviousMediaItem()
    }

    fun release() {
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
    }

    private fun refreshState() {
        val player = controller ?: return
        val metadata = player.mediaMetadata
        _state.value = PlayerUiState(
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            currentTitle = metadata.title?.toString(),
            currentArtist = metadata.artist?.toString(),
            currentArtworkUrl = metadata.artworkUri?.toString(),
            hasPrevious = player.hasPreviousMediaItem(),
            hasNext = player.hasNextMediaItem(),
        )
    }

    private fun Track.toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(uploaderName)
            .setArtworkUri(thumbnailUrl?.let { Uri.parse(it) })
            .build()
        return MediaItem.Builder()
            .setMediaId(videoId)
            .setUri(YoutubeDataSourceResolver.placeholderUri(videoId))
            .setMediaMetadata(metadata)
            .build()
    }
}
