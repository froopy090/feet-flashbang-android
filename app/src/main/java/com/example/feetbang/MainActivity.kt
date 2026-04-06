package com.example.feetbang

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.example.feetbang.ui.theme.FeetbangTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FeetbangTheme {
                FlashbangScreen()
            }
        }
    }
}

@Composable
fun FlashbangScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var hasPermission by remember { mutableStateOf(Settings.System.canWrite(context)) }
    var isTriggered by remember { mutableStateOf(false) }
    
    val overlayAlpha = remember { Animatable(0f) }
    val brightness = remember { Animatable(0.5f) }

    // Media Player setup
    val mediaPlayer = remember {
        MediaPlayer.create(context, R.raw.flashbang_sound).apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .build()
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer.release()
        }
    }

    // Permission check
    LaunchedEffect(Unit) {
        if (!hasPermission) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    // Update system brightness when the state changes
    LaunchedEffect(brightness.value) {
        if (hasPermission) {
            setSystemBrightness(context, brightness.value)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable {
                if (!isTriggered) {
                    isTriggered = true
                    
                    // Maximize volume
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    audioManager.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                        0
                    )
                    
                    mediaPlayer.start()

                    scope.launch {
                        // Flash
                        overlayAlpha.snapTo(1f)
                        brightness.snapTo(1f)
                        
                        // Fade out
                        launch {
                            overlayAlpha.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(durationMillis = 3000)
                            )
                        }
                        launch {
                            brightness.animateTo(
                                targetValue = 0.5f,
                                animationSpec = tween(durationMillis = 3000)
                            )
                        }
                    }
                }
            }
    ) {
        // Background Image
        Image(
            painter = painterResource(id = if (isTriggered) R.drawable.after_image else R.drawable.initial_image),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // White Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = overlayAlpha.value))
        )
    }
}

private fun setSystemBrightness(context: Context, brightness: Float) {
    try {
        val brightnessInt = (brightness * 255).toInt().coerceIn(0, 255)
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            brightnessInt
        )
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
