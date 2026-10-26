// Pruebas del cruce entre la despensa del usuario y las recetas.
const test = require('node:test');
const assert = require('node:assert/strict');
const { matchRecipesToPantry } = require('../src/nutrition/pantryMatch');

const ing = (name, foodId = null) => ({ name, foodId });
const recipe = (id, title, ingredients) => ({ id, title, ingredients });

const TORTILLA = recipe(1, 'Tortilla de patatas', [ing('Huevos', 10), ing('Patatas', 20), ing('Aceite de oliva', 30), ing('Sal')]);
const PISTO = recipe(2, 'Pisto', [ing('Calabacín', 40), ing('Pimiento rojo', 50), ing('Tomate', 60), ing('Cebolla', 70), ing('Sal')]);

test('receta totalmente cubierta: cobertura 1 y sin ingredientes que falten', () => {
  const pantry = [ing('huevo', 10), ing('patata', 20), ing('aceite de oliva', 30), ing('sal')];
  const [result] = matchRecipesToPantry(pantry, [TORTILLA]);
  assert.deepEqual(result, {
    id: 1, title: 'Tortilla de patatas', totalIngredients: 4, matchedCount: 4, missingIngredients: [], substitutions: [], coverage: 1,
  });
});

test('receta con ingredientes que faltan: los devuelve con el nombre de la receta', () => {
  const pantry = [ing('huevo', 10), ing('patata', 20)];
  const [result] = matchRecipesToPantry(pantry, [TORTILLA]);
  assert.equal(result.matchedCount, 2);
  assert.equal(result.coverage, 0.5);
  assert.deepEqual(result.missingIngredients, ['Aceite de oliva', 'Sal']);
});

test('receta por debajo del 50 % de cobertura: se descarta', () => {
  // 2 de 5 ingredientes del pisto (40 %).
  const pantry = [ing('tomate', 60), ing('sal')];
  assert.deepEqual(matchRecipesToPantry(pantry, [PISTO]), []);
  // Con uno más (60 %) ya aparece.
  const [result] = matchRecipesToPantry([...pantry, ing('cebolla', 70)], [PISTO]);
  assert.equal(result.coverage, 0.6);
});

test('coincidencia por foodId compartido aunque los nombres no se parezcan', () => {
  // "Huevo de gallina" y "Huevos" son el mismo alimento de BEDCA (id 10).
  const pantry = [ing('Huevo de gallina', 10), ing('patatas nuevas', 20)];
  const result = matchRecipesToPantry(pantry, [recipe(3, 'Huevos con patatas', [ing('Huevos', 10), ing('Patatas', 20)])]);
  assert.equal(result[0].coverage, 1);
});

test('con foodId en ambos lados manda el alimento, no el nombre', () => {
  // Mismo nombre pero distinto alimento asociado: no se considera cubierto.
  const pantry = [ing('leche', 100)];
  const result = matchRecipesToPantry(pantry, [recipe(4, 'Batido', [ing('leche', 101), ing('plátano', 102)])]);
  assert.deepEqual(result, []);
});

test('coincidencia solo por nombre cuando falta foodId en algún lado', () => {
  const recipes = [recipe(5, 'Ensalada', [ing('Aceite de oliva', 30), ing('Sal'), ing('Lechuga', 80)])];
  // "aceite" (sin alimento) está contenido en "Aceite de oliva"; "sal" coincide exacto; la lechuga falta.
  const [result] = matchRecipesToPantry([ing('aceite'), ing('Sal', 90)], recipes);
  assert.equal(result.matchedCount, 2);
  assert.deepEqual(result.missingIngredients, ['Lechuga']);
});

test('el nombre se compara por palabras completas: "sal" no cubre "salsa de tomate"', () => {
  const recipes = [recipe(6, 'Macarrones', [ing('Salsa de tomate'), ing('Macarrones')])];
  const [result] = matchRecipesToPantry([ing('sal'), ing('macarrón')], recipes);
  assert.deepEqual(result.missingIngredients, ['Salsa de tomate']);
});

test('orden: más cobertura primero y, a igualdad, menos ingredientes que faltan', () => {
  const a = recipe(10, 'A', [ing('huevo'), ing('sal')]); // 2/2
  const b = recipe(11, 'B', [ing('huevo'), ing('sal'), ing('harina'), ing('azúcar')]); // 2/4 → faltan 2
  const c = recipe(12, 'C', [ing('huevo'), ing('pimienta')]); // 1/2 → falta 1
  const result = matchRecipesToPantry([ing('huevo'), ing('sal')], [b, c, a]);
  assert.deepEqual(result.map((r) => r.id), [10, 12, 11]);
});

test('despensa vacía o receta sin ingredientes: sin resultados', () => {
  assert.deepEqual(matchRecipesToPantry([], [TORTILLA]), []);
  assert.deepEqual(matchRecipesToPantry([ing('sal')], [recipe(7, 'Vacía', [])]), []);
});

test('un sustituto cubre el ingrediente y queda anotado', () => {
  const recipes = [recipe(20, 'Bizcocho', [ing('Mantequilla', 200), ing('Harina', 210)])];
  // Con foodId distintos en ambos lados la margarina no cubre la mantequilla, pero la sustituye.
  const [result] = matchRecipesToPantry([ing('Margarina', 201), ing('harina', 210)], recipes);
  assert.equal(result.coverage, 1);
  assert.deepEqual(result.missingIngredients, []);
  assert.deepEqual(result.substitutions, [{ recipeIngredient: 'Mantequilla', pantryItem: 'Margarina' }]);
});

test('un sustituto no gana a una coincidencia exacta', () => {
  const recipes = [recipe(21, 'Salteado', [ing('Aceite de oliva', 30), ing('Pimiento', 50)])];
  const pantry = [ing('Aceite de girasol', 31), ing('Aceite de oliva', 30), ing('pimiento', 50)];
  const [result] = matchRecipesToPantry(pantry, recipes);
  assert.equal(result.coverage, 1);
  assert.deepEqual(result.substitutions, []);
});

test('un ingrediente sin sustituto conocido sigue faltando', () => {
  const recipes = [recipe(22, 'Crema', [ing('Nata'), ing('Huevo'), ing('Queso')])];
  const [result] = matchRecipesToPantry([ing('nata para cocinar'), ing('huevo'), ing('mantequilla')], recipes);
  assert.deepEqual(result.missingIngredients, ['Queso']);
  assert.deepEqual(result.substitutions, []); // "nata para cocinar" contiene "nata": cubre directamente
});

test('orden: a igual cobertura y faltas, primero la receta que no necesita sustitutos', () => {
  const conSustituto = recipe(30, 'Con sustituto', [ing('mantequilla'), ing('harina')]);
  const sinSustituto = recipe(31, 'Sin sustituto', [ing('margarina'), ing('harina')]);
  const result = matchRecipesToPantry([ing('margarina'), ing('harina')], [conSustituto, sinSustituto]);
  assert.deepEqual(result.map((r) => r.id), [31, 30]);
  assert.equal(result[1].substitutions.length, 1);
});
