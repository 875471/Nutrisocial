package com.example.nutrisocial.ui.log

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.nutrisocial.data.DailyLog
import com.example.nutrisocial.data.DailyRecommendations
import com.example.nutrisocial.data.LogEntry
import com.example.nutrisocial.data.Macros
import com.example.nutrisocial.data.RecipeRecommendation
import com.example.nutrisocial.data.RemainingMacros
import com.example.nutrisocial.ui.recipes.RecipeListUiState
import com.example.nutrisocial.ui.theme.NutriSocialTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Lo registrado en el día tiene que verse sin desplazarse aunque haya recomendaciones: antes
 * iban delante y, con las cinco del recomendador, "Registrado" quedaba fuera de la pantalla.
 */
@RunWith(AndroidJUnit4::class)
class LogScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val noopAdd = AddEntryActions({}, {}, {}, {}, {}, {}, {}, {}, {})

    @Test
    fun registeredEntriesAreVisibleAboveTheRecommendations() {
        val log = DailyLog(
            date = "2026-09-29",
            entries = listOf(
                LogEntry(1, "2026-09-29", "recipe", "Bizcocho de yogur", recipeId = 1, servings = 1.0,
                    kcal = 361.0, protein = 6.7, carbs = 48.3, fat = 15.5)
            ),
            totals = Macros(361.0, 6.7, 48.3, 15.5),
            dailyCalorieGoal = 3223, remainingKcal = 2862, excessKcal = 0, progress = 0.112
        )
        val recommendations = DailyRecommendations(
            date = "2026-09-29",
            remaining = RemainingMacros(2862, 194.0, 315.0, 91.0),
            recommendations = (1..5).map { i ->
                RecipeRecommendation(100 + i, "Receta recomendada $i", 600, 30.0, 70.0, 20.0, "Encaja con lo que te queda hoy")
            }
        )
        composeRule.setContent {
            NutriSocialTheme {
                LogScreen(
                    date = "2026-09-29",
                    dayState = DayUiState.Success(log),
                    recommendationsState = RecommendationsUiState.Success(recommendations),
                    addingRecommendationId = null,
                    addState = null,
                    recipesState = RecipeListUiState.Success(emptyList()),
                    message = null,
                    actions = LogActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, noopAdd)
                )
            }
        }

        composeRule.onNodeWithText("Registrado").assertIsDisplayed()
        composeRule.onNodeWithText("Bizcocho de yogur").assertIsDisplayed()
        composeRule.onNodeWithText(entryMacrosLabel(log.entries.first())).assertIsDisplayed()
    }
}
