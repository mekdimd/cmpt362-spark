package com.taptap.ui.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(connection.connectedUserName.ifEmpty { "Connection" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, "Refresh Profile")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, "Delete Connection")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Profile header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Profile picture placeholder
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

                // Connection info
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

                // Stale indicator
                if (connection.needsProfileRefresh()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "Profile may be outdated",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }

            // Contact details
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

                // Social media section
                val hasSocialMedia = connection.connectedUserLinkedIn.isNotEmpty() ||
                        connection.connectedUserGithub.isNotEmpty() ||
                        connection.connectedUserInstagram.isNotEmpty() ||
                        connection.connectedUserWebsite.isNotEmpty()

                if (hasSocialMedia) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Social Media",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (connection.connectedUserLinkedIn.isNotEmpty()) {
                        ContactInfoItem(
                            icon = Icons.Default.Link,
                            label = "LinkedIn",
                            value = connection.connectedUserLinkedIn
                        )
                    }

                    if (connection.connectedUserGithub.isNotEmpty()) {
                        ContactInfoItem(
                            icon = Icons.Default.Code,
                            label = "GitHub",
                            value = connection.connectedUserGithub
                        )
                    }

                    if (connection.connectedUserInstagram.isNotEmpty()) {
                        ContactInfoItem(
                            icon = Icons.Default.Photo,
                            label = "Instagram",
                            value = connection.connectedUserInstagram
                        )
                    }

                    if (connection.connectedUserWebsite.isNotEmpty()) {
                        ContactInfoItem(
                            icon = Icons.Default.Language,
                            label = "Website",
                            value = connection.connectedUserWebsite
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
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

