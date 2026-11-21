package com.example

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.model.VpnStatus
import com.example.ui.VpnViewModel
import com.example.ui.components.AtmosphericVpnBackground
import com.example.ui.screens.InspectorScreen
import com.example.ui.screens.ProfilesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.ShieldDashboardScreen
import com.example.ui.theme.MutigandaVpnTheme

object VpnRoutes {
    const val SHIELD = "shield"
    const val PROFILES = "profiles"
    const val INSPECTOR = "inspector"
    const val SETTINGS = "settings"
}

private data class NavDestinationItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MutigandaVpnApp()
        }
    }
}

@Composable
fun MutigandaVpnApp(vpnViewModel: VpnViewModel = viewModel()) {
    val uiState by vpnViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            vpnViewModel.startVpnConnection()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ ->
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            vpnViewModel.startVpnConnection()
        }
    }

    val requestConnectOrDisconnect: () -> Unit = {
        val status = uiState.telemetry.status
        if (status == VpnStatus.CONNECTED || status == VpnStatus.CONNECTING) {
            vpnViewModel.stopVpnConnection()
        } else {
            val needsNotificationPermission =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) != PackageManager.PERMISSION_GRANTED

            if (needsNotificationPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                val prepareIntent = VpnService.prepare(context)
                if (prepareIntent != null) {
                    vpnPermissionLauncher.launch(prepareIntent)
                } else {
                    vpnViewModel.startVpnConnection()
                }
            }
        }
    }

    LaunchedEffect(uiState.statusBannerMessage) {
        val msg = uiState.statusBannerMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            vpnViewModel.clearBanner()
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: VpnRoutes.SHIELD

    val navItems = remember {
        listOf(
            NavDestinationItem(
                route = VpnRoutes.SHIELD,
                labelRes = R.string.nav_shield,
                selectedIcon = Icons.Filled.Shield,
                unselectedIcon = Icons.Outlined.Shield,
                testTag = "nav_shield",
            ),
            NavDestinationItem(
                route = VpnRoutes.PROFILES,
                labelRes = R.string.nav_profiles,
                selectedIcon = Icons.Filled.Dns,
                unselectedIcon = Icons.Outlined.Dns,
                testTag = "nav_profiles",
            ),
            NavDestinationItem(
                route = VpnRoutes.INSPECTOR,
                labelRes = R.string.nav_inspector,
                selectedIcon = Icons.Filled.Analytics,
                unselectedIcon = Icons.Outlined.Analytics,
                testTag = "nav_inspector",
            ),
            NavDestinationItem(
                route = VpnRoutes.SETTINGS,
                labelRes = R.string.nav_settings,
                selectedIcon = Icons.Filled.Settings,
                unselectedIcon = Icons.Outlined.Settings,
                testTag = "nav_settings",
            ),
        )
    }

    val navigateTo: (String) -> Unit = { targetRoute ->
        navController.navigate(targetRoute) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    MutigandaVpnTheme(darkTheme = uiState.preferences.darkTheme) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavRail = maxWidth >= 600.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (!useNavRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        ) {
                            navItems.forEach { item ->
                                val selected = currentRoute == item.route
                                val label = stringResource(id = item.labelRes)
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTo(item.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = label,
                                        )
                                    },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag(item.testTag),
                                )
                            }
                        }
                    }
                },
            ) { innerPadding ->
                AtmosphericVpnBackground(
                    vpnStatus = uiState.telemetry.status,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (useNavRail) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                            ) {
                                navItems.forEach { item ->
                                    val selected = currentRoute == item.route
                                    val label = stringResource(id = item.labelRes)
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { navigateTo(item.route) },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                contentDescription = label,
                                            )
                                        },
                                        label = { Text(label) },
                                        modifier = Modifier.testTag(item.testTag),
                                    )
                                }
                            }
                        }

                        NavHost(
                            navController = navController,
                            startDestination = VpnRoutes.SHIELD,
                            modifier = Modifier.weight(1f),
                        ) {
                            composable(VpnRoutes.SHIELD) {
                                ShieldDashboardScreen(
                                    uiState = uiState,
                                    onToggleConnection = requestConnectOrDisconnect,
                                    onQuickSelectProfile = vpnViewModel::selectProfile,
                                    onOpenProfiles = { navigateTo(VpnRoutes.PROFILES) },
                                    onOpenInspector = { navigateTo(VpnRoutes.INSPECTOR) },
                                )
                            }
                        composable(VpnRoutes.PROFILES) {
                            ProfilesScreen(
                                uiState = uiState,
                                onSelectProfile = vpnViewModel::selectProfile,
                                onPingProfile = vpnViewModel::pingProfile,
                                onPingAllProfiles = vpnViewModel::pingAllProfiles,
                                onAddCustomProfile = vpnViewModel::addCustomProfile,
                                onImportShareLinks = vpnViewModel::importShareLinks,
                                onImportWireGuardUri = vpnViewModel::importWireGuardConfig,
                                onDeleteProfile = vpnViewModel::deleteCustomProfile,
                            )
                        }
                        composable(VpnRoutes.INSPECTOR) {
                            InspectorScreen(
                                uiState = uiState,
                                onRunDiagnostics = vpnViewModel::runDiagnostics,
                                onSearchQueryChange = vpnViewModel::updateAppsSearchQuery,
                                onToggleExcludedApp = vpnViewModel::toggleExcludedApp,
                            )
                        }
                            composable(VpnRoutes.SETTINGS) {
                                SettingsScreen(
                                    uiState = uiState,
                                    onSaveDnsOverride = vpnViewModel::updateDnsOverride,
                                    onSelectMtu = vpnViewModel::updateMtu,
                                    onToggleLanBypass = vpnViewModel::updateAllowLanBypass,
                                    onToggleDarkTheme = vpnViewModel::updateDarkTheme,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
