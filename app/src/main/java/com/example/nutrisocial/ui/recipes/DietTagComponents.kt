package com.example.nutrisocial.ui.recipes

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.nutrisocial.data.DIET_TAGS_DISCLAIMER
import com.example.nutrisocial.data.DietTag
import com.example.nutrisocial.ui.theme.NutriSocialTheme
import com.example.nutrisocial.ui.theme.Spacing

/**
 * Fila desplazable de chips para elegir etiquetas dietéticas: en el formulario de receta (las que
 * declara el autor) y en los filtros de Explorar y de la despensa (las que se exigen todas a la vez).
 */
@Composable
fun DietTagChips(
    selected: Set<DietTag>,
    onToggle: (DietTag) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding)
    ) {
        DietTag.entries.forEach { tag ->
            val isSelected = tag in selected
            FilterChip(
                selected = isSelected,
                onClick = { onToggle(tag) },
                enabled = enabled,
                label = { Text(tag.label) },
                leadingIcon = if (isSelected) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else null
            )
        }
    }
}

/** Sección del formulario: las etiquetas y, siempre visible debajo, el aviso de que no se verifican. */
@Composable
fun DietTagFormSection(selected: Set<DietTag>, onToggle: (DietTag) -> Unit, enabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(text = "Etiquetas dietéticas (opcional)", style = MaterialTheme.typography.titleMedium)
        DietTagChips(selected = selected, onToggle = onToggle, enabled = enabled)
        DietTagDisclaimer()
    }
}

/**
 * Aviso de que las etiquetas las declara quien publica la receta y NutriSocial no las comprueba:
 * alguien con alergia o intolerancia debe mirar la lista de ingredientes.
 */
@Composable
fun DietTagDisclaimer(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Filled.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 1.dp).size(14.dp)
        )
        Text(
            text = DIET_TAGS_DISCLAIMER,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.xs)
        )
    }
}

/** Etiquetas de una receta como chips pequeños de solo lectura (tarjeta del feed y detalle). Nada si no tiene. */
@Composable
fun DietTagBadges(tagIds: List<String>, modifier: Modifier = Modifier) {
    val tags = DietTag.fromIds(tagIds)
    if (tags.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = modifier.horizontalScroll(rememberScrollState())
    ) {
        tags.forEach { tag ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Text(
                    text = tag.label,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DietTagComponentsPreview() {
    NutriSocialTheme {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            DietTagFormSection(selected = setOf(DietTag.VEGANO, DietTag.SIN_GLUTEN), onToggle = {}, enabled = true)
            DietTagBadges(listOf("vegano", "sin_gluten"))
        }
    }
}
