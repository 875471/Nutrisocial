// Pruebas del diario (GET /log y PUT /log/:id): objetivos de macronutrientes junto al de kcal y
// edición de la cantidad de una entrada, sobre un cliente de Prisma en memoria.
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const jwt = require('jsonwebtoken');

const { compareMacrosWithGoals } = require('../src/nutrition/dailyLog');
const { getMacroTargets } = require('../src/nutrition/macroTargets');

test('compareMacrosWithGoals: lo que queda, el exceso y el progreso de cada macronutriente', () => {
  const totals = { kcal: 1500, protein: 100, carbs: 250, fat: 70 };
  assert.deepEqual(compareMacrosWithGoals(totals, { proteinG: 150, carbsG: 200, fatG: 70 }), {
    proteinGoal: 150, remainingProtein: 50, excessProtein: 0, proteinProgress: 0.667,
    carbsGoal: 200, remainingCarbs: 0, excessCarbs: 50, carbsProgress: 1.25,
    fatGoal: 70, remainingFat: 0, excessFat: 0, fatProgress: 1,
  });
  // Sin objetivos (perfil incompleto), todos los campos a null.
  const empty = compareMacrosWithGoals(totals, null);
  assert.equal(Object.keys(empty).length, 12);
  assert.ok(Object.values(empty).every((value) => value === null));
});

// ---- Rutas ----

// Usuario 1 con el perfil completo y usuario 2 sin perfil. La receta 10 tiene 4 raciones
// (100 kcal, 10 g de proteína... por ración); la 11 se ha borrado (la entrada 3 conserva su id
// para simular la carrera entre leer la entrada y leer la receta) y la entrada 4 es de una
// receta borrada antes (recipeId a null por onDelete: SetNull).
function fakePrisma() {
  const users = {
    1: { id: 1, birthDate: new Date('1996-01-01'), heightCm: 175, weightKg: 70, sex: 'M', activityLevel: 'ligero', goal: 'perder_peso' },
    2: { id: 2 },
  };
  const recipes = { 10: { id: 10, title: 'Lentejas', servings: 4, kcal: 400, protein: 40, carbs: 60, fat: 8 } };
  const foods = [{ id: 20, name: 'Manzana', kcal: 52, protein: 0.3, carbs: 14, fat: 0.2 }];
  const day = new Date('2026-09-29T00:00:00Z');
  const entries = [
    { id: 1, userId: 1, date: day, name: 'Lentejas', recipeId: 10, foodId: null, servings: 1, grams: null, kcal: 100, protein: 10, carbs: 15, fat: 2, createdAt: day },
    { id: 2, userId: 1, date: day, name: 'Manzana', recipeId: null, foodId: 20, servings: null, grams: 100, kcal: 52, protein: 0.3, carbs: 14, fat: 0.2, createdAt: day },
    { id: 3, userId: 1, date: day, name: 'Borrada', recipeId: 11, foodId: null, servings: 1, grams: null, kcal: 300, protein: 5, carbs: 5, fat: 5, createdAt: day },
    { id: 4, userId: 1, date: day, name: 'Borrada antes', recipeId: null, foodId: null, servings: 2, grams: null, kcal: 200, protein: 5, carbs: 5, fat: 5, createdAt: day },
    { id: 5, userId: 2, date: day, name: 'Lentejas', recipeId: 10, foodId: null, servings: 1, grams: null, kcal: 100, protein: 10, carbs: 15, fat: 2, createdAt: day },
  ];
  const matches = (e, where) => e.id === where.id && e.userId === where.userId;
  return {
    entries,
    user: { findUnique: async ({ where }) => users[where.id] ?? null },
    recipe: { findUnique: async ({ where }) => recipes[where.id] ?? null },
    food: { findMany: async () => foods },
    logEntry: {
      findMany: async ({ where }) => entries.filter((e) => e.userId === where.userId && +e.date === +where.date),
      findFirst: async ({ where }) => entries.find((e) => matches(e, where)) ?? null,
      updateMany: async ({ where, data }) => {
        const found = entries.filter((e) => matches(e, where));
        found.forEach((e) => Object.assign(e, data));
        return { count: found.length };
      },
    },
  };
}

async function startApi(db) {
  process.env.JWT_SECRET = 'secreto-de-prueba';
  const prismaPath = path.resolve(__dirname, '../src/prismaClient.js');
  require.cache[prismaPath] = { id: prismaPath, filename: prismaPath, loaded: true, exports: db };
  // matchFood guarda en memoria el índice de alimentos: se recarga para que lea de este Prisma.
  for (const mod of ['../src/routes/log', '../src/nutrition/matchFood', '../src/nutrition/recommend']) {
    delete require.cache[require.resolve(mod)];
  }
  const express = require('express');
  const app = express();
  app.use(express.json());
  app.use('/log', require('../src/routes/log'));
  const server = await new Promise((resolve) => { const s = app.listen(0, () => resolve(s)); });
  const base = `http://127.0.0.1:${server.address().port}`;
  const request = async (method, userId, url, body) => {
    const res = await fetch(base + url, {
      method,
      headers: {
        authorization: `Bearer ${jwt.sign({ userId }, process.env.JWT_SECRET)}`,
        ...(body && { 'content-type': 'application/json' }),
      },
      body: body && JSON.stringify(body),
    });
    return { status: res.status, body: res.status === 204 ? null : await res.json() };
  };
  return { request, close: () => new Promise((resolve) => server.close(resolve)) };
}

test('GET /log: objetivos de proteína, hidratos y grasas con perfil completo; null sin perfil', async () => {
  const db = fakePrisma();
  const api = await startApi(db);
  try {
    const { status, body } = await api.request('GET', 1, '/log?date=2026-09-29');
    assert.equal(status, 200);
    assert.ok(body.dailyCalorieGoal > 1000);
    const targets = getMacroTargets(await db.user.findUnique({ where: { id: 1 } }));
    assert.equal(body.proteinGoal, targets.proteinG);
    assert.equal(body.carbsGoal, targets.carbsG);
    assert.equal(body.fatGoal, targets.fatG);
    for (const [key, suffix] of [['protein', 'Protein'], ['carbs', 'Carbs'], ['fat', 'Fat']]) {
      const goal = body[`${key}Goal`];
      const consumed = body.totals[key];
      assert.ok(goal > consumed, key);
      assert.equal(body[`remaining${suffix}`], Math.round((goal - consumed) * 10) / 10, key);
      assert.equal(body[`excess${suffix}`], 0, key);
      assert.equal(body[`${key}Progress`], Math.round((consumed / goal) * 1000) / 1000, key);
    }

    const incomplete = await api.request('GET', 2, '/log?date=2026-09-29');
    assert.equal(incomplete.body.dailyCalorieGoal, null);
    for (const field of ['proteinGoal', 'remainingProtein', 'excessProtein', 'proteinProgress',
      'carbsGoal', 'remainingCarbs', 'excessCarbs', 'carbsProgress',
      'fatGoal', 'remainingFat', 'excessFat', 'fatProgress']) {
      assert.equal(incomplete.body[field], null, field);
    }
  } finally {
    await api.close();
  }
});

test('PUT /log/:id: cambiar las raciones de una receta recalcula sus valores', async () => {
  const db = fakePrisma();
  const api = await startApi(db);
  try {
    const { status, body } = await api.request('PUT', 1, '/log/1', { servings: 2.5 });
    assert.equal(status, 200);
    assert.equal(body.type, 'recipe');
    assert.equal(body.servings, 2.5);
    assert.deepEqual([body.kcal, body.protein, body.carbs, body.fat], [250, 25, 37.5, 5]);
    assert.equal(db.entries[0].kcal, 250);
  } finally {
    await api.close();
  }
});

test('PUT /log/:id: cambiar los gramos de un alimento recalcula sus valores', async () => {
  const db = fakePrisma();
  const api = await startApi(db);
  try {
    const { status, body } = await api.request('PUT', 1, '/log/2', { grams: 250 });
    assert.equal(status, 200);
    assert.equal(body.type, 'food');
    assert.equal(body.grams, 250);
    assert.deepEqual([body.kcal, body.protein, body.carbs, body.fat], [130, 0.8, 35, 0.5]);
  } finally {
    await api.close();
  }
});

test('PUT /log/:id: cantidades fuera de rango o del campo equivocado se rechazan con 400', async () => {
  const db = fakePrisma();
  const api = await startApi(db);
  try {
    for (const servings of [0, 0.05, 21, -1, '2', null]) {
      assert.equal((await api.request('PUT', 1, '/log/1', { servings })).status, 400, String(servings));
    }
    for (const grams of [0, 5001, '100']) {
      assert.equal((await api.request('PUT', 1, '/log/2', { grams })).status, 400, String(grams));
    }
    const wrongField = await api.request('PUT', 1, '/log/1', { grams: 100 });
    assert.equal(wrongField.status, 400);
    assert.match(wrongField.body.error, /receta/);
    assert.equal((await api.request('PUT', 1, '/log/2', { servings: 1 })).status, 400);
    assert.equal((await api.request('PUT', 1, '/log/abc', { servings: 1 })).status, 400);
    // Nada ha cambiado.
    assert.equal(db.entries[0].servings, 1);
    assert.equal(db.entries[1].grams, 100);
  } finally {
    await api.close();
  }
});

test('PUT /log/:id: la entrada de otro usuario o inexistente responde 404 sin modificarla', async () => {
  const db = fakePrisma();
  const api = await startApi(db);
  try {
    const other = await api.request('PUT', 1, '/log/5', { servings: 3 });
    const missing = await api.request('PUT', 1, '/log/999', { servings: 3 });
    assert.equal(other.status, 404);
    assert.deepEqual(other.body, missing.body);
    assert.equal(db.entries[4].servings, 1);
  } finally {
    await api.close();
  }
});

test('PUT /log/:id: si la receta ya no existe responde 404 y deja la entrada como estaba', async () => {
  const db = fakePrisma();
  const api = await startApi(db);
  try {
    for (const id of [3, 4]) {
      const before = { ...db.entries[id - 1] };
      const { status, body } = await api.request('PUT', 1, `/log/${id}`, { servings: 3 });
      assert.equal(status, 404);
      assert.match(body.error, /receta.*ya no existe/);
      assert.deepEqual(db.entries[id - 1], before);
    }
  } finally {
    await api.close();
  }
});
