package com.taptap.ui.home

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.nfc.NfcAdapter
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.taptap.model.User
import com.taptap.nfc.NfcReaderManager
import com.taptap.viewmodel.UserViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    userViewModel: UserViewModel,
    nfcAdapter: NfcAdapter?,
    onNavigateToDashboard: ((User) -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val currentUser by userViewModel.currentUser.observeAsState(User())
    val isLoading by userViewModel.isLoading.observeAsState(false)
    var showQrDialog by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // NFC Reader Manager
    val nfcReaderManager = remember { NfcReaderManager() }
    var isNfcReaderActive by remember { mutableStateOf(false) }

    // Handle NFC contact discovery
    LaunchedEffect(Unit) {
        nfcReaderManager.contactFound.collect { userId ->
            Log.d("HomeScreen", "NFC contact found: $userId")
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = "Contact found! Loading profile...",
                    duration = SnackbarDuration.Short
                )
            }

            // Load user profile from Firestore
            userViewModel.getUserFromFirestore(userId) { user ->
                if (user != null) {
                    Log.d("HomeScreen", "User loaded: ${user.fullName}")
                    Toast.makeText(context, "Profile received: ${user.fullName}", Toast.LENGTH_SHORT).show()
                    onNavigateToDashboard?.invoke(user)
                } else {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "User profile not found",
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            }
        }
    }

    // Handle NFC errors
    LaunchedEffect(Unit) {
        nfcReaderManager.error.collect { errorMessage ->
            Log.e("HomeScreen", "NFC Error: $errorMessage")
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = "NFC Error: $errorMessage",
                    duration = SnackbarDuration.Long
                )
            }
        }
    }

    // Manage NFC reader lifecycle - enable/disable based on isNfcReaderActive state
    DisposableEffect(isNfcReaderActive) {
        Log.d("HomeScreen", "╔═══════════════════════════════════════════════════╗")
        Log.d("HomeScreen", "║  DisposableEffect triggered                       ║")
        Log.d("HomeScreen", "║  isNfcReaderActive: $isNfcReaderActive${" ".repeat(28 - isNfcReaderActive.toString().length)}║")
        Log.d("HomeScreen", "╚═══════════════════════════════════════════════════╝")

        if (isNfcReaderActive && activity != null) {
            Log.d("HomeScreen", "→ Enabling NFC reader mode...")
            val enabled = nfcReaderManager.enableReaderMode(activity)
            Log.d("HomeScreen", "→ Enable result: $enabled")

            if (enabled) {
                Log.d("HomeScreen", "✓✓✓ NFC reader mode is now ACTIVE")
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = "NFC reader active - Hold phones back-to-back",
                        duration = SnackbarDuration.Long
                    )
                }
            } else {
                Log.e("HomeScreen", "✗✗✗ Failed to enable NFC reader mode")
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = "Failed to enable NFC reader",
                        duration = SnackbarDuration.Short
                    )
                }
            }
        } else if (!isNfcReaderActive && activity != null) {
            Log.d("HomeScreen", "→ Disabling NFC reader mode...")
            nfcReaderManager.disableReaderMode(activity)
            Log.d("HomeScreen", "✓ NFC reader mode is now INACTIVE")
        }

        onDispose {
            Log.d("HomeScreen", "→ DisposableEffect onDispose - cleaning up")
            if (activity != null && isNfcReaderActive) {
                Log.d("HomeScreen", "→ Disabling NFC reader in dispose")
                nfcReaderManager.disableReaderMode(activity)
            }
        }
    }

    // Handle refresh state
    LaunchedEffect(isLoading) {
        if (!isLoading) {
            isRefreshing = false
        }
    }

    // QR Scanner - when a profile is scanned, navigate to dashboard
    val qrScanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            Toast.makeText(context, "Scanned: ${result.contents}", Toast.LENGTH_SHORT).show()
            val user = handleScannedQrCode(result.contents, context)
            if (user != null) {
                // Trigger navigation to dashboard with scanned user
                onNavigateToDashboard?.invoke(user)
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        state = pullToRefreshState,
        onRefresh = {
            isRefreshing = true
            userViewModel.refreshUserProfile()
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            Text(
                text = "Your Profile",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Large Profile Card
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Profile Picture Placeholder
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentUser.fullName
                                .split(" ")
                                .mapNotNull { it.firstOrNull()?.uppercase() }
                                .take(2)
                                .joinToString(""),
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Name
                    Text(
                        text = currentUser.fullName.ifEmpty { "No Name" },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (currentUser.description.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = currentUser.description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Spacer(modifier = Modifier.height(24.dp))

                    HorizontalDivider()

                    Spacer(modifier = Modifier.height(16.dp))

                    if (currentUser.email.isNotEmpty()) {
                        ProfileInfoRow(
                            icon = Icons.Default.Email,
                            text = currentUser.email
                        )
                    }

                    if (currentUser.phone.isNotEmpty()) {
                        ProfileInfoRow(
                            icon = Icons.Default.Phone,
                            text = currentUser.phone
                        )
                    }

                    if (currentUser.location.isNotEmpty()) {
                        ProfileInfoRow(
                            icon = Icons.Default.LocationOn,
                            text = currentUser.location
                        )
                    }

                    // Social Links - Show visible links
                    val visibleLinks = currentUser.socialLinks.filter { it.isVisibleOnProfile }

                    if (visibleLinks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            visibleLinks.take(4).forEachIndexed { index, link ->
                                if (index > 0) Spacer(modifier = Modifier.width(8.dp))
                                AssistChip(
                                    onClick = { },
                                    label = { Text(link.label) },
                                    leadingIcon = {
                                        Icon(
                                            link.platform.icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Action Buttons Section
            Text(
                text = "Share Your Profile",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )

            // NFC Share Button
            if (nfcAdapter != null) {
                FilledTonalButton(
                    onClick = {
                        Log.d("HomeScreen", "=== NFC Button Clicked ===")
                        Log.d("HomeScreen", "NFC Adapter enabled: ${nfcAdapter.isEnabled}")
                        Log.d("HomeScreen", "Current isNfcReaderActive: $isNfcReaderActive")

                        if (!nfcAdapter.isEnabled) {
                            Log.w("HomeScreen", "NFC is disabled on device")
                            Toast.makeText(
                                context,
                                "Please enable NFC in settings",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            // Toggle NFC reader mode state - DisposableEffect will handle actual enable/disable
                            val newState = !isNfcReaderActive
                            Log.d("HomeScreen", "Toggling isNfcReaderActive from $isNfcReaderActive to $newState")
                            isNfcReaderActive = newState

                            if (newState) {
                                Log.d("HomeScreen", "✓ Set to ENABLE NFC reader (DisposableEffect will activate)")
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = "Activating NFC reader...",
                        duration = SnackbarDuration.Short
                    )
                }
                            } else {
                                Log.d("HomeScreen", "✓ Set to DISABLE NFC reader (DisposableEffect will deactivate)")
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = "Deactivating NFC reader...",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }

                            Log.d("HomeScreen", "Final isNfcReaderActive state: $isNfcReaderActive")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(bottom = 8.dp),
                    colors = if (isNfcReaderActive) {
                        ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        ButtonDefaults.filledTonalButtonColors()
                    }
                ) {
                    Icon(
                        Icons.Default.Nfc,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        if (isNfcReaderActive) "NFC Scanning Active (Tap to Stop)"
                        else "Scan via NFC",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Generate QR Code Button
            OutlinedButton(
                onClick = { showQrDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(
                    Icons.Default.QrCode2,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Share QR Code", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Scan QR Code Button
            OutlinedButton(
                onClick = {
                    val options = ScanOptions().apply {
                        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        setPrompt("")
                        setCameraId(0)
                        setBeepEnabled(false)
                        setBarcodeImageEnabled(true)
                        setOrientationLocked(true) // Lock orientation
                    }
                    qrScanner.launch(options)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(
                    Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Scan Code", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
    }

    if (showQrDialog) {
        QrCodeDialog(
            userViewModel = userViewModel,
            onDismiss = { showQrDialog = false }
        )
    }
}

@Composable
fun ProfileInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun QrCodeDialog(
    userViewModel: UserViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val qrCodeBitmap = remember {
        try {
            generateQrCode(userViewModel)
        } catch (e: Exception) {
            Toast.makeText(context, "Error generating QR code", Toast.LENGTH_SHORT).show()
            null
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Scan to Share Profile",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                qrCodeBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier
                            .size(300.dp)
                            .padding(8.dp)
                    )
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

private fun generateQrCode(userViewModel: UserViewModel): Bitmap {
    val profile = userViewModel.getUserProfileJson()
    val jsonString = profile.toString()

    var base64Data = Base64.encodeToString(jsonString.toByteArray(), Base64.DEFAULT)
    base64Data = base64Data.replace("\n", "")
    base64Data = base64Data.replace("=", "")
    base64Data = base64Data.replace("/", "_")
    base64Data = base64Data.replace("+", "-")

    val deepLink = "myapp://share?data=$base64Data"

    val hints = java.util.EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
    hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
    hints[EncodeHintType.MARGIN] = 1

    val bitMatrix: BitMatrix = MultiFormatWriter().encode(
        deepLink, BarcodeFormat.QR_CODE, 500, 500, hints
    )

    val width = bitMatrix.width
    val height = bitMatrix.height
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

    for (x in 0 until width) {
        for (y in 0 until height) {
            val isBlack = bitMatrix.get(x, y)
            if (isBlack) {
                bitmap.setPixel(x, y, Color.BLACK)
            } else {
                bitmap.setPixel(x, y, Color.WHITE)
            }
        }
    }
    return bitmap
}

private fun handleScannedQrCode(qrContent: String, context: android.content.Context): User? {
    return if (qrContent.startsWith("myapp://")) {
        val uri = android.net.Uri.parse(qrContent)
        handleDeepLink(uri, context)
    } else {
        processReceivedData(qrContent, "QR Code", context)
    }
}

private fun handleDeepLink(uri: android.net.Uri?, context: android.content.Context): User? {
    uri ?: return null

    return try {
        val encodedData = uri.getQueryParameter("data")
        if (encodedData != null) {
            var paddedData = encodedData
            val paddingNeeded = paddedData.length % 4
            if (paddingNeeded > 0) {
                paddedData += "====".substring(0, 4 - paddingNeeded)
            }

            val finalData = paddedData
                .replace("_", "/")
                .replace("-", "+")

            val decoded = Base64.decode(finalData, Base64.DEFAULT)
            val jsonString = String(decoded, java.nio.charset.StandardCharsets.UTF_8)
            processReceivedData(jsonString, "QR Code", context)
        } else {
            Toast.makeText(context, "Invalid QR code data", Toast.LENGTH_SHORT).show()
            null
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Invalid QR code data: ${e.message}", Toast.LENGTH_SHORT).show()
        null
    }
}

private fun processReceivedData(
    jsonString: String,
    source: String,
    context: android.content.Context
): User? {
    return try {
        val data = org.json.JSONObject(jsonString)

        if (!"com.taptap".equals(data.optString("app_id", ""))) {
            Toast.makeText(context, "Data not from Spark app", Toast.LENGTH_SHORT).show()
            return null
        }

        Toast.makeText(context, "Profile received via $source", Toast.LENGTH_SHORT).show()

        // Create User object from JSON
        User(
            userId = data.optString("userId", ""),
            createdAt = data.optLong("createdAt", 0),
            lastSeen = data.optString("lastSeen", ""),
            fullName = data.optString("fullName", ""),
            email = data.optString("email", ""),
            phone = data.optString("phone", ""),
            description = data.optString("description", ""),
            location = data.optString("location", ""),
            socialLinks = emptyList() // Will be parsed if available
        )
    } catch (e: Exception) {
        Toast.makeText(context, "Invalid data format: ${e.message}", Toast.LENGTH_SHORT).show()
        null
    }
}

@Composable
private fun getSocialIcon(platform: com.taptap.model.SocialLink.SocialPlatform): androidx.compose.ui.graphics.vector.ImageVector {
    return platform.icon
}
