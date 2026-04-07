package com.example.feetbang

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.feetbang.ui.theme.FeetbangTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FeetbangTheme {
                FlashbangApp()
            }
        }
    }
}

@Composable
fun FlashbangApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("feetbang_prefs", Context.MODE_PRIVATE) }
    
    var showSettings by remember { mutableStateOf(false) }
    
    var useCustomImage by remember { 
        mutableStateOf(prefs.getBoolean("use_custom_image", false)) 
    }
    var customImageUri by remember { 
        mutableStateOf(prefs.getString("custom_image_uri", null)?.let { Uri.parse(it) }) 
    }

    if (showSettings) {
        SettingsScreen(
            useCustomImage = useCustomImage,
            customImageUri = customImageUri,
            onBack = { showSettings = false },
            onToggleCustom = { 
                useCustomImage = it
                prefs.edit().putBoolean("use_custom_image", it).apply()
            },
            onImageSelected = { uri ->
                customImageUri = uri
                prefs.edit().putString("custom_image_uri", uri?.toString()).apply()
            }
        )
    } else {
        FlashbangScreen(
            useCustomImage = useCustomImage,
            customImageUri = customImageUri,
            onOpenSettings = { showSettings = true }
        )
    }
}

@Composable
fun SettingsScreen(
    useCustomImage: Boolean,
    customImageUri: Uri?,
    onBack: () -> Unit,
    onToggleCustom: (Boolean) -> Unit,
    onImageSelected: (Uri?) -> Unit
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            onImageSelected(uri)
        }
    }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(title = { Text("Settings") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = !useCustomImage,
                        onClick = { onToggleCustom(false) }
                    )
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = !useCustomImage, onClick = { onToggleCustom(false) })
                Text("Use default after image", modifier = Modifier.padding(start = 8.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = useCustomImage,
                        onClick = { onToggleCustom(true) }
                    )
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = useCustomImage, onClick = { onToggleCustom(true) })
                Text("Use custom after image", modifier = Modifier.padding(start = 8.dp))
            }

            if (useCustomImage) {
                Button(
                    onClick = { launcher.launch(arrayOf("image/*")) },
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text(if (customImageUri == null) "Select Image" else "Change Image")
                }
                
                customImageUri?.let {
                    Text("Image selected: ${it.lastPathSegment}", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Back")
            }
        }
    }
}

@Composable
fun FlashbangScreen(
    useCustomImage: Boolean,
    customImageUri: Uri?,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var hasPermission by remember { mutableStateOf(Settings.System.canWrite(context)) }
    var isTriggered by remember { mutableStateOf(false) }
    
    val overlayAlpha = remember { Animatable(0f) }
    val brightness = remember { Animatable(0.5f) }

    // Load custom image bitmap if selected
    val customImageBitmap by produceState<ImageBitmap?>(initialValue = null, useCustomImage, customImageUri) {
        value = if (useCustomImage && customImageUri != null) {
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(customImageUri)?.use { inputStream ->
                        BitmapFactory.decodeStream(inputStream)?.asImageBitmap()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
        } else {
            null
        }
    }

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
        onDispose { mediaPlayer.release() }
    }

    // Permission re-check on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = Settings.System.canWrite(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

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
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    audioManager.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                        0
                    )
                    mediaPlayer.seekTo(0)
                    mediaPlayer.start()
                    scope.launch {
                        overlayAlpha.snapTo(1f)
                        brightness.snapTo(1f)
                        launch { overlayAlpha.animateTo(0f, tween(3000)) }
                        launch { brightness.animateTo(0.5f, tween(3000)) }
                    }
                } else if (!overlayAlpha.isRunning) {
                    // Reset back to initial state if the animation has finished
                    isTriggered = false
                }
            }
    ) {
        if (!isTriggered) {
            Image(
                painter = painterResource(id = R.drawable.initial_image),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // Settings Icon
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.2f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White
                )
            }
        } else {
            if (useCustomImage && customImageBitmap != null) {
                Image(
                    bitmap = customImageBitmap!!,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.after_image),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

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
