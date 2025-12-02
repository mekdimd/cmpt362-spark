package com.taptap.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.taptap.model.SocialLink
import com.taptap.model.User
import com.taptap.viewmodel.UserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    userViewModel: UserViewModel,
    onNavigateBack: () -> Unit
) {
    val currentUser by userViewModel.currentUser.observeAsState(User())
    val socialLinks by userViewModel.socialLinks.observeAsState(emptyList())

    var showAddLinkSheet by remember { mutableStateOf(false) }
    var editingLink by remember { mutableStateOf<SocialLink?>(null) }

    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }

    LaunchedEffect(socialLinks) {
        android.util.Log.d("EditProfileScreen", "socialLinks updated: ${socialLinks.size} links")
        socialLinks.forEachIndexed { index, link ->
            android.util.Log.d("EditProfileScreen", "  Link $index: ${link.platform.displayName} - ${link.label} - ${link.url} - visible=${link.isVisibleOnProfile}")
        }
    }

    LaunchedEffect(currentUser) {
        android.util.Log.d("EditProfileScreen", "currentUser updated: ${currentUser.socialLinks.size} links in user")
        fullName = currentUser.fullName
        email = currentUser.email
        phone = currentUser.phone
        description = currentUser.description
        location = currentUser.location
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 56.dp, end = 24.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Edit Profile",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Personal Information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            ProfileTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = "Full Name",
                leadingIcon = Icons.Default.Person
            )

            Spacer(modifier = Modifier.height(12.dp))

            ProfileTextField(
                value = email,
                onValueChange = { email = it },
                label = "Email",
                leadingIcon = Icons.Default.Email,
                keyboardType = KeyboardType.Email
            )

            Spacer(modifier = Modifier.height(12.dp))

            ProfileTextField(
                value = phone,
                onValueChange = { phone = it },
                label = "Phone",
                leadingIcon = Icons.Default.Phone,
                keyboardType = KeyboardType.Phone
            )

            Spacer(modifier = Modifier.height(12.dp))

            ProfileTextField(
                value = description,
                onValueChange = { description = it },
                label = "Bio",
                leadingIcon = Icons.Default.Description
            )

            Spacer(modifier = Modifier.height(12.dp))

            ProfileTextField(
                value = location,
                onValueChange = { location = it },
                label = "Location",
                leadingIcon = Icons.Default.LocationOn
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Social Links",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Button(
                    onClick = { showAddLinkSheet = true },
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Link")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (socialLinks.isEmpty()) {
                EmptyLinksPlaceholder()
            } else {
                socialLinks.forEach { link ->
                    SocialLinkCard(
                        link = link,
                        onToggleVisibility = { userViewModel.toggleVisibility(link.id) },
                        onEdit = { editingLink = link },
                        onDelete = { userViewModel.deleteSocialLink(link.id) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    userViewModel.saveUserProfileWithLinks(
                        fullName = fullName,
                        phone = phone,
                        email = email,
                        description = description,
                        location = location,
                        socialLinks = socialLinks
                    )
                    onNavigateBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Save Changes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showAddLinkSheet) {
        AddLinkBottomSheet(
            onDismiss = { showAddLinkSheet = false },
            onAddLink = { link ->
                userViewModel.addSocialLink(link)
                showAddLinkSheet = false
            }
        )
    }

    if (editingLink != null) {
        EditLinkBottomSheet(
            link = editingLink!!,
            onDismiss = { editingLink = null },
            onSaveLink = { updatedLink ->
                userViewModel.updateSocialLink(editingLink!!.id, updatedLink)
                editingLink = null
            },
            onDelete = {
                userViewModel.deleteSocialLink(editingLink!!.id)
                editingLink = null
            }
        )
    }
}

@Composable
fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        minLines = minLines,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
    )
}

@Composable
fun SocialLinkCard(
    link: SocialLink,
    onToggleVisibility: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = link.platform.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = link.label.ifEmpty { link.platform.displayName },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = link.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            Switch(
                checked = link.isVisibleOnProfile,
                onCheckedChange = { onToggleVisibility() }
            )
        }
    }
}

@Composable
fun EmptyLinksPlaceholder() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Link,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No social links yet",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Add your social profiles to share with connections",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLinkBottomSheet(
    onDismiss: () -> Unit,
    onAddLink: (SocialLink) -> Unit
) {
    var selectedPlatform by remember { mutableStateOf(SocialLink.SocialPlatform.LINKEDIN) }
    var input by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(selectedPlatform) {
        label = selectedPlatform.displayName
        input = ""
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {

            Text(
                text = "Add Social Link",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Text(
                text = "Platform",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SocialLink.SocialPlatform.entries.forEach { platform ->
                    FilterChip(
                        selected = selectedPlatform == platform,
                        onClick = { selectedPlatform = platform },
                        label = { Text(platform.displayName) },
                        leadingIcon = {
                            Icon(
                                platform.icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (selectedPlatform == SocialLink.SocialPlatform.CUSTOM) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = {
                    Text(
                        when (selectedPlatform) {
                            SocialLink.SocialPlatform.CUSTOM, SocialLink.SocialPlatform.WEBSITE -> "URL"
                            else -> "Username"
                        }
                    )
                },
                placeholder = { Text(selectedPlatform.placeholder) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                leadingIcon = {
                    Icon(selectedPlatform.icon, contentDescription = null)
                },
                supportingText = {
                    if (input.isNotEmpty() && selectedPlatform != SocialLink.SocialPlatform.CUSTOM) {
                        val previewUrl = SocialLink.SocialPlatform.generateUrl(selectedPlatform, input)
                        Text(
                            text = previewUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (input.isNotEmpty()) {
                        val finalUrl = SocialLink.SocialPlatform.generateUrl(selectedPlatform, input)
                        onAddLink(
                            SocialLink(
                                platform = selectedPlatform,
                                url = finalUrl,
                                label = if (selectedPlatform == SocialLink.SocialPlatform.CUSTOM) label else selectedPlatform.displayName,
                                isVisibleOnProfile = true
                            )
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = input.isNotEmpty() && (selectedPlatform != SocialLink.SocialPlatform.CUSTOM || label.isNotEmpty())
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Add Link",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditLinkBottomSheet(
    link: SocialLink,
    onDismiss: () -> Unit,
    onSaveLink: (SocialLink) -> Unit,
    onDelete: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var label by remember { mutableStateOf(link.label) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(link) {
        input = when (link.platform) {
            SocialLink.SocialPlatform.CUSTOM, SocialLink.SocialPlatform.WEBSITE -> link.url
            else -> link.url.removePrefix(link.platform.urlPrefix)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {

            Text(
                text = "Edit Social Link",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Text(
                text = "Platform",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        link.platform.icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = link.platform.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (link.platform == SocialLink.SocialPlatform.CUSTOM) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = {
                    Text(
                        when (link.platform) {
                            SocialLink.SocialPlatform.CUSTOM, SocialLink.SocialPlatform.WEBSITE -> "URL"
                            else -> "Username"
                        }
                    )
                },
                placeholder = { Text(link.platform.placeholder) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                leadingIcon = {
                    Icon(link.platform.icon, contentDescription = null)
                },
                supportingText = {
                    if (input.isNotEmpty() && link.platform != SocialLink.SocialPlatform.CUSTOM) {
                        val previewUrl = SocialLink.SocialPlatform.generateUrl(link.platform, input)
                        Text(
                            text = previewUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (input.isNotEmpty()) {
                        val finalUrl = SocialLink.SocialPlatform.generateUrl(link.platform, input)
                        onSaveLink(
                            link.copy(
                                url = finalUrl,
                                label = if (link.platform == SocialLink.SocialPlatform.CUSTOM) label else link.platform.displayName
                            )
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = input.isNotEmpty() && (link.platform != SocialLink.SocialPlatform.CUSTOM || label.isNotEmpty())
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Save Changes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Delete Link",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Link") },
            text = { Text("Are you sure you want to delete this link?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                        onDismiss()
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
