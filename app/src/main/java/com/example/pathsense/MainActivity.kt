package com.example.pathsense

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.pathsense.accessibility.AccessibilityPreferences
import com.example.pathsense.accessibility.AudioFeedbackManager
import com.example.pathsense.accessibility.HapticFeedbackManager
import com.example.pathsense.camera.CameraStreamer
import com.example.pathsense.core.FrameHub
import com.example.pathsense.pipelines.PipelineCoordinator
import com.example.pathsense.ui.screens.MainScreen
import com.example.pathsense.ui.screens.SettingsScreen
import com.example.pathsense.ui.theme.PathSenseTheme
import kotlinx.coroutines.launch

/**
 * Main activity for PathSense accessible navigation app.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PathSenseApp() }
    }
}

/**
 * Navigation routes for the app.
 */
private object Routes {
    const val MAIN = "main"
    const val SETTINGS = "settings"
}

/**
 * Root composable for the PathSense app.
 * Manages accessibility settings, navigation, and camera setup.
 */
@Composable
fun PathSenseApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()

    // Initialize accessibility components
    val preferences = remember { AccessibilityPreferences(context) }
    val audioManager = remember { AudioFeedbackManager(context, preferences) }
    val hapticManager = remember { HapticFeedbackManager(context, preferences) }

    // Collect accessibility preferences for theme
    val highContrast by preferences.highContrast.collectAsState(initial = false)
    val largeText by preferences.largeText.collectAsState(initial = false)

    // Local state for theme (allows immediate updates)
    var localHighContrast by remember { mutableStateOf(false) }
    var localLargeText by remember { mutableStateOf(false) }

    // Sync local state with preferences
    LaunchedEffect(highContrast) { localHighContrast = highContrast }
    LaunchedEffect(largeText) { localLargeText = largeText }

    // Sync haptic enabled state
    LaunchedEffect(Unit) {
        preferences.hapticEnabled.collect { enabled ->
            hapticManager.setEnabled(enabled)
        }
    }

    // Sync speech settings
    LaunchedEffect(Unit) {
        scope.launch {
            preferences.speechRate.collect { rate ->
                audioManager.setSpeechRate(rate)
            }
        }
        scope.launch {
            preferences.speechPitch.collect { pitch ->
                audioManager.setSpeechPitch(pitch)
            }
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            audioManager.shutdown()
        }
    }

    PathSenseTheme(
        highContrast = localHighContrast,
        largeText = localLargeText,
        dynamicColor = !localHighContrast // Disable dynamic color in high contrast mode
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            NavHost(
                navController = navController,
                startDestination = Routes.MAIN
            ) {
                composable(Routes.MAIN) {
                    CameraScreen(
                        preferences = preferences,
                        audioManager = audioManager,
                        hapticManager = hapticManager,
                        onNavigateToSettings = {
                            navController.navigate(Routes.SETTINGS)
                        }
                    )
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        preferences = preferences,
                        audioManager = audioManager,
                        hapticManager = hapticManager,
                        onNavigateBack = {
                            navController.popBackStack()
                        },
                        onHighContrastChanged = { enabled ->
                            localHighContrast = enabled
                        },
                        onLargeTextChanged = { enabled ->
                            localLargeText = enabled
                        }
                    )
                }
            }
        }
    }
}

/**
 * Camera screen with permission handling and pipeline setup.
 */
@Composable
private fun CameraScreen(
    preferences: AccessibilityPreferences,
    audioManager: AudioFeedbackManager,
    hapticManager: HapticFeedbackManager,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // Camera permission state
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        granted = isGranted
        if (isGranted) {
            audioManager.announce("Camera ready")
        }
    }

    LaunchedEffect(Unit) {
        if (!granted) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!granted) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Camera permission required for PathSense to work",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
        return
    }

    // Initialize pipeline components
    val hub = remember { FrameHub() }
    val coordinator = remember {
        PipelineCoordinator(context.applicationContext, hub, scope)
    }

    // Create and remember the preview view
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }

    // Set up camera
    LaunchedEffect(granted) {
        if (granted) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val streamer = CameraStreamer(hub)
                val analysis = streamer.buildImageAnalysis()

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                } catch (_: Exception) {}
            }, ContextCompat.getMainExecutor(context))

            coordinator.start()
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            coordinator.stop()
        }
    }

    // Main screen with mode selection
    MainScreen(
        previewView = previewView,
        coordinator = coordinator,
        audioManager = audioManager,
        hapticManager = hapticManager,
        preferences = preferences,
        onNavigateToSettings = onNavigateToSettings
    )
}
