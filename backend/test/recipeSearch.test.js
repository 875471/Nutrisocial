// Pruebas del buscador de recetas por texto libre (funciones puras de social/recipeSearch.js).
const test = require('node:test');
const assert = require('node:assert/strict');

const { queryTerms, matchesQuery, parseSearchQuery, searchRecipes } = require('../src/social/recipeSearch');

const recipe = (id, title, ingredients, prepMinutes, day) => ({
  id, title, prepMinutes, createdAt: new Date(`2026-09-${String(day).padStart(2, '0')}T10:00:00Z`),
  ingredients: ingredients.map((name) => ({ name })),
});

const RECIPES = [
  recipe(1, 'Arroz con pollo', ['arroz', 'pechuga de pollo', 'pimiento rojo'], 45, 1),
  recipe(2, 'Tortilla de patatas', ['Patatas', 'huevos', 'cebolla'], 40, 2),
  recipe(3, 'Crema de calabaza', ['Calabaza', 'cebolla', 'patata'], 35, 3),
  recipe(4, 'Macedonia', ['naranja', 'plátano', 'limones'], null, 4),
  recipe(5, 'Arroz con leche', ['leche', 'arroz', 'azúcar'], 50, 5),
];

const search = (params) => searchRecipes(RECIPES, { q: '', sortBy: 'createdAt', order: 'desc', cursor: null, limit: 20, ...params });

test('queryTerms: normaliza, quita palabras vacías y pasa a singular', () => {
  assert.deepEqual(queryTerms('  Tortilla DE Patatas '), ['tortilla', 'patata']);
  assert.deepEqual(queryTerms('limones'), ['limon']);
  assert.deepEqual(queryTerms(''), []);
});

test('matchesQuery: busca en el título y en los ingredientes, sin tildes ni mayúsculas', () => {
  const [pollo, tortilla, , macedonia] = RECIPES;
  assert.ok(matchesQuery(pollo, queryTerms('POLLO')));
  // Todas las palabras tienen que aparecer, en el título o en algún ingrediente.
  assert.ok(matchesQuery(pollo, queryTerms('arroz pimiento')));
  assert.ok(!matchesQuery(pollo, queryTerms('arroz patata')));
  assert.ok(matchesQuery(macedonia, queryTerms('platano')));
  // El plural de la búsqueda encuentra el singular del ingrediente, y al revés.
  assert.ok(matchesQuery(macedonia, queryTerms('limón')));
  assert.ok(matchesQuery(tortilla, queryTerms('huevo')));
});

test('searchRecipes: sin texto devuelve todas, de la más reciente a la más antigua', () => {
  const r = search({});
  assert.deepEqual(r.ids, [5, 4, 3, 2, 1]);
  assert.equal(r.total, 5);
  assert.equal(r.nextCursor, null);
  assert.deepEqual(search({ order: 'asc' }).ids, [1, 2, 3, 4, 5]);
});

test('searchRecipes: por tiempo de preparación, las recetas sin tiempo van siempre al final', () => {
  assert.deepEqual(search({ sortBy: 'prepMinutes', order: 'asc' }).ids, [3, 2, 1, 5, 4]);
  assert.deepEqual(search({ sortBy: 'prepMinutes', order: 'desc' }).ids, [5, 1, 2, 3, 4]);
});

test('searchRecipes: filtra y pagina con cursor', () => {
  const arroz = search({ q: 'arroz' });
  assert.deepEqual(arroz.ids, [5, 1]);
  assert.equal(arroz.total, 2);

  const first = search({ q: 'cebolla', limit: 1 });
  assert.deepEqual(first.ids, [3]);
  assert.equal(first.nextCursor, 3);
  const second = search({ q: 'cebolla', limit: 1, cursor: first.nextCursor });
  assert.deepEqual(second.ids, [2]);
  assert.equal(second.nextCursor, null);

  assert.deepEqual(search({ q: 'lentejas' }).ids, []);
  assert.match(search({ q: 'arroz', cursor: 2 }).error, /Cursor no válido/);
});

test('parseSearchQuery: valores por defecto y validación', () => {
  assert.deepEqual(parseSearchQuery({}), { q: '', sortBy: 'createdAt', order: 'desc' });
  assert.deepEqual(parseSearchQuery({ q: ' pollo ', sortBy: 'prepMinutes', order: 'asc' }), { q: 'pollo', sortBy: 'prepMinutes', order: 'asc' });
  assert.ok(parseSearchQuery({ sortBy: 'likes' }).error);
  assert.ok(parseSearchQuery({ order: 'arriba' }).error);
  assert.ok(parseSearchQuery({ q: 'x'.repeat(101) }).error);
  assert.ok(parseSearchQuery({ q: ['a', 'b'] }).error);
});
