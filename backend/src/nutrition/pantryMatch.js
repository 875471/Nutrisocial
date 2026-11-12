const { words, containsWords } = require('./normalize');
const { areSubstitutes } = require('./substitutes');

// Por debajo de esta cobertura la receta no se muestra: le falta más de la mitad.
const MIN_COVERAGE = 0.5;

// Un ingrediente está cubierto por un elemento de la despensa si comparten alimento (foodId)
// o, cuando a alguno de los dos le falta, si un nombre coincide con el otro o lo contiene.
function covers(pantryItem, ingredient) {
  if (pantryItem.foodId != null && ingredient.foodId != null) {
    return pantryItem.foodId === ingredient.foodId;
  }
  return containsWords(pantryItem.words, ingredient.words) || containsWords(ingredient.words, pantryItem.words);
}

// Si nada de la despensa cubre el ingrediente, busca algo que lo sustituya (margarina por
// mantequilla). Se compara solo por nombre: los sustitutos son alimentos distintos.
function findSubstitute(pantry, ingredient) {
  return pantry.find((item) => areSubstitutes(item.words, ingredient.words)) ?? null;
}

function toPantry(pantryItems) {
  return pantryItems.map((item) => ({ name: item.name, foodId: item.foodId ?? null, words: words(item.name) }));
}

// Compara una receta con la despensa ya preparada por toPantry: los ingredientes que faltan (los
// objetos originales de la receta) y los cubiertos por un sustituto.
function compareRecipe(pantry, recipe) {
  const missing = [];
  const substitutions = [];
  for (const ing of recipe.ingredients) {
    const ingredient = { foodId: ing.foodId ?? null, words: words(ing.name) };
    if (pantry.some((item) => covers(item, ingredient))) continue;
    const substitute = findSubstitute(pantry, ingredient);
    if (substitute) substitutions.push({ recipeIngredient: ing.name, pantryItem: substitute.name });
    else missing.push(ing);
  }
  return { missing, substitutions };
}

/**
 * Ingredientes de UNA receta que la despensa no cubre, ni directamente ni con un sustituto. Es la
 * misma comparación que hace matchRecipesToPantry, pero sin el corte de cobertura mínima: la usa
 * la lista de la compra, donde interesa lo que falta aunque falte casi todo. `recipe.ingredients`
 * son objetos con al menos { name, foodId }; se devuelven los mismos objetos, en su orden.
 */
function missingIngredients(pantryItems, recipe) {
  return compareRecipe(toPantry(pantryItems), recipe).missing;
}

/**
 * Cruza la despensa con las recetas. `pantryItems`: [{ name, foodId }]; `recipes`:
 * [{ id, title, ingredients: [{ name, foodId }] }]. Devuelve las recetas con al menos la
 * mitad de ingredientes cubiertos, de más a menos cobertura y, a igualdad, con menos faltas y
 * después con menos sustituciones primero. Un ingrediente cubierto por un sustituto cuenta como
 * cubierto, y queda anotado en `substitutions` ({ recipeIngredient, pantryItem }).
 */
function matchRecipesToPantry(pantryItems, recipes) {
  const pantry = toPantry(pantryItems);

  const results = [];
  for (const recipe of recipes) {
    const totalIngredients = recipe.ingredients.length;
    if (totalIngredients === 0) continue;

    const { missing, substitutions } = compareRecipe(pantry, recipe);
    const missingIngredients = missing.map((ing) => ing.name);
    const matchedCount = totalIngredients - missingIngredients.length;
    const coverage = matchedCount / totalIngredients;
    if (coverage < MIN_COVERAGE) continue;

    results.push({
      id: recipe.id, title: recipe.title, totalIngredients, matchedCount, missingIngredients, substitutions, coverage,
    });
  }

  return results.sort((a, b) =>
    b.coverage - a.coverage
    || a.missingIngredients.length - b.missingIngredients.length
    || a.substitutions.length - b.substitutions.length);
}

module.exports = { matchRecipesToPantry, missingIngredients, MIN_COVERAGE };
