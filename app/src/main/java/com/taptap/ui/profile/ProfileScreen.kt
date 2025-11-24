package com.taptap.ui.profile

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.taptap.model.User
import com.taptap.viewmodel.UserViewModel

@Composable
fun ProfileScreen(
    userViewModel: UserViewModel
) {
    val context = LocalContext.current
    val currentUser by userViewModel.currentUser.observeAsState(User())

    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var linkedIn by remember { mutableStateOf("") }
    var github by remember { mutableStateOf("") }
    var instagram by remember { mutableStateOf("") }
    var website by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }

    LaunchedEffect(currentUser) {
        fullName = currentUser.fullName
        email = currentUser.email
        phone = currentUser.phone
        linkedIn = currentUser.linkedIn
        github = currentUser.github
        instagram = currentUser.instagram
        website = currentUser.website
        description = currentUser.description
        location = currentUser.location
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Edit Your Profile",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it },
            label = { Text("Full Name") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Phone") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = linkedIn,
            onValueChange = { linkedIn = it },
            label = { Text("LinkedIn") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = github,
            onValueChange = { github = it },
            label = { Text("GitHub") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = instagram,
            onValueChange = { instagram = it },
            label = { Text("Instagram") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = website,
            onValueChange = { website = it },
            label = { Text("Website") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            minLines = 2,
            maxLines = 4
        )

        OutlinedTextField(
            value = location,
            onValueChange = { location = it },
            label = { Text("Location") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            singleLine = true
        )

        FilledTonalButton(
            onClick = {
                if (fullName.isEmpty()) {
                    Toast.makeText(context, "Please enter your full name", Toast.LENGTH_SHORT)
                        .show()
                } else {
                    userViewModel.saveUserProfile(
                        fullName,
                        phone,
                        email,
                        linkedIn,
                        github,
                        instagram,
                        website,
                        description,
                        location
                    )
                    Toast.makeText(
                        context,
                        "Profile saved! Changes will show in Share tab",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Save Profile", style = MaterialTheme.typography.titleMedium)
        }
    }
}
