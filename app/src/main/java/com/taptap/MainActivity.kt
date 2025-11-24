package com.taptap

import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.taptap.ui.dashboard.DashboardScreen
import com.taptap.ui.home.HomeScreen
import com.taptap.ui.profile.ProfileScreen
import com.taptap.ui.theme.TapTapTheme
import com.taptap.viewmodel.UserViewModel
import com.taptap.viewmodel.UserViewModelFactory

class MainActivity : ComponentActivity() {

    private var nfcAdapter: NfcAdapter? = null
    lateinit var userViewModel: UserViewModel
    private var currentIntent: MutableState<Intent?> = mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize viewmodel first
        val factory = UserViewModelFactory(applicationContext)
        userViewModel = ViewModelProvider(this, factory)[UserViewModel::class.java]

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        currentIntent.value = intent

        setContent {
            TapTapTheme {
                MainScreen(
                    userViewModel = userViewModel,
                    nfcAdapter = nfcAdapter,
                    currentIntent = currentIntent.value
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        currentIntent.value = intent
    }
}

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Filled.Home)
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Filled.Info)
    object Profile : Screen("profile", "Profile", Icons.Filled.Edit)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    userViewModel: UserViewModel,
    nfcAdapter: NfcAdapter?,
    currentIntent: Intent?
) {
    val navController = rememberNavController()
    val items = listOf(Screen.Home, Screen.Dashboard, Screen.Profile)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TapTap") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    userViewModel = userViewModel,
                    nfcAdapter = nfcAdapter
                )
            }
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    intent = currentIntent
                )
            }
            composable(Screen.Profile.route) {
                ProfileScreen(
                    userViewModel = userViewModel
                )
            }
        }
    }
}
