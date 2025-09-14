package com.example.videoplayer.Presentation.AudioPlayer// presentation/ui/player/PlayerScreen.kt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.example.videoplayer.Presentation.VideoPlayer.MusicPlayerViewModel
import com.example.videoplayer.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: MusicPlayerViewModel,
    onNavigateToPlaylist: () -> Unit
) {
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateToPlaylist) {
                    Icon(
                        Icons.Default.List,
                        contentDescription = "Playlist"
                    )
                }
                
                Text(
                    text = "Now Playing",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(onClick = { /* More options */ }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More"
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Album Art
            Card(
                modifier = Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(16.dp)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Image(
                    painter = rememberAsyncImagePainter(
                        model = playerState.currentTrack?.albumArtUri,
                        placeholder = painterResource(R.drawable.ic_pre),
                        error = painterResource(R.drawable.ic_next)
                    ),
                    contentDescription = "Album Art",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Track Info
            playerState.currentTrack?.let { track ->
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Progress Bar
            PlayerProgressBar(
                currentPosition = playerState.currentPosition,
                duration = playerState.duration,
                onSeek = viewModel::seekTo
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Control Buttons
            PlayerControls(
                isPlaying = playerState.isPlaying,
                onPlayPause = viewModel::playPause,
                onSkipNext = viewModel::skipToNext,
                onSkipPrevious = viewModel::skipToPrevious
            )
        }
    }
}

@Composable
fun PlayerProgressBar(
    currentPosition: Long,
    duration: Long,
    onSeek: (Long) -> Unit
) {
    Column {
        var sliderPosition by remember { mutableFloatStateOf(0f) }
        var isUserInteracting by remember { mutableStateOf(false) }
        
        LaunchedEffect(currentPosition) {
            if (!isUserInteracting) {
                sliderPosition = if (duration > 0) {
                    currentPosition.toFloat() / duration.toFloat()
                } else 0f
            }
        }
        
        Slider(
            value = sliderPosition,
            onValueChange = { 
                sliderPosition = it
                isUserInteracting = true
            },
            onValueChangeFinished = {
                onSeek((sliderPosition * duration).toLong())
                isUserInteracting = false
            },
            modifier = Modifier.fillMaxWidth()
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(currentPosition),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                text = formatTime(duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun PlayerControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onSkipPrevious,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                Icons.Default.DateRange,
                contentDescription = "Previous",
                modifier = Modifier.size(32.dp)
            )
        }
        
        FloatingActionButton(
            onClick = onPlayPause,
            modifier = Modifier.size(64.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                if (isPlaying) Icons.Default.Person else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(32.dp)
            )
        }
        
        IconButton(
            onClick = onSkipNext,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = "Next",
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
