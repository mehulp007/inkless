package com.mehul.inkless.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.mehul.inkless.ui.auth.ForgotPasswordScreen
import com.mehul.inkless.ui.auth.LoginScreen
import com.mehul.inkless.ui.auth.SignUpScreen
import com.mehul.inkless.ui.auth.WelcomeScreen
import kotlinx.serialization.Serializable

@Serializable
object WelcomeRoute

@Serializable
object LoginRoute

@Serializable
object SignUpRoute

@Serializable
data class ForgotPasswordRoute(val identifier: String = "")

/** Screens for someone who isn't signed in. Once a sign-in works, the root swaps this whole graph out. */
@Composable
fun AuthNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = WelcomeRoute) {
        composable<WelcomeRoute> {
            WelcomeScreen(
                onSignUp = { navController.navigate(SignUpRoute) },
                onLogIn = { navController.navigate(LoginRoute) },
            )
        }
        composable<LoginRoute> {
            LoginScreen(
                onBack = { navController.popBackStack() },
                onForgotPassword = { navController.navigate(ForgotPasswordRoute(it)) },
                onSignUp = { navController.navigate(SignUpRoute) { popUpTo(WelcomeRoute) } },
            )
        }
        composable<SignUpRoute> {
            SignUpScreen(
                onBack = { navController.popBackStack() },
                onLogIn = { navController.navigate(LoginRoute) { popUpTo(WelcomeRoute) } },
            )
        }
        composable<ForgotPasswordRoute> { entry ->
            ForgotPasswordScreen(
                initialIdentifier = entry.toRoute<ForgotPasswordRoute>().identifier,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
