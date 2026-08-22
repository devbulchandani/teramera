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
import androidx.compose.runtime.mutableStateOf
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
        true -> MainApp(sessionViewModel)
    }
}

@HiltViewModel
class SessionViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val syncRepository: com.example.teramera.data.sync.SyncRepository,
) : ViewModel() {
    val isLoggedIn: StateFlow<Boolean?> = authRepository.isLoggedIn.stateIn(
        viewModelScope, SharingStarted.Eagerly, null,
    )

    fun refresh() {
        viewModelScope.launch { syncRepository.refreshNow() }
    }
}

@Composable
private fun MainApp(sessionViewModel: SessionViewModel) {
    // refresh server state on every entry into the app
    androidx.compose.runtime.LaunchedEffect(Unit) { sessionViewModel.refresh() }

    val navController = rememberNavController()
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
                HomeScreen(onOpenGroup = { groupId -> navController.navigate(Routes.groupDetail(groupId)) })
            }
            composable(Routes.GROUPS) {
                GroupsScreen(onOpenGroup = { groupId -> navController.navigate(Routes.groupDetail(groupId)) })
            }
            composable(Routes.GROUP_DETAIL) {
                GroupDetailScreen(
                    onBack = { navController.popBackStack() },
                    onAddExpense = { groupId ->
                        addExpenseGroupId = groupId
                        showAddExpense = true
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
