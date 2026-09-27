package com.example.nutrisocial.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.nutrisocial.data.ActivityOptions
import com.example.nutrisocial.data.GoalOptions
import com.example.nutrisocial.data.Profile
import com.example.nutrisocial.data.ProfileOption
import com.example.nutrisocial.data.SexOptions
import com.example.nutrisocial.data.User
import com.example.nutrisocial.data.profileFieldLabel
import com.example.nutrisocial.ui.displayDate
import com.example.nutrisocial.ui.isoToPickerMillis
import com.example.nutrisocial.ui.pickerMillisToIso
import com.example.nutrisocial.ui.recipes.CenteredMessage
import com.example.nutrisocial.ui.recipes.LoadingBox
import com.example.nutrisocial.ui.recipes.RecipeCardElevation
import com.example.nutrisocial.ui.theme.ButtonShape
import com.example.nutrisocial.ui.theme.CardShape
import com.example.nutrisocial.ui.theme.NutriSocialTheme
import com.example.nutrisocial.ui.theme.Spacing
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

/** Acciones del formulario de perfil, agrupadas para no pasar una lambda por campo. */
data class ProfileActions(
    val onBirthDateChange: (String) -> Unit,
    val onHeightChange: (String) -> Unit,
    val onWeightChange: (String) -> Unit,
    val onSexChange: (String) -> Unit,
    val onActivityChange: (String) -> Unit,
    val onGoalChange: (String) -> Unit,
    val onSave: () -> Unit,
    val onRetry: () -> Unit,
    val onSavedMessageShown: () -> Unit
)

/** Pestaña "Perfil": datos personales, objetivo calórico diario y cierre de sesión. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    user: User?,
    state: ProfileUiState,
    actions: ProfileActions,
    onLogout: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.savedMessage) {
        state.savedMessage?.let {
            snackbarHostState.showSnackbar(it)
            actions.onSavedMessageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Perfil") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when {
            state.isLoading -> LoadingBox(contentModifier)
            // Aunque el perfil no cargue (sin conexión, o una sesión de un usuario que ya no
            // existe), cerrar sesión tiene que seguir siendo posible.
            state.profile == null -> Column(
                modifier = contentModifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CenteredMessage(
                    title = "No se pudo cargar tu perfil",
                    message = state.loadError ?: "Inténtalo de nuevo en unos segundos.",
                    actionLabel = "Reintentar",
                    onAction = actions.onRetry,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = onLogout,
                    shape = ButtonShape,
                    modifier = Modifier.padding(bottom = Spacing.lg)
                ) {
                    Text("Cerrar sesión")
                }
            }
            else -> Column(
                modifier = contentModifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                UserHeader(name = user?.name ?: state.profile.name, email = user?.email ?: state.profile.email)
                CalorieGoalCard(state.profile)
                ProfileForm(state = state, actions = actions)
                OutlinedButton(
                    onClick = onLogout,
                    shape = ButtonShape,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = Spacing.lg)
                ) {
                    Text("Cerrar sesión")
                }
            }
        }
    }
}

@Composable
private fun UserHeader(name: String, email: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = name.trim().firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
        Column(modifier = Modifier.padding(start = Spacing.md)) {
            Text(text = name, style = MaterialTheme.typography.titleLarge)
            Text(
                text = email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Número de kcal con separador de miles: 2759 → "2.759". */
fun formatKcal(value: Int): String = NumberFormat.getIntegerInstance(Locale("es", "ES")).format(value)

/**
 * Objetivo calórico destacado. Con el perfil incompleto se muestra, en su lugar, qué datos
 * faltan para poder calcularlo.
 */
@Composable
private fun CalorieGoalCard(profile: Profile) {
    val goal = profile.dailyCalorieGoal
    val complete = goal != null
    ElevatedCard(
        shape = CardShape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (complete) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (complete) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = RecipeCardElevation),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            if (goal != null) {
                Text("Tu objetivo diario", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(text = formatKcal(goal), style = MaterialTheme.typography.displayMedium)
                    Text(
                        text = "kcal/día",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = Spacing.sm, bottom = Spacing.sm)
                    )
                }
                val goalLabel = GoalOptions.find { it.value == profile.goal }?.label
                Text(
                    text = listOfNotNull(
                        goalLabel,
                        profile.bmr?.let { "metabolismo basal ${formatKcal(it)} kcal" },
                        profile.tdee?.let { "gasto estimado ${formatKcal(it)} kcal" }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        text = "Completa tu perfil",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = Spacing.sm)
                    )
                }
                Text(
                    text = "Para calcular tu objetivo calórico diario falta: " +
                        joinSpanish(profile.missingFields.map(::profileFieldLabel)) + ".",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/** ["a", "b", "c"] → "a, b y c". */
private fun joinSpanish(items: List<String>): String = when (items.size) {
    0 -> ""
    1 -> items[0]
    else -> items.dropLast(1).joinToString(", ") + " y " + items.last()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileForm(state: ProfileUiState, actions: ProfileActions) {
    val enabled = !state.isSaving
    ElevatedCard(
        shape = CardShape,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = RecipeCardElevation),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text("Tus datos", style = MaterialTheme.typography.titleMedium)

            BirthDateField(
                birthDate = state.birthDate,
                age = state.profile?.age?.takeIf { state.birthDate == state.profile.birthDate },
                enabled = enabled,
                onChange = actions.onBirthDateChange
            )

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = state.heightCm,
                    onValueChange = actions.onHeightChange,
                    label = { Text("Altura") },
                    suffix = { Text("cm") },
                    singleLine = true,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = state.weightKg,
                    onValueChange = actions.onWeightChange,
                    label = { Text("Peso") },
                    suffix = { Text("kg") },
                    singleLine = true,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

            ChoiceRow(title = "Sexo", options = SexOptions, selected = state.sex, enabled = enabled, onSelect = actions.onSexChange)
            ActivityDropdown(selected = state.activityLevel, enabled = enabled, onSelect = actions.onActivityChange)
            ChoiceRow(title = "Objetivo", options = GoalOptions, selected = state.goal, enabled = enabled, onSelect = actions.onGoalChange)

            state.saveError?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = actions.onSave,
                enabled = enabled,
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
                    Text("Guardar y calcular")
                }
            }

            Text(
                text = "Estimación con la fórmula de Mifflin-St Jeor. Es orientativa y no sustituye " +
                    "el consejo de un profesional sanitario.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Campo de solo lectura que abre un calendario. Solo se pueden elegir edades de 14 a 100 años. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthDateField(birthDate: String, age: Int?, enabled: Boolean, onChange: (String) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val text = if (birthDate.isBlank()) "" else displayDate(birthDate) + (age?.let { " ($it años)" } ?: "")

    OutlinedTextField(
        value = text,
        onValueChange = {},
        label = { Text("Fecha de nacimiento") },
        placeholder = { Text("Elige una fecha") },
        trailingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
        singleLine = true,
        // Deshabilitado para que no abra el teclado; se pinta con los colores normales.
        enabled = false,
        colors = OutlinedTextFieldDefaults.colors(
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
            disabledBorderColor = MaterialTheme.colorScheme.outline,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { showPicker = true }
    )

    if (showPicker) {
        val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = isoToPickerMillis(birthDate),
            // Sin fecha previa, el calendario se abre en un año razonable en vez del actual.
            initialDisplayedMonthMillis = isoToPickerMillis(birthDate) ?: isoToPickerMillis("${currentYear - 30}-01-01"),
            yearRange = (currentYear - 100)..(currentYear - 14),
            selectableDates = remember {
                object : SelectableDates {
                    override fun isSelectableYear(year: Int) = year in (currentYear - 100)..(currentYear - 14)
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { onChange(pickerMillisToIso(it)) }
                        showPicker = false
                    },
                    enabled = pickerState.selectedDateMillis != null
                ) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancelar") } }
        ) {
            DatePicker(state = pickerState, title = null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceRow(
    title: String,
    options: List<ProfileOption>,
    selected: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option.value == selected,
                    onClick = { onSelect(option.value) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    label = { Text(option.label, maxLines = 1) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityDropdown(selected: String?, enabled: Boolean, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = ActivityOptions.find { it.value == selected }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it }
    ) {
        OutlinedTextField(
            value = current?.label.orEmpty(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            enabled = enabled,
            label = { Text("Nivel de actividad") },
            placeholder = { Text("Elige uno") },
            supportingText = current?.description?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ActivityOptions.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(option.label, style = MaterialTheme.typography.bodyLarge)
                            option.description?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    onClick = {
                        onSelect(option.value)
                        expanded = false
                    }
                )
            }
        }
    }
}

private val PreviewActions = ProfileActions({}, {}, {}, {}, {}, {}, {}, {}, {})

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    val profile = Profile(
        1, "ana@example.com", "Ana", birthDate = "1996-05-10", age = 30, heightCm = 165.0, weightKg = 60.0,
        sex = "F", activityLevel = "moderado", goal = "mantener", dailyCalorieGoal = 2085, bmr = 1345, tdee = 2085
    )
    NutriSocialTheme {
        ProfileScreen(
            user = User(1, "ana@example.com", "Ana"),
            state = ProfileUiState(
                isLoading = false, profile = profile, birthDate = "1996-05-10", heightCm = "165", weightKg = "60",
                sex = "F", activityLevel = "moderado", goal = "mantener"
            ),
            actions = PreviewActions,
            onLogout = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileIncompletePreview() {
    NutriSocialTheme {
        ProfileScreen(
            user = User(1, "ana@example.com", "Ana"),
            state = ProfileUiState(
                isLoading = false,
                profile = Profile(1, "ana@example.com", "Ana", missingFields = listOf("birthDate", "weightKg", "goal"))
            ),
            actions = PreviewActions,
            onLogout = {}
        )
    }
}
