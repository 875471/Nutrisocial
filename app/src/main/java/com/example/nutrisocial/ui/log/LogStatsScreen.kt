package com.example.nutrisocial.ui.log

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.nutrisocial.data.LogStats
import com.example.nutrisocial.data.LogStatsAverages
import com.example.nutrisocial.data.LogStatsDay
import com.example.nutrisocial.data.MacroTargets
import com.example.nutrisocial.ui.recipes.CenteredMessage
import com.example.nutrisocial.ui.recipes.LoadingBox
import com.example.nutrisocial.ui.recipes.formatNumber
import com.example.nutrisocial.ui.theme.CardShape
import com.example.nutrisocial.ui.theme.NutriSocialTheme
import com.example.nutrisocial.ui.theme.Spacing
import kotlin.math.max

/** Una serie del gráfico de macronutrientes: nombre, color y el valor de cada día. */
private data class MacroSeries(val label: String, val color: Color, val value: (LogStatsDay) -> Double, val target: Double?)

/**
 * "Estadísticas" del Diario: calorías de cada día de los últimos 7 o 30 días en barras (con el
 * objetivo como línea discontinua) y los tres macronutrientes en líneas, más las medias. Los
 * gráficos se dibujan con Canvas, sin librería: son dos gráficos sencillos y no compensa la
 * dependencia. Cada gráfico lleva una descripción para lectores de pantalla.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogStatsScreen(
    period: StatsPeriod,
    state: LogStatsUiState,
    onPeriodChange: (StatsPeriod) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Estadísticas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                StatsPeriod.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = option == period,
                        onClick = { onPeriodChange(option) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = StatsPeriod.entries.size)
                    ) { Text(option.label) }
                }
            }
            when (state) {
                LogStatsUiState.Loading -> LoadingBox(Modifier.height(240.dp))
                is LogStatsUiState.Error -> CenteredMessage(
                    icon = rememberVectorPainter(Icons.Filled.Warning),
                    isError = true,
                    title = "No se pudieron cargar las estadísticas",
                    message = state.message,
                    actionLabel = "Reintentar",
                    onAction = onRetry
                )
                is LogStatsUiState.Success -> StatsContent(state.stats)
            }
        }
    }
}

@Composable
private fun StatsContent(stats: LogStats) {
    if (stats.daysLogged == 0) {
        CenteredMessage(
            title = "Sin datos en este periodo",
            message = "Registra lo que comes en el Diario y aquí verás cómo evolucionan tus calorías y macros."
        )
        return
    }
    AveragesCard(stats.averages, stats.daysLogged, stats.days.size, stats.dailyCalorieGoal)
    ChartCard(title = "Calorías por día") {
        KcalBarChart(days = stats.days, goal = stats.dailyCalorieGoal)
        if (stats.dailyCalorieGoal != null) {
            LegendItem(color = MaterialTheme.colorScheme.secondary, label = "Objetivo: ${stats.dailyCalorieGoal} kcal", dashed = true)
        }
    }
    val series = macroSeries(stats.macroTargets)
    ChartCard(title = "Macronutrientes por día (g)") {
        MacroLineChart(days = stats.days, series = series)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            series.forEach { LegendItem(color = it.color, label = it.label) }
        }
    }
    Text(
        text = "Las medias solo cuentan los días con algo registrado. Los objetivos son los de tu perfil actual.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun macroSeries(targets: MacroTargets?) = listOf(
    MacroSeries("Proteínas", MaterialTheme.colorScheme.primary, { it.protein }, targets?.proteinG),
    MacroSeries("Hidratos", MaterialTheme.colorScheme.secondary, { it.carbs }, targets?.carbsG),
    MacroSeries("Grasas", MaterialTheme.colorScheme.tertiary, { it.fat }, targets?.fatG)
)

@Composable
private fun AveragesCard(averages: LogStatsAverages?, daysLogged: Int, totalDays: Int, goal: Int?) {
    if (averages == null) return
    Card(shape = CardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("Media diaria", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${averages.kcal} kcal" + (goal?.let { " de $it" } ?: ""),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Proteínas ${formatNumber(averages.protein)} g · Hidratos ${formatNumber(averages.carbs)} g · " +
                    "Grasas ${formatNumber(averages.fat)} g",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "$daysLogged de $totalDays días con registro",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Card(shape = CardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String, dashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (dashed) {
            Canvas(modifier = Modifier.size(width = 16.dp, height = 10.dp)) {
                drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
            }
        } else {
            Surface(shape = CircleShape, color = color, modifier = Modifier.size(10.dp)) {}
        }
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = Spacing.xs))
    }
}

/** Primera y última fecha del eje X ("17/11"), para orientar sin saturar el eje. */
@Composable
private fun AxisLabels(days: List<LogStatsDay>) {
    if (days.isEmpty()) return
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(shortDate(days.first().date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(shortDate(days.last().date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "2026-11-17" → "17/11". */
internal fun shortDate(iso: String): String = iso.split("-").let { if (it.size == 3) "${it[2]}/${it[1]}" else iso }

/** Resumen hablado del gráfico de calorías para TalkBack. */
internal fun kcalChartDescription(days: List<LogStatsDay>, goal: Int?): String {
    val logged = days.filter { it.entries > 0 }
    if (logged.isEmpty()) return "Gráfico de calorías sin datos"
    val max = logged.maxBy { it.kcal }
    val min = logged.minBy { it.kcal }
    val overGoal = goal?.let { g -> logged.count { it.kcal > g } }
    return "Gráfico de barras de calorías de ${days.size} días. Máximo ${max.kcal} kcal el ${shortDate(max.date)}, " +
        "mínimo ${min.kcal} kcal el ${shortDate(min.date)}." +
        (overGoal?.let { " $it ${if (it == 1) "día" else "días"} por encima del objetivo de $goal kcal." } ?: "")
}

@Composable
private fun KcalBarChart(days: List<LogStatsDay>, goal: Int?) {
    val barColor = MaterialTheme.colorScheme.primary
    val goalColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val description = kcalChartDescription(days, goal)
    Column {
        Box(modifier = Modifier.fillMaxWidth().height(180.dp).semantics { contentDescription = description }) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val top = max(days.maxOfOrNull { it.kcal } ?: 0, goal ?: 0).coerceAtLeast(1) * 1.1f
                drawLine(gridColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                val slot = size.width / days.size
                val barWidth = slot * 0.6f
                days.forEachIndexed { i, day ->
                    if (day.kcal <= 0) return@forEachIndexed
                    val h = size.height * (day.kcal / top)
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(i * slot + (slot - barWidth) / 2, size.height - h),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(barWidth / 4, barWidth / 4)
                    )
                }
                if (goal != null) {
                    val y = size.height * (1 - goal / top)
                    drawLine(goalColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())))
                }
            }
        }
        AxisLabels(days)
    }
}

@Composable
private fun MacroLineChart(days: List<LogStatsDay>, series: List<MacroSeries>) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val description = "Gráfico de líneas de macronutrientes de ${days.size} días. " + series.joinToString(" ") { s ->
        val logged = days.filter { it.entries > 0 }
        val avg = if (logged.isEmpty()) 0.0 else logged.sumOf(s.value) / logged.size
        "${s.label}: media ${formatNumber(Math.round(avg * 10) / 10.0)} g" + (s.target?.let { " de ${formatNumber(it)} g" } ?: "") + "."
    }
    Column {
        Box(modifier = Modifier.fillMaxWidth().height(180.dp).semantics { contentDescription = description }) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val top = (days.maxOfOrNull { d -> series.maxOf { it.value(d) } } ?: 0.0).coerceAtLeast(1.0) * 1.1
                drawLine(gridColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                val step = if (days.size > 1) size.width / (days.size - 1) else 0f
                series.forEach { s ->
                    // Los días sin registro cortan la línea: unirlos a cero daría caídas falsas.
                    var path: Path? = null
                    days.forEachIndexed { i, day ->
                        if (day.entries == 0) {
                            path?.let { drawPath(it, s.color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)) }
                            path = null
                            return@forEachIndexed
                        }
                        val point = Offset(i * step, (size.height * (1 - s.value(day) / top)).toFloat())
                        drawCircle(s.color, radius = 3.dp.toPx(), center = point)
                        path = (path ?: Path().apply { moveTo(point.x, point.y) }).apply { lineTo(point.x, point.y) }
                    }
                    path?.let { drawPath(it, s.color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)) }
                }
            }
        }
        AxisLabels(days)
    }
}

@Preview(showBackground = true)
@Composable
private fun LogStatsPreview() {
    val days = (11..17).map { d ->
        LogStatsDay("2026-11-$d", kcal = if (d == 13) 0 else 1600 + d * 30, protein = 80.0 + d, carbs = 200.0 - d, fat = 60.0, entries = if (d == 13) 0 else 3)
    }
    NutriSocialTheme {
        LogStatsScreen(
            period = StatsPeriod.WEEK,
            state = LogStatsUiState.Success(
                LogStats("2026-11-11", "2026-11-17", days, 6, LogStatsAverages(2010, 94.0, 186.0, 60.0), 2100, MacroTargets(130.0, 240.0, 70.0))
            ),
            onPeriodChange = {}, onRetry = {}, onBack = {}
        )
    }
}
