package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.MediaAction
import com.example.core.RingerTarget
import com.example.core.SystemPanelType
import com.example.core.UiState
import com.example.core.VolumeStream
import com.example.core.WebTarget
import com.example.managers.AppLaunchOutcome
import com.example.managers.NovaFileEntry
import com.example.services.NovaNotificationListenerService
import com.example.ui.NovaViewModel

private enum class ControlsSection(val label: String) {
    CONTROLS("Controls & Info"),
    APPS("Apps"),
    COMMS("Calling & SMS"),
    CAMERA("Camera & Vision"),
    MEDIA("Music"),
    FILES_WEB("Files & Web")
}

@Composable
fun ControlsAndAppsScreen(
    viewModel: NovaViewModel,
    initialSectionName: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var section by remember {
        mutableStateOf(
            ControlsSection.entries.firstOrNull { it.name == initialSectionName } ?: ControlsSection.CONTROLS
        )
    }

    val openCamEvent by viewModel.openCameraEvent.collectAsStateWithLifecycle()
    val openGalleryEvent by viewModel.openGalleryPickerEvent.collectAsStateWithLifecycle()
    LaunchedEffect(openCamEvent) {
        if (openCamEvent != null) section = ControlsSection.CAMERA
    }
    LaunchedEffect(openGalleryEvent) {
        if (openGalleryEvent) section = ControlsSection.CAMERA
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 760.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ControlsSection.entries.forEach { sec ->
                FilterChip(
                    selected = section == sec,
                    onClick = { section = sec },
                    label = { Text(sec.label) },
                    modifier = Modifier.testTag("controls_tab_${sec.name.lowercase()}")
                )
            }
        }

        when (section) {
            ControlsSection.CONTROLS -> PhoneControlAndDeviceSection(viewModel)
            ControlsSection.APPS -> AppLauncherSection(viewModel)
            ControlsSection.COMMS -> ContactsAndSmsSection(viewModel, context)
            ControlsSection.CAMERA -> CameraAndVisionSection(viewModel)
            ControlsSection.MEDIA -> MusicAndMediaSection(viewModel)
            ControlsSection.FILES_WEB -> FilesAndWebSection(viewModel, context)
        }
    }
}

@Composable
private fun PhoneControlAndDeviceSection(viewModel: NovaViewModel) {
    val snapshot by viewModel.deviceSnapshot.collectAsStateWithLifecycle()
    var volumes by remember { mutableStateOf(viewModel.volumeController.getVolumeLevels()) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Phone Quick Controls", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                val (_, msg) = viewModel.phoneControlManager.toggleFlashlight(null)
                                viewModel.showBanner(msg)
                            },
                            modifier = Modifier.weight(1f).testTag("toggle_flashlight_button")
                        ) {
                            Icon(Icons.Filled.FlashlightOn, contentDescription = "Toggle flashlight", modifier = Modifier.size(18.dp))
                            Text(" Torch", modifier = Modifier.padding(start = 4.dp))
                        }
                        OutlinedButton(
                            onClick = { viewModel.showBanner(viewModel.phoneControlManager.vibratePulse()) },
                            modifier = Modifier.weight(1f).testTag("vibrate_test_button")
                        ) {
                            Icon(Icons.Filled.Vibration, contentDescription = "Vibrate test", modifier = Modifier.size(18.dp))
                            Text(" Vibrate", modifier = Modifier.padding(start = 4.dp))
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        RingerTarget.entries.forEach { target ->
                            OutlinedButton(
                                onClick = { viewModel.showBanner(viewModel.volumeController.setRingerMode(target)) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(target.name.lowercase().replaceFirstChar { it.uppercase() })
                            }
                        }
                    }

                    VolumeStream.entries.forEach { stream ->
                        val pct = volumes[stream] ?: 50
                        Text("${stream.name} Volume: $pct%", style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = pct.toFloat(),
                            onValueChange = { next ->
                                val v = next.toInt()
                                volumes = volumes + (stream to v)
                                viewModel.volumeController.setVolumePercent(stream, v)
                            },
                            valueRange = 0f..100f
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            SystemPanelType.WIFI to "Wi-Fi Panel",
                            SystemPanelType.BLUETOOTH to "Bluetooth",
                            SystemPanelType.DISPLAY to "Brightness",
                            SystemPanelType.DND to "DND Access",
                            SystemPanelType.BATTERY to "Battery",
                            SystemPanelType.SETTINGS to "Settings"
                        ).forEach { (panel, title) ->
                            OutlinedButton(onClick = {
                                viewModel.showBanner(viewModel.phoneControlManager.openSystemPanel(panel))
                            }) { Text(title) }
                        }
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Live Device Telemetry", style = MaterialTheme.typography.titleMedium)
                        }
                        TextButton(onClick = { viewModel.refreshDeviceSnapshot() }) { Text("Refresh") }
                    }
                    Text("${snapshot.manufacturer} ${snapshot.deviceModel} · Android ${snapshot.androidVersion} (SDK ${snapshot.sdkInt})")
                    Text("Battery: ${snapshot.batteryPercent}% (${snapshot.chargingSource}, ${snapshot.batteryTempC}°C)")
                    Text("RAM: ${"%.2f".format(snapshot.availableRamGb)} / ${"%.2f".format(snapshot.totalRamGb)} GB available")
                    Text("Storage: ${"%.2f".format(snapshot.freeStorageGb)} / ${"%.2f".format(snapshot.totalStorageGb)} GB free")
                    Text("Network: ${snapshot.networkSummary} · ${viewModel.phoneControlManager.getWifiAndBluetoothSummary()}")
                    Text(
                        "Sensors (${snapshot.sensorNames.size}): ${snapshot.sensorNames.take(6).joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AppLauncherSection(viewModel: NovaViewModel) {
    val apps by viewModel.installedApps.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var inAppQuery by remember { mutableStateOf("") }
    var missingAppPrompt by remember { mutableStateOf<String?>(null) }

    missingAppPrompt?.let { missing ->
        AlertDialog(
            onDismissRequest = { missingAppPrompt = null },
            title = { Text("App Not Installed") },
            text = { Text("'$missing' is not installed on this phone. Search & install it from Google Play Store?") },
            confirmButton = {
                Button(onClick = {
                    viewModel.showBanner(viewModel.appLauncher.openPlayStoreSearch(missing))
                    missingAppPrompt = null
                }) { Text("Open Play Store") }
            },
            dismissButton = {
                TextButton(onClick = { missingAppPrompt = null }) { Text("Cancel") }
            }
        )
    }

    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter {
            it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Smart App Launcher & Play Store Fallback", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("App name (e.g. YouTube, Spotify, Canva)") },
                        modifier = Modifier.fillMaxWidth().testTag("app_search_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = inAppQuery,
                        onValueChange = { inAppQuery = it },
                        label = { Text("Optional search inside app") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                when (val res = viewModel.appLauncher.launchApp(query.ifBlank { "Settings" }, inAppQuery.ifBlank { null })) {
                                    is AppLaunchOutcome.Launched -> viewModel.showBanner(res.message)
                                    is AppLaunchOutcome.NotInstalled -> missingAppPrompt = res.requestedName
                                    is AppLaunchOutcome.Failed -> viewModel.showBanner(res.message)
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("launch_named_app_button")
                        ) {
                            Icon(Icons.Filled.Apps, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(" Launch / Search", modifier = Modifier.padding(start = 4.dp))
                        }
                        OutlinedButton(onClick = { viewModel.refreshInstalledApps() }) { Text("Refresh (${apps.size})") }
                    }
                }
            }
        }
        items(filtered, key = { it.packageName }) { app ->
            Card(shape = RoundedCornerShape(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.titleMedium)
                        Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = {
                        when (val out = viewModel.appLauncher.launchApp(app.label, inAppQuery.ifBlank { null })) {
                            is AppLaunchOutcome.Launched -> viewModel.showBanner(out.message)
                            is AppLaunchOutcome.NotInstalled -> missingAppPrompt = out.requestedName
                            is AppLaunchOutcome.Failed -> viewModel.showBanner(out.message)
                        }
                    }) { Text("Open") }
                }
            }
        }
    }
}

@Composable
private fun ContactsAndSmsSection(viewModel: NovaViewModel, context: android.content.Context) {
    val contacts by viewModel.contactSearchResults.collectAsStateWithLifecycle()
    var contactQuery by remember { mutableStateOf("") }
    var targetNumber by remember { mutableStateOf("") }
    var smsBody by remember { mutableStateOf("") }

    val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.searchContacts(contactQuery) else viewModel.showBanner("Contacts permission denied.")
    }
    val callLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.showBanner(if (granted) "Direct Call permission granted." else "Dialer mode active.")
    }
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.showBanner(if (granted) "SEND_SMS permission granted." else "Safe SMS composer mode active.")
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Contacts & Calling", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = contactQuery,
                        onValueChange = { contactQuery = it },
                        label = { Text("Search contact name or number") },
                        modifier = Modifier.fillMaxWidth().testTag("contact_search_input"),
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                                    viewModel.searchContacts(contactQuery)
                                } else {
                                    contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Contacts, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(" Search", modifier = Modifier.padding(start = 4.dp))
                        }
                        OutlinedButton(onClick = { viewModel.showBanner(viewModel.contactManager.openSystemCallHistory()) }) { Text("Call Log") }
                        OutlinedButton(onClick = { viewModel.showBanner(viewModel.contactManager.openInsertContact(contactQuery)) }) { Text("New") }
                    }
                }
            }
        }
        items(contacts, key = { it.id + it.phone }) { entry ->
            Card(shape = RoundedCornerShape(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.name, style = MaterialTheme.typography.titleMedium)
                        Text(entry.phone, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { viewModel.showBanner(viewModel.contactManager.openDialer(entry.phone)) }) {
                            Icon(Icons.Filled.Call, contentDescription = "Dial ${entry.name}")
                        }
                        IconButton(onClick = {
                            targetNumber = entry.phone
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                                callLauncher.launch(Manifest.permission.CALL_PHONE)
                            } else {
                                viewModel.executeCommand("call ${entry.phone}")
                            }
                        }) { Text("Call") }
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("SMS & WhatsApp Messaging", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = targetNumber,
                        onValueChange = { targetNumber = it },
                        label = { Text("Recipient phone number") },
                        modifier = Modifier.fillMaxWidth().testTag("sms_recipient_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = smsBody,
                        onValueChange = { smsBody = it },
                        label = { Text("Message text") },
                        modifier = Modifier.fillMaxWidth().testTag("sms_body_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                                    smsLauncher.launch(Manifest.permission.SEND_SMS)
                                } else {
                                    val (_, msg) = viewModel.smsManager.sendConfirmedSms(targetNumber, smsBody)
                                    viewModel.showBanner(msg)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Sms, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(" Send SMS", modifier = Modifier.padding(start = 4.dp))
                        }
                        OutlinedButton(onClick = { viewModel.showBanner(viewModel.smsManager.openSmsComposer(targetNumber, smsBody)) }) { Text("SMS App") }
                        OutlinedButton(onClick = { viewModel.showBanner(viewModel.smsManager.openWhatsAppMessage(targetNumber, smsBody)) }) { Text("WhatsApp") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraAndVisionSection(viewModel: NovaViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val visionState by viewModel.visionResult.collectAsStateWithLifecycle()
    val camEvent by viewModel.openCameraEvent.collectAsStateWithLifecycle()
    val galleryEvent by viewModel.openGalleryPickerEvent.collectAsStateWithLifecycle()

    var hasCamPerm by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var useFrontCamera by remember { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var pickedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var visionQuestion by remember { mutableStateOf("What is in this image? Explain in Hinglish.") }

    val camPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCamPerm = granted
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            pickedUri = uri
            pickedBitmap = runCatching {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }
    }

    LaunchedEffect(galleryEvent) {
        if (galleryEvent) {
            viewModel.consumeGalleryPickerEvent()
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    LaunchedEffect(camEvent, hasCamPerm, imageCapture) {
        val ev = camEvent ?: return@LaunchedEffect
        useFrontCamera = ev.second
        if (ev.first && hasCamPerm && imageCapture != null) {
            viewModel.cameraManager.capturePhoto(
                imageCapture = imageCapture!!,
                onSaved = { uri ->
                    pickedUri = uri
                    pickedBitmap = runCatching {
                        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    }.getOrNull()
                    viewModel.showBanner("Photo captured and saved to Pictures/NOVA!")
                },
                onError = { viewModel.showBanner(it) }
            )
            viewModel.consumeCameraEvent()
        } else if (ev.first && !hasCamPerm) {
            camPermLauncher.launch(Manifest.permission.CAMERA)
            viewModel.consumeCameraEvent()
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("CameraX Live Viewfinder & Voice Capture", style = MaterialTheme.typography.titleMedium)
                    if (hasCamPerm) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(14.dp))
                        ) {
                            AndroidView(
                                factory = { ctx -> PreviewView(ctx) },
                                update = { previewView ->
                                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                                    cameraProviderFuture.addListener({
                                        val provider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build().also {
                                            it.surfaceProvider = previewView.surfaceProvider
                                        }
                                        val capture = ImageCapture.Builder().build()
                                        imageCapture = capture
                                        val selector = if (useFrontCamera) {
                                            CameraSelector.DEFAULT_FRONT_CAMERA
                                        } else {
                                            CameraSelector.DEFAULT_BACK_CAMERA
                                        }
                                        runCatching {
                                            provider.unbindAll()
                                            provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                                        }
                                    }, ContextCompat.getMainExecutor(context))
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        Button(
                            onClick = { camPermLauncher.launch(Manifest.permission.CAMERA) },
                            modifier = Modifier.testTag("grant_camera_button")
                        ) { Text("Grant Camera Permission for CameraX") }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                val cap = imageCapture
                                if (hasCamPerm && cap != null) {
                                    viewModel.cameraManager.capturePhoto(
                                        cap,
                                        onSaved = { uri ->
                                            pickedUri = uri
                                            pickedBitmap = runCatching {
                                                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                                            }.getOrNull()
                                            viewModel.showBanner("Photo captured & saved!")
                                        },
                                        onError = { viewModel.showBanner(it) }
                                    )
                                } else {
                                    viewModel.showBanner(viewModel.cameraManager.openSystemCamera(false))
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("capture_photo_button")
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(" Click Photo", modifier = Modifier.padding(start = 4.dp))
                        }
                        OutlinedButton(onClick = { useFrontCamera = !useFrontCamera }) {
                            Text(if (useFrontCamera) "Back Lens" else "Front Lens")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.weight(1f).testTag("open_photo_picker_button")
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(" Pick Photo", modifier = Modifier.padding(start = 4.dp))
                        }
                        OutlinedButton(onClick = { viewModel.showBanner(viewModel.cameraManager.openSystemCamera(true)) }) {
                            Text("Record Video")
                        }
                        OutlinedButton(onClick = { viewModel.showBanner(viewModel.cameraManager.openSystemGallery()) }) {
                            Text("Gallery")
                        }
                    }
                }
            }
        }

        if (pickedBitmap != null) {
            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Gemini Vision Multimodal Analysis", style = MaterialTheme.typography.titleMedium)
                        Image(
                            bitmap = pickedBitmap!!.asImageBitmap(),
                            contentDescription = "Selected photo for analysis",
                            modifier = Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(12.dp))
                        )
                        OutlinedTextField(
                            value = visionQuestion,
                            onValueChange = { visionQuestion = it },
                            label = { Text("Ask Gemini about this photo") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.analyzeImageWithGemini(pickedBitmap!!, visionQuestion) },
                                modifier = Modifier.weight(1f).testTag("analyze_vision_button")
                            ) { Text("Analyze Photo with Gemini") }
                            pickedUri?.let { uri ->
                                OutlinedButton(onClick = { viewModel.showBanner(viewModel.cameraManager.shareMediaUri(uri)) }) {
                                    Text("Share")
                                }
                            }
                        }
                        when (val st = visionState) {
                            UiState.Loading -> CircularProgressIndicator(modifier = Modifier.size(22.dp))
                            is UiState.Success -> Text(st.data, style = MaterialTheme.typography.bodyMedium)
                            is UiState.Error -> Text(st.message, color = MaterialTheme.colorScheme.error)
                            else -> Unit
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicAndMediaSection(viewModel: NovaViewModel) {
    val nowPlaying by NovaNotificationListenerService.currentMediaTrack.collectAsStateWithLifecycle()
    var songQuery by remember { mutableStateOf("") }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.showBanner(viewModel.mediaManager.playLocalAudioUri(uri))
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Music & Media Control", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = "Now Playing: ${nowPlaying ?: "No active media metadata detected"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = songQuery,
                        onValueChange = { songQuery = it },
                        label = { Text("Song / Artist name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = {
                            viewModel.showBanner(viewModel.mediaManager.handleMediaAction(MediaAction.PLAY_QUERY, songQuery))
                        }, modifier = Modifier.weight(1f)) { Text("Play Search") }
                        OutlinedButton(onClick = { audioPicker.launch(arrayOf("audio/*")) }) { Text("Local Audio") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { viewModel.showBanner(viewModel.mediaManager.handleMediaAction(MediaAction.PREVIOUS)) }, modifier = Modifier.weight(1f)) { Text("Prev") }
                        Button(onClick = { viewModel.showBanner(viewModel.mediaManager.handleMediaAction(MediaAction.PAUSE_RESUME)) }, modifier = Modifier.weight(1f)) { Text("Play/Pause") }
                        OutlinedButton(onClick = { viewModel.showBanner(viewModel.mediaManager.handleMediaAction(MediaAction.NEXT)) }, modifier = Modifier.weight(1f)) { Text("Next") }
                        OutlinedButton(onClick = { viewModel.showBanner(viewModel.mediaManager.handleMediaAction(MediaAction.STOP)) }, modifier = Modifier.weight(1f)) { Text("Stop") }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilesAndWebSection(viewModel: NovaViewModel, context: android.content.Context) {
    val files by viewModel.files.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val weatherState by viewModel.weatherResult.collectAsStateWithLifecycle()
    val wikiState by viewModel.wikiResult.collectAsStateWithLifecycle()
    val headlinesState by viewModel.headlines.collectAsStateWithLifecycle()

    var fileQuery by remember { mutableStateOf("") }
    var newFolderName by remember { mutableStateOf("") }
    var deleteCandidate by remember { mutableStateOf<NovaFileEntry?>(null) }
    var webQuery by remember { mutableStateOf("Delhi") }

    val safLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            viewModel.onSafFolderPicked(uri)
        }
    }

    deleteCandidate?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Confirm File Delete") },
            text = { Text("Delete '${item.name}' permanently?") },
            confirmButton = {
                Button(onClick = {
                    val (_, msg) = viewModel.fileManager.deleteEntry(item)
                    viewModel.refreshFiles(fileQuery)
                    viewModel.showBanner(msg)
                    deleteCandidate = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Cancel") } }
        )
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Internet, Weather, Wikipedia & News", style = MaterialTheme.typography.titleMedium)
                    }
                    OutlinedTextField(
                        value = webQuery,
                        onValueChange = { webQuery = it },
                        label = { Text("City / Search / Topic / URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        Button(onClick = { viewModel.fetchWeather(webQuery) }) { Text("Weather") }
                        Button(onClick = { viewModel.fetchWikipedia(webQuery) }) { Text("Wikipedia") }
                        OutlinedButton(onClick = { viewModel.fetchHeadlines() }) { Text("News RSS") }
                        OutlinedButton(onClick = { viewModel.showBanner(viewModel.internetManager.performSearch(webQuery, WebTarget.GOOGLE)) }) { Text("Google") }
                        OutlinedButton(onClick = { viewModel.showBanner(viewModel.internetManager.openUrl(webQuery)) }) { Text("Open URL") }
                    }
                    when (val w = weatherState) {
                        UiState.Loading -> CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        is UiState.Success -> Text(w.data.summary, color = MaterialTheme.colorScheme.primary)
                        is UiState.Error -> Text(w.message, color = MaterialTheme.colorScheme.error)
                        else -> Unit
                    }
                    when (val wk = wikiState) {
                        UiState.Loading -> CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        is UiState.Success -> Text(wk.data, style = MaterialTheme.typography.bodySmall)
                        is UiState.Error -> Text(wk.message, color = MaterialTheme.colorScheme.error)
                        else -> Unit
                    }
                    when (val hl = headlinesState) {
                        UiState.Loading -> CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        is UiState.Success -> hl.data.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        is UiState.Error -> Text(hl.message, color = MaterialTheme.colorScheme.error)
                        else -> Unit
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("SAF & Workspace File Manager", style = MaterialTheme.typography.titleMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { safLauncher.launch(null) }, modifier = Modifier.weight(1f).testTag("pick_saf_folder_button")) {
                            Text("Link SAF Folder")
                        }
                        OutlinedButton(onClick = {
                            viewModel.fileManager.createSandboxSampleNoteFile("nova_readme_${System.currentTimeMillis() % 1000}.txt", "Created by NOVA Voice AI")
                            viewModel.refreshFiles(fileQuery)
                        }) { Text("+ Sample File") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newFolderName,
                            onValueChange = { newFolderName = it },
                            label = { Text("New folder name") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(onClick = {
                            val (_, msg) = viewModel.fileManager.createFolder(settings.safTreeUri, newFolderName)
                            viewModel.refreshFiles(fileQuery)
                            viewModel.showBanner(msg)
                            newFolderName = ""
                        }) { Text("Create") }
                    }
                    OutlinedTextField(
                        value = fileQuery,
                        onValueChange = {
                            fileQuery = it
                            viewModel.refreshFiles(it)
                        },
                        label = { Text("Search files (${files.size})") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }

        items(files, key = { it.uriString }) { entry ->
            Card(shape = RoundedCornerShape(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (entry.isDirectory) "📁 ${entry.name}" else "📄 ${entry.name}", style = MaterialTheme.typography.titleSmall)
                        Text(if (entry.isSandboxFile) "NOVA Workspace" else "SAF Folder", style = MaterialTheme.typography.labelSmall)
                    }
                    Row {
                        TextButton(onClick = { viewModel.showBanner(viewModel.fileManager.openEntry(entry)) }) { Text("Open") }
                        IconButton(onClick = { viewModel.showBanner(viewModel.fileManager.shareEntry(entry)) }, modifier = Modifier.minimumInteractiveComponentSize()) {
                            Icon(Icons.Filled.Share, contentDescription = "Share ${entry.name}")
                        }
                        IconButton(onClick = { deleteCandidate = entry }, modifier = Modifier.minimumInteractiveComponentSize()) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete ${entry.name}", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
