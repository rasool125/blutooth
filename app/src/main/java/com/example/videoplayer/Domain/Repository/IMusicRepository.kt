package com.example.videoplayer.Domain.Repository// domain/repository/IMusicRepository.kt
import com.example.videoplayer.Domain.Models.Track
import kotlinx.coroutines.flow.Flow

interface IMusicRepository {
    suspend fun getAllTracks(): Flow<List<Track>>
    suspend fun getTrackById(id: Long): Track?
}
