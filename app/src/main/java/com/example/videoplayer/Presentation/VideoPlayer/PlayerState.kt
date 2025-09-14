package com.example.videoplayer.Presentation.VideoPlayer// presentation/viewmodel/MusicPlayerViewModel.kt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.videoplayer.Domain.Models.Track
import com.example.videoplayer.Domain.UseCase.MusicPlayerUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerState(
    val isPlaying: Boolean = false,
    val currentTrack: Track? = null,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val playlist: List<Track> = emptyList(),
    val currentIndex: Int = 0
)

class MusicPlayerViewModel(
    private val musicPlayerUseCase: MusicPlayerUseCase,
    private val exoPlayer: ExoPlayer
) : ViewModel() {
    
    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()
    
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playerState.value = _playerState.value.copy(isPlaying = isPlaying)
        }
        
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateCurrentTrack()
        }
        
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                _playerState.value = _playerState.value.copy(
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                )
            }
        }
    }
    
    init {
        exoPlayer.addListener(playerListener)
        loadTracks()
        startPositionUpdates()
    }
    
    private fun loadTracks() {
        viewModelScope.launch {
            musicPlayerUseCase.getAllTracks().collect { tracks ->
                _playerState.value = _playerState.value.copy(playlist = tracks)
                if (tracks.isNotEmpty()) {
                    setupPlaylist(tracks)
                }
            }
        }
    }
    
    private fun setupPlaylist(tracks: List<Track>) {
        val mediaItems = tracks.map { track ->
            MediaItem.Builder()
                .setUri(track.uri)
                .setMediaId(track.id.toString())
                .build()
        }
        exoPlayer.setMediaItems(mediaItems)
        exoPlayer.prepare()
        updateCurrentTrack()
    }
    
    private fun updateCurrentTrack() {
        val currentIndex = exoPlayer.currentMediaItemIndex
        val playlist = _playerState.value.playlist
        if (currentIndex >= 0 && currentIndex < playlist.size) {
            _playerState.value = _playerState.value.copy(
                currentTrack = playlist[currentIndex],
                currentIndex = currentIndex
            )
        }
    }
    
    private fun startPositionUpdates() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                if (exoPlayer.isPlaying) {
                    _playerState.value = _playerState.value.copy(
                        currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                    )
                }
            }
        }
    }
    
    fun playPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }
    
    fun seekTo(position: Long) {
        exoPlayer.seekTo(position)
    }
    
    fun skipToNext() {
        if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNext()
        }
    }
    
    fun skipToPrevious() {
        if (exoPlayer.hasPreviousMediaItem()) {
            exoPlayer.seekToPrevious()
        }
    }
    
    fun playTrack(trackIndex: Int) {
        exoPlayer.seekToDefaultPosition(trackIndex)
        exoPlayer.play()
    }
    
    fun stop() {
        exoPlayer.stop()
    }
    
    override fun onCleared() {
        super.onCleared()
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
    }
}
