package com.englishtutor.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.englishtutor.app.ui.conversation.ConversationScreen
import com.englishtutor.app.ui.conversation.ConversationViewModel
import com.englishtutor.app.ui.history.HistoryScreen
import com.englishtutor.app.ui.history.HistoryViewModel
import com.englishtutor.app.ui.history.SessionDetailScreen
import com.englishtutor.app.ui.history.SessionDetailViewModel
import com.englishtutor.app.ui.settings.SettingsScreen
import com.englishtutor.app.ui.settings.SettingsViewModel
import com.englishtutor.app.ui.vocab.VocabScreen
import com.englishtutor.app.ui.vocab.VocabViewModel

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    CONVERSATION("conversation", "Conversar", Icons.Filled.Face),
    HISTORY("history", "Historial", Icons.Filled.DateRange),
    VOCAB("vocab", "Vocabulario", Icons.Filled.Star),
    SETTINGS("settings", "Ajustes", Icons.Filled.Settings),
}

/** Barra inferior + NavHost con las 4 pantallas (y el detalle de una sesión). */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    // La conversación vive a nivel de Activity: no se pierde al cambiar de pestaña
    val conversationVm: ConversationViewModel = viewModel(factory = ConversationViewModel.Factory)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            val backStack by navController.currentBackStackEntryAsState()
            val destination = backStack?.destination
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = destination?.hierarchy?.any { it.route == tab.route } == true ||
                            (tab == Tab.HISTORY && destination?.route?.startsWith("history/") == true),
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = Tab.CONVERSATION.route, modifier = Modifier.padding(padding)) {
            composable(Tab.CONVERSATION.route) { ConversationScreen(conversationVm) }
            composable(Tab.HISTORY.route) {
                HistoryScreen(viewModel(factory = HistoryViewModel.Factory)) { id -> navController.navigate("history/$id") }
            }
            composable("history/{sessionId}", arguments = listOf(navArgument("sessionId") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("sessionId") ?: return@composable
                SessionDetailScreen(viewModel(factory = SessionDetailViewModel.factory(id))) { navController.popBackStack() }
            }
            composable(Tab.VOCAB.route) { VocabScreen(viewModel(factory = VocabViewModel.Factory)) }
            composable(Tab.SETTINGS.route) { SettingsScreen(viewModel(factory = SettingsViewModel.Factory)) }
        }
    }
}
