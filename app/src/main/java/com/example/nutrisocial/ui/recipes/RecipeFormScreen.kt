package com.example.nutrisocial.ui.recipes

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.nutrisocial.ui.theme.ButtonShape
import com.example.nutrisocial.ui.theme.NutriSocialTheme
import com.example.nutrisocial.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeFormScreen(
    state: RecipeFormState,
    onTitleChange: (String) -> Unit,
    onServingsChange: (String) -> Unit,
    onPrepMinutesChange: (String) -> Unit,
    onItemChange: (RecipeListField, Int, String) -> Unit,
    onAddItem: (RecipeListField) -> Unit,
    onRemoveItem: (RecipeListField, Int) -> Unit,
    onSave: () -> Unit,
    onSaved: () -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }
    val enabled = !state.isSaving

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nueva receta") },
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

            EditableListSection(
                title = "Ingredientes",
                items = state.ingredients,
                itemLabel = { "Ingrediente ${it + 1}" },
                addLabel = "Añadir ingrediente",
                singleLine = true,
                enabled = enabled,
                onItemChange = { index, value -> onItemChange(RecipeListField.INGREDIENTS, index, value) },
                onAdd = { onAddItem(RecipeListField.INGREDIENTS) },
                onRemove = { onRemoveItem(RecipeListField.INGREDIENTS, it) }
            )

            EditableListSection(
                title = "Pasos",
                items = state.steps,
                itemLabel = { "Paso ${it + 1}" },
                addLabel = "Añadir paso",
                singleLine = false,
                enabled = enabled,
                onItemChange = { index, value -> onItemChange(RecipeListField.STEPS, index, value) },
                onAdd = { onAddItem(RecipeListField.STEPS) },
                onRemove = { onRemoveItem(RecipeListField.STEPS, it) }
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
                ingredients = listOf("1 calabaza", "1 cebolla"),
                steps = listOf("Pelar y trocear la calabaza."),
                servings = "4",
                error = "Añade al menos un paso"
            ),
            onTitleChange = {}, onServingsChange = {}, onPrepMinutesChange = {},
            onItemChange = { _, _, _ -> }, onAddItem = {}, onRemoveItem = { _, _ -> },
            onSave = {}, onSaved = {}, onBack = {}
        )
    }
}
