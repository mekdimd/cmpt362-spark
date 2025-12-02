package com.taptap.ui.connection

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.taptap.model.Connection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionDetailScreen(
    connection: Connection,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            kotlinx.coroutines.delay(1500)
            isRefreshing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(connection.connectedUserName.ifEmpty { "Connection" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, "Delete Connection")
                    }
                }
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                onRefresh()
            },
            state = pullToRefreshState,
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = connection.connectedUserName
                                .split(" ")
                                .mapNotNull { it.firstOrNull()?.uppercase() }
                                .take(2)
                                .joinToString(""),
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = connection.connectedUserName.ifEmpty { "Unknown" },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (connection.connectedUserDescription.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = connection.connectedUserDescription,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = connection.getRelativeTimeString(),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text("•", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = connection.connectionMethod,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Contact Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (connection.connectedUserEmail.isNotEmpty()) {
                        ContactInfoItem(
                            icon = Icons.Default.Email,
                            label = "Email",
                            value = connection.connectedUserEmail
                        )
                    }

                    if (connection.connectedUserPhone.isNotEmpty()) {
                        ContactInfoItem(
                            icon = Icons.Default.Phone,
                            label = "Phone",
                            value = connection.connectedUserPhone
                        )
                    }

                    if (connection.connectedUserLocation.isNotEmpty()) {
                        ContactInfoItem(
                            icon = Icons.Default.LocationOn,
                            label = "Location",
                            value = connection.connectedUserLocation
                        )
                    }

                    val visibleSocialLinks = connection.connectedUserSocialLinks.filter { it.isVisibleOnProfile }
                    Log.d("ConnectionDetailScreen", "Displaying social links: ${visibleSocialLinks.size} out of ${connection.connectedUserSocialLinks.size}")
                    if (visibleSocialLinks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Social Media",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        visibleSocialLinks.forEachIndexed { index, socialLink ->
                            Log.d("ConnectionDetailScreen", "  Displaying link $index: ${socialLink.label} - ${socialLink.url}")
                            ContactInfoItem(
                                icon = socialLink.platform.icon,
                                label = socialLink.label.ifEmpty { socialLink.platform.displayName },
                                value = socialLink.url
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Connection?") },
            text = {
                Text("Are you sure you want to delete this connection with ${connection.connectedUserName}? This action cannot be undone.")
            },
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
fun ContactInfoItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            val intent = when (label) {
                "Email" -> Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:$value")
                }

                "Phone" -> Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$value")
                }

                "Location" -> Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("geo:0,0?q=${Uri.encode(value)}")
                    setPackage("com.google.android.apps.maps")
                }

                "LinkedIn", "GitHub", "Instagram", "Website" -> Intent(Intent.ACTION_VIEW).apply {
                    val url = if (value.startsWith("http")) value else "https://$value"
                    data = Uri.parse(url)
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }

                else -> null
            }

            intent?.let {
                try {
                    if (label == "Location") {
                        try {
                            context.startActivity(it)
                        } catch (_: Exception) {
                            val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("geo:0,0?q=${Uri.encode(value)}")
                            }
                            context.startActivity(fallbackIntent)
                        }
                    } else {
                        context.startActivity(it)
                    }
                } catch (e: Exception) {
                    android.widget.Toast.makeText(
                        context,
                        "Could not open $label",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        },
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 4.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

