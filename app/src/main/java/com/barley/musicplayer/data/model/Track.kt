package com.barley.musicplayer.data.model

data class Track(
    val videoId: String,
    val title: String,
    val uploaderName: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long,
)
