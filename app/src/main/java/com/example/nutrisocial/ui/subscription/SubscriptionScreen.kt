package com.example.nutrisocial.ui.subscription

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.nutrisocial.data.Plan
import com.example.nutrisocial.data.SubscriptionStatus
import com.example.nutrisocial.data.formatCents
import com.example.nutrisocial.data.savingsPercent
import com.example.nutrisocial.ui.displayDate
import com.example.nutrisocial.ui.theme.ButtonShape
import com.example.nutrisocial.ui.theme.CardElevation
import com.example.nutrisocial.ui.theme.CardShape
import com.example.nutrisocial.ui.theme.NutriSocialTheme
import com.example.nutrisocial.ui.theme.Spacing

data class SubscriptionActions(
    val onBack: () -> Unit,
    val onSubscribe: (Plan) -> Unit,
    val onCancel: () -> Unit,
    val onRetryPlans: () -> Unit,
    val onRetryStatus: () -> Unit,
    val onMessageShown: () -> Unit,
    // Alta o baja hecha: quien la abrió vuelve atrás y enseña [String] como aviso.
    val onFinished: (String) -> Unit
)

/**
 * Pantalla de planes Premium. Sin suscripción activa lista los planes que da el backend; con
 * ella enseña el plan actual y el botón para cancelarla. Es una simulación: no se cobra nada.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(state: SubscriptionUiState, actions: SubscriptionActions) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            actions.onMessageShown()
        }
    }
    LaunchedEffect(state.confirmation) {
        state.confirmation?.let(actions.onFinished)
    }
    var confirmCancel by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NutriSocial Premium") },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            val status = state.status
            when {
                status == null && state.statusError == null -> Loading()
                status == null -> ErrorCard(
                    message = state.statusError.orEmpty(),
                    onRetry = actions.onRetryStatus
                )
                status.isPremium -> CurrentPlanCard(
                    status = status,
                    planLabel = state.plans.find { it.id == status.plan }?.label ?: status.plan.orEmpty(),
                    canceling = state.canceling,
                    onCancel = { confirmCancel = true }
                )
                else -> PlansContent(state = state, actions = actions)
            }
            Text(
                text = "Suscripción de demostración: no se realiza ningún cobro.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("¿Cancelar la suscripción?") },
            text = { Text("Perderás al momento el escaneo de recetas y las recomendaciones del diario.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    actions.onCancel()
                }) { Text("Cancelar suscripción") }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Mantener Premium") } }
        )
    }
}

@Composable
private fun PlansContent(state: SubscriptionUiState, actions: SubscriptionActions) {
    Text(
        text = "Con Premium desbloqueas:",
        style = MaterialTheme.typography.titleMedium
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        listOf(
            "Escanear recetas en papel con la cámara y pasarlas al formulario.",
            "Recomendaciones de recetas para completar las kcal y los macros de tu día."
        ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
    }
    when {
        state.plansLoading -> Loading()
        state.plansError != null -> ErrorCard(message = state.plansError, onRetry = actions.onRetryPlans)
        else -> state.plans.forEach { plan ->
            PlanCard(
                plan = plan,
                savings = savingsPercent(plan, state.plans),
                submitting = state.submittingPlanId == plan.id,
                enabled = !state.busy,
                onSubscribe = { actions.onSubscribe(plan) }
            )
        }
    }
}

@Composable
private fun PlanCard(plan: Plan, savings: Int?, submitting: Boolean, enabled: Boolean, onSubscribe: () -> Unit) {
    ElevatedCard(
        shape = CardShape,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = CardElevation),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = plan.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                savings?.let {
                    Surface(shape = CardShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            text = "Ahorras ~$it%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                        )
                    }
                }
            }
            Text(
                text = "${plan.formattedPrice} ${periodLabel(plan.periodMonths)}",
                style = MaterialTheme.typography.headlineSmall
            )
            if (plan.periodMonths > 1) {
                Text(
                    text = "Equivale a ${formatCents(plan.monthlyCents, plan.currency)} al mes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onSubscribe,
                enabled = enabled,
                shape = ButtonShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs)
                    .height(48.dp)
            ) {
                if (submitting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Suscribirme")
                }
            }
        }
    }
}

@Composable
private fun CurrentPlanCard(status: SubscriptionStatus, planLabel: String, canceling: Boolean, onCancel: () -> Unit) {
    ElevatedCard(
        shape = CardShape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = CardElevation),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Star, contentDescription = null)
                Text(
                    text = "Eres Premium",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = Spacing.sm)
                )
            }
            Text(text = "Plan ${planLabel.lowercase()}", style = MaterialTheme.typography.bodyLarge)
            status.currentPeriodEnd?.let {
                Text(text = "Periodo actual hasta el ${displayDate(it.take(10))}", style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(
                onClick = onCancel,
                enabled = !canceling,
                shape = ButtonShape,
                modifier = Modifier.padding(top = Spacing.sm)
            ) {
                if (canceling) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Cancelar suscripción")
                }
            }
        }
    }
}

@Composable
private fun Loading() {
    Box(modifier = Modifier.fillMaxWidth().padding(Spacing.lg), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Column {
        Text(text = message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry) { Text("Reintentar") }
    }
}

private fun periodLabel(months: Int): String = when (months) {
    1 -> "al mes"
    12 -> "al año"
    else -> "cada $months meses"
}

/** Fila "Suscripción" del perfil: lleva a la pantalla de planes y dice si es Premium o Gratis. */
@Composable
fun SubscriptionRow(isPremium: Boolean?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm + Spacing.xs)
        ) {
            Text(text = "Suscripción", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                text = when (isPremium) {
                    true -> "Premium"
                    false -> "Gratis"
                    null -> "…"
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (isPremium == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

private val PreviewPlans = listOf(
    Plan(id = "monthly", priceCents = 399, periodMonths = 1, label = "Mensual"),
    Plan(id = "annual", priceCents = 2999, periodMonths = 12, label = "Anual")
)

private val PreviewActions = SubscriptionActions({}, {}, {}, {}, {}, {}, {})

@Preview(showBackground = true)
@Composable
private fun SubscriptionScreenPlansPreview() {
    NutriSocialTheme {
        SubscriptionScreen(
            state = SubscriptionUiState(status = SubscriptionStatus(isPremium = false), plans = PreviewPlans),
            actions = PreviewActions
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SubscriptionScreenPremiumPreview() {
    NutriSocialTheme {
        SubscriptionScreen(
            state = SubscriptionUiState(
                status = SubscriptionStatus(isPremium = true, plan = "annual", status = "active", currentPeriodEnd = "2027-11-04T10:00:00.000Z"),
                plans = PreviewPlans
            ),
            actions = PreviewActions
        )
    }
}
