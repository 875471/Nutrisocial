package com.example.nutrisocial.ui.home

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
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
import androidx.compose.runtime.remember
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
import com.example.nutrisocial.ui.log.AddEntryActions
import com.example.nutrisocial.ui.log.LogActions
import com.example.nutrisocial.ui.log.LogScreen
import com.example.nutrisocial.ui.log.LogViewModel
import com.example.nutrisocial.ui.recipes.IngredientActions
import com.example.nutrisocial.ui.recipes.RecipeDetailScreen
import com.example.nutrisocial.ui.recipes.RecipeFormScreen
import com.example.nutrisocial.ui.recipes.RecipeListScreen
import com.example.nutrisocial.ui.recipes.RecipeViewModel
import com.example.nutrisocial.ui.scan.ScanRecipeScreen
import com.example.nutrisocial.ui.scan.ScanRecipeViewModel

private object HomeRoutes {
    const val INICIO = "inicio"
    const val RECETAS = "recetas"
    const val DIARIO = "diario"
    const val PERFIL = "perfil"
    const val NUEVA_RECETA = "recetas/nueva"
    const val ESCANEAR_RECETA = "recetas/escanear"
    const val DETALLE_RECETA = "recetas/detalle/{id}"
    fun detalleReceta(id: Int) = "recetas/detalle/$id"
}

private enum class HomeTab(val route: String, val label: String, val icon: ImageVector) {
    INICIO(HomeRoutes.INICIO, "Inicio", Icons.Filled.Home),
    RECETAS(HomeRoutes.RECETAS, "Mis recetas", Icons.AutoMirrored.Filled.List),
    DIARIO(HomeRoutes.DIARIO, "Diario", Icons.Filled.DateRange),
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
    // Mismo ámbito: el día elegido en el diario y el perfil se conservan al cambiar de pestaña.
    logViewModel: LogViewModel = viewModel(),
    profileViewModel: ProfileViewModel = viewModel(),
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
                    onRetry = recipeViewModel::loadMyRecipes,
                    onScanRecipe = { navController.navigate(HomeRoutes.ESCANEAR_RECETA) }
                )
            }

            composable(HomeRoutes.ESCANEAR_RECETA) {
                // Con ámbito en esta pantalla: el reconocedor de ML Kit se libera al salir de ella.
                val scanViewModel: ScanRecipeViewModel = viewModel()
                val scanState by scanViewModel.state.collectAsStateWithLifecycle()
                ScanRecipeScreen(
                    state = scanState,
                    onImageSelected = scanViewModel::processImage,
                    onRetry = scanViewModel::retry,
                    onProposalReady = { proposal ->
                        recipeViewModel.loadOcrProposal(proposal)
                        scanViewModel.onProposalConsumed()
                        // El escáner se sustituye por el formulario: al volver se regresa a la lista.
                        navController.navigate(HomeRoutes.NUEVA_RECETA) {
                            popUpTo(HomeRoutes.ESCANEAR_RECETA) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(HomeRoutes.DIARIO) {
                // Al entrar se recarga el día: el objetivo puede haber cambiado en el perfil.
                LaunchedEffect(Unit) { logViewModel.loadDay() }
                val date by logViewModel.date.collectAsStateWithLifecycle()
                val dayState by logViewModel.dayState.collectAsStateWithLifecycle()
                val recommendationsState by logViewModel.recommendationsState.collectAsStateWithLifecycle()
                val addingRecommendationId by logViewModel.addingRecommendationId.collectAsStateWithLifecycle()
                val addState by logViewModel.addState.collectAsStateWithLifecycle()
                val message by logViewModel.message.collectAsStateWithLifecycle()
                val recipesState by recipeViewModel.listState.collectAsStateWithLifecycle()
                LogScreen(
                    date = date,
                    dayState = dayState,
                    recommendationsState = recommendationsState,
                    addingRecommendationId = addingRecommendationId,
                    addState = addState,
                    recipesState = recipesState,
                    message = message,
                    actions = remember(logViewModel) {
                        LogActions(
                            onPreviousDay = logViewModel::previousDay,
                            onNextDay = logViewModel::nextDay,
                            onToday = logViewModel::goToToday,
                            onSelectDate = logViewModel::selectDate,
                            onRetry = logViewModel::loadDay,
                            onDelete = logViewModel::deleteEntry,
                            onMessageShown = logViewModel::onMessageShown,
                            onOpenProfile = { navController.navigateToTab(HomeRoutes.PERFIL) },
                            onAddRecommendation = logViewModel::addRecommendation,
                            onRetryRecommendations = logViewModel::loadRecommendations,
                            add = AddEntryActions(
                                onOpen = {
                                    // La lista de recetas puede haber fallado o estar desfasada.
                                    recipeViewModel.loadMyRecipes()
                                    logViewModel.openAddSheet()
                                },
                                onDismiss = logViewModel::closeAddSheet,
                                onSourceChange = logViewModel::onSourceChange,
                                onRecipeSelected = logViewModel::onRecipeSelected,
                                onServingsChange = logViewModel::onServingsChange,
                                onFoodQueryChange = logViewModel::onFoodQueryChange,
                                onFoodSelected = logViewModel::onFoodSelected,
                                onGramsChange = logViewModel::onGramsChange,
                                onSave = logViewModel::saveEntry
                            )
                        )
                    }
                )
            }

            composable(HomeRoutes.PERFIL) {
                val profileState by profileViewModel.state.collectAsStateWithLifecycle()
                ProfileScreen(
                    user = user,
                    state = profileState,
                    actions = remember(profileViewModel) {
                        ProfileActions(
                            onBirthDateChange = profileViewModel::onBirthDateChange,
                            onHeightChange = profileViewModel::onHeightChange,
                            onWeightChange = profileViewModel::onWeightChange,
                            onSexChange = profileViewModel::onSexChange,
                            onActivityChange = profileViewModel::onActivityChange,
                            onGoalChange = profileViewModel::onGoalChange,
                            onSave = profileViewModel::save,
                            onRetry = profileViewModel::loadProfile,
                            onSavedMessageShown = profileViewModel::onSavedMessageShown
                        )
                    },
                    onLogout = onLogout
                )
            }

            composable(HomeRoutes.NUEVA_RECETA) {
                val formState by recipeViewModel.formState.collectAsStateWithLifecycle()
                RecipeFormScreen(
                    state = formState,
                    onTitleChange = recipeViewModel::onTitleChange,
                    onServingsChange = recipeViewModel::onServingsChange,
                    onPrepMinutesChange = recipeViewModel::onPrepMinutesChange,
                    ingredientActions = remember(recipeViewModel) {
                        IngredientActions(
                            onNameChange = recipeViewModel::onIngredientNameChange,
                            onQuantityChange = recipeViewModel::onIngredientQuantityChange,
                            onUnitChange = recipeViewModel::onIngredientUnitChange,
                            onSuggestionSelected = recipeViewModel::onSuggestionSelected,
                            onNameFocusLost = recipeViewModel::onIngredientFocusLost,
                            onAdd = recipeViewModel::addIngredient,
                            onRemove = recipeViewModel::removeIngredient
                        )
                    },
                    onStepChange = recipeViewModel::onStepChange,
                    onAddStep = recipeViewModel::addStep,
                    onRemoveStep = recipeViewModel::removeStep,
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
                    onBack = { navController.popBackStack() },
                    onDismissOcrNotice = recipeViewModel::dismissOcrNotice
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
