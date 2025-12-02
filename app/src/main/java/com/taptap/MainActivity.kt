package com.taptap

import android.Manifest
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.taptap.notification.NotificationHelper
import com.taptap.ui.auth.ForgotPasswordScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    private lateinit var notificationHelper: NotificationHelper

    // Notification permission launcher for Android 13+
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d("MainActivity", "Notification permission granted")
        } else {
            Log.d("MainActivity", "Notification permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize viewmodels
        val factory = UserViewModelFactory(applicationContext)
        userViewModel = ViewModelProvider(this, factory)[UserViewModel::class.java]
        authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]
        connectionViewModel = ViewModelProvider(this)[ConnectionViewModel::class.java]

        // Initialize services
        connectionViewModel.initializeLocationService(this)
        notificationHelper = NotificationHelper(this)

        // Request notification permission for Android 13+
        requestNotificationPermission()

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        currentIntent.value = intent

        // Handle deep link from notification
        handleDeepLink(intent)

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
        handleDeepLink(intent)
    }

    /**
     * Request notification permission for Android 13+
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!notificationHelper.hasNotificationPermission()) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * Handle deep links from notifications and NFC
     */
    private fun handleDeepLink(intent: Intent?) {
        intent?.data?.let { uri ->
            Log.d("MainActivity", "Deep link received: $uri")

            // Handle connection deep link: myapp://connection/{userId}
            if (uri.scheme == "myapp" && uri.host == "connection") {
                val userId = uri.pathSegments.getOrNull(0)
                val userName = intent.getStringExtra(NotificationHelper.EXTRA_USER_NAME)

                Log.d("MainActivity", "Opening connection for user: $userName (ID: $userId)")

                if (userId != null) {
                    // Find the connection with this user ID using lifecycleScope
                    lifecycleScope.launch {
                        try {
                            // Load connections first if not loaded
                            val currentUserId = authViewModel.currentUser.value?.uid
                            if (currentUserId != null) {
                                connectionViewModel.loadConnections(currentUserId)
                            }

                            // Wait a bit for connections to load
                            delay(500)

                            val connections = connectionViewModel.connections.value ?: emptyList()
                            val connection = connections.find { it.connectedUserId == userId }

                            if (connection != null) {
                                Log.d("MainActivity", "Found connection ID: ${connection.connectionId}")
                                // Store pending connection ID to navigate after UI is ready
                                // You'll handle this in MainScreenContent
                            } else {
                                Log.w("MainActivity", "Connection not found for user ID: $userId")
                            }
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error handling deep link", e)
                        }
                    }
                }
            }

            // Handle NFC deep link: taptap://connect/{userId}
            if (uri.scheme == "taptap" && uri.host == "connect") {
                val userId = uri.pathSegments.getOrNull(0)
                Log.d("MainActivity", "NFC deep link - connecting with user: $userId")

                if (userId != null) {
                    // This will be handled in MainScreenContent
                    // The currentIntent state will trigger navigation
                }
            }
        }
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
    object EditProfile : MainScreen("edit_profile", "Edit Profile", Icons.Filled.Edit)
    object ConnectionDetail :
        MainScreen("connection_detail/{connectionId}", "Connection", Icons.Filled.Person) {
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
    val items = listOf(MainScreen.Home, MainScreen.Dashboard, MainScreen.Map, MainScreen.Profile)
    val currentUser by authViewModel.currentUser.observeAsState()
    val connections by connectionViewModel.connections.observeAsState(emptyList())

    // Shared state for scanned user from HomeScreen to DashboardScreen
    var pendingScannedUser by remember { mutableStateOf<com.taptap.model.User?>(null) }

    // Handle deep link navigation from notifications and NFC
    LaunchedEffect(currentIntent) {
        currentIntent?.data?.let { uri ->
            // Handle notification connection deep link: myapp://connection/{userId}
            if (uri.scheme == "myapp" && uri.host == "connection") {
                val userId = uri.pathSegments.getOrNull(0)
                if (userId != null) {
                    // Load connections if not already loaded
                    currentUser?.uid?.let { currentUserId ->
                        if (connections.isEmpty()) {
                            connectionViewModel.loadConnections(currentUserId)
                        }

                        // Wait for connections to load
                        delay(500)

                        // Find the connection
                        val connection = connectionViewModel.connections.value?.find {
                            it.connectedUserId == userId
                        }

                        if (connection != null) {
                            Log.d("MainScreenContent", "Navigating to connection: ${connection.connectionId}")
                            // Navigate to the connection detail screen
                            navController.navigate(
                                MainScreen.ConnectionDetail.createRoute(connection.connectionId)
                            ) {
                                // Clear the back stack to dashboard
                                popUpTo(MainScreen.Dashboard.route) {
                                    inclusive = false
                                }
                                launchSingleTop = true
                            }
                        } else {
                            Log.w("MainScreenContent", "Connection not found for userId: $userId")
                        }
                    }
                }
            }

            // Handle NFC deep link: taptap://connect/{userId}
            if (uri.scheme == "taptap" && uri.host == "connect") {
                val userId = uri.pathSegments.getOrNull(0)
                if (userId != null) {
                    Log.d("MainScreenContent", "NFC tap detected - userId: $userId")

                    currentUser?.uid?.let { currentUserId ->
                        // Load user profile and potentially create connection
                        userViewModel.getUserFromFirestore(userId) { user ->
                            if (user != null) {
                                pendingScannedUser = user
                                // Navigate to Dashboard where the user can confirm the connection
                                navController.navigate(MainScreen.Dashboard.route) {
                                    popUpTo(MainScreen.Home.route) {
                                        inclusive = false
                                    }
                                    launchSingleTop = true
                                }
                            } else {
                                Log.w("MainScreenContent", "User not found for NFC userId: $userId")
                            }
                        }
                    }
                }
            }
        }
    }

    // Check if we're on the detail screen
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isDetailScreen = currentRoute?.startsWith("connection_detail") == true

    Scaffold(
        topBar = {
            if (!isDetailScreen) {
                TopAppBar(
                    title = { Text("Spark") },
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
            }
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
                            // If already on this screen, pop to start destination
                            val isCurrentScreen = currentDestination?.route == screen.route

                            if (isCurrentScreen) {
                                // Navigate to the home of this section (pop to self)
                                navController.navigate(screen.route) {
                                    popUpTo(screen.route) {
                                        inclusive = true
                                    }
                                    launchSingleTop = true
                                }
                            } else {
                                // Normal navigation
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = MainScreen.Home.route,
                modifier = if (!isDetailScreen) Modifier.padding(innerPadding) else Modifier.fillMaxSize()
            ) {
                composable(MainScreen.Home.route) {
                    HomeScreen(
                        userViewModel = userViewModel,
                        nfcAdapter = nfcAdapter,
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
                            navController.navigate(
                                MainScreen.ConnectionDetail.createRoute(
                                    connectionId
                                )
                            )
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
                        userViewModel = userViewModel,
                        onNavigateToEditProfile = {
                            navController.navigate(MainScreen.EditProfile.route)
                        }
                    )
                }


                composable(MainScreen.EditProfile.route) {
                    com.taptap.ui.settings.EditProfileScreen(
                        userViewModel = userViewModel,
                        onNavigateBack = {
                            navController.popBackStack()
                        }
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
}
