package com.example.teramera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.teramera.ui.components.PlusIcon
import com.example.teramera.ui.theme.Ink900
import com.example.teramera.ui.theme.Paper000
import com.example.teramera.ui.theme.Paper100
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
import com.example.teramera.ui.components.Baanto
import com.example.teramera.ui.components.BaantoState
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
    val availableUpdate by sessionViewModel.availableUpdate.collectAsStateWithLifecycle()

    // Update checks must not depend on being signed in — a broken sign-in is
    // exactly when a newer build matters most.
    androidx.compose.runtime.LaunchedEffect(Unit) { sessionViewModel.checkForUpdate() }

    when (loggedIn) {
        null -> Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Baanto(state = BaantoState.Thinking, size = 72.dp, animate = true)
            Spacer(Modifier.height(14.dp))
            Text(
                "Loading…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        false -> LoginScreen(onLoggedIn = { /* isLoggedIn flow flips the gate */ })
        true -> MainApp(loggedIn, sessionViewModel)
    }

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
}

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncRepository: com.example.teramera.data.sync.SyncRepository,
    private val updateManager: com.example.teramera.core.update.AppUpdateManager,
    private val deviceTokenUploader: com.example.teramera.core.push.DeviceTokenUploader,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
) : ViewModel() {

    private val _availableUpdate = MutableStateFlow<com.example.teramera.core.update.UpdateInfo?>(null)
    val availableUpdate: StateFlow<com.example.teramera.core.update.UpdateInfo?> = _availableUpdate
    val isLoggedIn: StateFlow<Boolean?> = authRepository.isLoggedIn.stateIn(
        viewModelScope, SharingStarted.Eagerly, null,
    )

    fun refresh() {
        viewModelScope.launch {
            syncRepository.refreshNow()
            registerPushToken()
        }
    }

    /** Silent update check that runs even before sign-in. */
    fun checkForUpdate() {
        viewModelScope.launch {
            _availableUpdate.value = updateManager.checkForUpdate(BuildConfig.VERSION_CODE)
        }
    }

    /** Registers the FCM token once Firebase is configured and we have a session. */
    private fun registerPushToken() {
        runCatching {
            if (com.google.firebase.FirebaseApp.getApps(appContext).isEmpty()) return
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    viewModelScope.launch { deviceTokenUploader.upload(token) }
                }
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
    androidx.compose.runtime.LaunchedEffect(Unit) { sessionViewModel.refresh() }

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
    // invites require explicit confirmation — a malicious app could fire the deep link
    val uuidLike = remember { Regex("^[0-9a-fA-F-]{8,64}$") }
    val validInvite = pendingInvite?.takeIf { uuidLike.matches(it) }
    if (loggedIn == true && validInvite != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                pendingInvite = null
            },
            shape = MaterialTheme.shapes.large,
            title = { Text("Join group?") },
            text = { Text("You've been invited to a teramera group. Your name and balances will be visible to its members.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val groupId = validInvite
                    pendingInvite = null
                    sessionViewModel.joinGroup(groupId) { id ->
                        navController.navigate(Routes.groupDetail(id))
                    }
                }) { Text("Join", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { pendingInvite = null }) { Text("Not now") }
            },
        )
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.HOME
    var showAddExpense by rememberSaveable { mutableStateOf(false) }
    var addExpenseGroupId by rememberSaveable { mutableStateOf<String?>(null) }

    val navItems = remember {
        listOf(
            NavItem(Routes.HOME, "Home") { tint -> HomeIcon(tint = tint) },
            NavItem(Routes.GROUPS, "Groups") { tint -> GroupsIcon(tint = tint) },
            NavItem(Routes.ACTIVITY, "Activity") { tint -> ActivityIcon(tint = tint) },
            NavItem(Routes.SETTLE, "Settle") { tint -> SettleIcon(tint = tint) },
        )
    }
    val onGroupDetail = currentRoute.startsWith("groups/")
    fun isSelected(route: String): Boolean = when (route) {
        // drilling into a group keeps its parent tab highlighted
        Routes.GROUPS -> currentRoute == Routes.GROUPS || onGroupDetail
        else -> currentRoute == route
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // Headers own the status bar and the floating pill owns the nav bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // group detail brings its own action bar; two stacked bars waste the screen
            if (!onGroupDetail) {
                FloatingNav(
                    items = navItems,
                    isSelected = { isSelected(it) },
                    showAdd = currentRoute == Routes.HOME || currentRoute == Routes.GROUPS,
                    onSelect = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onAdd = {
                        // a group picked by an earlier group-screen add must not leak in
                        addExpenseGroupId = null
                        showAddExpense = true
                    },
                )
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

/**
 * Floating ink pill. Four tabs, 44dp minimum targets, cream inactive and
 * peach active. The cream FAB overlaps the pill's top edge.
 */
@Composable
private fun FloatingNav(
    items: List<NavItem>,
    isSelected: (String) -> Boolean,
    showAdd: Boolean,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val inactive = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.78f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 18.dp)
            .height(96.dp),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .shadow(18.dp, CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.inverseSurface)
                .heightIn(min = 64.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val selected = isSelected(item.route)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(CircleShape)
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { onSelect(item.route) },
                        )
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    item.icon(if (selected) MaterialTheme.colorScheme.primary else inactive)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.primary else inactive,
                    )
                }
            }
        }
        if (showAdd) {
            FloatingActionButton(
                onClick = onAdd,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 6.dp)
                    .size(56.dp)
                    .shadow(14.dp, CircleShape)
                    .semantics { contentDescription = "Add expense" },
                shape = CircleShape,
                containerColor = Paper000,
                contentColor = Ink900,
            ) {
                PlusIcon(tint = Ink900, size = 24.dp)
            }
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
