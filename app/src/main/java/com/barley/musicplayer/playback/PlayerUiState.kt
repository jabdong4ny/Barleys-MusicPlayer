package com.barley.musicplayer.playback

data class PlayerUiState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentTitle: String? = null,
    val currentArtist: String? = null,
    val currentArtworkUrl: String? = null,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
)
