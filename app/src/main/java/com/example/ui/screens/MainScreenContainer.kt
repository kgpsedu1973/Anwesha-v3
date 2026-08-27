package com.example.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.AppLanguage
import com.example.util.Language
import com.example.viewmodel.MainViewModel
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (currentLanguage == Language.BANGLA) "অন্বেষা" else "ANWESHA",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = schoolInfo?.schoolName ?: "ANWESHA School Platform",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = schoolInfo?.tagline ?: if (currentLanguage == Language.BANGLA) "জ্ঞান, মনন ও স্বপ্নের সোপান" else "Knowledge, Wisdom & Excellence",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    // Quick Language Switch Chip
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                val nextLang = if (currentLanguage == Language.BANGLA) Language.ENGLISH else Language.BANGLA
                                viewModel.setAppLanguage(nextLang)
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Filled.Language,
                                contentDescription = "Language",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (currentLanguage == Language.BANGLA) "বাং" else "EN",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    if (currentRoute != "students") {
                        IconButton(onClick = { navigateTo("students") }) {
                            Icon(Icons.Filled.Search, contentDescription = "Student Search", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = { navigateTo("settings") }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
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
                        icon = { Icon(screen.icon, contentDescription = label) },
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
                Screen.Students.route -> StudentScreen(viewModel = viewModel)
                Screen.ToolsHub.route -> ToolsHubScreen(viewModel = viewModel)
                "tools_hub/admit_card" -> ToolsHubScreen(
                    viewModel = viewModel,
                    initialToolRoute = "admit_card_maker"
                )
                Screen.CustomFields.route -> CustomFieldsScreen(
                    viewModel = viewModel,
                    onNavigateToStudents = { navigateTo(Screen.Students.route) }
                )
                Screen.Settings.route -> SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToCustomFields = { navigateTo(Screen.CustomFields.route) }
                )
                else -> DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToSection = { route -> navigateTo(route) }
                )
            }
        }
    }
}
