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
import com.barley.musicplayer.data.youtube.YoutubeExtractorService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val youtubeExtractorService: YoutubeExtractorService,
) {
    private var controller: MediaController? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var descriptionJob: Job? = null
    private var positionPollJob: Job? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = refreshState()
        override fun onPlaybackStateChanged(playbackState: Int) = refreshState()
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = refreshState()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            refreshState()
            loadDescription(mediaItem?.mediaId)
        }
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
                startPositionPolling()
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

    /**
     * 긴 믹스 영상(설명에 타임스탬프로 곡 목록이 있는 경우) 재생 중이면 다음 챕터(곡)로 이동하고,
     * 아니면 원래대로 다음 곡(미디어 아이템)으로 넘어간다.
     */
    fun skipNext() {
        val player = controller ?: return
        val chapters = _state.value.chapters
        if (chapters.isNotEmpty()) {
            val positionSeconds = player.currentPosition / 1000
            val nextChapter = chapters.firstOrNull { it.startSeconds > positionSeconds }
            if (nextChapter != null) {
                player.seekTo(nextChapter.startSeconds * 1000)
                return
            }
        }
        if (player.hasNextMediaItem()) player.seekToNextMediaItem()
    }

    /** 현재 챕터 시작 후 얼마 안 됐으면 이전 챕터로, 아니면 현재 챕터 처음으로 되돌아간다. */
    fun skipPrevious() {
        val player = controller ?: return
        val chapters = _state.value.chapters
        if (chapters.isNotEmpty()) {
            val positionSeconds = player.currentPosition / 1000
            val currentIndex = chapters.indexOfLast { it.startSeconds <= positionSeconds }
            val target = if (currentIndex > 0 && positionSeconds - chapters[currentIndex].startSeconds <= RESTART_THRESHOLD_SECONDS) {
                chapters[currentIndex - 1]
            } else {
                chapters.getOrNull(currentIndex)
            }
            if (target != null) {
                player.seekTo(target.startSeconds * 1000)
                return
            }
        }
        if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
    }

    fun seekToChapter(index: Int) {
        val chapter = _state.value.chapters.getOrNull(index) ?: return
        controller?.seekTo(chapter.startSeconds * 1000)
    }

    fun release() {
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
        descriptionJob?.cancel()
        positionPollJob?.cancel()
    }

    private fun refreshState() {
        val player = controller ?: return
        val metadata = player.mediaMetadata
        _state.update { previous ->
            previous.copy(
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                currentTitle = metadata.title?.toString(),
                currentArtist = metadata.artist?.toString(),
                currentArtworkUrl = metadata.artworkUri?.toString(),
                // 챕터가 있으면 마지막 곡이라도 "다음"으로 챕터 처음/끝까지는 이동할 수 있으니 항상 활성화해둔다.
                hasPrevious = player.hasPreviousMediaItem() || previous.chapters.isNotEmpty(),
                hasNext = player.hasNextMediaItem() || previous.chapters.isNotEmpty(),
            )
        }
    }

    private fun loadDescription(videoId: String?) {
        descriptionJob?.cancel()
        _state.update { it.copy(currentDescription = null, chapters = emptyList()) }
        if (videoId == null) return
        descriptionJob = scope.launch {
            val description = try {
                youtubeExtractorService.fetchDescription(videoId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("PlayerController", "description fetch failed: ${e.message}")
                null
            }
            val parsed = description?.let { parseDescriptionChapters(it) }
            _state.update {
                it.copy(
                    currentDescription = parsed?.preamble?.takeIf { text -> text.isNotBlank() },
                    chapters = parsed?.chapters.orEmpty(),
                )
            }
        }
    }

    private fun startPositionPolling() {
        if (positionPollJob?.isActive == true) return
        positionPollJob = scope.launch {
            while (isActive) {
                controller?.let { player ->
                    _state.update { it.copy(currentPositionMillis = player.currentPosition) }
                }
                delay(POSITION_POLL_INTERVAL_MILLIS)
            }
        }
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

    companion object {
        private const val RESTART_THRESHOLD_SECONDS = 3L
        private const val POSITION_POLL_INTERVAL_MILLIS = 1000L
    }
}
