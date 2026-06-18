package com.example.ui.components

import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.example.model.ExerciseMedia
import com.example.model.MediaType

@Composable
fun LoopingMediaView(
    media: ExerciseMedia,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Set up Coil image loader configured with GIF support
    val gifImageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        if (media.mediaUri.isBlank()) {
            // Render nothing or let calling screen show fallback visual orbits
        } else {
            when (media.mediaType) {
                MediaType.IMAGE -> {
                    AsyncImage(
                        model = media.mediaUri,
                        contentDescription = "Exercise visual demonstration",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                MediaType.GIF -> {
                    AsyncImage(
                        model = media.mediaUri,
                        imageLoader = gifImageLoader,
                        contentDescription = "Looping movement guide",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                MediaType.VIDEO -> {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                try {
                                    setVideoURI(Uri.parse(media.mediaUri))
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                setOnPreparedListener { mediaPlayer ->
                                    mediaPlayer.isLooping = true
                                    mediaPlayer.setVolume(0f, 0f) // muted as requested
                                    mediaPlayer.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                                    start()
                                }
                                setOnErrorListener { _, _, _ ->
                                    true // Suppress error popups on screen
                                }
                            }
                        },
                        update = { view ->
                            if (view.tag != media.mediaUri) {
                                try {
                                    view.setVideoURI(Uri.parse(media.mediaUri))
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                view.tag = media.mediaUri
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
