package com.example.videoplayer.Data.Repository// data/repository/MusicRepository.kt
import com.example.videoplayer.Domain.Models.AudioScanner
import com.example.videoplayer.Domain.Models.Track
import com.example.videoplayer.Domain.Repository.IMusicRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow



class MusicRepository (
    private val audioScanner: AudioScanner
) : IMusicRepository {
    
    private var cachedTracks: List<Track>? = null
    
    override suspend fun getAllTracks(): Flow<List<Track>> = flow {
        if (cachedTracks == null) {
            cachedTracks = audioScanner.scanAudioFiles().map { audioTrack ->
                Track(
                    id = audioTrack.id,
                    title = audioTrack.title,
                    artist = audioTrack.artist,
                    album = audioTrack.album,
                    duration = audioTrack.duration,
                    uri = audioTrack.uri,
                    albumArtUri = audioTrack.albumArtUri
                )
            }
        }
        emit(cachedTracks ?: emptyList())
    }
    
    override suspend fun getTrackById(id: Long): Track? {
        return cachedTracks?.find { it.id == id }
    }
}
