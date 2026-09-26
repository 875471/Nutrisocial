const { matchFood, findFoodById } = require('./matchFood');
const { toGrams } = require('./unitConversion');

const MACROS = ['kcal', 'protein', 'carbs', 'fat'];

// Resuelve cada ingrediente contra la tabla Food y suma sus aportes. Si el cliente
// eligió un alimento en el autocompletado llega su foodId; si no, se busca por nombre.
// Un ingrediente sin alimento o sin cantidad convertible a gramos no suma nada, pero
// se guarda igual para mostrarlo en la receta.
async function resolveIngredients(ingredients) {
  const totals = { kcal: 0, protein: 0, carbs: 0, fat: 0 };
  // Peso de la receta en crudo: suma de los ingredientes cuyo peso se conoce, tengan o no
  // alimento asociado (el peso no depende de BEDCA). null si ninguno tiene peso.
  let totalWeightGrams = null;
  const resolved = [];

  for (const [position, ing] of ingredients.entries()) {
    const food = ing.foodId != null ? await findFoodById(ing.foodId) : await matchFood(ing.name);
    if (ing.foodId != null && !food) {
      const err = new Error(`El alimento ${ing.foodId} no existe`);
      err.status = 400;
      throw err;
    }
    const grams = toGrams(ing.quantity, ing.unit, ing.name, food?.name);
    if (food && grams != null) {
      for (const key of MACROS) totals[key] += (food[key] * grams) / 100;
    }
    if (grams != null) totalWeightGrams = (totalWeightGrams ?? 0) + grams;
    resolved.push({
      position,
      name: ing.name,
      quantity: ing.quantity,
      unit: ing.unit,
      grams,
      foodId: food?.id ?? null,
    });
  }
  return { ingredients: resolved, totals: { ...totals, totalWeightGrams } };
}

function roundMacros(values, servings = 1) {
  return {
    kcal: Math.round(values.kcal / servings),
    protein: Math.round((values.protein / servings) * 10) / 10,
    carbs: Math.round((values.carbs / servings) * 10) / 10,
    fat: Math.round((values.fat / servings) * 10) / 10,
  };
}

// Bloque "nutrition" de la respuesta de una receta. Los ingredientes que no han
// contado en el cálculo se listan para que la app avise de que el total es parcial.
function nutritionSummary(recipe, ingredients) {
  const uncounted = ingredients.filter((i) => i.foodId == null || i.grams == null).map((i) => i.name);
  const weight = recipe.totalWeightGrams;
  return {
    total: roundMacros(recipe),
    perServing: roundMacros(recipe, recipe.servings),
    // Peso en crudo, sin factores de rendimiento por cocción (ver informe 04).
    totalWeightGrams: weight == null ? null : Math.round(weight),
    gramsPerServing: weight == null ? null : Math.round(weight / recipe.servings),
    uncountedIngredients: uncounted,
  };
}

module.exports = { resolveIngredients, nutritionSummary };
