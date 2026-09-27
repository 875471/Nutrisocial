package com.example.nutrisocial.ui.recipes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.nutrisocial.data.FoodRef
import com.example.nutrisocial.data.Macros
import com.example.nutrisocial.data.Nutrition
import com.example.nutrisocial.data.Recipe
import com.example.nutrisocial.data.RecipeIngredient
import com.example.nutrisocial.data.isOpenFoodFacts
import com.example.nutrisocial.ui.home.decimalInput
import com.example.nutrisocial.ui.home.formatKcal
import com.example.nutrisocial.ui.home.parseDecimal
import com.example.nutrisocial.ui.log.QuickAddState
import com.example.nutrisocial.ui.theme.ButtonShape
import com.example.nutrisocial.ui.theme.CardShape
import com.example.nutrisocial.ui.theme.NutriSocialTheme
import com.example.nutrisocial.ui.theme.Spacing
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    state: RecipeDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    quickAddState: QuickAddState = QuickAddState(),
    onAddToDiary: (recipeId: Int, servings: Double) -> Unit = { _, _ -> },
    onQuickAddMessageShown: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(quickAddState.message) {
        quickAddState.message?.let {
            snackbarHostState.showSnackbar(it)
            onQuickAddMessageShown()
        }
    }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            if (state is RecipeDetailUiState.Success) {
                ExtendedFloatingActionButton(
                    onClick = { if (!quickAddState.isSaving) showAddDialog = true },
                    icon = {
                        if (quickAddState.isSaving) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(Icons.Filled.Add, contentDescription = null)
                        }
                    },
                    text = { Text("Añadir a mi diario de hoy") },
                    shape = ButtonShape,
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Receta") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when (state) {
            RecipeDetailUiState.Loading -> LoadingBox(contentModifier)
            is RecipeDetailUiState.Error -> CenteredMessage(
                title = "No se pudo cargar la receta",
                message = state.message,
                actionLabel = "Reintentar",
                onAction = onRetry,
                modifier = contentModifier
            )
            is RecipeDetailUiState.Success -> RecipeDetailContent(state.recipe, contentModifier)
        }
    }

    if (showAddDialog && state is RecipeDetailUiState.Success) {
        AddToDiaryDialog(
            recipe = state.recipe,
            onConfirm = { servings ->
                showAddDialog = false
                onAddToDiary(state.recipe.id, servings)
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

/** Pide cuántas raciones se han comido y muestra las kcal resultantes antes de confirmar. */
@Composable
private fun AddToDiaryDialog(recipe: Recipe, onConfirm: (Double) -> Unit, onDismiss: () -> Unit) {
    var servings by rememberSaveable { mutableStateOf("1") }
    // Mismo rango que la hoja "Añadir al diario" y que valida el servidor.
    val value = parseDecimal(servings)?.takeIf { it in 0.1..20.0 }
    val kcal = recipe.nutrition.perServing.kcal
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir a mi diario de hoy") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("¿Cuántas raciones de «${recipe.title}» has comido?")
                OutlinedTextField(
                    value = servings,
                    onValueChange = { servings = decimalInput(it, 4) },
                    label = { Text("Raciones") },
                    singleLine = true,
                    isError = value == null,
                    supportingText = {
                        Text(
                            when {
                                value == null -> "Entre 0,1 y 20 raciones"
                                kcal > 0 -> "≈ ${formatKcal((kcal * value).roundToInt())} kcal"
                                else -> "Esta receta no tiene datos nutricionales"
                            }
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { value?.let(onConfirm) }, enabled = value != null) { Text("Añadir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun RecipeDetailContent(recipe: Recipe, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md)
            // Hueco para que el botón flotante no tape el último paso.
            .padding(bottom = 72.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(text = recipe.title, style = MaterialTheme.typography.headlineMedium)
            RecipeInfoRow(servings = recipe.servings, prepMinutes = recipe.prepMinutes)
        }

        NutritionSection(recipe)

        DetailSection(title = "Ingredientes") {
            recipe.ingredients.forEach { ingredient ->
                Row {
                    Box(
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .size(Spacing.sm)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                    Column(modifier = Modifier.padding(start = Spacing.md)) {
                        Text(text = ingredient.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = ingredientDetail(ingredient),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        DetailSection(title = "Preparación") {
            recipe.steps.forEachIndexed { index, step ->
                Row {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(Spacing.xl)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = "${index + 1}", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Text(
                        text = step,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = Spacing.md, top = Spacing.xs)
                    )
                }
            }
        }
    }
}

/** Cantidad, peso estimado y avisos de un ingrediente, en una línea secundaria. */
private fun ingredientDetail(ingredient: RecipeIngredient): String {
    val quantity = ingredient.quantity
    val amount = when {
        quantity == null -> "Sin cantidad"
        ingredient.unit in listOf("g", "ml") -> "${formatNumber(quantity)} ${ingredient.unit}"
        ingredient.grams != null -> "${quantityLabel(quantity, ingredient.unit)} (≈ ${formatNumber(ingredient.grams)} g)"
        else -> quantityLabel(quantity, ingredient.unit)
    }
    val food = ingredient.food
    val note = when {
        quantity == null -> null
        food == null -> "sin datos nutricionales"
        ingredient.grams == null -> "no se pudo estimar su peso"
        // Emparejado por nombre en el servidor: se muestra con qué alimento se calculó.
        !food.name.equals(ingredient.name, ignoreCase = true) -> "calculado como «${food.name}»"
        else -> null
    }
    // Los datos de Open Food Facts son menos fiables que los de BEDCA: se indica siempre.
    val source = if (ingredient.grams != null && isOpenFoodFacts(food?.source)) "datos de Open Food Facts" else null
    return listOfNotNull(amount, note, source).joinToString(" · ")
}

private fun quantityLabel(quantity: Double, unit: String?): String {
    val plural = quantity != 1.0 && unit in listOf("unidad", "cucharada", "cucharadita", "taza", "pizca")
    val unitText = when {
        unit == null -> ""
        plural && unit == "unidad" -> "unidades"
        plural -> unit + "s"
        else -> unit
    }
    return "${formatNumber(quantity)} $unitText".trim()
}

@Composable
private fun NutritionSection(recipe: Recipe) {
    val nutrition = recipe.nutrition
    DetailSection(title = "Información nutricional") {
        if (nutrition.total.kcal <= 0.0 && nutrition.uncountedIngredients.size == recipe.ingredients.size) {
            Text(
                text = "No hay datos suficientes para calcularla. Indica la cantidad de los ingredientes " +
                    "y elígelos de la lista al crear la receta.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@DetailSection
        }

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = formatNumber(nutrition.perServing.kcal),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "kcal por ración",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.sm, bottom = 6.dp)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            MacroStat("Proteínas", nutrition.perServing.protein, Modifier.weight(1f))
            MacroStat("Hidratos", nutrition.perServing.carbs, Modifier.weight(1f))
            MacroStat("Grasas", nutrition.perServing.fat, Modifier.weight(1f))
        }
        nutrition.gramsPerServing?.let { perServing ->
            ServingWeight(perServing = perServing, total = nutrition.totalWeightGrams)
        }
        Text(
            text = "Receta completa (${servingsLabel(recipe.servings)}): ${formatNumber(nutrition.total.kcal)} kcal · " +
                "${formatNumber(nutrition.total.protein)} g proteínas · ${formatNumber(nutrition.total.carbs)} g hidratos · " +
                "${formatNumber(nutrition.total.fat)} g grasas",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (nutrition.uncountedIngredients.isNotEmpty()) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Valores aproximados. No se han contado: " +
                        nutrition.uncountedIngredients.joinToString(", ") + ".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.sm)
                )
            }
        }
        val usesOpenFoodFacts = recipe.ingredients.any { it.grams != null && isOpenFoodFacts(it.food?.source) }
        Text(
            text = "Fuente: BEDCA (Base de Datos Española de Composición de Alimentos)" +
                if (usesOpenFoodFacts) " y, para lo que no está en ella, Open Food Facts." else ".",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Peso aproximado de la ración (en crudo), para dar sentido a los valores por ración. */
@Composable
private fun ServingWeight(perServing: Double, total: Double?) {
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "1 ración ≈ ${formatNumber(perServing)} g",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            if (total != null) {
                Text(
                    text = "Receta ≈ ${formatNumber(total)} g en crudo",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
fun MacroStat(label: String, grams: Double, modifier: Modifier = Modifier) {
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "${formatNumber(grams)} g", style = MaterialTheme.typography.titleMedium)
            Text(text = label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    ElevatedCard(
        shape = CardShape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = RecipeCardElevation),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun RecipeDetailScreenPreview() {
    NutriSocialTheme {
        RecipeDetailScreen(
            state = RecipeDetailUiState.Success(
                Recipe(
                    id = 1,
                    title = "Crema de calabaza",
                    ingredients = listOf(
                        RecipeIngredient("Calabaza, cruda", 800.0, "g", 800.0, FoodRef(1, "Calabaza, cruda")),
                        RecipeIngredient("Cebolla, cruda", 1.0, "unidad", 150.0, FoodRef(2, "Cebolla, cruda")),
                        RecipeIngredient("Aceite de oliva", 2.0, "cucharada", 30.0, FoodRef(3, "Aceite de oliva")),
                        RecipeIngredient("Sal y pimienta")
                    ),
                    steps = listOf(
                        "Pelar y trocear la calabaza y la cebolla.",
                        "Pochar la cebolla con un poco de aceite.",
                        "Añadir la calabaza, cubrir de agua y cocer 20 minutos. Triturar."
                    ),
                    servings = 4,
                    prepMinutes = 35,
                    authorId = 1,
                    createdAt = "",
                    nutrition = Nutrition(
                        total = Macros(560.0, 12.4, 58.0, 31.2),
                        perServing = Macros(140.0, 3.1, 14.5, 7.8),
                        totalWeightGrams = 980.0,
                        gramsPerServing = 245.0,
                        uncountedIngredients = listOf("Sal y pimienta")
                    )
                )
            ),
            onBack = {},
            onRetry = {}
        )
    }
}
