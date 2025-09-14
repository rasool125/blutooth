package com.example.videoplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.videoplayer.Presentation.AudioPlayer.Utils.Screen
import com.example.videoplayer.Presentation.AudioPlayer.playerScreen.PlayerScreen
import com.example.videoplayer.Presentation.AudioPlayer.videosScreen.VideosScreen
import com.example.videoplayer.Presentation.AudioPlayer.videosScreen.ViewViewModel
import com.example.videoplayer.Presentation.PlaylistScreen
import com.example.videoplayer.Presentation.VideoPlayer.MusicPlayerViewModel
import com.example.videoplayer.ui.theme.VideoPlayerTheme
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VideoPlayerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val navController = rememberNavController()

                    val mediaViewModel: ViewViewModel = koinViewModel()

                    val viewModel: MusicPlayerViewModel = koinViewModel()
                    NavHost(
                        modifier = Modifier.padding(innerPadding),
                        navController = navController,
                        startDestination = Screen.PlaylistScreen.route
                    ) {
                        composable(route = Screen.PlayerScreen.route) {
                            PlayerScreen(navController = navController , mediaViewModel)
                        }

                        composable(route = Screen.videosScreen.route) {
                            VideosScreen(navController = navController, mediaViewModel)
                        }
                        composable(route = Screen.PlaylistScreen.route) {
                            PlaylistScreen(navController = navController , viewModel)
                        }

                    }
                }
            }
        }
    }
}
