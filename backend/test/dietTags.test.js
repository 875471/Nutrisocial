// Pruebas de las etiquetas dietéticas: validación de la lista fija, creación y edición de recetas
// con etiquetas y el filtro `?dietTags=` (hasEvery: la receta tiene que tenerlas todas) en el
// feed, el buscador y el buscador por despensa. Las rutas se prueban de verdad (Express + JWT) con
// un cliente de Prisma en memoria, como en subscription.test.js.
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const jwt = require('jsonwebtoken');

const { DIET_TAGS, cleanDietTags, parseDietTagsQuery, dietTagsWhere } = require('../src/nutrition/dietTags');

// ---- Funciones puras ----

test('cleanDietTags: deduplica, ordena según la lista fija y rechaza etiquetas desconocidas', () => {
  assert.deepEqual(cleanDietTags([]), { tags: [] });
  assert.deepEqual(cleanDietTags(['sin_gluten', 'vegano', 'sin_gluten']), { tags: ['vegano', 'sin_gluten'] });
  assert.deepEqual(cleanDietTags([...DIET_TAGS]), { tags: DIET_TAGS });
  assert.match(cleanDietTags(['vegano', 'keto']).error, /keto/);
  // Sin normalizar: la app manda los identificadores tal cual.
  assert.ok(cleanDietTags(['Vegano']).error);
  assert.ok(cleanDietTags('vegano').error);
  assert.ok(cleanDietTags([1]).error);
});

test('parseDietTagsQuery: lista separada por comas, vacío = sin filtro', () => {
  assert.deepEqual(parseDietTagsQuery(undefined), { tags: [] });
  assert.deepEqual(parseDietTagsQuery(''), { tags: [] });
  assert.deepEqual(parseDietTagsQuery('vegano'), { tags: ['vegano'] });
  assert.deepEqual(parseDietTagsQuery('sin_gluten, vegano,'), { tags: ['vegano', 'sin_gluten'] });
  assert.deepEqual(parseDietTagsQuery(['vegano', 'sin_lactosa']), { tags: ['vegano', 'sin_lactosa'] });
  assert.ok(parseDietTagsQuery('vegano,paleo').error);
});

test('dietTagsWhere: hasEvery con etiquetas, ninguna condición sin ellas', () => {
  assert.deepEqual(dietTagsWhere([]), {});
  assert.deepEqual(dietTagsWhere(['vegano', 'sin_gluten']), { dietTags: { hasEvery: ['vegano', 'sin_gluten'] } });
});

// ---- Rutas ----

const FOODS = [
  { id: 1, name: 'Lenteja, seca', kcal: 304, protein: 23.8, carbs: 48.8, fat: 1.7, source: 'BEDCA' },
  { id: 2, name: 'Arroz, blanco, crudo', kcal: 354, protein: 7, carbs: 77, fat: 0.6, source: 'BEDCA' },
  { id: 3, name: 'Leche de vaca, entera', kcal: 65, protein: 3.1, carbs: 4.7, fat: 3.8, source: 'BEDCA' },
];

// ¿Cumple `row` la parte de `where` que usan estas rutas (id, authorId y dietTags.hasEvery)?
function matchesWhere(row, where) {
  if (!where) return true;
  for (const [key, cond] of Object.entries(where)) {
    if (key === 'dietTags') {
      if (!cond.hasEvery.every((tag) => row.dietTags.includes(tag))) return false;
    } else if (cond && typeof cond === 'object' && 'in' in cond) {
      if (!cond.in.includes(row[key])) return false;
    } else if (row[key] !== cond) {
      return false;
    }
  }
  return true;
}

// Usuario 1 (Ana) y 2 (Luis). Solo imita las consultas de requireAuth y de las rutas probadas.
function fakePrisma() {
  const users = [{ id: 1, name: 'Ana' }, { id: 2, name: 'Luis' }];
  const recipes = [];
  const pantryItems = [];
  let nextId = 1;
  let clock = Date.parse('2026-11-12T10:00:00Z');

  // La fila completa con todo lo que puedan pedir select/include: autor, contadores y la parte
  // social vacía, e ingredientes con su alimento.
  const view = (r) => ({
    ...r,
    author: { name: users.find((u) => u.id === r.authorId).name, followers: [] },
    ingredients: r.ingredients.map((ing) => ({ ...ing, food: FOODS.find((f) => f.id === ing.foodId) ?? null })),
    _count: { likes: 0, comments: 0 },
    likes: [],
    saves: [],
    comments: [],
  });

  return {
    recipes,
    pantryItems,
    user: { findUnique: async ({ where }) => users.find((u) => u.id === where.id) ?? null },
    food: { findMany: async () => FOODS },
    pantryItem: { findMany: async ({ where }) => pantryItems.filter((p) => p.userId === where.userId) },
    recipe: {
      create: async ({ data }) => {
        const { ingredients, ...fields } = data;
        const row = {
          id: nextId++, dietTags: [], aiGenerated: false, imageBase64: null, createdAt: new Date(clock += 1000),
          ...fields, ingredients: ingredients.create,
        };
        recipes.push(row);
        return view(row);
      },
      update: async ({ where, data }) => {
        const row = recipes.find((r) => r.id === where.id);
        const { ingredients, ...fields } = data;
        Object.assign(row, fields);
        if (ingredients) row.ingredients = ingredients.create;
        return view(row);
      },
      findUnique: async ({ where }) => {
        const row = recipes.find((r) => r.id === where.id);
        return row ? view(row) : null;
      },
      findMany: async ({ where, take } = {}) => {
        const rows = recipes.filter((r) => matchesWhere(r, where))
          .sort((a, b) => b.createdAt - a.createdAt || b.id - a.id)
          .map(view);
        return take == null ? rows : rows.slice(0, take);
      },
    },
  };
}

async function startApi(db) {
  process.env.JWT_SECRET = 'secreto-de-prueba';
  const prismaPath = path.resolve(__dirname, '../src/prismaClient.js');
  require.cache[prismaPath] = { id: prismaPath, filename: prismaPath, loaded: true, exports: db };
  // El índice de alimentos de matchFood se carga una vez por módulo: se recargan para que lo
  // lean de esta base en memoria.
  for (const mod of ['../src/routes/recipes', '../src/nutrition/calculate', '../src/nutrition/matchFood']) {
    delete require.cache[require.resolve(mod)];
  }

  const express = require('express');
  const app = express();
  app.use(express.json());
  app.use('/recipes', require('../src/routes/recipes'));
  const server = await new Promise((resolve) => { const s = app.listen(0, () => resolve(s)); });
  const base = `http://127.0.0.1:${server.address().port}`;

  const as = (userId) => async (method, url, body) => {
    const res = await fetch(base + url, {
      method,
      headers: { 'content-type': 'application/json', authorization: `Bearer ${jwt.sign({ userId }, process.env.JWT_SECRET)}` },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    return { status: res.status, body: await res.json() };
  };
  return { ana: as(1), luis: as(2), close: () => new Promise((resolve) => server.close(resolve)) };
}

// Cuerpo mínimo válido de POST /recipes. Ingredientes con foodId, para no buscar por nombre.
function recipeBody(title, dietTags, foodId = 1) {
  return {
    title,
    ingredients: [{ name: 'ingrediente', quantity: 100, unit: 'g', foodId }],
    steps: ['Cocinar'],
    servings: 2,
    ...(dietTags !== undefined && { dietTags }),
  };
}

test('POST /recipes: guarda las etiquetas válidas sin duplicados; sin el campo, ninguna', async () => {
  const db = fakePrisma();
  const api = await startApi(db);
  try {
    const tagged = await api.ana('POST', '/recipes', recipeBody('Lentejas', ['vegano', 'sin_gluten', 'vegano']));
    assert.equal(tagged.status, 201);
    assert.deepEqual(tagged.body.dietTags, ['vegano', 'sin_gluten']);
    assert.deepEqual(db.recipes[0].dietTags, ['vegano', 'sin_gluten']);

    const plain = await api.ana('POST', '/recipes', recipeBody('Arroz', undefined, 2));
    assert.equal(plain.status, 201);
    assert.deepEqual(plain.body.dietTags, []);

    // El detalle también las devuelve.
    const detail = await api.luis('GET', `/recipes/${tagged.body.id}`);
    assert.deepEqual(detail.body.dietTags, ['vegano', 'sin_gluten']);
  } finally {
    await api.close();
  }
});

test('POST y PUT /recipes: 400 con una etiqueta desconocida, sin guardar nada', async () => {
  const db = fakePrisma();
  const api = await startApi(db);
  try {
    for (const dietTags of [['keto'], ['vegano', 'sin-gluten'], 'vegano', [null]]) {
      const res = await api.ana('POST', '/recipes', recipeBody('Lentejas', dietTags));
      assert.equal(res.status, 400, JSON.stringify(dietTags));
    }
    assert.equal(db.recipes.length, 0);

    const created = await api.ana('POST', '/recipes', recipeBody('Lentejas', ['vegano']));
    const res = await api.ana('PUT', `/recipes/${created.body.id}`, recipeBody('Lentejas', ['vegano', 'paleo']));
    assert.equal(res.status, 400);
    assert.match(res.body.error, /paleo/);
    assert.deepEqual(db.recipes[0].dietTags, ['vegano']);
  } finally {
    await api.close();
  }
});

test('PUT /recipes/:id: cambia las etiquetas; sin el campo conserva las que había', async () => {
  const db = fakePrisma();
  const api = await startApi(db);
  try {
    const { body: recipe } = await api.ana('POST', '/recipes', recipeBody('Lentejas', ['vegano']));

    const edited = await api.ana('PUT', `/recipes/${recipe.id}`, recipeBody('Lentejas', ['sin_lactosa', 'vegetariano']));
    assert.equal(edited.status, 200);
    assert.deepEqual(edited.body.dietTags, ['vegetariano', 'sin_lactosa']);

    const kept = await api.ana('PUT', `/recipes/${recipe.id}`, recipeBody('Lentejas con arroz'));
    assert.equal(kept.status, 200);
    assert.deepEqual(kept.body.dietTags, ['vegetariano', 'sin_lactosa']);

    const cleared = await api.ana('PUT', `/recipes/${recipe.id}`, recipeBody('Lentejas', []));
    assert.deepEqual(cleared.body.dietTags, []);
  } finally {
    await api.close();
  }
});

// Tres recetas: solo vegana, vegana y sin gluten, y solo sin gluten.
async function seedTagged(api) {
  const ids = {};
  for (const [title, tags] of [['Hummus', ['vegano']], ['Lentejas', ['vegano', 'sin_gluten']], ['Tortilla', ['sin_gluten']]]) {
    ids[title] = (await api.ana('POST', '/recipes', recipeBody(title, tags))).body.id;
  }
  return ids;
}

const titles = (recipes) => recipes.map((r) => r.title).sort();

test('filtro dietTags en /feed y /search: con una etiqueta y con varias a la vez (todas)', async () => {
  const api = await startApi(fakePrisma());
  try {
    await seedTagged(api);
    for (const route of ['/recipes/feed', '/recipes/search']) {
      assert.deepEqual(titles((await api.luis('GET', route)).body.recipes), ['Hummus', 'Lentejas', 'Tortilla'], route);

      const vegan = await api.luis('GET', `${route}?dietTags=vegano`);
      assert.equal(vegan.status, 200);
      assert.deepEqual(titles(vegan.body.recipes), ['Hummus', 'Lentejas'], route);
      assert.ok(vegan.body.recipes.every((r) => r.dietTags.includes('vegano')));

      // Las dos a la vez: solo la que tiene ambas, no las que tienen alguna.
      const both = await api.luis('GET', `${route}?dietTags=sin_gluten,vegano`);
      assert.deepEqual(titles(both.body.recipes), ['Lentejas'], route);

      const none = await api.luis('GET', `${route}?dietTags=sin_frutos_secos`);
      assert.deepEqual(none.body.recipes, [], route);

      assert.equal((await api.luis('GET', `${route}?dietTags=vegano,keto`)).status, 400, route);
    }
    // El buscador combina el texto con las etiquetas.
    const text = await api.luis('GET', '/recipes/search?q=tortilla&dietTags=vegano');
    assert.deepEqual(text.body.recipes, []);
  } finally {
    await api.close();
  }
});

test('filtro dietTags en /by-pantry: solo las recetas con todas las etiquetas', async () => {
  const db = fakePrisma();
  const api = await startApi(db);
  try {
    await seedTagged(api);
    // Las tres usan el alimento 1, así que la despensa de Luis las cubre todas.
    db.pantryItems.push({ userId: 2, name: 'lentejas', foodId: 1 });

    const all = await api.luis('GET', '/recipes/by-pantry');
    assert.deepEqual(titles(all.body.readyToCook), ['Hummus', 'Lentejas', 'Tortilla']);

    const glutenFree = await api.luis('GET', '/recipes/by-pantry?dietTags=sin_gluten');
    assert.deepEqual(titles(glutenFree.body.readyToCook), ['Lentejas', 'Tortilla']);
    assert.ok(glutenFree.body.readyToCook.every((r) => r.dietTags.includes('sin_gluten')));

    const both = await api.luis('GET', '/recipes/by-pantry?dietTags=vegano,sin_gluten');
    assert.deepEqual(titles(both.body.readyToCook), ['Lentejas']);

    assert.equal((await api.luis('GET', '/recipes/by-pantry?dietTags=crudivegano')).status, 400);
  } finally {
    await api.close();
  }
});
