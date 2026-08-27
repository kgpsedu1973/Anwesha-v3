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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.delay

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "ড্যাশবোর্ড", Icons.Filled.Dashboard)
    object Students : Screen("students", "শিক্ষার্থী", Icons.Filled.People)
    object CustomFields : Screen("custom_fields", "ফিল্ড ও সূত্র", Icons.Filled.Tune)
    object Settings : Screen("settings", "বিদ্যালয় ও সেটিংস", Icons.Filled.School)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenContainer(viewModel: MainViewModel) {
    var currentRoute by remember { mutableStateOf("dashboard") }
    val userMessage by viewModel.userMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val schoolInfo by viewModel.schoolInfo.collectAsState()

    var backPressedOnce by remember { mutableStateOf(false) }

    LaunchedEffect(backPressedOnce) {
        if (backPressedOnce) {
            delay(2000L)
            backPressedOnce = false
        }
    }

    if (currentRoute == Screen.Dashboard.route) {
        BackHandler(enabled = true) {
            if (backPressedOnce) {
                (context as? Activity)?.finish()
            } else {
                backPressedOnce = true
                Toast.makeText(context, "অ্যাপ থেকে বের হতে আবার ব্যাক চাপুন", Toast.LENGTH_SHORT).show()
            }
        }
    } else {
        BackHandler(enabled = true) {
            currentRoute = Screen.Dashboard.route
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
        Screen.CustomFields,
        Screen.Settings
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "অন্বেষা",
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
                                text = schoolInfo?.tagline ?: "জ্ঞান, মনন ও স্বপ্নের সোপান",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    if (currentRoute != "students") {
                        IconButton(onClick = { currentRoute = "students" }) {
                            Icon(Icons.Filled.Search, contentDescription = "Student Search", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = { currentRoute = "settings" }) {
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
                    NavigationBarItem(
                        selected = selected,
                        onClick = { currentRoute = screen.route },
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
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
                    onNavigateToSection = { route -> currentRoute = route }
                )
                Screen.Students.route -> StudentScreen(viewModel = viewModel)
                Screen.CustomFields.route -> CustomFieldsScreen(
                    viewModel = viewModel,
                    onNavigateToStudents = { currentRoute = Screen.Students.route }
                )
                Screen.Settings.route -> SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToCustomFields = { currentRoute = Screen.CustomFields.route }
                )
                else -> DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToSection = { route -> currentRoute = route }
                )
            }
        }
    }
}
