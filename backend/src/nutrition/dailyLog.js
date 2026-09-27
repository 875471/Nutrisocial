// Cálculos del registro nutricional diario. Funciones puras: las rutas leen la receta o el
// alimento de la base de datos y guardan lo que devuelven estas funciones.

const MACROS = ['kcal', 'protein', 'carbs', 'fat'];

// Límites de lo que se puede registrar en una entrada.
const LOG_LIMITS = {
  servings: { min: 0.1, max: 20 },
  grams: { min: 1, max: 5000 },
};

function round1(value) {
  return Math.round(value * 10) / 10;
}

/**
 * Valores de `servings` raciones de una receta. La receta guarda los totales de la receta
 * completa, así que se reparten entre sus raciones.
 */
function macrosFromRecipe(recipe, servings) {
  const factor = servings / recipe.servings;
  return Object.fromEntries(MACROS.map((key) => [key, round1(recipe[key] * factor)]));
}

/** Valores de `grams` gramos de un alimento de la tabla Food (valores por 100 g). */
function macrosFromFood(food, grams) {
  const factor = grams / 100;
  return Object.fromEntries(MACROS.map((key) => [key, round1(food[key] * factor)]));
}

/** Suma de las entradas del día. */
function sumTotals(entries) {
  const totals = { kcal: 0, protein: 0, carbs: 0, fat: 0 };
  for (const entry of entries) {
    for (const key of MACROS) totals[key] += entry[key];
  }
  return Object.fromEntries(MACROS.map((key) => [key, key === 'kcal' ? Math.round(totals[key]) : round1(totals[key])]));
}

/**
 * Comparación del consumo con el objetivo: kcal que quedan o, si se ha pasado, el exceso.
 * Con objetivo null (perfil incompleto) no hay comparación.
 */
function compareWithGoal(consumedKcal, dailyCalorieGoal) {
  if (dailyCalorieGoal == null) {
    return { dailyCalorieGoal: null, remainingKcal: null, excessKcal: null, progress: null };
  }
  const diff = dailyCalorieGoal - consumedKcal;
  return {
    dailyCalorieGoal,
    remainingKcal: Math.max(0, Math.round(diff)),
    excessKcal: Math.max(0, Math.round(-diff)),
    // Fracción del objetivo consumida (puede superar 1).
    progress: dailyCalorieGoal > 0 ? Math.round((consumedKcal / dailyCalorieGoal) * 1000) / 1000 : null,
  };
}

module.exports = { LOG_LIMITS, macrosFromRecipe, macrosFromFood, sumTotals, compareWithGoal };
