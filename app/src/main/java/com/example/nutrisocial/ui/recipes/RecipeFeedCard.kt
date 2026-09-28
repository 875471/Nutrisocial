package com.example.nutrisocial.ui.recipes

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.nutrisocial.R
import com.example.nutrisocial.data.CommentPreview
import com.example.nutrisocial.data.FeedRecipe
import com.example.nutrisocial.data.MAX_COMMENT_LENGTH
import com.example.nutrisocial.data.Recipe
import com.example.nutrisocial.data.RecipeIngredient
import com.example.nutrisocial.ui.InitialsAvatar
import com.example.nutrisocial.ui.formatRelativeTime
import com.example.nutrisocial.ui.theme.CardElevation
import com.example.nutrisocial.ui.theme.CardShape
import com.example.nutrisocial.ui.theme.NutriSocialTheme
import com.example.nutrisocial.ui.theme.Spacing

// Tarjeta de receta con la estructura de las publicaciones de Hevy: cabecera con autor y
// tiempo, título y descripción, barra de estadísticas, carrusel (foto → ingredientes → pasos),
// acciones, "Le gusta a...", últimos comentarios y campo para comentar. La usan el feed y,
// con la lista completa desplegada, el detalle de la receta.

/** Lo que enseña la tarjeta, sacado de una [FeedRecipe] (feed) o de una [Recipe] (detalle). */
data class RecipeCardData(
    val id: Int,
    val title: String,
    val description: String?,
    val authorName: String,
    val createdAt: String,
    val imageBase64: String?,
    val prepMinutes: Int?,
    val kcalPerServing: Double,
    val proteinPerServing: Double,
    val servings: Int,
    val ingredients: List<CardIngredient>,
    val steps: List<String>,
    val likesCount: Int,
    val likedByMe: Boolean,
    val likersPreview: List<String>,
    val commentsCount: Int,
    val commentsPreview: List<CommentPreview>,
    val savedByMe: Boolean = false
)

/** Ingrediente del carrusel: "200 g · Harina de trigo" y, en el detalle, un aviso del cálculo. */
data class CardIngredient(val amount: String?, val name: String, val note: String? = null) {
    val label: String get() = if (amount == null) name else "$amount · $name"
}

private fun RecipeIngredient.amountLabel(): String? = quantity?.let { quantityLabel(it, unit) }

fun FeedRecipe.toCardData() = RecipeCardData(
    id = id,
    title = title,
    description = description,
    authorName = authorName,
    createdAt = createdAt,
    imageBase64 = imageBase64,
    prepMinutes = prepMinutes,
    kcalPerServing = kcalPerServing,
    proteinPerServing = proteinPerServing,
    servings = servings,
    ingredients = ingredients.map { CardIngredient(it.amountLabel(), it.name) },
    steps = steps,
    likesCount = likesCount,
    likedByMe = likedByMe,
    likersPreview = likersPreview,
    commentsCount = commentsCount,
    commentsPreview = commentsPreview,
    savedByMe = savedByMe
)

/** [ingredientNote] añade a cada ingrediente lo que haya que saber de su cálculo nutricional. */
fun Recipe.toCardData(ingredientNote: (RecipeIngredient) -> String? = { null }) = RecipeCardData(
    id = id,
    title = title,
    description = description,
    authorName = authorName,
    createdAt = createdAt,
    imageBase64 = imageBase64,
    prepMinutes = prepMinutes,
    kcalPerServing = nutrition.perServing.kcal,
    proteinPerServing = nutrition.perServing.protein,
    servings = servings,
    ingredients = ingredients.map { CardIngredient(it.amountLabel(), it.name, ingredientNote(it)) },
    steps = steps,
    likesCount = likesCount,
    likedByMe = likedByMe,
    likersPreview = likersPreview,
    commentsCount = commentsCount,
    // El detalle no trae vista previa: se enlaza a la lista completa.
    commentsPreview = emptyList(),
    savedByMe = savedByMe
)

/** Texto del campo de comentario de una tarjeta y si se está enviando. */
data class CommentDraft(val text: String = "", val isSending: Boolean = false) {
    val canSend: Boolean get() = text.isNotBlank() && !isSending
}

/** Acciones de la tarjeta. Con [onOpen] null (en el propio detalle) la tarjeta no se puede pulsar. */
class RecipeCardActions(
    val onOpen: (() -> Unit)?,
    val onToggleLike: () -> Unit,
    val onOpenComments: () -> Unit,
    val onDraftChange: (String) -> Unit,
    val onSendComment: () -> Unit,
    val onToggleSave: () -> Unit = {}
) {
    companion object {
        val Noop = RecipeCardActions(null, {}, {}, {}, {})
    }
}

/**
 * Tarjeta completa. [currentUserName] se usa para el avatar del campo de comentario y para
 * redactar "Te gusta a ti...". En el detalle, [expandLists] despliega ingredientes y pasos y
 * [showCommentsPreview] = false cambia la vista previa por el enlace a los comentarios.
 */
@Composable
fun RecipeFeedCard(
    recipe: RecipeCardData,
    currentUserName: String,
    draft: CommentDraft,
    actions: RecipeCardActions,
    modifier: Modifier = Modifier,
    expandLists: Boolean = false,
    showCommentsPreview: Boolean = true
) {
    ElevatedCard(
        shape = CardShape,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = CardElevation),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = Spacing.md)) {
            val openModifier = actions.onOpen?.let { Modifier.clickable(onClickLabel = "Abrir la receta", onClick = it) } ?: Modifier
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = openModifier.padding(horizontal = Spacing.md)
            ) {
                RecipeCardHeader(authorName = recipe.authorName, createdAt = recipe.createdAt)
                Text(text = recipe.title, style = MaterialTheme.typography.titleLarge)
                recipe.description?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                RecipeStatsBar(recipe, modifier = Modifier.padding(vertical = Spacing.xs))
            }
            RecipeCarousel(
                recipe = recipe,
                expandLists = expandLists,
                onPhotoClick = actions.onOpen,
                modifier = Modifier.padding(top = Spacing.sm)
            )
            RecipeActionsRow(recipe = recipe, actions = actions, modifier = Modifier.padding(horizontal = Spacing.xs))
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.padding(horizontal = Spacing.md)
            ) {
                likersText(recipe.likesCount, recipe.likedByMe, recipe.likersPreview, currentUserName)?.let {
                    Text(
                        text = it.toAnnotatedString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (showCommentsPreview) {
                    recipe.commentsPreview.take(2).forEach { CommentPreviewLine(it) }
                }
                val shown = if (showCommentsPreview) recipe.commentsPreview.size.coerceAtMost(2) else 0
                if (recipe.commentsCount > shown) {
                    Text(
                        text = if (recipe.commentsCount == 1) "Ver el comentario" else "Ver los ${recipe.commentsCount} comentarios",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clickable(onClick = actions.onOpenComments)
                            .padding(vertical = Spacing.xs)
                    )
                }
                CommentInput(
                    userName = currentUserName,
                    draft = draft,
                    onDraftChange = actions.onDraftChange,
                    onSend = actions.onSendComment,
                    modifier = Modifier.padding(top = Spacing.xs)
                )
            }
        }
    }
}

/** Avatar con iniciales, nombre del autor y hace cuánto se publicó. */
@Composable
fun RecipeCardHeader(authorName: String, createdAt: String, modifier: Modifier = Modifier) {
    val name = authorName.ifBlank { "Usuario de NutriSocial" }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        InitialsAvatar(name = name)
        Column(modifier = Modifier.padding(start = Spacing.md)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val time = formatRelativeTime(createdAt)
            if (time.isNotEmpty()) {
                Text(
                    text = time,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Tiempo, kcal, raciones y proteína en una fila, separados por líneas verticales finas. */
@Composable
private fun RecipeStatsBar(recipe: RecipeCardData, modifier: Modifier = Modifier) {
    // Sin datos nutricionales (ningún ingrediente con peso conocido) se muestra "—", no un 0 engañoso.
    val hasNutrition = recipe.kcalPerServing > 0
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        StatItem(R.drawable.ic_schedule, "Tiempo", recipe.prepMinutes?.let(::prepTimeLabel) ?: "—", Modifier.weight(1f))
        StatDivider()
        StatItem(R.drawable.ic_whatshot, "Kcal", if (hasNutrition) formatNumber(recipe.kcalPerServing) else "—", Modifier.weight(1f))
        StatDivider()
        StatItem(R.drawable.ic_restaurant, "Raciones", recipe.servings.toString(), Modifier.weight(1f))
        StatDivider()
        StatItem(
            R.drawable.ic_fitness_center,
            "Proteína",
            if (hasNutrition) "${formatNumber(recipe.proteinPerServing)} g" else "—",
            Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatDivider() {
    VerticalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier
            .fillMaxHeight()
            .padding(horizontal = Spacing.sm)
    )
}

@Composable
private fun StatItem(@DrawableRes icon: Int, label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        // Un lector de pantalla lee "Kcal: 410" de una vez, en vez de icono, etiqueta y número sueltos.
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $value" }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(start = Spacing.xs)
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private enum class CarouselPage { PHOTO, INGREDIENTS, STEPS }

/**
 * Carrusel deslizable: la foto (si la hay), los ingredientes y los pasos, con puntos debajo.
 * Con foto, las páginas de texto miden al menos lo mismo que ella, para que la tarjeta no
 * cambie de alto al deslizar mientras las listas están plegadas.
 */
@Composable
private fun RecipeCarousel(
    recipe: RecipeCardData,
    expandLists: Boolean,
    onPhotoClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val photo = recipe.imageBase64
    val pages = buildList {
        if (photo != null) add(CarouselPage.PHOTO)
        add(CarouselPage.INGREDIENTS)
        add(CarouselPage.STEPS)
    }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints {
            val minPageHeight = if (photo != null) (maxWidth - Spacing.md * 2) * 0.75f else 0.dp
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = Spacing.md),
                pageSpacing = Spacing.sm,
                verticalAlignment = Alignment.Top
            ) { index ->
                when (pages.getOrNull(index)) {
                    CarouselPage.PHOTO -> photo?.let {
                        val clickModifier = onPhotoClick?.let { Modifier.clickable(onClickLabel = "Abrir la receta", onClick = it) } ?: Modifier
                        Base64Image(
                            base64 = it,
                            contentDescription = "Foto de ${recipe.title}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(4f / 3f)
                                .clip(CardShape)
                                .then(clickModifier)
                        )
                    }
                    CarouselPage.INGREDIENTS -> CarouselListPage(
                        title = "Ingredientes",
                        items = recipe.ingredients,
                        collapsedCount = 5,
                        moreLabel = { n -> if (n == 1) "Ver 1 ingrediente más" else "Ver $n ingredientes más" },
                        expandedByDefault = expandLists,
                        stateKey = "ingredients-${recipe.id}",
                        modifier = Modifier.heightIn(min = minPageHeight)
                    ) { _, ingredient -> IngredientLine(ingredient) }
                    CarouselPage.STEPS -> CarouselListPage(
                        title = "Preparación",
                        items = recipe.steps,
                        collapsedCount = 3,
                        moreLabel = { n -> if (n == 1) "Ver 1 paso más" else "Ver $n pasos más" },
                        expandedByDefault = expandLists,
                        stateKey = "steps-${recipe.id}",
                        modifier = Modifier.heightIn(min = minPageHeight)
                    ) { i, step -> StepLine(i + 1, step) }
                    null -> Unit
                }
            }
        }
        PagerDots(current = pagerState.currentPage, count = pages.size, modifier = Modifier.padding(top = Spacing.sm))
    }
}

/** Página de lista del carrusel: los primeros [collapsedCount] elementos y "Ver N más" para desplegar. */
@Composable
private fun <T> CarouselListPage(
    title: String,
    items: List<T>,
    collapsedCount: Int,
    moreLabel: (Int) -> String,
    expandedByDefault: Boolean,
    stateKey: String,
    modifier: Modifier = Modifier,
    itemContent: @Composable (Int, T) -> Unit
) {
    var expanded by rememberSaveable(stateKey) { mutableStateOf(expandedByDefault) }
    val visible = if (expanded) items else items.take(collapsedCount)
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.padding(Spacing.md)
        ) {
            Text(
                text = "$title · ${items.size}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            visible.forEachIndexed { i, item -> itemContent(i, item) }
            val hidden = items.size - visible.size
            if (hidden > 0) {
                TextButton(onClick = { expanded = true }, contentPadding = PaddingValues(0.dp)) { Text(moreLabel(hidden)) }
            } else if (expanded && items.size > collapsedCount && !expandedByDefault) {
                TextButton(onClick = { expanded = false }, contentPadding = PaddingValues(0.dp)) { Text("Ver menos") }
            }
        }
    }
}

@Composable
private fun IngredientLine(ingredient: CardIngredient) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            painter = painterResource(R.drawable.ic_brand_plate),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp)
        )
        Column(modifier = Modifier.padding(start = Spacing.sm)) {
            Text(text = ingredient.label, style = MaterialTheme.typography.bodyMedium)
            ingredient.note?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StepLine(number: Int, step: String) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = "$number", style = MaterialTheme.typography.labelMedium)
            }
        }
        Text(
            text = step,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.sm, top = 2.dp)
        )
    }
}

@Composable
private fun PagerDots(current: Int, count: Int, modifier: Modifier = Modifier) {
    if (count < 2) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.semantics { contentDescription = "Página ${current + 1} de $count" }
    ) {
        repeat(count) { i ->
            Box(
                modifier = Modifier
                    .size(if (i == current) 8.dp else 6.dp)
                    .background(
                        if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape
                    )
            )
        }
    }
}

/** Me gusta (el corazón animado de siempre), comentarios, guardar y compartir. */
@Composable
private fun RecipeActionsRow(recipe: RecipeCardData, actions: RecipeCardActions, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        LikeButton(liked = recipe.likedByMe, count = recipe.likesCount, onToggle = actions.onToggleLike)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics(mergeDescendants = true) {}
        ) {
            IconButton(onClick = actions.onOpenComments) {
                Icon(
                    painter = painterResource(R.drawable.ic_chat_bubble_outline),
                    contentDescription = "Comentarios",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = recipe.commentsCount.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { contentDescription = "${recipe.commentsCount} comentarios" }
            )
        }
        Box(modifier = Modifier.weight(1f))
        IconButton(onClick = actions.onToggleSave) {
            Icon(
                painter = painterResource(if (recipe.savedByMe) R.drawable.ic_bookmark else R.drawable.ic_bookmark_border),
                contentDescription = if (recipe.savedByMe) "Quitar de guardadas" else "Guardar receta",
                tint = if (recipe.savedByMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = {
            // No hay enlaces a recetas concretas: se comparte el título como texto.
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Mira esta receta en NutriSocial: ${recipe.title}")
            }
            try {
                context.startActivity(Intent.createChooser(send, "Compartir receta"))
            } catch (e: ActivityNotFoundException) {
                // Sin ninguna app para compartir texto no hay nada que hacer.
            }
        }) {
            Icon(Icons.Filled.Share, contentDescription = "Compartir", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "Le gusta a *Lucía* y a otras 4 personas", con el nombre en negrita. */
data class LikersText(val before: String, val name: String?, val after: String) {
    val plain: String get() = before + name.orEmpty() + after

    fun toAnnotatedString(): AnnotatedString = buildAnnotatedString {
        append(before)
        name?.let { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(it) } }
        append(after)
    }
}

private fun otherPeople(n: Int): String = if (n == 1) "otra persona" else "otras $n personas"

/**
 * Frase de "me gusta" bajo las acciones, o null si no hay ninguno. [likersPreview] puede ir
 * por detrás de [likedByMe] (el like propio se pinta al momento, sin recargar los nombres), así
 * que el nombre del propio usuario se descarta y, si le gusta, se dice "Te gusta a ti".
 */
fun likersText(likesCount: Int, likedByMe: Boolean, likersPreview: List<String>, myName: String): LikersText? {
    if (likesCount <= 0) return null
    val named = likersPreview.firstOrNull { it != myName }
    if (likedByMe) {
        val others = likesCount - 1
        return when {
            others <= 0 -> LikersText("Te gusta a ti", null, "")
            named == null -> LikersText("Te gusta a ti y a $others ${if (others == 1) "persona" else "personas"} más", null, "")
            others == 1 -> LikersText("Te gusta a ti y a ", named, "")
            else -> LikersText("Te gusta a ti, a ", named, " y a ${otherPeople(others - 1)}")
        }
    }
    return when {
        named == null -> LikersText(if (likesCount == 1) "Le gusta a 1 persona" else "Le gusta a $likesCount personas", null, "")
        likesCount == 1 -> LikersText("Le gusta a ", named, "")
        else -> LikersText("Le gusta a ", named, " y a ${otherPeople(likesCount - 1)}")
    }
}

@Composable
private fun CommentPreviewLine(comment: CommentPreview) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(comment.authorName) }
            append("  ")
            append(comment.text)
        },
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

/** Campo compacto "Añadir un comentario..." con el avatar propio y el botón de enviar. */
@Composable
fun CommentInput(
    userName: String,
    draft: CommentDraft,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        InitialsAvatar(name = userName, size = 32.dp)
        OutlinedTextField(
            value = draft.text,
            onValueChange = { onDraftChange(it.take(MAX_COMMENT_LENGTH)) },
            placeholder = { Text("Añadir un comentario...") },
            textStyle = MaterialTheme.typography.bodyMedium,
            enabled = !draft.isSending,
            maxLines = 4,
            shape = MaterialTheme.shapes.extraLarge,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Send
            ),
            keyboardActions = KeyboardActions(onSend = { if (draft.canSend) onSend() }),
            trailingIcon = {
                if (draft.isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = onSend, enabled = draft.canSend) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Publicar comentario")
                    }
                }
            },
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.sm)
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun RecipeFeedCardPreview() {
    NutriSocialTheme {
        RecipeFeedCard(
            recipe = RecipeCardData(
                id = 1,
                title = "Crema de calabaza",
                description = "Para las tardes de otoño. Con un chorrito de nata queda aún más suave.",
                authorName = "Lucía Martín",
                createdAt = "2026-09-28T10:00:00.000Z",
                imageBase64 = null,
                prepMinutes = 35,
                kcalPerServing = 140.0,
                proteinPerServing = 3.1,
                servings = 4,
                ingredients = listOf(
                    CardIngredient("800 g", "Calabaza"),
                    CardIngredient("1 unidad", "Cebolla"),
                    CardIngredient("2 cucharadas", "Aceite de oliva"),
                    CardIngredient("500 ml", "Caldo de verduras"),
                    CardIngredient("1 pizca", "Sal"),
                    CardIngredient(null, "Pimienta")
                ),
                steps = listOf("Pelar y trocear.", "Pochar la cebolla.", "Añadir la calabaza y el caldo.", "Triturar."),
                likesCount = 5,
                likedByMe = false,
                likersPreview = listOf("Hugo Fernández", "Carmen Ruiz"),
                commentsCount = 4,
                commentsPreview = listOf(
                    CommentPreview(2, "¡Me ha encantado!", "Pablo Gómez"),
                    CommentPreview(1, "Le puse jengibre y genial", "Marta Sánchez")
                )
            ),
            currentUserName = "Ana",
            draft = CommentDraft(),
            actions = RecipeCardActions.Noop
        )
    }
}
