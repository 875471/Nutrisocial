package com.example.nutrisocial.ui.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.nutrisocial.data.FoodRef
import com.example.nutrisocial.data.FoodSuggestion
import com.example.nutrisocial.data.IngredientUnits
import com.example.nutrisocial.ui.theme.ButtonShape
import com.example.nutrisocial.ui.theme.CardShape
import com.example.nutrisocial.ui.theme.NutriSocialTheme
import com.example.nutrisocial.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeFormScreen(
    state: RecipeFormState,
    onTitleChange: (String) -> Unit,
    onServingsChange: (String) -> Unit,
    onPrepMinutesChange: (String) -> Unit,
    ingredientActions: IngredientActions,
    onStepChange: (Int, String) -> Unit,
    onAddStep: () -> Unit,
    onRemoveStep: (Int) -> Unit,
    onSave: () -> Unit,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    onDismissOcrNotice: () -> Unit = {}
) {
    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }
    val enabled = !state.isSaving

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.fromOcr) "Revisar receta escaneada" else "Nueva receta") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            if (state.fromOcr) OcrNotice(onDismiss = onDismissOcrNotice)

            OutlinedTextField(
                value = state.title,
                onValueChange = onTitleChange,
                label = { Text("Título") },
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OutlinedTextField(
                    value = state.servings,
                    onValueChange = onServingsChange,
                    label = { Text("Raciones") },
                    singleLine = true,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = state.prepMinutes,
                    onValueChange = onPrepMinutesChange,
                    label = { Text("Tiempo (opcional)") },
                    suffix = { Text("min") },
                    singleLine = true,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f)
                )
            }

            IngredientsSection(state = state, enabled = enabled, actions = ingredientActions)

            EditableListSection(
                title = "Pasos",
                items = state.steps,
                itemLabel = { "Paso ${it + 1}" },
                addLabel = "Añadir paso",
                singleLine = false,
                enabled = enabled,
                onItemChange = onStepChange,
                onAdd = onAddStep,
                onRemove = onRemoveStep
            )

            if (state.error != null) {
                Text(
                    text = state.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Button(
                onClick = onSave,
                enabled = enabled,
                shape = ButtonShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Text("Guardar receta")
                }
            }
        }
    }
}

/** Aviso de que el contenido es una propuesta automática, no una receta guardada. */
@Composable
private fun OcrNotice(onDismiss: () -> Unit) {
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(start = Spacing.md, top = Spacing.sm, bottom = Spacing.sm)) {
            Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.padding(top = Spacing.xs).size(20.dp))
            Column(modifier = Modifier.weight(1f).padding(start = Spacing.sm, top = Spacing.xs)) {
                Text("Extraído automáticamente de una foto", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Revisa el título, los ingredientes, las cantidades y los pasos antes de guardar: " +
                        "la lectura puede tener errores. Los valores nutricionales se calculan al guardar.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Ocultar aviso")
            }
        }
    }
}

/** Acciones de las filas de ingredientes, agrupadas para no alargar la firma de la pantalla. */
data class IngredientActions(
    val onNameChange: (Int, String) -> Unit,
    val onQuantityChange: (Int, String) -> Unit,
    val onUnitChange: (Int, String) -> Unit,
    val onSuggestionSelected: (Int, FoodSuggestion) -> Unit,
    val onNameFocusLost: (Int) -> Unit,
    val onAdd: () -> Unit,
    val onRemove: (Int) -> Unit
) {
    companion object {
        val Noop = IngredientActions({ _, _ -> }, { _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {}, {})
    }
}

@Composable
private fun IngredientsSection(state: RecipeFormState, enabled: Boolean, actions: IngredientActions) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(text = "Ingredientes", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Elige el alimento de la lista para afinar el cálculo nutricional. " +
                "Deja la cantidad vacía si es «al gusto».",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        state.ingredients.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))
            IngredientRow(
                index = index,
                item = item,
                suggestions = if (state.suggestionsFor == index) state.suggestions else emptyList(),
                enabled = enabled,
                actions = actions
            )
        }
        OutlinedButton(onClick = actions.onAdd, enabled = enabled, shape = ButtonShape) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = "Añadir ingrediente", modifier = Modifier.padding(start = Spacing.sm))
        }
    }
}

@Composable
private fun IngredientRow(
    index: Int,
    item: IngredientFormItem,
    suggestions: List<FoodSuggestion>,
    enabled: Boolean,
    actions: IngredientActions
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = item.name,
                onValueChange = { actions.onNameChange(index, it) },
                label = { Text("Ingrediente ${index + 1}") },
                singleLine = true,
                enabled = enabled,
                trailingIcon = if (item.food != null) {
                    {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Alimento reconocido",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else null,
                supportingText = item.food?.takeIf { !it.name.equals(item.name.trim(), ignoreCase = true) }?.let { food ->
                    { Text("Se calculará como «${food.name}»") }
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { if (!it.isFocused) actions.onNameFocusLost(index) }
            )
            IconButton(onClick = { actions.onRemove(index) }, enabled = enabled) {
                Icon(Icons.Filled.Close, contentDescription = "Quitar ingrediente ${index + 1}")
            }
        }

        if (suggestions.isNotEmpty()) {
            SuggestionList(suggestions = suggestions, onSelect = { actions.onSuggestionSelected(index, it) })
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedTextField(
                value = item.quantity,
                onValueChange = { actions.onQuantityChange(index, it) },
                label = { Text("Cantidad") },
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            UnitDropdown(
                unit = item.unit,
                enabled = enabled,
                onUnitChange = { actions.onUnitChange(index, it) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Sugerencias del autocompletado, dibujadas en línea bajo el campo para no quitarle el foco. */
@Composable
private fun SuggestionList(suggestions: List<FoodSuggestion>, onSelect: (FoodSuggestion) -> Unit) {
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = Spacing.xs)) {
            suggestions.take(6).forEach { food ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(food) }
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = food.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${food.kcal.toInt()} kcal/100 g",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.sm)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitDropdown(unit: String, enabled: Boolean, onUnitChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = unit,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            enabled = enabled,
            label = { Text("Unidad") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            IngredientUnits.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onUnitChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun EditableListSection(
    title: String,
    items: List<String>,
    itemLabel: (Int) -> String,
    addLabel: String,
    singleLine: Boolean,
    enabled: Boolean,
    onItemChange: (Int, String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        items.forEachIndexed { index, value ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { onItemChange(index, it) },
                    label = { Text(itemLabel(index)) },
                    singleLine = singleLine,
                    minLines = if (singleLine) 1 else 2,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onRemove(index) }, enabled = enabled) {
                    Icon(Icons.Filled.Close, contentDescription = "Quitar ${itemLabel(index).lowercase()}")
                }
            }
        }
        OutlinedButton(onClick = onAdd, enabled = enabled, shape = ButtonShape) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = addLabel, modifier = Modifier.padding(start = Spacing.sm))
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun RecipeFormScreenPreview() {
    NutriSocialTheme {
        RecipeFormScreen(
            state = RecipeFormState(
                title = "Crema de calabaza",
                ingredients = listOf(
                    IngredientFormItem("Calabaza, cruda", "800", "g", FoodRef(1, "Calabaza, cruda")),
                    IngredientFormItem("Cebo", "1", "unidad")
                ),
                suggestionsFor = 1,
                suggestions = listOf(
                    FoodSuggestion(2, "Cebolla, cruda", 32.0, 1.2, 5.3, 0.2),
                    FoodSuggestion(3, "Cebolleta, cruda", 27.0, 1.8, 4.4, 0.2)
                ),
                steps = listOf("Pelar y trocear la calabaza."),
                servings = "4",
                error = "Añade al menos un paso"
            ),
            onTitleChange = {}, onServingsChange = {}, onPrepMinutesChange = {},
            ingredientActions = IngredientActions.Noop,
            onStepChange = { _, _ -> }, onAddStep = {}, onRemoveStep = {},
            onSave = {}, onSaved = {}, onBack = {}
        )
    }
}
