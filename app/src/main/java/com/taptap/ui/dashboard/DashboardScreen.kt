package com.taptap.ui.dashboard

import android.content.Intent
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.saveable.rememberSaveable
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

// Sort options enum
enum class SortOption {
    DATE, NAME, LOCATION
}

// Sort direction enum
enum class SortDirection {
    ASCENDING, DESCENDING
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    intent: Intent?,
    connectionViewModel: ConnectionViewModel,
    onNavigateToDetail: (String) -> Unit,
    scannedUserFromHome: User? = null,
    scanMethodFromHome: String? = null, // Added parameter to track scan method
    onScannedUserHandled: () -> Unit = {}
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

    // Search and Sort states (persisted across navigation)
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedSort by rememberSaveable { mutableStateOf(SortOption.DATE) }
    var sortDirection by rememberSaveable { mutableStateOf(SortDirection.DESCENDING) }

    // Pull to refresh state
    val pullToRefreshState = rememberPullToRefreshState()

    // Load connections on screen load
    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotEmpty()) {
            connectionViewModel.loadConnections(currentUserId)
        }
    }

    // Stop refreshing when loading completes
    LaunchedEffect(isLoading) {
        if (!isLoading && isRefreshing) {
            isRefreshing = false
        }
    }

    // Handle scanned user from HomeScreen
    LaunchedEffect(scannedUserFromHome) {
        if (scannedUserFromHome != null) {
            scannedUser = scannedUserFromHome
            scanMethod = scanMethodFromHome ?: "QR" // Use the passed method, default to QR if null
            showConfirmationDialog = true
            onScannedUserHandled()
        }
    }

    // Handle incoming intent (NFC or deep link)
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

    // Filter and sort connections
    val filteredAndSortedConnections = remember(connections, searchQuery, selectedSort, sortDirection) {
        val filtered = if (searchQuery.isEmpty()) {
            connections
        } else {
            connections.filter { connection ->
                connection.connectedUserName.contains(searchQuery, ignoreCase = true) ||
                        connection.connectedUserDescription.contains(
                            searchQuery,
                            ignoreCase = true
                        ) ||
                        connection.connectedUserLocation.contains(searchQuery, ignoreCase = true)
            }
        }

        val sorted = when (selectedSort) {
            SortOption.DATE -> filtered.sortedBy { it.timestamp }
            SortOption.NAME -> filtered.sortedBy { it.connectedUserName }
            SortOption.LOCATION -> filtered.sortedBy { it.connectedUserLocation }
        }

        // Apply direction
        if (sortDirection == SortDirection.DESCENDING) {
            sorted.reversed()
        } else {
            sorted
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            state = pullToRefreshState,
            onRefresh = {
                isRefreshing = true
                connectionViewModel.refreshAllConnections(currentUserId)
            }
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Custom Header Section
                ConnectionsHeader(
                    connectionCount = connections.size,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onScanClick = {
                        val options = ScanOptions().apply {
                            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            setPrompt("")
                            setCameraId(0)
                            setBeepEnabled(false)
                            setBarcodeImageEnabled(true)
                            setOrientationLocked(true)
                        }
                        qrScanner.launch(options)
                    }
                )

                // Sort Chips Row
                ExpressiveSortRow(
                    selectedSort = selectedSort,
                    sortDirection = sortDirection,
                    onSortChange = { newSort ->
                        if (newSort == selectedSort) {
                            // Toggle direction if same option is clicked
                            sortDirection = if (sortDirection == SortDirection.ASCENDING) {
                                SortDirection.DESCENDING
                            } else {
                                SortDirection.ASCENDING
                            }
                        } else {
                            // New option selected, set default direction based on the option
                            selectedSort = newSort
                            sortDirection = when (newSort) {
                                SortOption.DATE -> SortDirection.DESCENDING // Newest first by default
                                SortOption.NAME, SortOption.LOCATION -> SortDirection.ASCENDING // A-Z by default
                            }
                        }
                    }
                )

                // Loading indicator
                if (isLoading && connections.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (filteredAndSortedConnections.isEmpty()) {
                    // Empty state
                    ConnectionsEmptyState(
                        isSearching = searchQuery.isNotEmpty()
                    )
                } else {
                    // Connections list
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(
                            items = filteredAndSortedConnections,
                            key = { it.connectionId }
                        ) { connection ->
                            ConnectionCard(
                                connection = connection,
                                onConnectionClick = { onNavigateToDetail(connection.connectionId) },
                                modifier = Modifier.animateItem()
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
                    connectionViewModel.saveConnectionWithLocation(
                        userId = currentUserId,
                        connectedUser = scannedUser!!,
                        connectionMethod = scanMethod,
                        context = context
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

/**
 * Custom Header Section with Search Bar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsHeader(
    connectionCount: Int,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onScanClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row with Title and Scan Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Connections",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$connectionCount Connection${if (connectionCount != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledTonalButton(
                    onClick = onScanClick,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Search Bar with 100.dp corner radius
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                placeholder = { Text("Search connections...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                shape = RoundedCornerShape(100.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                singleLine = true
            )
        }
    }
}

/**
 * Expressive Sort Row with Elevated Filter Chips
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveSortRow(
    selectedSort: SortOption,
    sortDirection: SortDirection,
    onSortChange: (SortOption) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // "Sort By" label
        Text(
            text = "Sort By",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Sort Options Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SortOption.entries.forEach { sortOption ->
                val isSelected = selectedSort == sortOption
                val label = when (sortOption) {
                    SortOption.DATE -> "Date"
                    SortOption.NAME -> "Name"
                    SortOption.LOCATION -> "Location"
                }
                val icon = when (sortOption) {
                    SortOption.DATE -> Icons.Default.CalendarToday
                    SortOption.NAME -> Icons.Default.SortByAlpha
                    SortOption.LOCATION -> Icons.Default.LocationOn
                }

                ElevatedFilterChip(
                    selected = isSelected,
                    onClick = { onSortChange(sortOption) },
                    label = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            // Show direction indicator only for selected chip
                            if (isSelected) {
                                Icon(
                                    imageVector = if (sortDirection == SortDirection.ASCENDING)
                                        Icons.Default.ArrowUpward
                                    else
                                        Icons.Default.ArrowDownward,
                                    contentDescription = if (sortDirection == SortDirection.ASCENDING)
                                        "Ascending"
                                    else
                                        "Descending",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    elevation = FilterChipDefaults.elevatedFilterChipElevation(
                        elevation = if (isSelected) 8.dp else 4.dp,
                        pressedElevation = 10.dp
                    ),
                    colors = FilterChipDefaults.elevatedFilterChipColors(
                        containerColor = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surface,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        labelColor = if (isSelected)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurface,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        iconColor = if (isSelected)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    border = if (!isSelected) FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = false,
                        borderColor = MaterialTheme.colorScheme.outlineVariant
                    ) else null
                )
            }
        }
    }
}

/**
 * Material 3 Expressive Connection Card
 * Large rounded corners (24.dp), clear layout with avatar, name, job title, date, and location
 */
@Composable
fun ConnectionCard(
    connection: Connection,
    onConnectionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        onClick = { onConnectionClick(connection.connectionId) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 4.dp,
            pressedElevation = 2.dp
        ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Row 1: Avatar + Name & Job Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Large Circular Avatar
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 4.dp
                ) {
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = connection.connectedUserName
                                .split(" ")
                                .mapNotNull { it.firstOrNull()?.uppercase() }
                                .take(2)
                                .joinToString(""),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Name and Job Title Column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = connection.connectedUserName.ifEmpty { "Unknown" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (connection.connectedUserDescription.isNotEmpty()) {
                        Text(
                            text = connection.connectedUserDescription,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Connection Method Badge
                Surface(
                    color = if (connection.connectionMethod == "NFC")
                        MaterialTheme.colorScheme.tertiaryContainer
                    else
                        MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = connection.connectionMethod,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (connection.connectionMethod == "NFC")
                            MaterialTheme.colorScheme.onTertiaryContainer
                        else
                            MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Row 2: Date and Location Icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Date Icon + Text
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = connection.getRelativeTimeString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Location Icon + Text
                if (connection.connectedUserLocation.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = connection.connectedUserLocation,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Empty State for Connections
 */
@Composable
fun ConnectionsEmptyState(
    isSearching: Boolean
) {
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
                        imageVector = if (isSearching) Icons.Default.SearchOff else Icons.Default.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = if (isSearching) "No matching connections" else "No connections yet",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isSearching)
                    "Try adjusting your search query"
                else
                    "Scan a QR code to add your first connection",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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

                        // Display social links from modern structure (only visible ones)
                        user.socialLinks.filter { it.isVisibleOnProfile }.forEach { link ->
                            DetailRow(link.platform.icon, "${link.label}: ${link.url}")
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
            description = data.optString("description", ""),
            location = data.optString("location", ""),
            socialLinks = emptyList() // Will be parsed if available in JSON
        )
    } catch (e: Exception) {
        Toast.makeText(context, "Invalid data format: ${e.message}", Toast.LENGTH_SHORT).show()
        null
    }
}

