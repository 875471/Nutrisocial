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

// Macronutrientes con objetivo en gramos y prefijo de sus campos en la respuesta de GET /log.
const GOAL_MACROS = { protein: 'Protein', carbs: 'Carbs', fat: 'Fat' };

/**
 * Comparación de los gramos consumidos de proteína, hidratos y grasas con los objetivos de
 * getMacroTargets ({ proteinG, carbsG, fatG }). Mismo criterio que compareWithGoal para las kcal:
 * por cada macronutriente, el objetivo, lo que queda o el exceso (nunca negativos) y la fracción
 * consumida (puede superar 1). Con objetivos null (perfil incompleto), todo null.
 */
function compareMacrosWithGoals(totals, macroTargets) {
  const result = {};
  for (const [key, suffix] of Object.entries(GOAL_MACROS)) {
    const goal = macroTargets?.[`${key}G`] ?? null;
    if (goal == null) {
      Object.assign(result, {
        [`${key}Goal`]: null, [`remaining${suffix}`]: null, [`excess${suffix}`]: null, [`${key}Progress`]: null,
      });
      continue;
    }
    const diff = goal - totals[key];
    Object.assign(result, {
      [`${key}Goal`]: goal,
      [`remaining${suffix}`]: Math.max(0, round1(diff)),
      [`excess${suffix}`]: Math.max(0, round1(-diff)),
      [`${key}Progress`]: goal > 0 ? Math.round((totals[key] / goal) * 1000) / 1000 : null,
    });
  }
  return result;
}

// Margen del calendario mensual: un día es "adecuado" si sus kcal quedan a ±10 % del objetivo.
const CALENDAR_TOLERANCE = 0.1;

/**
 * Estado de un día del calendario: "adecuado" (dentro de ±10 % del objetivo), "excesivo" (más
 * del 110 %) o "insuficiente" (menos del 90 %). null si no hay objetivo (perfil incompleto).
 */
function dayStatus(kcal, dailyCalorieGoal) {
  if (dailyCalorieGoal == null || !(dailyCalorieGoal > 0)) return null;
  if (kcal > dailyCalorieGoal * (1 + CALENDAR_TOLERANCE)) return 'excesivo';
  if (kcal < dailyCalorieGoal * (1 - CALENDAR_TOLERANCE)) return 'insuficiente';
  return 'adecuado';
}

/**
 * Días del calendario a partir de las entradas de un mes ([{ date, kcal }]): uno por cada día
 * con alguna entrada, en orden, con sus kcal totales y su estado frente al objetivo. Los días
 * sin entradas no aparecen.
 */
function calendarDays(entries, dailyCalorieGoal, formatDay) {
  const byDay = new Map();
  for (const entry of entries) {
    const day = formatDay(entry.date);
    byDay.set(day, (byDay.get(day) ?? 0) + entry.kcal);
  }
  return [...byDay.keys()].sort().map((date) => {
    const kcal = Math.round(byDay.get(date));
    return { date, kcal, status: dayStatus(kcal, dailyCalorieGoal) };
  });
}

module.exports = {
  LOG_LIMITS, CALENDAR_TOLERANCE, macrosFromRecipe, macrosFromFood, sumTotals, compareWithGoal,
  compareMacrosWithGoals, dayStatus, calendarDays,
};
