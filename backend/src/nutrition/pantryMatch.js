const { normalize } = require('./normalize');

// Por debajo de esta cobertura la receta no se muestra: le falta más de la mitad.
const MIN_COVERAGE = 0.5;

// Singular aproximado: "macarrones" → "macarron", "huevos" → "huevo". Las palabras
// cortas se dejan tal cual para no tocar "sal" o "gas".
function singular(word) {
  if (word.length <= 3) return word;
  if (/[lnrdj]es$/.test(word)) return word.slice(0, -2);
  return word.endsWith('s') ? word.slice(0, -1) : word;
}

// Palabras de un nombre normalizado, sin comas y en singular
// ("Huevos" → ["huevo"], "Calabaza, cruda" → ["calabaza", "cruda"]).
function words(name) {
  return normalize(name).replace(/,/g, ' ').split(' ').filter(Boolean).map(singular);
}

// true si la secuencia `inner` aparece seguida dentro de `outer`, palabra a palabra.
// Se compara por palabras completas para que "sal" no cubra "salsa de tomate".
function containsWords(outer, inner) {
  if (inner.length === 0 || inner.length > outer.length) return false;
  for (let start = 0; start + inner.length <= outer.length; start++) {
    if (inner.every((w, i) => outer[start + i] === w)) return true;
  }
  return false;
}

// Un ingrediente está cubierto por un elemento de la despensa si comparten alimento (foodId)
// o, cuando a alguno de los dos le falta, si un nombre coincide con el otro o lo contiene.
function covers(pantryItem, ingredient) {
  if (pantryItem.foodId != null && ingredient.foodId != null) {
    return pantryItem.foodId === ingredient.foodId;
  }
  return containsWords(pantryItem.words, ingredient.words) || containsWords(ingredient.words, pantryItem.words);
}

/**
 * Cruza la despensa con las recetas. `pantryItems`: [{ name, foodId }]; `recipes`:
 * [{ id, title, ingredients: [{ name, foodId }] }]. Devuelve las recetas con al menos la
 * mitad de ingredientes cubiertos, de más a menos cobertura y, a igualdad, con menos faltas primero.
 */
function matchRecipesToPantry(pantryItems, recipes) {
  const pantry = pantryItems.map((item) => ({ foodId: item.foodId ?? null, words: words(item.name) }));

  const results = [];
  for (const recipe of recipes) {
    const totalIngredients = recipe.ingredients.length;
    if (totalIngredients === 0) continue;

    const missingIngredients = [];
    for (const ing of recipe.ingredients) {
      const ingredient = { foodId: ing.foodId ?? null, words: words(ing.name) };
      if (!pantry.some((item) => covers(item, ingredient))) missingIngredients.push(ing.name);
    }
    const matchedCount = totalIngredients - missingIngredients.length;
    const coverage = matchedCount / totalIngredients;
    if (coverage < MIN_COVERAGE) continue;

    results.push({ id: recipe.id, title: recipe.title, totalIngredients, matchedCount, missingIngredients, coverage });
  }

  return results.sort((a, b) =>
    b.coverage - a.coverage || a.missingIngredients.length - b.missingIngredients.length);
}

module.exports = { matchRecipesToPantry, MIN_COVERAGE };
