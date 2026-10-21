package com.barley.musicplayer.playback

data class PlayerUiState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentTitle: String? = null,
    val currentArtist: String? = null,
    val currentArtworkUrl: String? = null,
    val currentDescription: String? = null,
    val chapters: List<Chapter> = emptyList(),
    val currentPositionMillis: Long = 0L,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
)
