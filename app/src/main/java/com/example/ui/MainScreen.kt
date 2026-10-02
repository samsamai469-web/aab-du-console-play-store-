package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import com.example.ui.navigation.Screen
import com.example.ui.screens.*
import com.example.util.TtsManager
import com.example.util.AppStrings
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.util.SmartNotificationManager
import androidx.compose.runtime.collectAsState

@Composable
fun MainScreen(viewModel: SamAiViewModel, ttsManager: TtsManager) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val appLanguage by viewModel.appLanguage.collectAsState()

    LaunchedEffect(currentDestination) {
        ttsManager.stop()
        SmartNotificationManager.recordUserActivity(context)
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                val hideBottomBar = currentDestination?.hierarchy?.any {
                    it.hasRoute(Screen.AdminMoore::class)
                } == true

                if (!hideBottomBar) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp
                    ) {
                        data class BottomBarItem(
                            val label: String,
                            val icon: ImageVector,
                            val screen: Screen,
                            val isSelected: Boolean
                        )

                        val items = listOf(
                            BottomBarItem(
                                label = AppStrings.navHome(appLanguage),
                                icon = Icons.Default.Home,
                                screen = Screen.Home,
                                isSelected = currentDestination?.hierarchy?.any { it.hasRoute(Screen.Home::class) } == true
                            ),
                            BottomBarItem(
                                label = AppStrings.navTranslation(appLanguage),
                                icon = Icons.Default.Translate,
                                screen = Screen.Translation(),
                                isSelected = currentDestination?.hierarchy?.any { it.hasRoute(Screen.Translation::class) } == true
                            ),
                            BottomBarItem(
                                label = AppStrings.navLearning(appLanguage),
                                icon = Icons.Default.School,
                                screen = Screen.Learning,
                                isSelected = currentDestination?.hierarchy?.any { it.hasRoute(Screen.Learning::class) } == true
                            ),
                            BottomBarItem(
                                label = AppStrings.navNews(appLanguage),
                                icon = Icons.Default.Newspaper,
                                screen = Screen.News,
                                isSelected = currentDestination?.hierarchy?.any { it.hasRoute(Screen.News::class) } == true
                            ),
                            BottomBarItem(
                                label = AppStrings.navProfile(appLanguage),
                                icon = Icons.Default.Person,
                                screen = Screen.Profile,
                                isSelected = currentDestination?.hierarchy?.any { it.hasRoute(Screen.Profile::class) } == true
                            )
                        )
                        items.forEach { item ->
                            NavigationBarItem(
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                                selected = item.isSelected,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                onClick = {
                                    ttsManager.stop()
                                    if (item.screen == Screen.Home) {
                                        navController.navigate(Screen.Home) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                inclusive = false
                                            }
                                            launchSingleTop = true
                                        }
                                    } else if (!item.isSelected) {
                                        navController.navigate(item.screen) {
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
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable<Screen.Home>(deepLinks = listOf(navDeepLink { uriPattern = "sam-ai://home" })) { HomeScreen(viewModel, ttsManager, navController) }
                composable<Screen.Translation>(deepLinks = listOf(navDeepLink { uriPattern = "sam-ai://translation" })) { backStackEntry ->
                    val translation: Screen.Translation = backStackEntry.toRoute()
                    TranslationScreen(viewModel, ttsManager, translation.initialText, translation.initialTargetLang)
                }
                composable<Screen.Learning>(deepLinks = listOf(navDeepLink { uriPattern = "sam-ai://learning" })) { LearningScreen(viewModel, ttsManager) }
                composable<Screen.News>(deepLinks = listOf(navDeepLink { uriPattern = "sam-ai://news" })) { NewsScreen(viewModel, ttsManager, navController) }
                composable<Screen.History>(deepLinks = listOf(navDeepLink { uriPattern = "sam-ai://history" })) { HistoryScreen(viewModel, ttsManager, navController) }
                composable<Screen.Profile> { ProfileScreen(viewModel, navController) }
                composable<Screen.Favorites> { com.example.ui.screens.FavoritesScreen(viewModel, ttsManager, navController) }
                composable<Screen.Notifications> { com.example.ui.screens.NotificationsScreen(navController, viewModel) }
                composable<Screen.Storage> { com.example.ui.screens.StorageScreen(navController, viewModel) }
                composable<Screen.Language> { com.example.ui.screens.LanguageScreen(navController, viewModel) }
                composable<Screen.Support> { backStackEntry ->
                    val supportRoute: Screen.Support = backStackEntry.toRoute()
                    SupportScreen(viewModel, navController, supportRoute.initialType)
                }
                composable<Screen.About> { AboutScreen(viewModel, navController) }
                composable<Screen.AdminMoore> { AdminMooreScreen(viewModel, navController) }
            }
        }
    }
}
