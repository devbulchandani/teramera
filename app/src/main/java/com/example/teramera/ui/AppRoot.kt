package com.example.teramera.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.teramera.BuildConfig
import com.example.teramera.core.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.teramera.ui.activity.ActivityScreen
import com.example.teramera.ui.addexpense.AddExpenseSheet
import com.example.teramera.ui.auth.LoginScreen
import com.example.teramera.ui.components.ActivityIcon
import com.example.teramera.ui.components.GroupsIcon
import com.example.teramera.ui.components.HomeIcon
import com.example.teramera.ui.components.SettleIcon
import com.example.teramera.ui.home.HomeScreen
import com.example.teramera.ui.groups.GroupDetailScreen
import com.example.teramera.ui.groups.GroupsScreen
import com.example.teramera.ui.settle.SettleScreen

object Routes {
    const val HOME = "home"
    const val GROUPS = "groups"
    const val GROUP_DETAIL = "groups/{groupId}"
    const val ACTIVITY = "activity"
    const val SETTLE = "settle"

    fun groupDetail(groupId: String) = "groups/$groupId"
}

private data class NavItem(
    val route: String,
    val label: String,
    val icon: @Composable (Color) -> Unit,
)

@Composable
fun AppRoot() {
    val sessionViewModel: SessionViewModel = hiltViewModel()
    val loggedIn by sessionViewModel.isLoggedIn.collectAsStateWithLifecycle(initialValue = null)

    when (loggedIn) {
        null -> Box(Modifier.fillMaxSize()) // restoring session
        false -> LoginScreen(onLoggedIn = { /* isLoggedIn flow flips the gate */ })
        true -> MainApp(loggedIn, sessionViewModel)
    }
}

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncRepository: com.example.teramera.data.sync.SyncRepository,
    private val updateManager: com.example.teramera.core.update.AppUpdateManager,
) : ViewModel() {

    private val _availableUpdate = MutableStateFlow<com.example.teramera.core.update.UpdateInfo?>(null)
    val availableUpdate: StateFlow<com.example.teramera.core.update.UpdateInfo?> = _availableUpdate
    val isLoggedIn: StateFlow<Boolean?> = authRepository.isLoggedIn.stateIn(
        viewModelScope, SharingStarted.Eagerly, null,
    )

    fun refresh() {
        viewModelScope.launch {
            syncRepository.refreshNow()
            _availableUpdate.value = updateManager.checkForUpdate(BuildConfig.VERSION_CODE)
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onDone()
        }
    }

    fun dismissUpdate() {
        _availableUpdate.value = null
    }

    fun startUpdate(apkUrl: String) = updateManager.downloadAndInstall(apkUrl)

    fun joinGroup(groupId: String, onJoined: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { syncRepository.joinGroup(groupId) }
            syncRepository.refreshNow()
            onJoined(groupId)
        }
    }
}

@Composable
private fun MainApp(loggedIn: Boolean?, sessionViewModel: SessionViewModel) {
    // refresh server state on every entry into the app + check for updates
    val availableUpdate by sessionViewModel.availableUpdate.collectAsState()
    androidx.compose.runtime.LaunchedEffect(Unit) { sessionViewModel.refresh() }

    availableUpdate?.let { update ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { sessionViewModel.dismissUpdate() },
            shape = MaterialTheme.shapes.large,
            title = { Text("Update teramera") },
            text = { Text("Version ${update.versionName} is available. Update for the latest fixes and features.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    sessionViewModel.dismissUpdate()
                    sessionViewModel.startUpdate(update.apkUrl)
                }) { Text("Update", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { sessionViewModel.dismissUpdate() }) {
                    Text("Later")
                }
            },
        )
    }

    val navController = rememberNavController()

    // ---- group invite deep links (teramera://invite/<id> or https://…/invite/<id>) ----
    val activity = androidx.compose.ui.platform.LocalContext.current as? androidx.activity.ComponentActivity
    var pendingInvite by rememberSaveable { mutableStateOf(activity?.intent?.let { extractInviteId(it) }) }
    val newIntentListener = remember {
        androidx.core.util.Consumer<android.content.Intent> { intent ->
            extractInviteId(intent)?.let { pendingInvite = it }
        }
    }
    androidx.compose.runtime.DisposableEffect(activity) {
        activity?.addOnNewIntentListener(newIntentListener)
        onDispose { activity?.removeOnNewIntentListener(newIntentListener) }
    }
    androidx.compose.runtime.LaunchedEffect(loggedIn, pendingInvite) {
        val groupId = pendingInvite
        if (loggedIn == true && groupId != null) {
            sessionViewModel.joinGroup(groupId) { id ->
                navController.navigate(Routes.groupDetail(id))
            }
            pendingInvite = null
        }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.HOME
    var showAddExpense by rememberSaveable { mutableStateOf(false) }
    var addExpenseGroupId by rememberSaveable { mutableStateOf<String?>(null) }

    val navItems = listOf(
        NavItem(Routes.HOME, "Home") { tint -> HomeIcon(tint = tint) },
        NavItem(Routes.GROUPS, "Groups") { tint -> GroupsIcon(tint = tint) },
        NavItem(Routes.ACTIVITY, "Activity") { tint -> ActivityIcon(tint = tint) },
        NavItem(Routes.SETTLE, "Settle") { tint -> SettleIcon(tint = tint) },
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                navItems.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { item.icon(MaterialTheme.colorScheme.onSurface) },
                        label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentRoute == Routes.HOME) {
                ExtendedFloatingActionButton(
                    onClick = { showAddExpense = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                ) {
                    Text("+  Add expense", style = MaterialTheme.typography.labelLarge)
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenGroup = { groupId -> navController.navigate(Routes.groupDetail(groupId)) },
                    onLogout = {
                        sessionViewModel.logout {
                            navController.navigate(Routes.HOME) { popUpTo(0) }
                        }
                    },
                )
            }
            composable(Routes.GROUPS) {
                GroupsScreen(onOpenGroup = { groupId -> navController.navigate(Routes.groupDetail(groupId)) })
            }
            composable(Routes.GROUP_DETAIL) {
                val context = androidx.compose.ui.platform.LocalContext.current
                GroupDetailScreen(
                    onBack = { navController.popBackStack() },
                    onAddExpense = { groupId ->
                        addExpenseGroupId = groupId
                        showAddExpense = true
                    },
                    onShareGroup = { groupName, groupId ->
                        val link = "https://teramera-api.devbulchandani876.workers.dev/invite/$groupId"
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                android.content.Intent.EXTRA_TEXT,
                                "Join my \"$groupName\" group on teramera — we split expenses fairly: $link"
                            )
                        }
                        context.startActivity(android.content.Intent.createChooser(send, "Share group invite"))
                    },
                )
            }
            composable(Routes.ACTIVITY) { ActivityScreen() }
            composable(Routes.SETTLE) { SettleScreen() }
        }

        if (showAddExpense) {
            AddExpenseSheet(
                onDismiss = { showAddExpense = false },
                fixedGroupId = addExpenseGroupId,
            )
        }
    }
}

@Composable
private fun Placeholder(title: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            "Coming next",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun extractInviteId(intent: android.content.Intent?): String? {
    val uri = intent?.data ?: return null
    return when {
        uri.scheme == "teramera" && uri.host == "invite" -> uri.lastPathSegment
        uri.scheme == "https" && uri.path?.startsWith("/invite") == true -> uri.lastPathSegment
        else -> null
    }
}
