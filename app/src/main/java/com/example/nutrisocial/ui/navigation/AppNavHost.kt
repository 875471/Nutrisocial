package com.example.nutrisocial.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.nutrisocial.ui.auth.AuthViewModel
import com.example.nutrisocial.ui.auth.LoginScreen
import com.example.nutrisocial.ui.auth.RegisterScreen
import com.example.nutrisocial.ui.auth.SessionState
import com.example.nutrisocial.ui.home.HomeScreen

object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val HOME = "home"
}

@Composable
fun AppNavHost(
    authViewModel: AuthViewModel = viewModel(),
    navController: NavHostController = rememberNavController()
) {
    val sessionState by authViewModel.sessionState.collectAsStateWithLifecycle()

    // Mientras se lee DataStore por primera vez no se sabe a qué pantalla ir.
    if (sessionState is SessionState.Checking) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val startDestination = rememberSaveable {
        if (sessionState is SessionState.LoggedIn) Routes.HOME else Routes.LOGIN
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            val loginState by authViewModel.loginState.collectAsStateWithLifecycle()
            LoginScreen(
                state = loginState,
                onLogin = authViewModel::login,
                onGoToRegister = {
                    authViewModel.resetStates()
                    navController.navigate(Routes.REGISTER) { launchSingleTop = true }
                }
            )
        }
        composable(Routes.REGISTER) {
            val registerState by authViewModel.registerState.collectAsStateWithLifecycle()
            RegisterScreen(
                state = registerState,
                onRegister = authViewModel::register,
                onGoToLogin = {
                    authViewModel.resetStates()
                    if (!navController.popBackStack(Routes.LOGIN, inclusive = false)) {
                        navController.navigate(Routes.LOGIN) { launchSingleTop = true }
                    }
                }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                user = (sessionState as? SessionState.LoggedIn)?.user,
                onLogout = authViewModel::logout
            )
        }
    }

    // La sesión guardada en DataStore es la fuente de verdad: al iniciar sesión (o registrarse)
    // se va a Home y al cerrarla se vuelve a Login, limpiando la pila en ambos casos.
    LaunchedEffect(sessionState) {
        val currentRoute = navController.currentDestination?.route
        when (sessionState) {
            is SessionState.LoggedIn -> if (currentRoute != Routes.HOME) {
                navController.navigate(Routes.HOME) {
                    popUpTo(navController.graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            }
            SessionState.LoggedOut -> if (currentRoute == Routes.HOME) {
                navController.navigate(Routes.LOGIN) {
                    popUpTo(navController.graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            }
            SessionState.Checking -> Unit
        }
    }
}
