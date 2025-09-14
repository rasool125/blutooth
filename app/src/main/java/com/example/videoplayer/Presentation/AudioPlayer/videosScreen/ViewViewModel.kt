package com.example.videoplayer.Presentation.AudioPlayer.videosScreen

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.Flow
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

import kotlinx.coroutines.flow.flow

data class Video(
    val id: Long,
    val uri: Uri,
    val name: String,
    val duration: Long,
    val size: Long
)



class ViewViewModel(context: Context) : ViewModel() {

    val videos: Flow<List<Video>> = getAllVideos(context)

    private val _currentVideoIndex = mutableStateOf(0)
    // Public read-only state
    val currentVideoIndex: State<Int> = _currentVideoIndex




    fun setCurrentVideoIndex(index: Int) {
        _currentVideoIndex.value = index
    }



    fun getAllVideos(context: Context): Flow<List<Video>> = flow {
        val videoList = mutableListOf<Video>()
        val contentResolver: ContentResolver = context.contentResolver

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val duration = cursor.getLong(durationColumn)
                val size = cursor.getLong(sizeColumn)
                val contentUri = Uri.withAppendedPath(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )

                videoList.add(Video(id, contentUri, name, duration, size))
            }
        }

        emit(videoList)
    }

}