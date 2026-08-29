package com.example.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.QuickSyncBottomSheet
import com.example.ui.components.SyncStatusPill
import com.example.ui.theme.AppThemeMode
import com.example.util.AppLanguage
import com.example.util.Language
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.SyncViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

sealed class Screen(val route: String, val titleKey: String, val defaultTitle: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "nav_dashboard", "ড্যাশবোর্ড", Icons.Filled.Dashboard)
    object Students : Screen("students", "nav_students", "শিক্ষার্থী", Icons.Filled.People)
    object ToolsHub : Screen("tools_hub", "nav_tools_hub", "টুলস হাব", Icons.Filled.Widgets)
    object CustomFields : Screen("custom_fields", "nav_custom_fields", "ফিল্ড ও সূত্র", Icons.Filled.Tune)
    object Settings : Screen("settings", "nav_settings", "বিদ্যালয় ও সেটিংস", Icons.Filled.School)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenContainer(viewModel: MainViewModel) {
    val syncViewModel: SyncViewModel = viewModel()
    var showQuickSyncSheet by remember { mutableStateOf(false) }

    var routeHistory by remember { mutableStateOf(listOf("dashboard")) }
    val currentRoute = routeHistory.lastOrNull() ?: "dashboard"

    fun navigateTo(newRoute: String) {
        if (currentRoute != newRoute) {
            routeHistory = routeHistory + newRoute
        }
    }

    fun navigateBack(): Boolean {
        return if (routeHistory.size > 1) {
            routeHistory = routeHistory.dropLast(1)
            true
        } else {
            false
        }
    }

    val userMessage by viewModel.userMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val schoolInfo by viewModel.schoolInfo.collectAsState()
    val currentLanguage by viewModel.appLanguage.collectAsState()

    var backPressedOnce by remember { mutableStateOf(false) }

    LaunchedEffect(backPressedOnce) {
        if (backPressedOnce) {
            delay(2000L)
            backPressedOnce = false
        }
    }

    BackHandler(enabled = true) {
        if (currentRoute == Screen.Dashboard.route && routeHistory.size <= 1) {
            if (backPressedOnce) {
                (context as? Activity)?.finish()
            } else {
                backPressedOnce = true
                Toast.makeText(context, if (currentLanguage == Language.BANGLA) "অ্যাপ থেকে বের হতে আবার ব্যাক চাপুন" else "Press back again to exit", Toast.LENGTH_SHORT).show()
            }
        } else {
            val handled = navigateBack()
            if (!handled) {
                if (backPressedOnce) {
                    (context as? Activity)?.finish()
                } else {
                    backPressedOnce = true
                    Toast.makeText(context, if (currentLanguage == Language.BANGLA) "অ্যাপ থেকে বের হতে আবার ব্যাক চাপুন" else "Press back again to exit", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    val bottomNavItems = listOf(
        Screen.Dashboard,
        Screen.Students,
        Screen.ToolsHub,
        Screen.Settings
    )

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // 1. App name "অন্বেষা"
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        ) {
                            Text(
                                text = "অন্বেষা",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // 2. সার্চ বার
                        val searchQuery by viewModel.searchQuery.collectAsState()
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Search,
                                    contentDescription = "Search",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = {
                                        viewModel.searchQuery.value = it
                                        if (it.isNotBlank() && currentRoute != "students") {
                                            navigateTo("students")
                                        }
                                    },
                                    singleLine = true,
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    textStyle = TextStyle(
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    decorationBox = { innerTextField ->
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = "শিক্ষার্থী খুঁজুন...",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                        innerTextField()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("main_top_search_bar")
                                )
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { viewModel.searchQuery.value = "" },
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Clear search",
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                actions = {
                    // Sync Status Indicator Pill
                    SyncStatusPill(
                        syncViewModel = syncViewModel,
                        onOpenSyncSheet = { showQuickSyncSheet = true },
                        modifier = Modifier.padding(end = 4.dp)
                    )

                    // 3. Night/Light Toggle
                    val currentThemeMode by viewModel.appThemeMode.collectAsState()
                    val isSystemDark = isSystemInDarkTheme()
                    val isDarkMode = when (currentThemeMode) {
                        AppThemeMode.DARK -> true
                        AppThemeMode.LIGHT -> false
                        AppThemeMode.SYSTEM -> isSystemDark
                    }

                    IconButton(
                        onClick = {
                            val nextMode = if (isDarkMode) AppThemeMode.LIGHT else AppThemeMode.DARK
                            viewModel.setAppThemeMode(nextMode)
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("top_theme_toggle_btn")
                    ) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = if (isDarkMode) "Light Mode" else "Dark Mode",
                            tint = if (isDarkMode) Color(0xFFFFD54F) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 4. Settings button
                    IconButton(
                        onClick = { navigateTo("settings") },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("top_settings_btn")
                    ) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("main_bottom_nav")
            ) {
                bottomNavItems.forEach { screen ->
                    val selected = currentRoute == screen.route
                    val label = AppLanguage.t(screen.titleKey, currentLanguage)
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navigateTo(screen.route) },
                        icon = { Icon(screen.icon, contentDescription = label, modifier = Modifier.size(22.dp)) },
                        label = { Text(label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.testTag("nav_item_${screen.route}")
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentRoute) {
                Screen.Dashboard.route -> DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToSection = { route -> navigateTo(route) }
                )
                Screen.Students.route -> StudentScreen(
                    viewModel = viewModel,
                    onNavigateToDashboard = { navigateTo(Screen.Dashboard.route) }
                )
                Screen.ToolsHub.route -> ToolsHubScreen(
                    viewModel = viewModel,
                    onNavigateToDashboard = { navigateTo(Screen.Dashboard.route) }
                )
                "tools_hub/admit_card" -> ToolsHubScreen(
                    viewModel = viewModel,
                    initialToolRoute = "admit_card_maker",
                    onNavigateToDashboard = { navigateTo(Screen.Dashboard.route) }
                )
                "tools_hub/certificate_maker" -> ToolsHubScreen(
                    viewModel = viewModel,
                    initialToolRoute = "certificate_maker",
                    onNavigateToDashboard = { navigateTo(Screen.Dashboard.route) }
                )
                Screen.CustomFields.route -> CustomFieldsScreen(
                    viewModel = viewModel,
                    onNavigateToStudents = { navigateTo(Screen.Students.route) },
                    onNavigateBack = { navigateTo(Screen.Settings.route) }
                )
                Screen.Settings.route -> SettingsScreen(
                    viewModel = viewModel,
                    syncViewModel = syncViewModel,
                    onNavigateToCustomFields = { navigateTo(Screen.CustomFields.route) },
                    onNavigateToDashboard = { navigateTo(Screen.Dashboard.route) }
                )
                else -> DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToSection = { route -> navigateTo(route) }
                )
            }
        }

        if (showQuickSyncSheet) {
            QuickSyncBottomSheet(
                syncViewModel = syncViewModel,
                onDismiss = { showQuickSyncSheet = false },
                onNavigateToSettings = {
                    showQuickSyncSheet = false
                    navigateTo(Screen.Settings.route)
                }
            )
        }
    }
}
