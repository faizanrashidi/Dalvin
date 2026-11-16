package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.NovaRepository
import com.example.data.NovaSettingsState
import com.example.data.SettingsManager
import com.example.ui.auth.AuthScreen
import com.example.ui.screens.AutomationAndAiScreen
import com.example.ui.screens.ControlsAndAppsScreen
import com.example.ui.screens.NovaHubScreen
import com.example.ui.screens.ProductivityScreen
import com.example.ui.screens.SecurityAndRegisterScreen
import com.example.ui.theme.NovaVoiceTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class NovaDestination(val label: String, val icon: ImageVector) {
    HUB("Voice", Icons.Filled.Mic),
    CONTROLS("Device", Icons.Filled.Devices),
    PRODUCTIVITY("Tasks", Icons.Filled.EventNote),
    AUTOMATION("Auto AI", Icons.Filled.AutoAwesome),
    SECURITY("Security", Icons.Filled.Security)
}

@Composable
fun NovaAppRoot(
    voiceTriggerCount: Int = 0
) {
    val context = LocalContext.current
    val settingsManager = remember(context) { SettingsManager(context.applicationContext) }
    val preAuthSettings by settingsManager.settingsFlow.collectAsStateWithLifecycle(initialValue = NovaSettingsState())
    val currentUser by authStateFlow().collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    NovaVoiceTheme(themeMode = preAuthSettings.themeMode) {
        val user = currentUser
        if (user == null) {
            Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { innerPadding ->
                AuthScreen(
                    onAuthSuccess = {},
                    modifier = Modifier.padding(innerPadding)
                )
            }
        } else {
            key(user.uid) {
                val repository = remember(context) { NovaRepository(context.applicationContext) }
                val viewModel: NovaViewModel = viewModel(
                    key = user.uid,
                    factory = NovaViewModel.Factory(
                        context = context.applicationContext,
                        userId = user.uid,
                        repository = repository,
                        settingsManager = settingsManager
                    )
                )
                AuthenticatedNovaShell(
                    viewModel = viewModel,
                    userEmail = user.email ?: user.displayName ?: user.uid,
                    voiceTriggerCount = voiceTriggerCount
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedNovaShell(
    viewModel: NovaViewModel,
    userEmail: String,
    voiceTriggerCount: Int
) {
    var selectedTab by rememberSaveable { mutableStateOf(NovaDestination.HUB) }
    val snackbarHostState = remember { SnackbarHostState() }
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val openCamEvent by viewModel.openCameraEvent.collectAsStateWithLifecycle()
    val openGalleryEvent by viewModel.openGalleryPickerEvent.collectAsStateWithLifecycle()

    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    LaunchedEffect(openCamEvent, openGalleryEvent) {
        if (openCamEvent != null || openGalleryEvent) {
            selectedTab = NovaDestination.CONTROLS
        }
    }

    LaunchedEffect(voiceTriggerCount) {
        if (voiceTriggerCount > 0) {
            selectedTab = NovaDestination.HUB
            viewModel.startVoiceListening()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        NovaDestination.entries.forEach { dest ->
                            NavigationBarItem(
                                selected = selectedTab == dest,
                                onClick = { selectedTab = dest },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("nav_tab_${dest.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail {
                        NovaDestination.entries.forEach { dest ->
                            NavigationRailItem(
                                selected = selectedTab == dest,
                                onClick = { selectedTab = dest },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("nav_rail_${dest.name.lowercase()}")
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    when (selectedTab) {
                        NovaDestination.HUB -> NovaHubScreen(viewModel = viewModel)
                        NovaDestination.CONTROLS -> ControlsAndAppsScreen(viewModel = viewModel)
                        NovaDestination.PRODUCTIVITY -> ProductivityScreen(viewModel = viewModel)
                        NovaDestination.AUTOMATION -> AutomationAndAiScreen(viewModel = viewModel)
                        NovaDestination.SECURITY -> SecurityAndRegisterScreen(
                            viewModel = viewModel,
                            userEmail = userEmail
                        )
                    }
                }
            }
        }
    }
}

private fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val auth = Firebase.auth
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    auth.addAuthStateListener(listener)
    awaitClose { auth.removeAuthStateListener(listener) }
}
