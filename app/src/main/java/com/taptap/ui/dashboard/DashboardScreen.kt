package com.taptap.ui.dashboard

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

@Composable
fun DashboardScreen(
    intent: Intent?,
    connectionViewModel: ConnectionViewModel
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

    // Load connections on screen load
    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotEmpty()) {
            connectionViewModel.loadConnections(currentUserId)
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

            Button(
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
                Text("Scan QR")
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
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No connections yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Scan a QR code to add your first connection",
                        style = MaterialTheme.typography.bodyMedium,
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
                    ConnectionCard(
                        connection = connection,
                        onDelete = {
                            connectionViewModel.deleteConnection(connection.connectionId)
                        }
                    )
                }
            }
        }
    }

    // Confirmation Dialog
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

@Composable
fun ConnectionCard(
    connection: Connection,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = connection.connectedUserName.ifEmpty { "Unknown" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = connection.getFormattedDate(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

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
            }

            // Expanded details
            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                if (connection.connectedUserEmail.isNotEmpty()) {
                    DetailRow(Icons.Default.Email, connection.connectedUserEmail)
                }
                if (connection.connectedUserPhone.isNotEmpty()) {
                    DetailRow(Icons.Default.Phone, connection.connectedUserPhone)
                }
                if (connection.connectedUserLinkedIn.isNotEmpty()) {
                    DetailRow(Icons.Default.Link, connection.connectedUserLinkedIn)
                }
                if (connection.connectedUserDescription.isNotEmpty()) {
                    DetailRow(Icons.Default.Description, connection.connectedUserDescription)
                }
                if (connection.connectedUserLocation.isNotEmpty()) {
                    DetailRow(Icons.Default.LocationOn, connection.connectedUserLocation)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Delete button
                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Connection")
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Connection?") },
            text = { Text("Are you sure you want to delete this connection with ${connection.connectedUserName}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
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
                            DetailRow(Icons.Default.Link, user.linkedIn)
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
                    Button(
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

