package com.example.videoplayer

import android.content.Context
import android.media.session.MediaSession
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.videoplayer.Data.Repository.MusicRepository
import com.example.videoplayer.Domain.Models.AudioScanner
import com.example.videoplayer.Domain.Repository.IMusicRepository
import com.example.videoplayer.Domain.UseCase.MusicPlayerUseCase
import com.example.videoplayer.Presentation.AudioPlayer.playerScreen.VideoPlayerViewModel

import com.example.videoplayer.Presentation.AudioPlayer.videosScreen.ViewViewModel
import com.example.videoplayer.Presentation.VideoPlayer.MusicPlayerViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

object AppModule {
    fun getModule(): Module = module {


        factory<Player> {
            ExoPlayer.Builder(androidContext())
                .build()
        }

        viewModel { VideoPlayerViewModel() }

        viewModel { ViewViewModel(androidContext()) }


        // ============= DATA LAYER =============

        // Audio Scanner
        single { AudioScanner(context = get()) }

        // Repository Implementation
        single<IMusicRepository> {
            MusicRepository(audioScanner = get())
        }

        // Content Resolver
        single {
            get<Context>().contentResolver
        }

        // ============= DOMAIN LAYER =============

        // Use Cases
        single {
            MusicPlayerUseCase(repository = get())
        }

        // ============= PLAYER LAYER =============

        // ExoPlayer - Single instance throughout the app
        single<ExoPlayer> {
            ExoPlayer.Builder(get<Context>()).build()
        }

        // ============= PRESENTATION LAYER =============

        // Main Music Player ViewModel
        viewModel {
            MusicPlayerViewModel(
                musicPlayerUseCase = get(),
                exoPlayer = get(),
            )
        }


        factory<MediaSession?> { null }


    }

}
