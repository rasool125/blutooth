package com.example.videoplayer.Presentation.AudioPlayer.playerScreen

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.videoplayer.Presentation.AudioPlayer.Utils.VideoItem
import com.example.videoplayer.Presentation.AudioPlayer.videosScreen.Video
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale


class VideoPlayerViewModel : ViewModel() {

    private var exoPlayer: ExoPlayer? = null

    // Video playlist management
    private var currentVideoIndex = 0
    private var videoPlaylist: List<Video> = emptyList()

    private val _videoState = MutableStateFlow(
        VideoState(
            isPlaying = false,
            currentPosition = 0L,
            duration = 0L,
            brightness = 0.5f,
            volume = 1.0f,
            isLandscape = false,
            currentVideoIndex = 0,
            hasNextVideo = false,
            hasPreviousVideo = false,
            currentVideoTitle = ""
        )
    )
    val videoState: StateFlow<VideoState> = _videoState.asStateFlow()

    // Position update job
    private var positionUpdateJob: Job? = null

    fun setPlayer(player: ExoPlayer) {
        exoPlayer = player
        startPositionUpdates()

        // Update duration when media is ready
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _videoState.value = _videoState.value.copy(
                        duration = player.duration
                    )
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateVideoInfo()
            }
        })
    }

    // Set video playlist
    fun setVideoPlaylist(videos: List<Video>, startIndex: Int = 0) {
        videoPlaylist = videos

        updateVideoInfo()
    }

    private fun updateVideoInfo() {
        _videoState.value = _videoState.value.copy(
            currentVideoIndex = currentVideoIndex,
            hasNextVideo = currentVideoIndex < videoPlaylist.size - 1,
            hasPreviousVideo = currentVideoIndex > 0,
            currentVideoTitle = if (videoPlaylist.isNotEmpty()) {
                // Extract filename from path for title
                videoPlaylist[currentVideoIndex].name
                    .substringBeforeLast(".")
            } else ""
        )
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = viewModelScope.launch {
            while (true) {
                exoPlayer?.let { player ->
                    _videoState.value = _videoState.value.copy(
                        currentPosition = player.currentPosition,
                        isPlaying = player.isPlaying
                    )
                }
                delay(100) // Update every 100ms
            }
        }
    }

    fun togglePlayPause() {
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
    }

    fun seekForward() {
        exoPlayer?.let { player ->
            val newPosition = (player.currentPosition + 10000).coerceAtMost(player.duration)
            player.seekTo(newPosition)
        }
    }

    fun seekBackward() {
        exoPlayer?.let { player ->
            val newPosition = (player.currentPosition - 10000).coerceAtLeast(0)
            player.seekTo(newPosition)
        }
    }

    fun updateBrightness(delta: Float) {
        val newBrightness = (_videoState.value.brightness + delta * 0.01f).coerceIn(0f, 1f)
        _videoState.value = _videoState.value.copy(brightness = newBrightness)
    }

    fun updateVolume(delta: Float) {
        val newVolume = (_videoState.value.volume + delta * 0.01f).coerceIn(0f, 1f)
        _videoState.value = _videoState.value.copy(volume = newVolume)
        exoPlayer?.volume = newVolume
    }


    fun toggleOrientation() {
        val newOrientation = !_videoState.value.isLandscape
        _videoState.value = _videoState.value.copy(isLandscape = newOrientation)
    }

    fun setOrientation(isLandscape: Boolean) {
        _videoState.value = _videoState.value.copy(isLandscape = isLandscape)
    }

    fun playNextVideo() {

        if (currentVideoIndex < videoPlaylist.size - 1) {
            Log.d("tag__", "playVideoAtIndex: next")
            currentVideoIndex++
            playVideoAtIndex(currentVideoIndex)
        }
    }

    fun playPreviousVideo() {

        if (currentVideoIndex > 0) {

            Log.d("tag__", "playVideoAtIndex: Pre")
            currentVideoIndex--
            playVideoAtIndex(currentVideoIndex)
        }
    }

    fun playVideoAtIndex(index: Int) {
        if (index in 0 until videoPlaylist.size) {

            Log.d("tag__", "playVideoAtIndex: call")
            currentVideoIndex = index
            val videoUri = Uri.parse(videoPlaylist[index].uri.toString())
            val mediaItem = MediaItem.fromUri(videoUri)

            exoPlayer?.let { player ->
                player.setMediaItem(mediaItem)
                player.prepare()
                player.play()
            }

            updateVideoInfo()
        }
    }

    // Seek to specific position
    fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
    }



    private fun formatTime(timeMs: Long): String {
        val totalSeconds = timeMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    // Auto-play next video when current ends
    fun handleVideoEnd() {
        if (currentVideoIndex < videoPlaylist.size - 1) {
            playNextVideo()
        }
    }

    // Set playback speed
    fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.setPlaybackSpeed(speed)
    }



    override fun onCleared() {
        super.onCleared()
        positionUpdateJob?.cancel()
        exoPlayer?.release()
    }
}

// Updated VideoState data class
data class VideoState(
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val brightness: Float = 0.5f,
    val volume: Float = 1.0f,
    val isLandscape: Boolean = false,
    val currentVideoIndex: Int = 0,
    val hasNextVideo: Boolean = false,
    val hasPreviousVideo: Boolean = false,
    val currentVideoTitle: String = ""
)






