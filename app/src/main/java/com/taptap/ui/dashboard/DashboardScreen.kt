package com.taptap.ui.dashboard

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.firebase.auth.FirebaseAuth
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.taptap.model.Connection
import com.taptap.model.User
import com.taptap.viewmodel.ConnectionViewModel
import org.json.JSONObject
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    intent: Intent?,
    connectionViewModel: ConnectionViewModel,
    onNavigateToDetail: (String) -> Unit
) {
    val context = LocalContext.current
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    val connections by connectionViewModel.connections.observeAsState(emptyList())
    val isLoading by connectionViewModel.isLoading.observeAsState(false)
    val errorMessage by connectionViewModel.errorMessage.observeAsState()
    val successMessage by connectionViewModel.successMessage.observeAsState()

    var showConfirmationDialog by remember { mutableStateOf(false) }
    var scannedUser by remember { mutableStateOf<User?>(null) }
    var scanMethod by remember { mutableStateOf("QR") }
    var isRefreshing by remember { mutableStateOf(false) }

    // Load connections on screen load
    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotEmpty()) {
            connectionViewModel.loadConnections(currentUserId)
            // Refresh stale profiles (older than 1 hour)
            connectionViewModel.refreshStaleProfiles(currentUserId)
        }
    }

    // Handle incoming NFC/Deep link data
    LaunchedEffect(intent) {
        intent?.let {
            val user = checkForIncomingData(it, context)
            if (user != null) {
                scannedUser = user
                scanMethod = if (it.action == NfcAdapter.ACTION_NDEF_DISCOVERED) "NFC" else "QR"
                showConfirmationDialog = true
            }
        }
    }

    // QR Scanner
    val qrScanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val user = handleScannedQrCode(result.contents, context)
            if (user != null) {
                scannedUser = user
                scanMethod = "QR"
                showConfirmationDialog = true
            }
        }
    }

    // Show success message
    LaunchedEffect(successMessage) {
        successMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            connectionViewModel.clearMessages()
        }
    }

    // Show error message
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
    }

    // Handle refresh state
    LaunchedEffect(isLoading) {
        if (!isLoading) {
            isRefreshing = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                connectionViewModel.loadConnections(currentUserId)
                connectionViewModel.refreshStaleProfiles(currentUserId)
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header with scan button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Connections",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // Scan button
                    FilledTonalButton(
                        onClick = {
                            val options = ScanOptions().apply {
                                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                setPrompt("Scan a QR code from Spark")
                                setCameraId(0)
                                setBeepEnabled(false)
                                setBarcodeImageEnabled(true)
                                setOrientationLocked(true)
                                captureActivity = CaptureActivityPortrait::class.java
                            }
                            qrScanner.launch(options)
                        }
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Connections count
                Text(
                    text = "${connections.size} Connection${if (connections.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                        Spacer(modifier = Modifier.height(16.dp))

                // Loading indicator
                if (isLoading && connections.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                        } else if (connections.isEmpty()) {
                    // Empty state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(32.dp)
                                ) {
                            Surface(
                                modifier = Modifier.size(120.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.PersonAdd,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "No connections yet",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Scan a QR code to add your first connection",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                        } else {
                    // Connections list
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(connections) { connection ->
                            ConnectionSummaryCard(
                                connection = connection,
                                onClick = { onNavigateToDetail(connection.connectionId) }
                            )
                        }
                    }
                }
            }
        }

        // Confirmation Dialog (outside PullToRefreshBox but inside Box)
        if (showConfirmationDialog && scannedUser != null) {
            ConfirmConnectionDialog(
                user = scannedUser!!,
                scanMethod = scanMethod,
                onConfirm = {
                    connectionViewModel.saveConnection(
                        userId = currentUserId,
                        connectedUser = scannedUser!!,
                        connectionMethod = scanMethod
                    )
                    showConfirmationDialog = false
                    scannedUser = null
                },
                onDismiss = {
                    showConfirmationDialog = false
                    scannedUser = null
                }
            )
        }
    }
}

@Composable
fun ConnectionSummaryCard(
    connection: Connection,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp,
            hoveredElevation = 6.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Profile picture placeholder
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = connection.connectedUserName
                        .split(" ")
                        .mapNotNull { it.firstOrNull()?.uppercase() }
                        .take(2)
                        .joinToString(""),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }

            // Connection info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = connection.connectedUserName.ifEmpty { "Unknown" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (connection.connectedUserDescription.isNotEmpty()) {
                    Text(
                        text = connection.connectedUserDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = connection.getRelativeTimeString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (connection.connectedUserLocation.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = connection.connectedUserLocation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Badges column
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Method badge
                Surface(
                    color = if (connection.connectionMethod == "NFC")
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = connection.connectionMethod,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                // Stale indicator
                if (connection.needsProfileRefresh()) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "Update",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun ConfirmConnectionDialog(
    user: User,
    scanMethod: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Profile Scanned",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Received via $scanMethod",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                // User details
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        if (user.fullName.isNotEmpty()) {
                            DetailRow(Icons.Default.Person, user.fullName)
                        }
                        if (user.email.isNotEmpty()) {
                            DetailRow(Icons.Default.Email, user.email)
                        }
                        if (user.phone.isNotEmpty()) {
                            DetailRow(Icons.Default.Phone, user.phone)
                        }
                        if (user.linkedIn.isNotEmpty()) {
                            DetailRow(Icons.Default.Link, "LinkedIn: ${user.linkedIn}")
                        }
                        if (user.github.isNotEmpty()) {
                            DetailRow(Icons.Default.Code, "GitHub: ${user.github}")
                        }
                        if (user.instagram.isNotEmpty()) {
                            DetailRow(Icons.Default.Photo, "Instagram: ${user.instagram}")
                        }
                        if (user.website.isNotEmpty()) {
                            DetailRow(Icons.Default.Language, "Website: ${user.website}")
                        }
                        if (user.description.isNotEmpty()) {
                            DetailRow(Icons.Default.Description, user.description)
                        }
                        if (user.location.isNotEmpty()) {
                            DetailRow(Icons.Default.LocationOn, user.location)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Save this connection?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    FilledTonalButton(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save")
                    }
                }
            }
        }
    }
}

// Custom Capture Activity for Portrait QR scanning
class CaptureActivityPortrait : com.journeyapps.barcodescanner.CaptureActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
}

private fun checkForIncomingData(intent: Intent, context: android.content.Context): User? {
    return when (intent.action) {
        Intent.ACTION_VIEW -> handleDeepLink(intent.data, context)
        NfcAdapter.ACTION_NDEF_DISCOVERED -> handleNfcData(intent, context)
        else -> null
    }
}

private fun handleScannedQrCode(qrContent: String, context: android.content.Context): User? {
    return if (qrContent.startsWith("myapp://")) {
        val uri = Uri.parse(qrContent)
        handleDeepLink(uri, context)
    } else {
        processReceivedData(qrContent, "QR Code", context)
    }
}

private fun handleDeepLink(uri: Uri?, context: android.content.Context): User? {
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
            val jsonString = String(decoded, StandardCharsets.UTF_8)
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

private fun handleNfcData(intent: Intent, context: android.content.Context): User? {
    return try {
        val messages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        if (messages != null && messages.isNotEmpty()) {
            val message = messages[0] as NdefMessage
            if (message.records.isNotEmpty()) {
                val jsonString = String(message.records[0].payload, StandardCharsets.UTF_8)
                processReceivedData(jsonString, "NFC", context)
            } else {
                Toast.makeText(context, "Error reading NFC data", Toast.LENGTH_SHORT).show()
                null
            }
        } else {
            Toast.makeText(context, "Error reading NFC data", Toast.LENGTH_SHORT).show()
            null
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Error reading NFC data: ${e.message}", Toast.LENGTH_SHORT).show()
        null
    }
}

private fun processReceivedData(
    jsonString: String,
    source: String,
    context: android.content.Context
): User? {
    return try {
        val data = JSONObject(jsonString)

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
            linkedIn = data.optString("linkedIn", ""),
            github = data.optString("github", ""),
            instagram = data.optString("instagram", ""),
            website = data.optString("website", ""),
            description = data.optString("description", ""),
            location = data.optString("location", "")
        )
    } catch (e: Exception) {
        Toast.makeText(context, "Invalid data format: ${e.message}", Toast.LENGTH_SHORT).show()
        null
    }
}

