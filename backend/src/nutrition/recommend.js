// Recomendador de recetas para completar el día: dado lo consumido y el objetivo del usuario,
// puntúa las recetas de la base de datos por lo bien que encajan con lo que le queda.
//
// La puntuación de cada receta (por ración) combina dos partes, cada una entre 0 y 1:
//   - encaje calórico: ratio = kcal de la ración / kcal que quedan. Vale `ratio` si no se
//     pasa y cae deprisa si se pasa (1 − 3·(ratio − 1)); más allá del 15 % se descarta.
//   - encaje de macros: 1 − d/√2, con d la distancia euclídea entre el reparto energético
//     (proteína, hidratos, grasas) de la ración y el de lo que queda. √2 es la distancia
//     máxima posible entre dos repartos, así que 1 es idéntico y 0 lo más distinto.
// puntuación = 0,5·calórico + 0,5·macros − 0,15 si la ración cubre menos del 30 % de lo que
// queda (una ración tan pequeña no completa el día).

const { calculateCalorieGoal } = require('./calorieGoal');
const { KCAL_PER_GRAM, macroTargetsFromGoal } = require('./macroTargets');

const MAX_RECOMMENDATIONS = 5;
// Por debajo de este margen se considera el objetivo del día cubierto.
const COVERED_MARGIN_KCAL = 50;
const MAX_OVERSHOOT = 1.15;
const OVERSHOOT_SLOPE = 3;
const SMALL_PORTION_RATIO = 0.3;
const SMALL_PORTION_PENALTY = 0.15;
const KCAL_WEIGHT = 0.5;
const MACRO_WEIGHT = 0.5;

function round1(value) {
  return Math.round(value * 10) / 10;
}

function formatGrams(value) {
  return String(round1(value)).replace('.', ',');
}

/** Lo que queda del día; lo ya cubierto de más cuenta como 0. */
function computeRemaining(dailyCalorieGoal, targets, consumed) {
  return {
    kcal: Math.max(0, Math.round(dailyCalorieGoal - consumed.kcal)),
    proteinG: Math.max(0, round1(targets.proteinG - consumed.protein)),
    carbsG: Math.max(0, round1(targets.carbsG - consumed.carbs)),
    fatG: Math.max(0, round1(targets.fatG - consumed.fat)),
  };
}

/** Fracción de la energía que aporta cada macronutriente; null si no hay ninguno. */
function energyShares(proteinG, carbsG, fatG) {
  const protein = proteinG * KCAL_PER_GRAM.protein;
  const carbs = carbsG * KCAL_PER_GRAM.carbs;
  const fat = fatG * KCAL_PER_GRAM.fat;
  const total = protein + carbs + fat;
  if (total <= 0) return null;
  return [protein / total, carbs / total, fat / total];
}

function distance(a, b) {
  return Math.sqrt(a.reduce((sum, value, i) => sum + (value - b[i]) ** 2, 0));
}

function perServing(recipe) {
  return {
    kcal: recipe.kcal / recipe.servings,
    protein: recipe.protein / recipe.servings,
    carbs: recipe.carbs / recipe.servings,
    fat: recipe.fat / recipe.servings,
  };
}

/** Puntuación de encaje de una ración (ver cabecera) o null si no se puede recomendar. */
function scoreServing(serving, remainingKcal, remainingShares) {
  const ratio = serving.kcal / remainingKcal;
  if (ratio > MAX_OVERSHOOT) return null;
  const shares = energyShares(serving.protein, serving.carbs, serving.fat);
  if (!shares) return null;

  const kcalFit = ratio <= 1 ? ratio : 1 - OVERSHOOT_SLOPE * (ratio - 1);
  const macroFit = 1 - distance(shares, remainingShares) / Math.SQRT2;
  const penalty = ratio < SMALL_PORTION_RATIO ? SMALL_PORTION_PENALTY : 0;
  return { ratio, score: KCAL_WEIGHT * kcalFit + MACRO_WEIGHT * macroFit - penalty };
}

function buildMotivo(serving, ratio, remainingKcal) {
  const base = `Te aporta ${Math.round(serving.kcal)} kcal y ${formatGrams(serving.protein)} g de proteína`;
  if (ratio > 1) {
    return `${base}; se pasa solo ${Math.round(serving.kcal - remainingKcal)} kcal de lo que te queda hoy`;
  }
  if (ratio >= 0.8) return `${base}, casi justo lo que te queda hoy`;
  if (ratio < SMALL_PORTION_RATIO) return `${base}; es ligera, tendrás que completarla con algo más`;
  return `${base}, encaja con lo que te queda hoy`;
}

/**
 * Ordena las recetas por encaje con lo que queda y devuelve las mejores. Función pura:
 * `recipes` son filas de Recipe con los totales de la receta completa y sus raciones.
 */
function rankRecipes(recipes, remaining, { excludeRecipeIds = [], defaultShares, limit = MAX_RECOMMENDATIONS } = {}) {
  const excluded = new Set(excludeRecipeIds);
  // Si todos los macros están cubiertos pero aún quedan kcal, se busca el reparto general.
  const remainingShares = energyShares(remaining.proteinG, remaining.carbsG, remaining.fatG) ?? defaultShares;

  const scored = [];
  for (const recipe of recipes) {
    // Sin raciones o sin valores calculados no hay nada que comparar.
    if (excluded.has(recipe.id) || !(recipe.servings > 0) || !(recipe.kcal > 0)) continue;
    const serving = perServing(recipe);
    const result = scoreServing(serving, remaining.kcal, remainingShares);
    if (!result) continue;
    scored.push({ recipe, serving, ...result });
  }

  scored.sort((a, b) => b.score - a.score || a.recipe.id - b.recipe.id);
  return scored.slice(0, limit).map(({ recipe, serving, ratio, score }) => ({
    id: recipe.id,
    title: recipe.title,
    kcalPorRacion: Math.round(serving.kcal),
    proteinPorRacion: round1(serving.protein),
    carbsPorRacion: round1(serving.carbs),
    fatPorRacion: round1(serving.fat),
    motivo: buildMotivo(serving, ratio, remaining.kcal),
    score: Math.round(score * 1000) / 1000,
  }));
}

/**
 * Recomendaciones para completar el día. `consumedTotals` tiene la forma de `sumTotals`
 * ({ kcal, protein, carbs, fat }). `db` permite inyectar un cliente de Prisma en las pruebas.
 */
async function recommendRecipes({
  user, consumedTotals, excludeRecipeIds = [], today = new Date(), db = require('../prismaClient'),
}) {
  const { dailyCalorieGoal, missingFields } = calculateCalorieGoal(user, today);
  if (dailyCalorieGoal == null) {
    return { remaining: null, recommendations: [], reason: 'perfil_incompleto', missingFields };
  }

  const targets = macroTargetsFromGoal(dailyCalorieGoal, user.goal);
  const remaining = computeRemaining(dailyCalorieGoal, targets, consumedTotals);
  if (remaining.kcal <= COVERED_MARGIN_KCAL) {
    return { remaining, recommendations: [], reason: 'objetivo_cubierto' };
  }

  const recipes = await db.recipe.findMany({
    select: { id: true, title: true, servings: true, kcal: true, protein: true, carbs: true, fat: true },
  });
  const defaultShares = energyShares(targets.proteinG, targets.carbsG, targets.fatG);
  return { remaining, recommendations: rankRecipes(recipes, remaining, { excludeRecipeIds, defaultShares }) };
}

module.exports = { computeRemaining, energyShares, rankRecipes, recommendRecipes };
