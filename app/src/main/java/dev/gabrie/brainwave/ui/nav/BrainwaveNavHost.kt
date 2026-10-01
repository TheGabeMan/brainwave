package dev.gabrie.brainwave.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.gabrie.brainwave.ui.detail.DetailScreen
import dev.gabrie.brainwave.ui.home.HomeScreen
import dev.gabrie.brainwave.ui.record.RecordScreen
import dev.gabrie.brainwave.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val RECORD = "record"
    const val SETTINGS = "settings"
    const val DETAIL = "detail/{id}"

    fun detail(id: Long) = "detail/$id"
}

@Composable
fun BrainwaveNavHost(
    navController: NavHostController = rememberNavController(),
    /** Set when the app was launched by tapping a reminder notification. */
    openBrainwaveId: Long? = null,
    onOpenHandled: () -> Unit = {},
) {
    LaunchedEffect(openBrainwaveId) {
        if (openBrainwaveId != null && openBrainwaveId > 0L) {
            navController.navigate(Routes.detail(openBrainwaveId))
            onOpenHandled()
        }
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onRecord = { navController.navigate(Routes.RECORD) },
                onOpenBrainwave = { id -> navController.navigate(Routes.detail(id)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.RECORD) {
            RecordScreen(
                onDone = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            DetailScreen(
                brainwaveId = entry.arguments?.getLong("id") ?: 0L,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
