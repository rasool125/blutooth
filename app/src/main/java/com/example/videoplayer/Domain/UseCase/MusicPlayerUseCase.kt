package com.example.videoplayer.Domain.UseCase// domain/usecase/MusicPlayerUseCase.kt
import com.example.videoplayer.Domain.Models.Track
import com.example.videoplayer.Domain.Repository.IMusicRepository
import kotlinx.coroutines.flow.Flow



class MusicPlayerUseCase (
    private val repository: IMusicRepository
) {
    suspend fun getAllTracks(): Flow<List<Track>> = repository.getAllTracks()
    
    suspend fun getTrackById(id: Long): Track? = repository.getTrackById(id)
}
