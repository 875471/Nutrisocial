package com.example.nutrisocial.ui.log

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.nutrisocial.data.CalendarDay
import com.example.nutrisocial.data.CalendarMonth
import com.example.nutrisocial.ui.home.formatKcal
import com.example.nutrisocial.ui.monthGrid
import com.example.nutrisocial.ui.monthLabel
import com.example.nutrisocial.ui.recipes.CenteredMessage
import com.example.nutrisocial.ui.recipes.LoadingBox
import com.example.nutrisocial.ui.theme.CardElevation
import com.example.nutrisocial.ui.theme.CardShape
import com.example.nutrisocial.ui.theme.NutriSocialTheme
import com.example.nutrisocial.ui.theme.NutritionStatusColors
import com.example.nutrisocial.ui.theme.Spacing
import com.example.nutrisocial.ui.theme.statusColors
import com.example.nutrisocial.ui.todayIso

private val WEEKDAYS = listOf("L", "M", "X", "J", "V", "S", "D")

/** Texto que lee un lector de pantalla para una casilla: "15: 2.400 kcal, excesivo". */
fun calendarDayDescription(dayOfMonth: Int, day: CalendarDay?): String = when {
    day == null -> "$dayOfMonth: sin registros"
    day.status == null -> "$dayOfMonth: ${formatKcal(day.kcal)} kcal"
    else -> "$dayOfMonth: ${formatKcal(day.kcal)} kcal, ${day.status}"
}

private fun NutritionStatusColors.forStatus(status: String?): Color? = when (status) {
    CalendarDay.STATUS_ADEQUATE -> adequate
    CalendarDay.STATUS_EXCESS -> excess
    CalendarDay.STATUS_INSUFFICIENT -> insufficient
    else -> null
}

/**
 * Vista de mes del diario: rejilla de 7 columnas (de lunes a domingo) con un círculo de color
 * en cada día registrado según sus kcal frente al objetivo. Al tocar un día se abre su vista.
 */
@Composable
fun MonthCalendarContent(
    month: String,
    state: CalendarUiState,
    selectedDate: String,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDayClick: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenProfile: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPreviousMonth) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Mes anterior")
            }
            Text(
                text = monthLabel(month),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onNextMonth) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Mes siguiente")
            }
        }
        when (state) {
            CalendarUiState.Loading -> LoadingBox()
            is CalendarUiState.Error -> CenteredMessage(
                icon = rememberVectorPainter(Icons.Filled.Warning),
                isError = true,
                title = "No se pudo cargar el mes",
                message = state.message,
                actionLabel = "Reintentar",
                onAction = onRetry
            )
            is CalendarUiState.Success -> MonthGrid(
                month = month,
                data = state.data,
                selectedDate = selectedDate,
                onDayClick = onDayClick,
                onOpenProfile = onOpenProfile
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: String,
    data: CalendarMonth,
    selectedDate: String,
    onDayClick: (String) -> Unit,
    onOpenProfile: () -> Unit
) {
    val cells = monthGrid(month)
    val byDate = data.days.associateBy { it.date }
    val today = todayIso()
    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        itemsIndexed(WEEKDAYS, key = { i, _ -> "weekday-$i" }) { _, label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = Spacing.xs)
            )
        }
        itemsIndexed(cells, key = { i, date -> date ?: "blank-$i" }) { _, date ->
            if (date == null) {
                Box(modifier = Modifier.aspectRatio(1f))
            } else {
                DayCell(
                    date = date,
                    day = byDate[date],
                    isToday = date == today,
                    isSelected = date == selectedDate,
                    onClick = { onDayClick(date) }
                )
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "legend") {
            CalendarLegend(
                dailyCalorieGoal = data.dailyCalorieGoal,
                onOpenProfile = onOpenProfile,
                modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xl * 3)
            )
        }
    }
}

@Composable
private fun DayCell(date: String, day: CalendarDay?, isToday: Boolean, isSelected: Boolean, onClick: () -> Unit) {
    val dayOfMonth = date.takeLast(2).toInt()
    val statusColor = MaterialTheme.statusColors.forStatus(day?.status)
    // Con entradas pero sin objetivo (perfil incompleto) el día se marca en un tono neutro.
    val fill = statusColor ?: if (day != null) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent
    val textColor = if (statusColor != null) MaterialTheme.statusColors.onStatus else MaterialTheme.colorScheme.onSurface
    val border = when {
        isSelected -> BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface)
        isToday -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else -> null
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(role = Role.Button, onClickLabel = "Ver el día", onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = calendarDayDescription(dayOfMonth, day) }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .background(fill, CircleShape)
                .then(if (border != null) Modifier.border(border, CircleShape) else Modifier)
        ) {
            Text(
                text = dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday || day != null) FontWeight.Bold else FontWeight.Normal,
                color = textColor
            )
        }
    }
}

/** Qué significa cada color y el objetivo con el que se han comparado los días. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CalendarLegend(dailyCalorieGoal: Int?, onOpenProfile: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(
        shape = CardShape,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = CardElevation),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (dailyCalorieGoal == null) {
                Text(
                    text = "Completa tu perfil para comparar cada día con tu objetivo calórico.",
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = onOpenProfile, contentPadding = PaddingValues(0.dp)) { Text("Ir al perfil") }
                return@Column
            }
            Text(
                text = "Comparado con tu objetivo actual: ${formatKcal(dailyCalorieGoal)} kcal/día",
                style = MaterialTheme.typography.titleSmall
            )
            val colors = MaterialTheme.statusColors
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                LegendItem(colors.adequate, "Adecuado (±10 %)")
                LegendItem(colors.excess, "Excesivo")
                LegendItem(colors.insufficient, "Insuficiente")
            }
            Text(
                text = "Toca un día para ver lo que registraste.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, CircleShape)
        )
        Text(text = label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = Spacing.xs))
    }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun MonthCalendarPreview() {
    NutriSocialTheme {
        MonthCalendarContent(
            month = "2026-09",
            state = CalendarUiState.Success(
                CalendarMonth(
                    month = "2026-09",
                    dailyCalorieGoal = 2000,
                    days = listOf(
                        CalendarDay("2026-09-01", 1950, CalendarDay.STATUS_ADEQUATE),
                        CalendarDay("2026-09-02", 2600, CalendarDay.STATUS_EXCESS),
                        CalendarDay("2026-09-05", 1200, CalendarDay.STATUS_INSUFFICIENT)
                    )
                )
            ),
            selectedDate = "2026-09-05",
            onPreviousMonth = {},
            onNextMonth = {},
            onDayClick = {},
            onRetry = {},
            onOpenProfile = {}
        )
    }
}
