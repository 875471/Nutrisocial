package com.example.nutrisocial.ui.log

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import com.example.nutrisocial.ui.recipes.InlineEmptyState
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import com.example.nutrisocial.R
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.nutrisocial.data.DailyLog
import com.example.nutrisocial.data.DailyRecommendations
import com.example.nutrisocial.data.LogEntry
import com.example.nutrisocial.data.Macros
import com.example.nutrisocial.data.RecipeRecommendation
import com.example.nutrisocial.data.RemainingMacros
import com.example.nutrisocial.ui.dayLabel
import com.example.nutrisocial.ui.home.decimalInput
import com.example.nutrisocial.ui.home.formatKcal
import com.example.nutrisocial.ui.home.parseDecimal
import com.example.nutrisocial.ui.isoToPickerMillis
import com.example.nutrisocial.ui.pickerMillisToIso
import com.example.nutrisocial.ui.recipes.CenteredMessage
import com.example.nutrisocial.ui.recipes.InfoPill
import com.example.nutrisocial.ui.recipes.InitialBadge
import com.example.nutrisocial.ui.recipes.LoadingBox
import com.example.nutrisocial.ui.recipes.RecipeListUiState
import com.example.nutrisocial.ui.recipes.formatNumber
import com.example.nutrisocial.ui.theme.ButtonShape
import com.example.nutrisocial.ui.theme.CardElevation
import com.example.nutrisocial.ui.theme.CardShape
import com.example.nutrisocial.ui.theme.NutriSocialTheme
import com.example.nutrisocial.ui.theme.Spacing
import com.example.nutrisocial.ui.todayIso
import kotlin.math.roundToInt

/** Acciones de la pantalla del diario, agrupadas como [com.example.nutrisocial.ui.recipes.IngredientActions]. */
data class LogActions(
    val onPreviousDay: () -> Unit,
    val onNextDay: () -> Unit,
    val onToday: () -> Unit,
    val onSelectDate: (String) -> Unit,
    val onRetry: () -> Unit,
    val onDelete: (Int) -> Unit,
    val onMessageShown: () -> Unit,
    val onOpenProfile: () -> Unit,
    val onAddRecommendation: (RecipeRecommendation) -> Unit,
    val onRetryRecommendations: () -> Unit,
    val add: AddEntryActions,
    // Abre el detalle de una receta (recomendada o ya registrada), como en el resto de listas.
    val onOpenRecipe: (Int) -> Unit = {},
    // Vista de mes (calendario nutricional).
    val onShowMonth: () -> Unit = {},
    val onShowDay: () -> Unit = {},
    val onPreviousMonth: () -> Unit = {},
    val onNextMonth: () -> Unit = {},
    val onCalendarDayClick: (String) -> Unit = {},
    val onRetryCalendar: () -> Unit = {},
    // Nueva cantidad de una entrada: (id, raciones, gramos); solo uno de los dos no es null.
    val onUpdateEntry: (Int, Double?, Double?) -> Unit = { _, _, _ -> }
)

/** Pestaña "Diario": registro de lo que se ha comido cada día frente al objetivo calórico. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    date: String,
    dayState: DayUiState,
    recommendationsState: RecommendationsUiState,
    addingRecommendationId: Int?,
    addState: AddEntryState?,
    recipesState: RecipeListUiState,
    message: String?,
    actions: LogActions,
    viewMode: LogViewMode = LogViewMode.DAY,
    month: String = "",
    calendarState: CalendarUiState = CalendarUiState.Loading
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            actions.onMessageShown()
        }
    }
    var entryToDelete by remember { mutableStateOf<LogEntry?>(null) }
    var entryToEdit by remember { mutableStateOf<LogEntry?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diario") },
                actions = {
                    if (viewMode == LogViewMode.DAY && date != todayIso()) {
                        TextButton(onClick = actions.onToday) { Text("Hoy") }
                    }
                    // Alterna entre la vista del día y el calendario del mes.
                    if (viewMode == LogViewMode.DAY) {
                        IconButton(onClick = actions.onShowMonth) {
                            Icon(painterResource(R.drawable.ic_calendar_month), contentDescription = "Ver el mes")
                        }
                    } else {
                        IconButton(onClick = actions.onShowDay) {
                            Icon(painterResource(R.drawable.ic_today), contentDescription = "Ver el día")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            // Se añade siempre al día seleccionado: en la vista de mes no está claro cuál es.
            if (viewMode == LogViewMode.DAY) ExtendedFloatingActionButton(
                onClick = actions.add.onOpen,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Añadir") },
                shape = ButtonShape,
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (viewMode == LogViewMode.MONTH) {
            Column(modifier = Modifier.padding(padding)) {
                MonthCalendarContent(
                    month = month,
                    state = calendarState,
                    selectedDate = date,
                    onPreviousMonth = actions.onPreviousMonth,
                    onNextMonth = actions.onNextMonth,
                    onDayClick = actions.onCalendarDayClick,
                    onRetry = actions.onRetryCalendar,
                    onOpenProfile = actions.onOpenProfile
                )
            }
        } else Column(modifier = Modifier.padding(padding)) {
            DateSelector(
                date = date,
                onPrevious = actions.onPreviousDay,
                onNext = actions.onNextDay,
                onSelect = actions.onSelectDate
            )
            when (dayState) {
                DayUiState.Loading -> LoadingBox()
                is DayUiState.Error -> CenteredMessage(
                    icon = rememberVectorPainter(Icons.Filled.Warning),
                    isError = true,
                    title = "No se pudo cargar el día",
                    message = dayState.message,
                    actionLabel = "Reintentar",
                    onAction = actions.onRetry
                )
                is DayUiState.Success -> DayContent(
                    log = dayState.log,
                    recommendationsState = recommendationsState,
                    addingRecommendationId = addingRecommendationId,
                    onDelete = { entryToDelete = it },
                    onEdit = { entryToEdit = it },
                    actions = actions
                )
            }
        }
    }

    entryToDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("¿Quitar del diario?") },
            text = { Text("Se quitará «${entry.name}» (${formatKcal(entry.kcal.roundToInt())} kcal) de este día.") },
            confirmButton = {
                // Mismo color de peligro que el resto de confirmaciones destructivas de la app.
                TextButton(
                    onClick = {
                        actions.onDelete(entry.id)
                        entryToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Quitar") }
            },
            dismissButton = { TextButton(onClick = { entryToDelete = null }) { Text("Cancelar") } }
        )
    }

    entryToEdit?.let { entry ->
        EditEntryDialog(
            entry = entry,
            onConfirm = { servings, grams ->
                actions.onUpdateEntry(entry.id, servings, grams)
                entryToEdit = null
            },
            onDismiss = { entryToEdit = null }
        )
    }

    if (addState != null) {
        AddEntrySheet(state = addState, recipesState = recipesState, actions = actions.add)
    }
}

/** Flechas para ir al día anterior o siguiente y, en el centro, el día (abre un calendario). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateSelector(date: String, onPrevious: () -> Unit, onNext: () -> Unit, onSelect: (String) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Día anterior")
        }
        TextButton(onClick = { showPicker = true }, modifier = Modifier.weight(1f)) {
            Text(
                text = dayLabel(date).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleMedium
            )
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Día siguiente")
        }
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = isoToPickerMillis(date))
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onSelect(pickerMillisToIso(it)) }
                    showPicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancelar") } }
        ) {
            DatePicker(state = pickerState, title = null)
        }
    }
}

@Composable
private fun DayContent(
    log: DailyLog,
    recommendationsState: RecommendationsUiState,
    addingRecommendationId: Int?,
    onDelete: (LogEntry) -> Unit,
    onEdit: (LogEntry) -> Unit,
    actions: LogActions
) {
    LazyColumn(
        // Hueco inferior extra para que el FAB no tape la última entrada.
        contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, top = Spacing.xs, bottom = Spacing.xl * 3),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        item { SummaryCard(log = log, onOpenProfile = actions.onOpenProfile) }
        // Lo registrado va justo debajo del resumen y antes de las recomendaciones: con cinco
        // tarjetas recomendadas delante, la lista quedaba dos pantallas más abajo y parecía que
        // lo añadido no aparecía.
        item { SectionTitle("Registrado") }
        if (log.entries.isEmpty()) {
            item {
                InlineEmptyState(
                    icon = rememberVectorPainter(Icons.Filled.DateRange),
                    text = "Todavía no has añadido nada este día. Pulsa «Añadir» para registrar una de tus " +
                        "recetas o un alimento suelto."
                )
            }
        }
        items(log.entries, key = { it.id }) { entry ->
            EntryRow(
                entry = entry,
                onDelete = { onDelete(entry) },
                // Sin la receta o el alimento de origen no hay con qué recalcular los valores.
                onEdit = if (entry.recipeId != null || entry.foodId != null) {
                    { onEdit(entry) }
                } else null,
                // Los alimentos sueltos no tienen detalle; las recetas borradas tampoco (recipeId null).
                onOpen = entry.recipeId?.let { id -> { actions.onOpenRecipe(id) } }
            )
        }
        recommendationsSection(
            state = recommendationsState,
            addingRecipeId = addingRecommendationId,
            actions = actions
        )
    }
}

/**
 * Resumen del día: kcal consumidas frente al objetivo con su barra de progreso y, debajo, lo
 * mismo en pequeño para proteínas, hidratos y grasas.
 */
@Composable
private fun SummaryCard(log: DailyLog, onOpenProfile: () -> Unit) {
    val goal = log.dailyCalorieGoal
    val consumed = log.totals.kcal.roundToInt()
    val exceeded = (log.excessKcal ?: 0) > 0
    ElevatedCard(
        shape = CardShape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = CardElevation),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = formatKcal(consumed), style = MaterialTheme.typography.displaySmall)
                Text(
                    text = if (goal != null) "de ${formatKcal(goal)} kcal" else "kcal",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = Spacing.sm, bottom = 6.dp)
                )
            }
            if (goal != null) {
                LinearProgressIndicator(
                    progress = { (log.progress ?: 0.0).toFloat().coerceIn(0f, 1f) },
                    color = if (exceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                )
                Text(
                    text = if (exceeded) "Te has pasado ${formatKcal(log.excessKcal ?: 0)} kcal del objetivo"
                    else "Te quedan ${formatKcal(log.remainingKcal ?: 0)} kcal",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (exceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer
                )
            } else {
                Text(
                    text = "Completa tu perfil para ver tu objetivo calórico diario.",
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = onOpenProfile, contentPadding = PaddingValues(0.dp)) { Text("Ir al perfil") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                MacroProgressStat("Proteínas", log.totals.protein, log.proteinGoal, log.proteinProgress, log.excessProtein, Modifier.weight(1f))
                MacroProgressStat("Hidratos", log.totals.carbs, log.carbsGoal, log.carbsProgress, log.excessCarbs, Modifier.weight(1f))
                MacroProgressStat("Grasas", log.totals.fat, log.fatGoal, log.fatProgress, log.excessFat, Modifier.weight(1f))
            }
        }
    }
}

/**
 * Gramos consumidos de un macronutriente y, si hay objetivo ([goal] no null), "de X g" y una
 * barra fina que se pone en color de error al pasarse, igual que la de kcal. Sin objetivo
 * (perfil incompleto) solo muestra los gramos.
 */
@Composable
private fun MacroProgressStat(
    label: String,
    grams: Double,
    goal: Double?,
    progress: Double?,
    excess: Double?,
    modifier: Modifier = Modifier
) {
    val exceeded = (excess ?: 0.0) > 0
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = Spacing.sm, horizontal = Spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "${formatNumber(grams)} g",
                style = MaterialTheme.typography.titleMedium,
                color = if (exceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (goal != null) {
                Text(
                    text = "de ${goal.roundToInt()} g",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
                LinearProgressIndicator(
                    progress = { (progress ?: 0.0).toFloat().coerceIn(0f, 1f) },
                    color = if (exceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.sm, vertical = 2.dp)
                        .height(4.dp)
                )
            }
            Text(text = label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = Spacing.sm)
    )
}

/**
 * "Recomendado para completar tu día": recetas que encajan con lo que queda o, si no hay, el
 * motivo (perfil incompleto u objetivo ya cubierto). Las claves llevan prefijo para no chocar
 * con los ids de las entradas del diario, que comparten la misma lista.
 */
private fun LazyListScope.recommendationsSection(
    state: RecommendationsUiState,
    addingRecipeId: Int?,
    actions: LogActions
) {
    item(key = "recommendations-title") { SectionTitle("Recomendado para completar tu día") }
    when (state) {
        RecommendationsUiState.Loading -> item(key = "recommendations-loading") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            }
        }
        is RecommendationsUiState.Error -> item(key = "recommendations-error") {
            NoticeCard(
                title = "No se pudieron cargar las recomendaciones",
                message = state.message,
                actionLabel = "Reintentar",
                onAction = actions.onRetryRecommendations
            )
        }
        is RecommendationsUiState.Success -> {
            val data = state.data
            when {
                data.reason == DailyRecommendations.REASON_INCOMPLETE_PROFILE -> item(key = "recommendations-profile") {
                    NoticeCard(
                        title = "Completa tu perfil",
                        message = "Con tu objetivo calórico podremos sugerirte recetas que encajen con lo que te queda cada día.",
                        actionLabel = "Ir al perfil",
                        onAction = actions.onOpenProfile
                    )
                }
                data.reason == DailyRecommendations.REASON_GOAL_COVERED -> item(key = "recommendations-covered") {
                    GoalCoveredCard()
                }
                data.recommendations.isEmpty() -> item(key = "recommendations-empty") {
                    InlineEmptyState(
                        icon = rememberVectorPainter(Icons.Filled.Search),
                        text = "Ninguna receta encaja ahora con lo que te queda" +
                            (data.remaining?.let { " (${formatKcal(it.kcal)} kcal)" } ?: "") +
                            ". Prueba con una receta más ligera o añade un alimento suelto."
                    )
                }
                else -> items(data.recommendations, key = { "recommendation-${it.id}" }) { recommendation ->
                    RecommendationCard(
                        recommendation = recommendation,
                        isAdding = addingRecipeId == recommendation.id,
                        enabled = addingRecipeId == null,
                        onAdd = { actions.onAddRecommendation(recommendation) },
                        onOpen = { actions.onOpenRecipe(recommendation.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NoticeCard(title: String, message: String, actionLabel: String, onAction: () -> Unit) {
    ElevatedCard(
        shape = CardShape,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = CardElevation),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.md)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onAction, contentPadding = PaddingValues(0.dp)) { Text(actionLabel) }
        }
    }
}

@Composable
private fun GoalCoveredCard() {
    ElevatedCard(
        shape = CardShape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = CardElevation),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null)
            Column(modifier = Modifier.padding(start = Spacing.md)) {
                Text(text = "¡Objetivo del día cubierto!", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Ya has llegado a tus kcal de hoy, no hace falta sumar más recetas.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/** Mismo estilo que [com.example.nutrisocial.ui.recipes.RecipeCard], con valores por ración y el motivo. */
@Composable
private fun RecommendationCard(
    recommendation: RecipeRecommendation,
    isAdding: Boolean,
    enabled: Boolean,
    onAdd: () -> Unit,
    onOpen: () -> Unit
) {
    ElevatedCard(
        onClick = onOpen,
        shape = CardShape,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = CardElevation),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InitialBadge(text = recommendation.title)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Text(
                        text = recommendation.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    InfoPill(
                        text = "${formatKcal(recommendation.kcalPorRacion)} kcal/ración",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            Text(
                text = "Proteínas ${formatNumber(recommendation.proteinPorRacion)} g · " +
                    "Hidratos ${formatNumber(recommendation.carbsPorRacion)} g · " +
                    "Grasas ${formatNumber(recommendation.fatPorRacion)} g",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = recommendation.motivo,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(
                onClick = onAdd,
                enabled = enabled,
                shape = ButtonShape,
                modifier = Modifier.align(Alignment.End)
            ) {
                if (isAdding) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Text("Añadir al registro", modifier = Modifier.padding(start = Spacing.sm))
            }
        }
    }
}

/** "1 ración", "1,5 raciones" o "150 g". */
fun entryQuantityLabel(entry: LogEntry): String = when {
    entry.grams != null -> "${formatNumber(entry.grams)} g"
    entry.servings != null -> "${formatNumber(entry.servings)} " + if (entry.servings == 1.0) "ración" else "raciones"
    else -> ""
}

@Composable
private fun EntryRow(entry: LogEntry, onDelete: () -> Unit, onEdit: (() -> Unit)?, onOpen: (() -> Unit)?) {
    ElevatedCard(
        shape = CardShape,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = CardElevation),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onOpen != null) Modifier.clip(CardShape).clickable(onClick = onOpen) else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.md, top = Spacing.sm, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InitialBadge(text = entry.name)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Spacing.md)
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOf(
                        if (entry.type == "food") "Alimento" else "Receta",
                        entryQuantityLabel(entry)
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = entryMacrosLabel(entry),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${formatKcal(entry.kcal.roundToInt())} kcal",
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(start = Spacing.sm)
            )
            if (onEdit != null) {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Editar ${entry.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Quitar ${entry.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * "P: 18 g · H: 32 g · G: 9 g". Cada valor va unido a su unidad con espacios de no separación
 * para que, si la línea no cabe, no se corte entre el número y la "g".
 */
fun entryMacrosLabel(entry: LogEntry): String =
    listOf("P" to entry.protein, "H" to entry.carbs, "G" to entry.fat)
        .joinToString(" · ") { (label, grams) -> "$label: ${formatNumber(grams)} g" }

/**
 * Cambia las raciones (receta) o los gramos (alimento) de una entrada, con los mismos rangos que
 * al añadirla. Las kcal aproximadas salen de escalar las actuales de la entrada; el valor exacto
 * lo recalcula el servidor con la receta o el alimento.
 */
@Composable
private fun EditEntryDialog(entry: LogEntry, onConfirm: (servings: Double?, grams: Double?) -> Unit, onDismiss: () -> Unit) {
    val isFood = entry.type == "food"
    val current = if (isFood) entry.grams else entry.servings
    var text by rememberSaveable(entry.id) { mutableStateOf(current?.let(::formatNumber) ?: "") }
    val range = if (isFood) 1.0..5000.0 else 0.1..20.0
    val value = parseDecimal(text)?.takeIf { it in range }
    val previewKcal = if (value != null && current != null && current > 0) entry.kcal * value / current else null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar cantidad") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("«${entry.name}»")
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = decimalInput(it, if (isFood) 6 else 4) },
                    label = { Text(if (isFood) "Gramos" else "Raciones") },
                    singleLine = true,
                    isError = value == null,
                    supportingText = {
                        Text(
                            when {
                                value == null -> if (isFood) "Entre 1 y 5000 g" else "Entre 0,1 y 20 raciones"
                                previewKcal != null -> "≈ ${formatKcal(previewKcal.roundToInt())} kcal"
                                else -> ""
                            }
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { value?.let { if (isFood) onConfirm(null, it) else onConfirm(it, null) } },
                enabled = value != null && value != current
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private val PreviewAddActions = AddEntryActions({}, {}, {}, {}, {}, {}, {}, {}, {})

@Preview(showBackground = true)
@Composable
private fun LogScreenPreview() {
    val log = DailyLog(
        date = "2026-09-27",
        entries = listOf(
            LogEntry(1, "2026-09-27", "recipe", "Crema de calabaza", recipeId = 1, servings = 1.5, kcal = 310.0, protein = 8.0, carbs = 40.2, fat = 10.7),
            LogEntry(2, "2026-09-27", "food", "Manzana, cruda", foodId = 2, grams = 150.0, kcal = 78.0, protein = 0.4, carbs = 21.0, fat = 0.3)
        ),
        totals = Macros(388.0, 8.4, 61.2, 11.0),
        dailyCalorieGoal = 2000,
        remainingKcal = 1612,
        excessKcal = 0,
        progress = 0.194,
        proteinGoal = 175.0, remainingProtein = 166.6, excessProtein = 0.0, proteinProgress = 0.048,
        carbsGoal = 175.0, remainingCarbs = 113.8, excessCarbs = 0.0, carbsProgress = 0.35,
        fatGoal = 10.0, remainingFat = 0.0, excessFat = 1.0, fatProgress = 1.1
    )
    NutriSocialTheme {
        LogScreen(
            date = todayIso(),
            dayState = DayUiState.Success(log),
            recommendationsState = RecommendationsUiState.Success(
                DailyRecommendations(
                    date = "2026-09-27",
                    remaining = RemainingMacros(1612, 164.0, 249.0, 56.0),
                    recommendations = listOf(
                        RecipeRecommendation(
                            3, "Pollo al horno con patatas", 640, 48.0, 62.0, 20.5,
                            "Te aporta 640 kcal y 48 g de proteína, encaja con lo que te queda hoy"
                        )
                    )
                )
            ),
            addingRecommendationId = null,
            addState = null,
            recipesState = RecipeListUiState.Success(emptyList()),
            message = null,
            actions = LogActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, PreviewAddActions)
        )
    }
}
