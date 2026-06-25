package com.example

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private lateinit var audioManager: AudioManager

    // State variables
    private var isAudioPlaying by mutableStateOf(false)
    private var isUsingCustomAsset by mutableStateOf(false)
    private var isVideoPlaying by mutableStateOf(true)
    private var isUsingCustomVideoAsset by mutableStateOf(false)
    private var currentVolume by mutableStateOf(0)
    private var maxVolume by mutableStateOf(0)
    private var isEnforcerActive by mutableStateOf(true)
    private var runningTimeSeconds by mutableStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable Edge to Edge full screen layout
        enableEdgeToEdge()
        
        // Get Audio Manager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        // Make application full-screen (Immersive sticky & hide navigation)
        setImmersiveFullScreen()

        // Keep Screen On
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Force Max Volume & Screen Brightness Initially
        enforceMaxSettings()

        // Start playing ses.mp3 (with auto-synthesized fallback)
        startMediaPlayer()

        // Enforcer and status background loops
        startBackgroundLoops()

        setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0D0E12) // Slate black cinematic TV background
                ) {
                    TVDashboardScreen(
                        isAudioPlaying = isAudioPlaying,
                        isUsingCustomAsset = isUsingCustomAsset,
                        isVideoPlaying = isVideoPlaying,
                        isUsingCustomVideoAsset = isUsingCustomVideoAsset,
                        currentVolume = currentVolume,
                        maxVolume = maxVolume,
                        isEnforcerActive = isEnforcerActive,
                        runningTimeSeconds = runningTimeSeconds,
                        onTogglePlayback = { togglePlayback() },
                        onToggleVideoPlayback = { isVideoPlaying = !isVideoPlaying },
                        onToggleEnforcer = { isEnforcerActive = !isEnforcerActive },
                        onForceMaxNow = { enforceMaxSettings() },
                        onVideoStatusChange = { isCustom, isPlaying ->
                            isUsingCustomVideoAsset = isCustom
                        },
                        onExitApp = { finishAndRemoveTask() }
                    )
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setImmersiveFullScreen()
        }
    }

    private fun setImmersiveFullScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }
    }

    private fun enforceMaxSettings() {
        try {
            // Set screen brightness to absolute max (1.0f)
            val lp = window.attributes
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            window.attributes = lp

            // Set system STREAM_MUSIC volume to max
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, AudioManager.FLAG_SHOW_UI)
            
            // Sync status
            currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startBackgroundLoops() {
        // Loop 1: Core System Enforcer and status updater
        lifecycleScope.launch {
            while (isActive) {
                try {
                    // Update volume details
                    currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

                    if (isEnforcerActive) {
                        enforceMaxSettings()
                    }

                    // Ensure player is running if it should be
                    if (isAudioPlaying && mediaPlayer?.isPlaying == false) {
                        mediaPlayer?.start()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(1000)
            }
        }

        // Loop 2: Running Time counter
        lifecycleScope.launch {
            while (isActive) {
                delay(1000)
                runningTimeSeconds++
            }
        }
    }

    private fun getAudioSourceFile(): File? {
        // First, check if custom ses.mp3 asset is present in the build assets
        try {
            assets.open("ses.mp3").use {
                return null // Return null to play directly from APK's Assets
            }
        } catch (e: Exception) {
            // Asset not found, fall back to programmatic generation in internal cache
        }

        // Generate a highly audible, loopable sine wave WAV file in cache
        val file = File(cacheDir, "synthetic_ses.mp3")
        if (file.exists() && file.length() > 0) {
            return file
        }

        try {
            val sampleRate = 11025
            val durationSeconds = 3
            val frequency = 880.0 // Pitch frequency: Clear double A alarm pitch
            val numSamples = durationSeconds * sampleRate
            val dataSize = numSamples * 2
            val fileSize = 36 + dataSize
            
            val byteBuffer = java.nio.ByteBuffer.allocate(44 + dataSize)
            byteBuffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)

            // RIFF header
            byteBuffer.put("RIFF".toByteArray())
            byteBuffer.putInt(fileSize)
            byteBuffer.put("WAVE".toByteArray())

            // Format Chunk
            byteBuffer.put("fmt ".toByteArray())
            byteBuffer.putInt(16)
            byteBuffer.putShort(1.toShort()) // PCM format
            byteBuffer.putShort(1.toShort()) // Mono
            byteBuffer.putInt(sampleRate)
            byteBuffer.putInt(sampleRate * 2) // ByteRate
            byteBuffer.putShort(2.toShort()) // BlockAlign
            byteBuffer.putShort(16.toShort()) // BitsPerSample

            // Data Chunk
            byteBuffer.put("data".toByteArray())
            byteBuffer.putInt(dataSize)

            // Audio generation (Siren frequency oscillation)
            for (i in 0 until numSamples) {
                // Dual sweep frequency for an immersive pulsing alarm/beep
                val oscFrequency = frequency + 200.0 * Math.sin(2.0 * Math.PI * (i.toDouble() / sampleRate) * 1.5)
                val angle = 2.0 * Math.PI * oscFrequency * i / sampleRate
                val sampleValue = (Math.sin(angle) * Short.MAX_VALUE * 0.9).toInt().toShort()
                byteBuffer.putShort(sampleValue)
            }

            FileOutputStream(file).use { fos ->
                fos.write(byteBuffer.array())
            }
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun startMediaPlayer() {
        try {
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer()
            } else {
                mediaPlayer?.reset()
            }

            val audioSource = getAudioSourceFile()
            if (audioSource == null) {
                val fd = assets.openFd("ses.mp3")
                mediaPlayer?.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                isUsingCustomAsset = true
            } else {
                mediaPlayer?.setDataSource(audioSource.absolutePath)
                isUsingCustomAsset = false
            }

            // Set modern Audio Attributes
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            mediaPlayer?.setAudioAttributes(attributes)

            mediaPlayer?.isLooping = true
            mediaPlayer?.prepare()
            mediaPlayer?.start()
            isAudioPlaying = true
        } catch (e: Exception) {
            e.printStackTrace()
            isAudioPlaying = false
        }
    }

    private fun togglePlayback() {
        try {
            if (isAudioPlaying) {
                mediaPlayer?.pause()
                isAudioPlaying = false
            } else {
                if (mediaPlayer == null) {
                    startMediaPlayer()
                } else {
                    mediaPlayer?.start()
                    isAudioPlaying = true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

// FORMAT HELPER
fun formatElapsedTime(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return String.format("%02d:%02d:%02d", hrs, mins, secs)
}

// CUSTOM CANVAS DRAWABLES FOR EXTENDED ICONS COMPATIBILITY

@Composable
fun CustomTvIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val rectHeight = size.height * 0.75f
        // Screen
        drawRoundRect(
            color = color,
            topLeft = Offset(0f, 0f),
            size = Size(size.width, rectHeight),
            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
            style = Stroke(width = 3.dp.toPx())
        )
        // Stand neck
        drawLine(
            color = color,
            start = Offset(size.width / 2, rectHeight),
            end = Offset(size.width / 2, size.height * 0.9f),
            strokeWidth = 4.dp.toPx()
        )
        // Stand base
        drawLine(
            color = color,
            start = Offset(size.width * 0.3f, size.height * 0.9f),
            end = Offset(size.width * 0.7f, size.height * 0.9f),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun SpeakerIcon(color: Color, isMuted: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.15f, size.height * 0.35f)
            lineTo(size.width * 0.4f, size.height * 0.35f)
            lineTo(size.width * 0.7f, size.height * 0.15f)
            lineTo(size.width * 0.7f, size.height * 0.85f)
            lineTo(size.width * 0.4f, size.height * 0.65f)
            lineTo(size.width * 0.15f, size.height * 0.65f)
            close()
        }
        drawPath(path, color = color)

        if (!isMuted) {
            // Volume wave lines
            drawArc(
                color = color,
                startAngle = -45f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(size.width * 0.45f, size.height * 0.2f),
                size = Size(size.width * 0.5f, size.height * 0.6f),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        } else {
            // Draw an X to show muted
            drawLine(
                color = color,
                start = Offset(size.width * 0.8f, size.height * 0.35f),
                end = Offset(size.width * 0.95f, size.height * 0.65f),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(size.width * 0.95f, size.height * 0.35f),
                end = Offset(size.width * 0.8f, size.height * 0.65f),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun BrightnessIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        
        // Draw sun core
        drawCircle(
            color = color,
            radius = size.width * 0.22f,
            center = Offset(centerX, centerY)
        )
        
        // Draw 8 rays
        val numRays = 8
        val innerR = size.width * 0.3f
        val outerR = size.width * 0.45f
        for (i in 0 until numRays) {
            val angle = i * (2.0 * Math.PI / numRays)
            val startX = (centerX + Math.cos(angle) * innerR).toFloat()
            val startY = (centerY + Math.sin(angle) * innerR).toFloat()
            val endX = (centerX + Math.cos(angle) * outerR).toFloat()
            val endY = (centerY + Math.sin(angle) * outerR).toFloat()
            
            drawLine(
                color = color,
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun VideoIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val rectWidth = size.width * 0.65f
        val rectHeight = size.height * 0.6f
        val topY = (size.height - rectHeight) / 2
        
        // Main camera body
        drawRoundRect(
            color = color,
            topLeft = Offset(0f, topY),
            size = Size(rectWidth, rectHeight),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            style = Stroke(width = 3.dp.toPx())
        )
        
        // Camera lens triangle
        val path = Path().apply {
            moveTo(rectWidth, size.height * 0.4f)
            lineTo(size.width, size.height * 0.25f)
            lineTo(size.width, size.height * 0.75f)
            lineTo(rectWidth, size.height * 0.6f)
            close()
        }
        drawPath(path, color = color)
    }
}

@Composable
fun SimulatedVideoFeed(isPlaying: Boolean, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    
    // Animate scan line angle
    val sweepAngle by if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(4000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "sweep"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    // Animate target pulse
    val pulseAlpha by if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse"
        )
    } else {
        remember { mutableStateOf(0.7f) }
    }

    Box(
        modifier = modifier
            .background(Color(0xFF07090E)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = Math.min(size.width, size.height) * 0.45f

            // Draw radial grid lines
            drawCircle(
                color = Color(0xFF00E676).copy(alpha = 0.15f),
                radius = maxRadius,
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = Color(0xFF00E676).copy(alpha = 0.12f),
                radius = maxRadius * 0.66f,
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = Color(0xFF00E676).copy(alpha = 0.08f),
                radius = maxRadius * 0.33f,
                style = Stroke(width = 1.dp.toPx())
            )

            // Draw crosshairs
            drawLine(
                color = Color(0xFF00E676).copy(alpha = 0.15f),
                start = Offset(center.x - maxRadius, center.y),
                end = Offset(center.x + maxRadius, center.y),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color(0xFF00E676).copy(alpha = 0.15f),
                start = Offset(center.x, center.y - maxRadius),
                end = Offset(center.x, center.y + maxRadius),
                strokeWidth = 1.dp.toPx()
            )

            if (isPlaying) {
                // Draw sweeping scan line
                val angleRad = Math.toRadians(sweepAngle.toDouble())
                val endX = (center.x + Math.cos(angleRad) * maxRadius).toFloat()
                val endY = (center.y + Math.sin(angleRad) * maxRadius).toFloat()
                
                drawLine(
                    color = Color(0xFF00E676).copy(alpha = 0.6f),
                    start = center,
                    end = Offset(endX, endY),
                    strokeWidth = 2.5.dp.toPx()
                )

                // Simulated telemetry targets (pulsing dots)
                drawCircle(
                    color = Color(0xFFFF1744).copy(alpha = pulseAlpha),
                    radius = 6.dp.toPx(),
                    center = Offset(center.x + maxRadius * 0.4f, center.y - maxRadius * 0.3f)
                )
                
                drawCircle(
                    color = Color(0xFF00E676).copy(alpha = pulseAlpha * 0.8f),
                    radius = 4.dp.toPx(),
                    center = Offset(center.x - maxRadius * 0.5f, center.y + maxRadius * 0.2f)
                )
            }
        }

        // Overlay status text on top
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "CANLI TELEMETRİ RADARI",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E676)
                )
                Text(
                    text = "SİNYAL: GÜÇLÜ",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF00E676).copy(alpha = 0.8f)
                )
            }

            Text(
                text = "assets/video.mp4 aranıyor...\n[Dahili Radar Feed Aktif]",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.6f),
                fontFamily = FontFamily.Monospace,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
fun VideoPlayer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    onStatusChange: (Boolean, Boolean) -> Unit
) {
    val context = LocalContext.current
    
    // Copy video.mp4 from assets to internal cache for playback
    val videoFile = remember {
        val file = File(context.cacheDir, "loop_video.mp4")
        try {
            context.assets.open("video.mp4").use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    if (videoFile != null && videoFile.exists()) {
        DisposableEffect(isPlaying) {
            onStatusChange(true, isPlaying)
            onDispose {}
        }

        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoPath(videoFile.absolutePath)
                    setOnPreparedListener { mp ->
                        mp.isLooping = true
                        mp.setVolume(0f, 0f) // Keep video silent so that ses.mp3 audio is clear and unmixed
                        if (isPlaying) {
                            start()
                        }
                    }
                    setOnErrorListener { _, _, _ ->
                        true // Suppress errors
                    }
                }
            },
            update = { videoView ->
                if (isPlaying) {
                    if (!videoView.isPlaying) {
                        videoView.start()
                    }
                } else {
                    if (videoView.isPlaying) {
                        videoView.pause()
                    }
                }
            },
            modifier = modifier
        )
    } else {
        DisposableEffect(isPlaying) {
            onStatusChange(false, isPlaying)
            onDispose {}
        }

        SimulatedVideoFeed(
            isPlaying = isPlaying,
            modifier = modifier
        )
    }
}

@Composable
fun TVDashboardScreen(
    isAudioPlaying: Boolean,
    isUsingCustomAsset: Boolean,
    isVideoPlaying: Boolean,
    isUsingCustomVideoAsset: Boolean,
    currentVolume: Int,
    maxVolume: Int,
    isEnforcerActive: Boolean,
    runningTimeSeconds: Long,
    onTogglePlayback: () -> Unit,
    onToggleVideoPlayback: () -> Unit,
    onToggleEnforcer: () -> Unit,
    onForceMaxNow: () -> Unit,
    onVideoStatusChange: (Boolean, Boolean) -> Unit,
    onExitApp: () -> Unit
) {
    var currentTimeString by remember { mutableStateOf("") }

    // Real-time Clock loop for the TV Screen
    LaunchedEffect(Unit) {
        while (isActive) {
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            currentTimeString = sdf.format(Date())
            delay(1000)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // COLUMN 1: Control Panel & Status
        Column(
            modifier = Modifier
                .weight(1.0f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFFE53935), Color(0xFFB71C1C))
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    CustomTvIcon(
                        color = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column {
                    Text(
                        text = "TV MAX BOOSTER",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "TV & Box Kontrol Arabirimi",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Card (Supports remote focus / click styling)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF151821)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(1.dp, Color(0xFF2C3246), RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = "Donanım Denetimi",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Control 1: Play / Pause Loop
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SpeakerIcon(
                                color = if (isAudioPlaying) Color(0xFF00E676) else Color.LightGray,
                                isMuted = !isAudioPlaying,
                                modifier = Modifier.size(20.dp)
                            )
                            Text("Döngü Sesi", color = Color.White, fontSize = 14.sp)
                        }

                        Button(
                            onClick = onTogglePlayback,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isAudioPlaying) Color(0xFFD32F2F) else Color(0xFF388E3C)
                            ),
                            modifier = Modifier
                                .testTag("play_pause_button")
                                .focusable()
                        ) {
                            Text(
                                text = if (isAudioPlaying) "SESİ DURDUR" else "SESİ BAŞLAT",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 11.sp
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFF232838), thickness = 1.dp)

                    // Control 2: Continuous volume/brightness enforcer switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = if (isEnforcerActive) Color(0xFFFFC107) else Color.LightGray,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text("Sürekli Zorlayıcı", color = Color.White, fontSize = 14.sp)
                                Text("Limitleri sürekli max tut", color = Color.Gray, fontSize = 10.sp)
                            }
                        }

                        Switch(
                            checked = isEnforcerActive,
                            onCheckedChange = { onToggleEnforcer() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFFFC107),
                                checkedTrackColor = Color(0xFFFFC107).copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.testTag("enforcer_toggle")
                        )
                    }

                    HorizontalDivider(color = Color(0xFF232838), thickness = 1.dp)

                    // Manual Override Button
                    Button(
                        onClick = onForceMaxNow,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2538)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("force_max_button")
                            .focusable()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("MAX AYARLARI ZORLA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Footer metadata
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF10121A)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF1C202C), RoundedCornerShape(8.dp))
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Ses Kaynağı:", fontSize = 10.sp, color = Color.Gray)
                        Text(
                            text = if (isUsingCustomAsset) "Özel ses.mp3" else "Dahili Siren",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isUsingCustomAsset) Color(0xFF00E676) else Color(0xFFFF9100)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Süreç:", fontSize = 10.sp, color = Color.Gray)
                        Text(
                            text = formatElapsedTime(runningTimeSeconds),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // COLUMN 2: LIVE VIDEO FEED (YAN TARAFDA)
        Column(
            modifier = Modifier
                .weight(1.4f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF11141D)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.dp, Color(0xFF252B3E), RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            VideoIcon(
                                color = Color(0xFF00E676),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "MONİTÖR 01: VİDEO YAYINI",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Button(
                            onClick = onToggleVideoPlayback,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isVideoPlaying) Color(0xFF2C3246) else Color(0xFF388E3C)
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(
                                text = if (isVideoPlaying) "YAYINI DURDUR" else "YAYINI OYNAT",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // Video content display box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF1E2333), RoundedCornerShape(12.dp))
                    ) {
                        VideoPlayer(
                            isPlaying = isVideoPlaying,
                            onStatusChange = onVideoStatusChange,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Top-left watermark overlay
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.65f)),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .padding(8.dp)
                                .align(Alignment.TopStart)
                        ) {
                            Text(
                                text = if (isUsingCustomVideoAsset) "KAYNAK: assets/video.mp4" else "KAYNAK: SIM_RADAR_LOOP",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isUsingCustomVideoAsset) Color(0xFF00E676) else Color(0xFFFFD600),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // COLUMN 3: Live Performance Visualizers & Gauges
        Column(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF13151D)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.dp, Color(0xFF242A3A), RoundedCornerShape(20.dp))
                    .padding(12.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Clock
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TELEMETRİ",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE53935).copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.border(1.dp, Color(0xFFE53935).copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                        ) {
                            Text(
                                text = currentTimeString,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF5252),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Gauges Layout
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Gauge 1: Max Volume Display
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1A1D29))
                                .border(1.dp, Color(0xFF2A2E42), RoundedCornerShape(10.dp))
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                SpeakerIcon(
                                    color = Color(0xFF00E676),
                                    isMuted = false,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("SES", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "$currentVolume / $maxVolume",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                                Text(
                                    text = "MAKSİMUM",
                                    fontSize = 9.sp,
                                    color = Color(0xFF00E676),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Gauge 2: Screen Brightness
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1A1D29))
                                .border(1.dp, Color(0xFF2A2E42), RoundedCornerShape(10.dp))
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                BrightnessIcon(
                                    color = Color(0xFFFFD600),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("PARLAKLIK", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "100%",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                                Text(
                                    text = "EN ÜST",
                                    fontSize = 9.sp,
                                    color = Color(0xFFFFD600),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Equalizer Visualizer section
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isAudioPlaying) "SES DALGASI AKTİF" else "SES DALGASI BEKLEMEDE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isAudioPlaying) Color(0xFF00E676) else Color.Gray,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        // Render beautiful equalizer bars
                        AudioVisualizer(
                            isPlaying = isAudioPlaying,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0B0D12))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }

    // Tiny, unnoticeable mini exit button in the bottom right corner (stealth kiosk mode exit)
    Box(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(4.dp)
            .size(48.dp) // Maintain 48dp minimum click area for accessibility
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null // No visible ripple or focus effect
            ) {
                onExitApp()
            },
        contentAlignment = Alignment.BottomEnd
    ) {
        // A single almost invisible microscopic slate grey dot
        Box(
            modifier = Modifier
                .padding(4.dp)
                .size(3.dp)
                .background(Color(0xFF1E2333).copy(alpha = 0.4f), CircleShape)
        )
    }
}
}

@Composable
fun AudioVisualizer(isPlaying: Boolean, modifier: Modifier = Modifier) {
    val barCount = 12
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer")
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        for (i in 0 until barCount) {
            val duration = remember { (400..800).random() }
            val delay = remember { (0..300).random() }
            
            val heightFraction by if (isPlaying) {
                infiniteTransition.animateFloat(
                    initialValue = 0.15f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(duration, delayMillis = delay, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "bar_$i"
                )
            } else {
                remember { mutableStateOf(0.15f) }
            }
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(heightFraction)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF00E676),
                                Color(0xFF2979FF)
                            )
                        ),
                        shape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
                    )
            )
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}
