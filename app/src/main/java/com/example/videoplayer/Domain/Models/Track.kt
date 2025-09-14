package com.example.videoplayer.Domain.Models

// domain/model/Track.kt
data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: String,
    val albumArtUri: String? = null
)
