package com.example.videoplayer.Presentation.AudioPlayer.videosScreen

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.videoplayer.Presentation.AudioPlayer.Utils.Screen


@Composable
fun VideosScreen(navController: NavHostController, mediaViewModel: ViewViewModel) {


    val videoList by mediaViewModel.videos.collectAsState(initial = emptyList())


    LazyColumn {
        itemsIndexed(videoList) { index, video ->

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .clickable {
                        // 👉 You now have both the video and its position
                        mediaViewModel.setCurrentVideoIndex(index)
                        navController.navigate(Screen.PlayerScreen.route)
                    }
            ) {
                Text(text = "${index + 1}. ${video.name}")
            }
        }
    }


}