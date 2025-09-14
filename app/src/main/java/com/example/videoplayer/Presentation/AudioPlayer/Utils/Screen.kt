package com.example.videoplayer.Presentation.AudioPlayer.Utils

    sealed class Screen(val route: String) {
        data object videosScreen: Screen("videos_screen")
        data object PlayerScreen: Screen("player_screen")
        data object PlaylistScreen: Screen("PlaylistScreen")
    }