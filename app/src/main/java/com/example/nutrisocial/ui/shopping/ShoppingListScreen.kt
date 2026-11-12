package com.example.nutrisocial.ui.shopping

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.nutrisocial.data.ShoppingListItem
import com.example.nutrisocial.ui.recipes.CenteredMessage
import com.example.nutrisocial.ui.theme.CardElevation
import com.example.nutrisocial.ui.theme.CardShape
import com.example.nutrisocial.ui.theme.NutriSocialTheme
import com.example.nutrisocial.ui.theme.Spacing

/** Acciones de la pantalla, agrupadas para no alargar su firma. */
data class ShoppingListActions(
    val onRetry: () -> Unit,
    val onRefresh: () -> Unit,
    val onNewLabelChange: (String) -> Unit,
    val onNewQuantityChange: (String) -> Unit,
    val onAdd: () -> Unit,
    val onToggle: (ShoppingListItem) -> Unit,
    val onDelete: (ShoppingListItem) -> Unit,
    val onClearChecked: () -> Unit,
    val onOpenRecipe: (Int) -> Unit,
    val onMessageShown: () -> Unit
) {
    companion object {
        val Noop = ShoppingListActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

/**
 * Lista de la compra: un campo para añadir algo suelto, lo pendiente y, debajo, lo ya comprado
 * con un botón para vaciarlo. Lo que falta de una receta se añade desde su detalle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(state: ShoppingListUiState, actions: ShoppingListActions) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            actions.onMessageShown()
        }
    }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lista de la compra") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val items = state.items
        when {
            items == null && state.loadError != null -> CenteredMessage(
                isError = true,
                title = "No se pudo cargar la lista",
                message = state.loadError,
                actionLabel = "Reintentar",
                onAction = actions.onRetry,
                modifier = Modifier.padding(padding)
            )
            items == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = actions.onRefresh,
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                val pending = items.filterNot { it.checked }
                val checked = items.filter { it.checked }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().imePadding(),
                    contentPadding = PaddingValues(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    item(key = "input") { AddItemField(state = state, actions = actions) }
                    if (items.isEmpty()) {
                        item(key = "empty") {
                            CenteredMessage(
                                icon = rememberVectorPainter(Icons.Filled.ShoppingCart),
                                title = "Tu lista está vacía",
                                message = "Apunta aquí lo que tengas que comprar, o abre una receta y pulsa " +
                                    "«Añadir lo que falta a la lista de la compra».",
                                modifier = Modifier.padding(top = Spacing.lg)
                            )
                        }
                    }
                    if (pending.isNotEmpty()) {
                        item(key = "pending") {
                            ItemsCard(title = "Por comprar (${pending.size})", items = pending, actions = actions)
                        }
                    }
                    if (checked.isNotEmpty()) {
                        item(key = "checked") {
                            ItemsCard(
                                title = "Comprado (${checked.size})",
                                items = checked,
                                actions = actions,
                                trailing = {
                                    TextButton(onClick = { confirmClear = true }, enabled = !state.isClearing) {
                                        Text("Vaciar comprados")
                                    }
                                }
                            )
                        }
                        item(key = "pantry-note") {
                            // Marcar no pasa nada a la despensa: es un paso manual, y conviene decirlo.
                            Text(
                                text = "Lo que marcas como comprado no se añade solo a tu despensa: apúntalo allí si quieres usarlo para buscar recetas.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("¿Vaciar lo comprado?") },
            text = { Text("Se quitarán de la lista los ${state.checkedCount} elementos marcados.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        actions.onClearChecked()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Vaciar") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun AddItemField(state: ShoppingListUiState, actions: ShoppingListActions) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = state.newLabel,
            onValueChange = actions.onNewLabelChange,
            label = { Text("Añadir a la lista") },
            singleLine = true,
            enabled = !state.isAdding,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            modifier = Modifier.weight(2f)
        )
        OutlinedTextField(
            value = state.newQuantity,
            onValueChange = actions.onNewQuantityChange,
            label = { Text("Cantidad") },
            singleLine = true,
            enabled = !state.isAdding,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { actions.onAdd() }),
            modifier = Modifier.weight(1f)
        )
        if (state.isAdding) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            IconButton(onClick = actions.onAdd, enabled = state.newLabel.isNotBlank()) {
                Icon(Icons.Filled.Add, contentDescription = "Añadir a la lista de la compra")
            }
        }
    }
}

@Composable
private fun ItemsCard(
    title: String,
    items: List<ShoppingListItem>,
    actions: ShoppingListActions,
    trailing: (@Composable () -> Unit)? = null
) {
    ElevatedCard(
        shape = CardShape,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = CardElevation),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = Spacing.sm)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = Spacing.md, end = Spacing.xs)
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
            items.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
                ItemRow(item = item, actions = actions)
            }
        }
    }
}

@Composable
private fun ItemRow(item: ShoppingListItem, actions: ShoppingListActions) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = if (item.checked) "Desmarcar" else "Marcar como comprado") { actions.onToggle(item) }
            .padding(start = Spacing.xs)
    ) {
        Checkbox(checked = item.checked, onCheckedChange = { actions.onToggle(item) })
        Column(modifier = Modifier.weight(1f)) {
            val muted = MaterialTheme.colorScheme.onSurfaceVariant
            Text(
                text = item.quantity?.let { "${item.label} · $it" } ?: item.label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (item.checked) muted else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (item.checked) TextDecoration.LineThrough else null
            )
            item.sourceRecipeTitle?.let { title ->
                Text(
                    text = "Para «$title»",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClickLabel = "Abrir la receta") {
                        item.sourceRecipeId?.let(actions.onOpenRecipe)
                    }
                )
            }
        }
        IconButton(onClick = { actions.onDelete(item) }) {
            Icon(Icons.Filled.Close, contentDescription = "Quitar ${item.label}")
        }
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun ShoppingListScreenPreview() {
    NutriSocialTheme {
        ShoppingListScreen(
            state = ShoppingListUiState(
                items = listOf(
                    ShoppingListItem(1, "Harina de trigo", "250 g", sourceRecipeId = 3, sourceRecipeTitle = "Bizcocho"),
                    ShoppingListItem(2, "Leche", "2 l"),
                    ShoppingListItem(3, "Pan", checked = true)
                )
            ),
            actions = ShoppingListActions.Noop
        )
    }
}
