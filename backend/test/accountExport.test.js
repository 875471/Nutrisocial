// Pruebas de la exportación de datos de la cuenta (GET /profile/export, src/account/export.js):
// formato del documento, que no salen la contraseña ni los tokens, que solo salen los datos del
// propio usuario y que la ruta lo sirve como archivo descargable.
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const jwt = require('jsonwebtoken');

const { buildAccountExport, EXPORT_FORMAT_VERSION } = require('../src/account/export');

const SECRET_FIELDS = ['password', 'verificationTokenHash', 'passwordResetTokenHash', 'isAdmin'];

// Base de datos en memoria con dos usuarios. Cada findMany filtra por el `where` que recibe, así
// que si la exportación olvidara filtrar por usuario, se colarían los datos de Luis.
function fakePrisma() {
  const at = (d) => new Date(`2026-11-${d}T10:00:00Z`);
  const users = [
    {
      id: 1, email: 'ana@example.com', name: 'Ana', password: '$2b$hash', verificationTokenHash: 'abc',
      passwordResetTokenHash: 'def', isAdmin: false, createdAt: at('01'), emailVerified: true,
      birthDate: new Date('1995-03-04T00:00:00Z'), heightCm: 165, weightKg: 60, sex: 'F', activityLevel: 'moderado', goal: 'mantener',
    },
    { id: 2, email: 'luis@example.com', name: 'Luis', password: 'x', createdAt: at('02') },
  ];
  const tables = {
    recipe: [
      {
        authorId: 1, id: 10, title: 'Lentejas', description: null, steps: '["Cocer"]', servings: 4, prepMinutes: 40,
        kcal: 1200, protein: 80, carbs: 160, fat: 20, totalWeightGrams: 900, imageBase64: null, aiGenerated: false,
        dietTags: ['vegano'], hidden: false, createdAt: at('03'),
        ingredients: [{ name: 'Lentejas', quantity: 300, unit: 'g', grams: 300, food: { name: 'Lenteja, seca', source: 'BEDCA' } }],
      },
      { authorId: 2, id: 11, title: 'De Luis', steps: '[]', ingredients: [], createdAt: at('04') },
    ],
    logEntry: [
      { userId: 1, date: new Date('2026-11-05T00:00:00Z'), name: 'Lentejas', recipeId: 10, servings: 1, grams: null, kcal: 300, protein: 20, carbs: 40, fat: 5, createdAt: at('05') },
      { userId: 2, date: new Date('2026-11-05T00:00:00Z'), name: 'Otra', kcal: 1 },
    ],
    pantryItem: [{ userId: 1, name: 'arroz', createdAt: at('06'), food: null }, { userId: 2, name: 'sal' }],
    comment: [
      { userId: 1, text: 'Rica', recipeId: 11, hidden: false, createdAt: at('07'), recipe: { title: 'De Luis' } },
      { userId: 2, text: 'De Luis', recipeId: 10 },
    ],
    recipeLike: [{ userId: 1, recipeId: 11, createdAt: at('08'), recipe: { title: 'De Luis' } }],
    savedRecipe: [],
    follow: [
      { followerId: 1, followingId: 2, createdAt: at('09'), following: { name: 'Luis' }, follower: { name: 'Ana' } },
    ],
    shoppingListItem: [{ userId: 1, label: 'Leche', quantity: '1 l', checked: false, sourceRecipeId: null, createdAt: at('10') }],
  };
  const matches = (row, where = {}) => Object.entries(where).every(([k, v]) => row[k] === v);
  // Devuelve solo los campos pedidos en `select` (primer nivel), como Prisma.
  const pick = (row, select) => (select ? Object.fromEntries(Object.keys(select).map((k) => [k, row[k]])) : row);
  const db = {
    user: { findUnique: async ({ where, select }) => pick(users.find((u) => u.id === where.id) ?? null, select) },
    subscription: { findUnique: async () => null },
  };
  for (const [name, rows] of Object.entries(tables)) {
    db[name] = { findMany: async ({ where, select }) => rows.filter((r) => matches(r, where)).map((r) => pick(r, select)) };
  }
  // requireAuth solo pide { id }, que pick deja pasar.
  const originalFind = db.user.findUnique;
  db.user.findUnique = async (args) => (users.find((u) => u.id === args.where.id) ? originalFind(args) : null);
  return db;
}

async function startApi(db) {
  process.env.JWT_SECRET = 'secreto-de-prueba';
  const prismaPath = path.resolve(__dirname, '../src/prismaClient.js');
  require.cache[prismaPath] = { id: prismaPath, filename: prismaPath, loaded: true, exports: db };
  delete require.cache[require.resolve('../src/routes/profile')];
  const express = require('express');
  const app = express();
  app.use('/profile', require('../src/routes/profile'));
  const server = await new Promise((resolve) => { const s = app.listen(0, () => resolve(s)); });
  const base = `http://127.0.0.1:${server.address().port}`;
  return {
    get: (userId) => fetch(`${base}/profile/export`, { headers: { authorization: `Bearer ${jwt.sign({ userId }, process.env.JWT_SECRET)}` } }),
    close: () => new Promise((resolve) => server.close(resolve)),
  };
}

test('GET /profile/export: archivo JSON con todos los datos de Ana y nada de Luis ni secretos', async () => {
  const api = await startApi(fakePrisma());
  try {
    const res = await api.get(1);
    assert.equal(res.status, 200);
    assert.match(res.headers.get('content-disposition'), /^attachment; filename="nutrisocial-mis-datos-\d{4}-\d{2}-\d{2}\.json"$/);
    const raw = await res.text();
    const data = JSON.parse(raw);

    assert.equal(data.format, 'nutrisocial-account-export');
    assert.equal(data.version, EXPORT_FORMAT_VERSION);
    assert.equal(data.profile.email, 'ana@example.com');
    assert.equal(data.profile.birthDate, '1995-03-04');
    for (const field of SECRET_FIELDS) assert.ok(!raw.includes(`"${field}"`), `no debe aparecer ${field}`);
    assert.ok(!raw.includes('$2b$hash'));

    assert.deepEqual(data.recipes.map((r) => r.title), ['Lentejas']);
    assert.deepEqual(data.recipes[0].steps, ['Cocer']);
    assert.deepEqual(data.recipes[0].nutritionTotal, { kcal: 1200, protein: 80, carbs: 160, fat: 20 });
    assert.equal(data.recipes[0].ingredients[0].food.name, 'Lenteja, seca');
    assert.deepEqual(data.logEntries.map((e) => [e.date, e.kcal]), [['2026-11-05', 300]]);
    assert.deepEqual(data.pantry.map((p) => p.name), ['arroz']);
    assert.deepEqual(data.comments.map((c) => [c.text, c.recipeTitle]), [['Rica', 'De Luis']]);
    assert.deepEqual(data.likedRecipes.map((l) => l.recipeTitle), ['De Luis']);
    assert.deepEqual(data.following, [{ name: 'Luis', since: '2026-11-09T10:00:00.000Z' }]);
    assert.deepEqual(data.followers, []);
    assert.deepEqual(data.shoppingList.map((i) => i.label), ['Leche']);
    assert.equal(data.subscription, null);
  } finally {
    await api.close();
  }
});

test('GET /profile/export: sin sesión, 401', async () => {
  const api = await startApi(fakePrisma());
  try {
    assert.equal((await api.get(99)).status, 401);
  } finally {
    await api.close();
  }
});

test('buildAccountExport: pasos que no son JSON y suscripción', () => {
  const data = buildAccountExport({
    user: { id: 1, email: 'a@b.c', name: 'A', createdAt: new Date(0), emailVerified: true },
    recipes: [{ id: 1, title: 'T', steps: 'texto suelto', ingredients: [], createdAt: new Date(0) }],
    logEntries: [], pantryItems: [], comments: [], likes: [], saves: [], following: [], followers: [], shoppingList: [],
    subscription: { plan: 'annual', status: 'active', startedAt: new Date(0), currentPeriodEnd: new Date(1), canceledAt: null, userId: 1, id: 7 },
  }, new Date('2026-11-17T00:00:00Z'));
  assert.deepEqual(data.recipes[0].steps, ['texto suelto']);
  assert.equal(data.exportedAt, '2026-11-17T00:00:00.000Z');
  assert.deepEqual(Object.keys(data.subscription), ['plan', 'status', 'startedAt', 'currentPeriodEnd', 'canceledAt']);
});
