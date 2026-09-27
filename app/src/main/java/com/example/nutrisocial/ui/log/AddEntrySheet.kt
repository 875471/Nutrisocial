package com.example.nutrisocial.ui.log

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.nutrisocial.data.FoodSuggestion
import com.example.nutrisocial.data.Recipe
import com.example.nutrisocial.ui.home.formatKcal
import com.example.nutrisocial.ui.recipes.RecipeListUiState
import com.example.nutrisocial.ui.recipes.formatNumber
import com.example.nutrisocial.ui.recipes.suggestionDetail
import com.example.nutrisocial.ui.theme.ButtonShape
import com.example.nutrisocial.ui.theme.CardShape
import com.example.nutrisocial.ui.theme.Spacing
import kotlin.math.roundToInt

data class AddEntryActions(
    val onOpen: () -> Unit,
    val onDismiss: () -> Unit,
    val onSourceChange: (AddSource) -> Unit,
    val onRecipeSelected: (Recipe?) -> Unit,
    val onServingsChange: (String) -> Unit,
    val onFoodQueryChange: (String) -> Unit,
    val onFoodSelected: (FoodSuggestion?) -> Unit,
    val onGramsChange: (String) -> Unit,
    val onSave: () -> Unit
)

/** Hoja inferior para añadir al día una de mis recetas (por raciones) o un alimento (por gramos). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEntrySheet(state: AddEntryState, recipesState: RecipeListUiState, actions: AddEntryActions) {
    ModalBottomSheet(
        onDismissRequest = actions.onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text("Añadir al diario", style = MaterialTheme.typography.titleLarge)

            val sources = listOf(AddSource.RECIPE to "Mis recetas", AddSource.FOOD to "Alimento suelto")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                sources.forEachIndexed { index, (source, label) ->
                    SegmentedButton(
                        selected = state.source == source,
                        onClick = { actions.onSourceChange(source) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = sources.size),
                        label = { Text(label) }
                    )
                }
            }

            when (state.source) {
                AddSource.RECIPE -> RecipePicker(state, recipesState, actions)
                AddSource.FOOD -> FoodPicker(state, actions)
            }

            state.error?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = actions.onSave,
                enabled = state.canSave,
                shape = ButtonShape,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    val kcal = state.previewKcal
                    Text(if (kcal != null) "Añadir (${formatKcal(kcal.roundToInt())} kcal)" else "Añadir")
                }
            }
        }
    }
}

@Composable
private fun RecipePicker(state: AddEntryState, recipesState: RecipeListUiState, actions: AddEntryActions) {
    when (recipesState) {
        RecipeListUiState.Loading -> Text("Cargando tus recetas…", style = MaterialTheme.typography.bodyMedium)
        is RecipeListUiState.Error -> Text(recipesState.message, color = MaterialTheme.colorScheme.error)
        is RecipeListUiState.Success -> if (recipesState.recipes.isEmpty()) {
            Text(
                text = "Aún no tienes recetas. Crea una en «Mis recetas» o añade un alimento suelto.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 260.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                items(recipesState.recipes, key = { it.id }) { recipe ->
                    SelectableRow(
                        title = recipe.title,
                        detail = "${formatNumber(recipe.nutrition.perServing.kcal)} kcal/ración",
                        selected = state.selectedRecipe?.id == recipe.id,
                        onClick = { actions.onRecipeSelected(recipe) }
                    )
                }
            }
            OutlinedTextField(
                value = state.servings,
                onValueChange = actions.onServingsChange,
                label = { Text("Raciones consumidas") },
                singleLine = true,
                isError = state.servings.isNotBlank() && state.servingsValue == null,
                supportingText = { Text("Entre 0,1 y 20. Admite medias raciones (0,5).") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun FoodPicker(state: AddEntryState, actions: AddEntryActions) {
    OutlinedTextField(
        value = state.foodQuery,
        onValueChange = actions.onFoodQueryChange,
        label = { Text("Buscar alimento") },
        placeholder = { Text("Ej.: manzana, arroz, yogur") },
        singleLine = true,
        trailingIcon = when {
            state.selectedFood != null -> {
                { Icon(Icons.Filled.Check, contentDescription = "Alimento elegido", tint = MaterialTheme.colorScheme.primary) }
            }
            state.isSearching -> {
                { CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp)) }
            }
            else -> null
        },
        modifier = Modifier.fillMaxWidth()
    )
    if (state.selectedFood == null && state.suggestions.isNotEmpty()) {
        LazyColumn(
            modifier = Modifier.heightIn(max = 260.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            items(state.suggestions, key = { it.id }) { food ->
                SelectableRow(
                    title = food.name,
                    detail = suggestionDetail(food),
                    selected = false,
                    onClick = { actions.onFoodSelected(food) }
                )
            }
        }
    } else if (state.selectedFood == null && state.foodQuery.trim().length >= 2 && !state.isSearching) {
        Text(
            text = "No se han encontrado alimentos con ese nombre.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    state.selectedFood?.let { food ->
        Text(
            text = "Por 100 g: ${food.kcal.roundToInt()} kcal · ${formatNumber(food.protein)} g proteínas · " +
                "${formatNumber(food.carbs)} g hidratos · ${formatNumber(food.fat)} g grasas",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = state.grams,
            onValueChange = actions.onGramsChange,
            label = { Text("Cantidad") },
            suffix = { Text("g") },
            singleLine = true,
            isError = state.grams.isNotBlank() && state.gramsValue == null,
            supportingText = { Text("Entre 1 y 5000 g") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SelectableRow(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = CardShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (selected) Spacing.sm else 0.dp)
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = Spacing.sm)
            )
        }
    }
}
