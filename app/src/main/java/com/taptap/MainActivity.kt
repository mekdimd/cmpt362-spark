package com.taptap

import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.taptap.ui.auth.ForgotPasswordScreen
import com.taptap.ui.auth.LoginScreen
import com.taptap.ui.auth.RegisterScreen
import com.taptap.ui.connection.ConnectionDetailScreen
import com.taptap.ui.dashboard.DashboardScreen
import com.taptap.ui.home.HomeScreen
import com.taptap.ui.map.MapScreen
import com.taptap.ui.profile.ProfileScreen
import com.taptap.ui.theme.TapTapTheme
import com.taptap.viewmodel.AuthViewModel
import com.taptap.viewmodel.ConnectionViewModel
import com.taptap.viewmodel.UserViewModel
import com.taptap.viewmodel.UserViewModelFactory

class MainActivity : ComponentActivity() {

    private var nfcAdapter: NfcAdapter? = null
    lateinit var userViewModel: UserViewModel
    lateinit var authViewModel: AuthViewModel
    lateinit var connectionViewModel: ConnectionViewModel
    private var currentIntent: MutableState<Intent?> = mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize viewmodels
        val factory = UserViewModelFactory(applicationContext)
        userViewModel = ViewModelProvider(this, factory)[UserViewModel::class.java]
        authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]
        connectionViewModel = ViewModelProvider(this)[ConnectionViewModel::class.java]

        // Initialize location service
        connectionViewModel.initializeLocationService(this)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        currentIntent.value = intent

        setContent {
            TapTapTheme {
                AppNavigation(
                    authViewModel = authViewModel,
                    userViewModel = userViewModel,
                    connectionViewModel = connectionViewModel,
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

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object ForgotPassword : Screen("forgot_password")
    object Main : Screen("main")
}

sealed class MainScreen(val route: String, val title: String, val icon: ImageVector) {
    object Home : MainScreen("home", "Home", Icons.Filled.Home)
    object Dashboard : MainScreen("dashboard", "Connections", Icons.Filled.People)
    object Map : MainScreen("map", "Map", Icons.Filled.Map)
    object Profile : MainScreen("profile", "Profile", Icons.Filled.AccountCircle)
    object ConnectionDetail : MainScreen("connection_detail/{connectionId}", "Connection", Icons.Filled.Person) {
        fun createRoute(connectionId: String) = "connection_detail/$connectionId"
    }
}

@Composable
fun AppNavigation(
    authViewModel: AuthViewModel,
    userViewModel: UserViewModel,
    connectionViewModel: ConnectionViewModel,
    nfcAdapter: NfcAdapter?,
    currentIntent: Intent?
) {
    val navController = rememberNavController()
    val isLoggedIn by authViewModel.isLoggedIn.observeAsState(false)
    val currentUser by authViewModel.currentUser.observeAsState()

    // Determine start destination based on auth state
    val startDestination = if (isLoggedIn) Screen.Main.route else Screen.Login.route

    // Initialize user profile when logged in
    LaunchedEffect(currentUser) {
        currentUser?.let { firebaseUser ->
            userViewModel.initializeUserProfile(
                userId = firebaseUser.uid,
                email = firebaseUser.email ?: "",
                displayName = firebaseUser.displayName ?: "User"
            )
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                authViewModel = authViewModel,
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                },
                onNavigateToForgotPassword = {
                    navController.navigate(Screen.ForgotPassword.route)
                },
                onLoginSuccess = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                authViewModel = authViewModel,
                onNavigateToLogin = {
                    navController.popBackStack()
                },
                onRegisterSuccess = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Register.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.ForgotPassword.route) {
            ForgotPasswordScreen(
                authViewModel = authViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Main.route) {
            MainScreenContent(
                authViewModel = authViewModel,
                userViewModel = userViewModel,
                connectionViewModel = connectionViewModel,
                nfcAdapter = nfcAdapter,
                currentIntent = currentIntent,
                onLogout = {
                    userViewModel.clearUserData()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Main.route) { inclusive = true }
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenContent(
    authViewModel: AuthViewModel,
    userViewModel: UserViewModel,
    connectionViewModel: ConnectionViewModel,
    nfcAdapter: NfcAdapter?,
    currentIntent: Intent?,
    onLogout: () -> Unit
) {
    val navController = rememberNavController()
    val items = listOf(MainScreen.Home, MainScreen.Dashboard,MainScreen.Map ,MainScreen.Profile)

    // Shared state for scanned user from HomeScreen to DashboardScreen
    var pendingScannedUser by remember { mutableStateOf<com.taptap.model.User?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔥 Spark") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = {
                        authViewModel.logoutUser()
                        onLogout()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Logout",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
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
            startDestination = MainScreen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(MainScreen.Home.route) {
                HomeScreen(
                    userViewModel = userViewModel,
                    nfcAdapter = nfcAdapter,
                    connectionViewModel = connectionViewModel,
                    onNavigateToDashboard = { scannedUser ->
                        // Store the scanned user and navigate to dashboard
                        pendingScannedUser = scannedUser
                        navController.navigate(MainScreen.Dashboard.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(MainScreen.Dashboard.route) {
                DashboardScreen(
                    intent = currentIntent,
                    connectionViewModel = connectionViewModel,
                    onNavigateToDetail = { connectionId ->
                        navController.navigate(MainScreen.ConnectionDetail.createRoute(connectionId))
                    },
                    scannedUserFromHome = pendingScannedUser,
                    onScannedUserHandled = {
                        // Clear the pending scanned user after it's been handled
                        pendingScannedUser = null
                    }
                )
            }

            composable(MainScreen.Map.route) {
                MapScreen(
                    connectionViewModel = connectionViewModel
                )
            }

            composable(MainScreen.Profile.route) {
                ProfileScreen(
                    userViewModel = userViewModel
                )
            }
            composable(MainScreen.ConnectionDetail.route) { backStackEntry ->
                val connectionId = backStackEntry.arguments?.getString("connectionId") ?: ""
                val connections by connectionViewModel.connections.observeAsState(emptyList())
                val connection = connections.find { it.connectionId == connectionId }

                if (connection != null) {
                    ConnectionDetailScreen(
                        connection = connection,
                        onBack = {
                            navController.popBackStack()
                        },
                        onRefresh = {
                            connectionViewModel.refreshConnectionProfile(connection)
                        },
                        onDelete = {
                            connectionViewModel.deleteConnection(connection.connectionId)
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
    }
}
