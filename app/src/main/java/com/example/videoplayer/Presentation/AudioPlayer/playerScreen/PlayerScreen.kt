package com.example.videoplayer.Presentation.AudioPlayer.playerScreen

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import com.example.videoplayer.Presentation.AudioPlayer.videosScreen.ViewViewModel
import com.example.videoplayer.R
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel


@Composable
fun PlayerScreen(navController: NavHostController, mediaViewModel: ViewViewModel) {
    val indexedValue = mediaViewModel.currentVideoIndex.value
    val videoList by mediaViewModel.videos.collectAsState(initial = emptyList())

    val videoUri: Uri? = videoList.getOrNull(indexedValue)?.uri

    val viewModel = koinViewModel<VideoPlayerViewModel>()
    val videoState by viewModel.videoState.collectAsState()

    // State for controlling overlay visibility
    var showControls by remember { mutableStateOf(true) }

    // Auto-hide timer
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(3000) // 3 seconds
            showControls = false
        }
    }

    // Reset timer when user interacts
    val resetControlTimer = {
        showControls = true
    }

    viewModel.setVideoPlaylist(videoList)

    if (videoState.isLandscape) {
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    } else {
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable {
                // Single tap toggles control visibility
                if (showControls) {
                    // If controls are showing, hide them immediately
                    showControls = false
                } else {
                    showControls = true
                }
            }
            .doubleTapGesture(
                onDoubleTapLeft = {
                    viewModel.seekBackward()
                    resetControlTimer()
                },
                onDoubleTapRight = {
                    viewModel.seekForward()
                    resetControlTimer()
                }
            )
            .dragGesture(
                onDragLeft = { delta ->
                    viewModel.updateBrightness(delta)
                    resetControlTimer()
                },
                onDragRight = { delta ->
                    viewModel.updateVolume(delta)
                    resetControlTimer()
                }
            )
    ) {
        // Video area
        if (videoUri != null) {
            VideoView(
                uri = videoUri,
                isPlaying = videoState.isPlaying,
                currentPosition = videoState.currentPosition,
                brightness = videoState.brightness,
                volume = videoState.volume,
                onPlayerReady = { player ->
                    viewModel.setPlayer(player)
                }
            )
        }

        // Control overlays with animation
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            ControlOverlays(
                currentPosition = videoState.currentPosition,
                name = videoState.currentVideoTitle,
                totalDuration = videoState.duration,
                isPlaying = videoState.isPlaying,
                onPlayPauseToggle = {
                    viewModel.togglePlayPause()
                    resetControlTimer()
                },
                onFullscreenToggle = {
                    viewModel.toggleOrientation()
                    resetControlTimer()
                },
                onSettingsOpen = {
                    resetControlTimer()
                    /* Implement settings menu */
                },
                onPlayNext = {
                    viewModel.playNextVideo()
                    resetControlTimer()
                },
                onPlayPre = {
                    viewModel.playPreviousVideo()
                    resetControlTimer()
                }
            )
        }
    }
}

// Modified VideoView - no changes needed
@Composable
fun VideoView(
    uri: Uri,
    isPlaying: Boolean,
    currentPosition: Long,
    brightness: Float,
    volume: Float,
    onPlayerReady: (ExoPlayer) -> Unit = {}
) {
    val context = LocalContext.current

    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context)
            .build()
            .apply {
                val mediaItem = MediaItem.fromUri(uri)
                setMediaItem(mediaItem)
                prepare()
                onPlayerReady(this)
            }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    LaunchedEffect(volume) {
        exoPlayer.volume = volume
    }

    LaunchedEffect(currentPosition) {
        val currentPlayerPosition = exoPlayer.currentPosition
        val positionDiff = kotlin.math.abs(currentPlayerPosition - currentPosition)
        if (positionDiff > 1000) {
            exoPlayer.seekTo(currentPosition)
        }
    }

    LaunchedEffect(brightness) {
        val window = (context as? Activity)?.window
        window?.attributes = window?.attributes?.apply {
            screenBrightness = brightness
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PlayerView(context).apply {
                    player = exoPlayer
                    useController = false
                }
            },
            update = { playerView ->
                if (playerView.player != exoPlayer) {
                    playerView.player = exoPlayer
                }
            }
        )
    }
}

// Modified ControlOverlays - no changes needed for functionality
@Composable
fun ControlOverlays(
    currentPosition: Long,
    totalDuration: Long,
    name: String,
    isPlaying: Boolean,
    onPlayPauseToggle: () -> Unit,
    onFullscreenToggle: () -> Unit,
    onSettingsOpen: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPre: () -> Unit
) {
    val currentTimeText = formatTime(currentPosition)
    val totalTimeText = formatTime(totalDuration)
    val context = LocalContext.current
    val activity = context as? Activity

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.6f),
                        Color.Black.copy(alpha = 0.6f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = 150f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onSettingsOpen,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Text(
                    modifier = Modifier.weight(1f),
                    text = name,
                    color = Color.White,
                    fontSize = 14.sp
                )

                IconButton(
                    onClick = {},
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = Color.White
                    )
                }

                IconButton(
                    onClick = {},
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentTimeText,
                    color = Color.White,
                    fontSize = 14.sp
                )

                ProgressBar(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 16.dp),
                    progress = if (totalDuration > 0) {
                        currentPosition.toFloat() / totalDuration.toFloat()
                    } else 0f,
                    color = Color.White
                )

                Text(
                    text = totalTimeText,
                    color = Color.White,
                    fontSize = 14.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onSettingsOpen,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.White
                    )
                }

                IconButton(
                    onClick = onPlayPre,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_next),
                        contentDescription = "Previous",
                        tint = Color.White
                    )
                }

                IconButton(
                    onClick = onPlayPauseToggle,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Person else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White
                    )
                }

                IconButton(
                    onClick = onPlayNext,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pre),
                        contentDescription = "Next",
                        tint = Color.White
                    )
                }

                IconButton(
                    onClick = {
                        activity?.let {
                            it.requestedOrientation = if (it.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            }
                        }
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Fullscreen",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

// Gesture handlers - modified to handle single tap properly
private fun Modifier.doubleTapGesture(
    onDoubleTapLeft: () -> Unit,
    onDoubleTapRight: () -> Unit
): Modifier = composed {
    pointerInput(Unit) {
        detectTapGestures(
            onDoubleTap = { offset ->
                if (offset.x < size.width / 2) {
                    onDoubleTapLeft()
                } else {
                    onDoubleTapRight()
                }
            }
        )
    }
}

private fun Modifier.dragGesture(
    onDragLeft: (Float) -> Unit,
    onDragRight: (Float) -> Unit
): Modifier = composed {
    pointerInput(Unit) {
        detectDragGestures { change, dragAmount ->
            val x = change.position.x
            val dragY = dragAmount.y
            if (x < size.width / 2) {
                onDragLeft(-dragY)
            } else {
                onDragRight(-dragY)
            }
        }
    }
}

@Composable
fun ProgressBar(
    modifier: Modifier = Modifier,
    progress: Float,
    color: Color
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(color.copy(alpha = 0.3f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(color)
        )
    }
}

private fun formatTime(timeMs: Long): String {
    val totalSeconds = (timeMs / 1000).toInt()
    val seconds = totalSeconds % 60
    val minutes = totalSeconds / 60
    return "%02d:%02d".format(minutes, seconds)
}
