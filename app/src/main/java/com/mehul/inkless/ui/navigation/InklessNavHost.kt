package com.mehul.inkless.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mehul.inkless.ui.capture.SmsSetupScreen
import com.mehul.inkless.ui.capture.UnreadScreen
import com.mehul.inkless.ui.home.HomeScreen
import com.mehul.inkless.ui.settings.SettingsScreen
import com.mehul.inkless.ui.transaction.TransactionScreen
import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
object SettingsRoute

@Serializable
data class TransactionRoute(val id: String)

@Serializable
object SmsSetupRoute

@Serializable
object UnreadRoute

@Composable
fun InklessNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenTransaction = { navController.navigate(TransactionRoute(it)) },
                onOpenSmsSetup = { navController.navigate(SmsSetupRoute) },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenSmsSetup = { navController.navigate(SmsSetupRoute) },
                onOpenUnread = { navController.navigate(UnreadRoute) },
            )
        }
        composable<TransactionRoute> {
            TransactionScreen(onBack = { navController.popBackStack() })
        }
        composable<SmsSetupRoute> {
            SmsSetupScreen(onDone = { navController.popBackStack() })
        }
        composable<UnreadRoute> {
            UnreadScreen(onBack = { navController.popBackStack() })
        }
    }
}
