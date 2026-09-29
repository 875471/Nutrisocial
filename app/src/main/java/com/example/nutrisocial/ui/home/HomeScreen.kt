package com.example.nutrisocial.ui.home

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
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
import com.example.nutrisocial.data.toPreview
import com.example.nutrisocial.ui.comments.CommentsActions
import com.example.nutrisocial.ui.explore.ExploreContent
import com.example.nutrisocial.ui.explore.RecipeSearchViewModel
import com.example.nutrisocial.ui.explore.SavedRecipesContent
import com.example.nutrisocial.ui.explore.SavedRecipesViewModel
import com.example.nutrisocial.ui.recipes.RecipesTab
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.nutrisocial.ui.comments.CommentsScreen
import com.example.nutrisocial.ui.comments.CommentsViewModel
import com.example.nutrisocial.ui.log.AddEntryActions
import com.example.nutrisocial.ui.log.LogActions
import com.example.nutrisocial.ui.log.LogScreen
import com.example.nutrisocial.ui.log.LogViewModel
import com.example.nutrisocial.ui.pantry.PantryActions
import com.example.nutrisocial.ui.pantry.PantryScreen
import com.example.nutrisocial.ui.pantry.PantryViewModel
import com.example.nutrisocial.ui.recipes.IngredientActions
import com.example.nutrisocial.ui.recipes.RecipeDetailActions
import com.example.nutrisocial.ui.recipes.RecipeDetailScreen
import com.example.nutrisocial.ui.recipes.RecipeDetailUiState
import com.example.nutrisocial.ui.recipes.RecipeFormScreen
import com.example.nutrisocial.ui.recipes.RecipeListScreen
import com.example.nutrisocial.ui.recipes.RecipeViewModel
import com.example.nutrisocial.ui.scan.ScanRecipeScreen
import com.example.nutrisocial.ui.scan.ScanRecipeViewModel

private object HomeRoutes {
    const val INICIO = "inicio"
    const val RECETAS = "recetas"
    const val DIARIO = "diario"
    const val DESPENSA = "despensa"
    const val PERFIL = "perfil"
    const val NUEVA_RECETA = "recetas/nueva"
    const val EDITAR_RECETA = "recetas/editar"
    const val ESCANEAR_RECETA = "recetas/escanear"
    const val DETALLE_RECETA = "recetas/detalle/{id}"
    fun detalleReceta(id: Int) = "recetas/detalle/$id"
    const val COMENTARIOS = "recetas/comentarios/{id}"
    fun comentarios(id: Int) = "recetas/comentarios/$id"
}

private enum class HomeTab(val route: String, val label: String, val icon: ImageVector) {
    INICIO(HomeRoutes.INICIO, "Inicio", Icons.Filled.Home),
    // Etiquetas de una palabra: con cinco pestañas, "Mis recetas" se cortaba en pantallas estrechas.
    RECETAS(HomeRoutes.RECETAS, "Recetas", Icons.AutoMirrored.Filled.List),
    DIARIO(HomeRoutes.DIARIO, "Diario", Icons.Filled.DateRange),
    DESPENSA(HomeRoutes.DESPENSA, "Despensa", Icons.Filled.ShoppingCart),
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
    // Mismo ámbito: la despensa y los últimos resultados se conservan al volver del detalle.
    pantryViewModel: PantryViewModel = viewModel(),
    feedViewModel: FeedViewModel = viewModel(),
    // "Explorar" de la pestaña Recetas: se conserva la búsqueda al abrir una receta y volver.
    recipeSearchViewModel: RecipeSearchViewModel = viewModel(),
    savedRecipesViewModel: SavedRecipesViewModel = viewModel(),
    navController: NavHostController = rememberNavController()
) {
    // Listas de tarjetas que deben reflejar lo que cambie en el detalle o en los comentarios.
    val feedLists = listOf(feedViewModel, recipeSearchViewModel, savedRecipesViewModel)
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
                val feedState by feedViewModel.state.collectAsStateWithLifecycle()
                HomeTabScreen(
                    userName = user?.name.orEmpty(),
                    state = feedState,
                    actions = remember(feedViewModel) {
                        feedActions(feedViewModel, navController).copy(
                            onCreateRecipe = {
                                recipeViewModel.resetForm()
                                navController.navigate(HomeRoutes.NUEVA_RECETA)
                            }
                        )
                    }
                )
            }

            composable(HomeRoutes.RECETAS) {
                // Se guarda con la entrada de navegación: al volver del detalle sigue el mismo segmento.
                var recipesTab by rememberSaveable { mutableStateOf(RecipesTab.MINE) }
                val listState by recipeViewModel.listState.collectAsStateWithLifecycle()
                val listMessage by recipeViewModel.listMessage.collectAsStateWithLifecycle()
                val listRefreshing by recipeViewModel.isRefreshingList.collectAsStateWithLifecycle()
                RecipeListScreen(
                    isRefreshing = listRefreshing,
                    onRefresh = recipeViewModel::refreshMyRecipes,
                    message = listMessage,
                    onMessageShown = recipeViewModel::onListMessageShown,
                    state = listState,
                    onRecipeClick = { id -> navController.navigate(HomeRoutes.detalleReceta(id)) },
                    onCreateRecipe = {
                        recipeViewModel.resetForm()
                        navController.navigate(HomeRoutes.NUEVA_RECETA)
                    },
                    onRetry = recipeViewModel::loadMyRecipes,
                    onScanRecipe = { navController.navigate(HomeRoutes.ESCANEAR_RECETA) },
                    selectedTab = recipesTab,
                    onTabSelected = { recipesTab = it },
                    exploreContent = { snackbarHostState ->
                        val searchState by recipeSearchViewModel.state.collectAsStateWithLifecycle()
                        val params by recipeSearchViewModel.params.collectAsStateWithLifecycle()
                        ExploreContent(
                            state = searchState,
                            params = params,
                            userName = user?.name.orEmpty(),
                            actions = remember(recipeSearchViewModel) { feedActions(recipeSearchViewModel, navController) },
                            onQueryChange = recipeSearchViewModel::onQueryChange,
                            onSortChange = recipeSearchViewModel::onSortChange,
                            onSearch = recipeSearchViewModel::search,
                            snackbarHostState = snackbarHostState
                        )
                    },
                    savedContent = { snackbarHostState ->
                        // Al abrir el segmento se recarga: se pueden haber guardado recetas desde el
                        // inicio, desde "Explorar" o desde el detalle.
                        LaunchedEffect(Unit) { savedRecipesViewModel.refresh() }
                        val savedState by savedRecipesViewModel.state.collectAsStateWithLifecycle()
                        SavedRecipesContent(
                            state = savedState,
                            userName = user?.name.orEmpty(),
                            actions = remember(savedRecipesViewModel) { feedActions(savedRecipesViewModel, navController) },
                            snackbarHostState = snackbarHostState
                        )
                    }
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
                    onProcessAnyway = scanViewModel::processAnyway,
                    onDiscardPhoto = scanViewModel::reset,
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
                LaunchedEffect(Unit) { logViewModel.onScreenShown() }
                val date by logViewModel.date.collectAsStateWithLifecycle()
                val dayState by logViewModel.dayState.collectAsStateWithLifecycle()
                val recommendationsState by logViewModel.recommendationsState.collectAsStateWithLifecycle()
                val addingRecommendationId by logViewModel.addingRecommendationId.collectAsStateWithLifecycle()
                val addState by logViewModel.addState.collectAsStateWithLifecycle()
                val message by logViewModel.message.collectAsStateWithLifecycle()
                val recipesState by recipeViewModel.listState.collectAsStateWithLifecycle()
                val viewMode by logViewModel.viewMode.collectAsStateWithLifecycle()
                val month by logViewModel.month.collectAsStateWithLifecycle()
                val calendarState by logViewModel.calendarState.collectAsStateWithLifecycle()
                LogScreen(
                    viewMode = viewMode,
                    month = month,
                    calendarState = calendarState,
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
                            ),
                            onOpenRecipe = { id -> navController.navigate(HomeRoutes.detalleReceta(id)) },
                            onShowMonth = logViewModel::showMonth,
                            onShowDay = logViewModel::showDay,
                            onPreviousMonth = logViewModel::previousMonth,
                            onNextMonth = logViewModel::nextMonth,
                            onCalendarDayClick = logViewModel::openCalendarDay,
                            onRetryCalendar = logViewModel::loadCalendar,
                            onUpdateEntry = logViewModel::updateEntry
                        )
                    }
                )
            }

            composable(HomeRoutes.DESPENSA) {
                val itemsState by pantryViewModel.itemsState.collectAsStateWithLifecycle()
                val input by pantryViewModel.input.collectAsStateWithLifecycle()
                val searchState by pantryViewModel.searchState.collectAsStateWithLifecycle()
                val message by pantryViewModel.message.collectAsStateWithLifecycle()
                val pantryRefreshing by pantryViewModel.isRefreshing.collectAsStateWithLifecycle()
                PantryScreen(
                    isRefreshing = pantryRefreshing,
                    itemsState = itemsState,
                    input = input,
                    searchState = searchState,
                    message = message,
                    actions = remember(pantryViewModel) {
                        PantryActions(
                            onQueryChange = pantryViewModel::onQueryChange,
                            onSuggestionSelected = pantryViewModel::onSuggestionSelected,
                            onDismissSuggestions = pantryViewModel::dismissSuggestions,
                            onAdd = pantryViewModel::addItem,
                            onDelete = pantryViewModel::deleteItem,
                            onRetryPantry = pantryViewModel::loadPantry,
                            onSearchRecipes = pantryViewModel::searchRecipes,
                            // Las recetas pueden ser de otros usuarios: el detalle las carga por id.
                            onRecipeClick = { id -> navController.navigate(HomeRoutes.detalleReceta(id)) },
                            onMessageShown = pantryViewModel::onMessageShown,
                            onRefresh = pantryViewModel::refresh
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
                            onSavedMessageShown = profileViewModel::onSavedMessageShown,
                            onEditToggle = profileViewModel::onEditToggle,
                            onDeleteAccount = profileViewModel::deleteAccount,
                            onDeleteAccountDismissed = profileViewModel::onDeleteAccountDismissed
                        )
                    },
                    onLogout = onLogout
                )
            }

            composable(HomeRoutes.NUEVA_RECETA) {
                RecipeFormDestination(
                    recipeViewModel = recipeViewModel,
                    onSaved = {
                        // Tras guardar se vuelve a "Mis recetas", que ya incluye la nueva receta.
                        recipeViewModel.resetForm()
                        // La nueva receta también es lo primero del feed.
                        feedViewModel.refresh()
                        if (!navController.popBackStack(HomeRoutes.RECETAS, inclusive = false)) {
                            // Se abrió desde Inicio: se quita el formulario y se cambia de pestaña.
                            navController.popBackStack()
                            navController.navigateToTab(HomeRoutes.RECETAS)
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(HomeRoutes.EDITAR_RECETA) {
                RecipeFormDestination(
                    recipeViewModel = recipeViewModel,
                    // Se vuelve al detalle, que ya muestra la receta actualizada.
                    onSaved = {
                        recipeViewModel.resetForm()
                        navController.popBackStack()
                    },
                    onBack = {
                        recipeViewModel.resetForm()
                        navController.popBackStack()
                    }
                )
            }

            composable(
                route = HomeRoutes.DETALLE_RECETA,
                arguments = listOf(navArgument("id") { type = NavType.IntType })
            ) { entry ->
                val id = entry.arguments?.getInt("id") ?: return@composable
                LaunchedEffect(id) { recipeViewModel.loadRecipe(id) }
                val detailState by recipeViewModel.detailState.collectAsStateWithLifecycle()
                val detailActionState by recipeViewModel.detailActionState.collectAsStateWithLifecycle()
                // Receta eliminada: fuera del feed y de vuelta a "Mis recetas", que muestra el aviso.
                LaunchedEffect(detailActionState.deletedRecipeId) {
                    val deletedId = detailActionState.deletedRecipeId ?: return@LaunchedEffect
                    feedLists.forEach { it.removeRecipe(deletedId) }
                    recipeViewModel.onDeletedHandled()
                    if (!navController.popBackStack(HomeRoutes.RECETAS, inclusive = false)) {
                        // Se abrió desde otra pestaña (Inicio, Diario, Despensa).
                        navController.popBackStack()
                        navController.navigateToTab(HomeRoutes.RECETAS)
                    }
                }
                // Lo que cambie aquí (likes, foto, edición) se copia a la tarjeta del feed.
                LaunchedEffect(detailState) {
                    (detailState as? RecipeDetailUiState.Success)?.let { success -> feedLists.forEach { it.syncRecipe(success.recipe) } }
                }
                // Un comentario publicado desde el detalle también aparece en la tarjeta del feed.
                val postedComment by recipeViewModel.postedComment.collectAsStateWithLifecycle()
                LaunchedEffect(postedComment) {
                    val (recipeId, comment) = postedComment ?: return@LaunchedEffect
                    feedLists.forEach { it.onCommentPosted(recipeId, comment) }
                    recipeViewModel.onPostedCommentHandled()
                }
                val commentDraft by recipeViewModel.commentDraft.collectAsStateWithLifecycle()
                // El LogViewModel compartido: al añadir la receta hoy, el diario se actualiza.
                val quickAddState by logViewModel.quickAddState.collectAsStateWithLifecycle()
                RecipeDetailScreen(
                    state = detailState,
                    onBack = { navController.popBackStack() },
                    onRetry = { recipeViewModel.loadRecipe(id) },
                    quickAddState = quickAddState,
                    onAddToDiary = logViewModel::addRecipeFromDetail,
                    onQuickAddMessageShown = logViewModel::onQuickAddMessageShown,
                    currentUserId = user?.id,
                    actions = remember(recipeViewModel) {
                        RecipeDetailActions(
                            onToggleLike = recipeViewModel::toggleLike,
                            onUpdatePhoto = recipeViewModel::updatePhoto,
                            onMessage = recipeViewModel::showDetailMessage,
                            onMessageShown = recipeViewModel::onDetailMessageShown,
                            onEdit = {
                                recipeViewModel.startEditing()
                                navController.navigate(HomeRoutes.EDITAR_RECETA)
                            },
                            onDelete = recipeViewModel::deleteRecipe,
                            onOpenComments = { navController.navigate(HomeRoutes.comentarios(id)) },
                            onCommentDraftChange = recipeViewModel::onCommentDraftChange,
                            onSendComment = recipeViewModel::sendComment,
                            onToggleSave = recipeViewModel::toggleSave
                        )
                    },
                    actionState = detailActionState,
                    currentUserName = user?.name.orEmpty(),
                    commentDraft = commentDraft
                )
            }

            composable(
                route = HomeRoutes.COMENTARIOS,
                arguments = listOf(navArgument("id") { type = NavType.IntType })
            ) { entry ->
                val id = entry.arguments?.getInt("id") ?: return@composable
                // Con ámbito en esta pantalla: al salir se descarta la lista cargada.
                val commentsViewModel: CommentsViewModel = viewModel()
                LaunchedEffect(id) { commentsViewModel.load(id) }
                val commentsState by commentsViewModel.state.collectAsStateWithLifecycle()
                // El recuento y los últimos comentarios se copian a la tarjeta del feed y al detalle.
                val latest = commentsState.comments.take(2)
                LaunchedEffect(commentsState.loaded, commentsState.total, latest) {
                    if (!commentsState.loaded) return@LaunchedEffect
                    feedLists.forEach { it.syncComments(id, commentsState.total, latest.map { comment -> comment.toPreview() }) }
                    recipeViewModel.syncCommentsCount(id, commentsState.total)
                }
                CommentsScreen(
                    state = commentsState,
                    currentUserId = user?.id,
                    currentUserName = user?.name.orEmpty(),
                    actions = remember(commentsViewModel) {
                        CommentsActions(
                            onBack = { navController.popBackStack() },
                            onRetry = commentsViewModel::retry,
                            onLoadMore = commentsViewModel::loadMore,
                            onDraftChange = commentsViewModel::onDraftChange,
                            onSend = commentsViewModel::send,
                            onDelete = commentsViewModel::delete,
                            onMessageShown = commentsViewModel::onMessageShown
                        )
                    }
                )
            }
        }
    }
}

/** Formulario de receta, compartido por crear y editar (el modo lo lleva el estado del ViewModel). */
@Composable
private fun RecipeFormDestination(recipeViewModel: RecipeViewModel, onSaved: () -> Unit, onBack: () -> Unit) {
    val formState by recipeViewModel.formState.collectAsStateWithLifecycle()
    RecipeFormScreen(
        state = formState,
        onTitleChange = recipeViewModel::onTitleChange,
        onDescriptionChange = recipeViewModel::onDescriptionChange,
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
        onSaved = onSaved,
        onBack = onBack,
        onDismissOcrNotice = recipeViewModel::dismissOcrNotice,
        onPhotoChange = recipeViewModel::onPhotoChange
    )
}

/** Acciones de una lista de tarjetas de receta (feed, buscador o guardadas) sobre su ViewModel. */
private fun feedActions(viewModel: FeedViewModel, navController: NavHostController) = FeedActions(
    onRefresh = viewModel::refresh,
    onLoadMore = viewModel::loadMore,
    onToggleLike = viewModel::toggleLike,
    onRecipeClick = { id -> navController.navigate(HomeRoutes.detalleReceta(id)) },
    onCreateRecipe = {},
    onMessageShown = viewModel::onMessageShown,
    onOpenComments = { id -> navController.navigate(HomeRoutes.comentarios(id)) },
    onCommentDraftChange = viewModel::onCommentDraftChange,
    onSendComment = viewModel::sendComment,
    onToggleSave = viewModel::toggleSave
)

/** Navegación estándar entre pestañas: conserva y restaura el estado de cada una. */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
