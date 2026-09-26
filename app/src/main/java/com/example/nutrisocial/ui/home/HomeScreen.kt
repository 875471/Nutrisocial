package com.example.nutrisocial.ui.home

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.nutrisocial.data.User
import com.example.nutrisocial.ui.recipes.RecipeDetailScreen
import com.example.nutrisocial.ui.recipes.RecipeFormScreen
import com.example.nutrisocial.ui.recipes.RecipeListScreen
import com.example.nutrisocial.ui.recipes.RecipeViewModel

private object HomeRoutes {
    const val INICIO = "inicio"
    const val RECETAS = "recetas"
    const val PERFIL = "perfil"
    const val NUEVA_RECETA = "recetas/nueva"
    const val DETALLE_RECETA = "recetas/detalle/{id}"
    fun detalleReceta(id: Int) = "recetas/detalle/$id"
}

private enum class HomeTab(val route: String, val label: String, val icon: ImageVector) {
    INICIO(HomeRoutes.INICIO, "Inicio", Icons.Filled.Home),
    RECETAS(HomeRoutes.RECETAS, "Mis recetas", Icons.AutoMirrored.Filled.List),
    PERFIL(HomeRoutes.PERFIL, "Perfil", Icons.Filled.Person)
}

/**
 * Punto de entrada tras iniciar sesión: barra de navegación inferior con las pestañas principales
 * y un grafo de navegación propio para las pantallas de recetas.
 */
@Composable
fun HomeScreen(
    user: User?,
    onLogout: () -> Unit,
    // Con ámbito en la entrada "home" del grafo principal: lo comparten lista, formulario y detalle,
    // y se destruye al cerrar sesión.
    recipeViewModel: RecipeViewModel = viewModel(),
    navController: NavHostController = rememberNavController()
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = HomeTab.entries.any { it.route == currentDestination?.route }

    Scaffold(
        // Cada pantalla interior gestiona sus propios márgenes de sistema con su Scaffold.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    HomeTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true,
                            onClick = { navController.navigateToTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoutes.INICIO,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
        ) {
            composable(HomeRoutes.INICIO) {
                val listState by recipeViewModel.listState.collectAsStateWithLifecycle()
                HomeTabScreen(
                    userName = user?.name.orEmpty(),
                    recipesState = listState,
                    onOpenRecipes = { navController.navigateToTab(HomeRoutes.RECETAS) },
                    onCreateRecipe = {
                        recipeViewModel.resetForm()
                        navController.navigate(HomeRoutes.NUEVA_RECETA)
                    }
                )
            }

            composable(HomeRoutes.RECETAS) {
                val listState by recipeViewModel.listState.collectAsStateWithLifecycle()
                RecipeListScreen(
                    state = listState,
                    onRecipeClick = { id -> navController.navigate(HomeRoutes.detalleReceta(id)) },
                    onCreateRecipe = {
                        recipeViewModel.resetForm()
                        navController.navigate(HomeRoutes.NUEVA_RECETA)
                    },
                    onRetry = recipeViewModel::loadMyRecipes
                )
            }

            composable(HomeRoutes.PERFIL) {
                ProfileScreen(user = user, onLogout = onLogout)
            }

            composable(HomeRoutes.NUEVA_RECETA) {
                val formState by recipeViewModel.formState.collectAsStateWithLifecycle()
                RecipeFormScreen(
                    state = formState,
                    onTitleChange = recipeViewModel::onTitleChange,
                    onServingsChange = recipeViewModel::onServingsChange,
                    onPrepMinutesChange = recipeViewModel::onPrepMinutesChange,
                    onItemChange = recipeViewModel::onItemChange,
                    onAddItem = recipeViewModel::addItem,
                    onRemoveItem = recipeViewModel::removeItem,
                    onSave = recipeViewModel::saveRecipe,
                    onSaved = {
                        // Tras guardar se vuelve a "Mis recetas", que ya incluye la nueva receta.
                        recipeViewModel.resetForm()
                        if (!navController.popBackStack(HomeRoutes.RECETAS, inclusive = false)) {
                            // Se abrió desde Inicio: se quita el formulario y se cambia de pestaña.
                            navController.popBackStack()
                            navController.navigateToTab(HomeRoutes.RECETAS)
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = HomeRoutes.DETALLE_RECETA,
                arguments = listOf(navArgument("id") { type = NavType.IntType })
            ) { entry ->
                val id = entry.arguments?.getInt("id") ?: return@composable
                LaunchedEffect(id) { recipeViewModel.loadRecipe(id) }
                val detailState by recipeViewModel.detailState.collectAsStateWithLifecycle()
                RecipeDetailScreen(
                    state = detailState,
                    onBack = { navController.popBackStack() },
                    onRetry = { recipeViewModel.loadRecipe(id) }
                )
            }
        }
    }
}

/** Navegación estándar entre pestañas: conserva y restaura el estado de cada una. */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
