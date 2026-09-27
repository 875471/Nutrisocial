// Pruebas de robustez y seguridad: editar y borrar recetas ajenas, borrar la cuenta, límite
// de intentos, validación del registro, arranque sin JWT_SECRET y que ninguna respuesta
// exponga el hash de la contraseña. Las rutas se prueban por HTTP (Express + JWT reales)
// sobre un cliente de Prisma en memoria.
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');

const SECRET = 'secreto-de-prueba';
const PASSWORD = 'contraseña-buena';
const PASSWORD_HASH = bcrypt.hashSync(PASSWORD, 4);
const SRC = path.resolve(__dirname, '../src');

// Ninguna prueba debe salir a Internet (Open Food Facts): si algo lo intenta, falla rápido.
// El fetch real se guarda para hablar con el servidor de prueba.
const fetchReal = globalThis.fetch;
globalThis.fetch = async () => { throw new Error('Sin red en las pruebas'); };

// Aplica un `select` de Prisma a una fila: solo las claves pedidas. Así, si una ruta pidiera
// el autor entero (con su contraseña), la prueba lo vería en la respuesta.
function pick(row, select) {
  if (!select || select === true) return { ...row };
  return Object.fromEntries(Object.keys(select).filter((k) => select[k]).map((k) => [k, row[k]]));
}

function fakePrisma() {
  const db = {
    users: [
      { id: 1, email: 'ana@example.com', name: 'Ana', password: PASSWORD_HASH },
      { id: 2, email: 'luis@example.com', name: 'Luis', password: PASSWORD_HASH },
    ],
    recipes: [
      { id: 10, title: 'Crema de calabaza', authorId: 1, servings: 2, prepMinutes: 30, kcal: 300, protein: 5, carbs: 40, fat: 10, totalWeightGrams: 500, steps: '["Cocer"]', imageBase64: null, createdAt: new Date('2026-09-01') },
    ],
    ingredients: [{ id: 1, recipeId: 10, position: 0, name: 'Calabaza', quantity: 500, unit: 'g', grams: 500, foodId: null }],
    logEntries: [
      { id: 1, userId: 1, recipeId: 10 },
      { id: 2, userId: 1, recipeId: 10 },
      { id: 3, userId: 2, recipeId: 10 },
    ],
    foods: [{ id: 5, name: 'Harina de trigo', kcal: 340, protein: 10, carbs: 72, fat: 1, source: 'BEDCA' }],
  };

  function shapeRecipe(recipe, query = {}) {
    const spec = query.include ?? query.select ?? {};
    const out = { ...recipe, ingredients: db.ingredients.filter((i) => i.recipeId === recipe.id).map((i) => ({ ...i, food: null })) };
    const author = db.users.find((u) => u.id === recipe.authorId);
    out.author = spec.author === true ? { ...author } : pick(author, spec.author?.select);
    out._count = { likes: 0 };
    out.likes = [];
    return out;
  }

  db.client = {
    $transaction: (ops) => Promise.all(ops),
    user: {
      findUnique: async ({ where, select }) => {
        const user = db.users.find((u) => (where.id != null ? u.id === where.id : u.email === where.email));
        return user ? pick(user, select) : null;
      },
      create: async ({ data }) => {
        const user = { id: db.users.length + 100, ...data };
        db.users.push(user);
        return { ...user };
      },
      delete: async ({ where }) => {
        const i = db.users.findIndex((u) => u.id === where.id);
        const [user] = db.users.splice(i, 1);
        // Cascada del esquema: se van sus recetas.
        db.recipes = db.recipes.filter((r) => r.authorId !== user.id);
        return user;
      },
    },
    recipe: {
      findUnique: async ({ where, ...query }) => {
        const recipe = db.recipes.find((r) => r.id === where.id);
        if (!recipe) return null;
        return query.select && !query.select.author ? pick(recipe, query.select) : shapeRecipe(recipe, query);
      },
      update: async ({ where, data, ...query }) => {
        const recipe = db.recipes.find((r) => r.id === where.id);
        const { ingredients, ...fields } = data;
        Object.assign(recipe, fields);
        if (ingredients) {
          db.ingredients = db.ingredients.filter((i) => i.recipeId !== recipe.id);
          ingredients.create.forEach((ing, n) => db.ingredients.push({ id: 100 + n, recipeId: recipe.id, ...ing }));
        }
        return shapeRecipe(recipe, query);
      },
      delete: async ({ where }) => {
        const recipe = db.recipes.find((r) => r.id === where.id);
        db.recipes = db.recipes.filter((r) => r.id !== where.id);
        // onDelete: SetNull en LogEntry.
        db.logEntries.forEach((e) => { if (e.recipeId === where.id) e.recipeId = null; });
        return recipe;
      },
    },
    logEntry: {
      count: async ({ where }) => db.logEntries.filter((e) => e.recipeId === where.recipeId && e.userId === where.userId).length,
    },
    food: { findMany: async () => db.foods.map((f) => ({ ...f })) },
  };
  return db;
}

// Arranca una app con las rutas indicadas sobre la base simulada. Los módulos se cargan de
// nuevo en cada prueba: así cada una empieza con los contadores del límite de intentos a cero.
async function startApi(db, mounts) {
  process.env.JWT_SECRET = SECRET;
  for (const key of Object.keys(require.cache)) {
    if (key.startsWith(SRC)) delete require.cache[key];
  }
  const prismaPath = path.join(SRC, 'prismaClient.js');
  require.cache[prismaPath] = { id: prismaPath, filename: prismaPath, loaded: true, exports: db.client };

  const express = require('express');
  const app = express();
  app.use(express.json());
  for (const [prefix, file] of Object.entries(mounts)) app.use(prefix, require(path.join(SRC, file)));
  const server = await new Promise((resolve) => { const s = app.listen(0, () => resolve(s)); });
  const base = `http://127.0.0.1:${server.address().port}`;

  const request = async (method, url, body, userId) => {
    const headers = { 'content-type': 'application/json' };
    if (userId != null) headers.authorization = `Bearer ${jwt.sign({ userId }, SECRET)}`;
    const res = await fetchReal(base + url, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    const text = await res.text();
    return { status: res.status, text, body: text ? JSON.parse(text) : null };
  };
  return { request, close: () => new Promise((resolve) => server.close(resolve)) };
}
const EDITED = {
  title: 'Crema de calabaza y harina',
  servings: 4,
  prepMinutes: 20,
  steps: ['Mezclar', 'Hornear'],
  ingredients: [{ name: 'Harina de trigo', quantity: 200, unit: 'g' }],
};

// ---- Editar y borrar recetas ----

test('PUT /recipes/:id: solo el autor puede editar, y los totales se recalculan', async () => {
  const db = fakePrisma();
  const api = await startApi(db, { '/recipes': 'routes/recipes.js' });
  try {
    const forbidden = await api.request('PUT', '/recipes/10', EDITED, 2);
    assert.equal(forbidden.status, 403);
    assert.equal(db.recipes[0].title, 'Crema de calabaza');

    const ok = await api.request('PUT', '/recipes/10', EDITED, 1);
    assert.equal(ok.status, 200);
    assert.equal(ok.body.title, 'Crema de calabaza y harina');
    assert.equal(ok.body.servings, 4);
    // 200 g de harina a 340 kcal/100 g = 680 kcal, 170 por ración de 4.
    assert.equal(ok.body.nutrition.total.kcal, 680);
    assert.equal(ok.body.nutrition.perServing.kcal, 170);
    assert.deepEqual(ok.body.ingredients.map((i) => i.name), ['Harina de trigo']);

    assert.equal((await api.request('PUT', '/recipes/10', { ...EDITED, title: ' ' }, 1)).status, 400);
    assert.equal((await api.request('PUT', '/recipes/999', EDITED, 1)).status, 404);
    assert.equal((await api.request('PUT', '/recipes/10', EDITED)).status, 401);
  } finally {
    await api.close();
  }
});

test('DELETE /recipes/:id: solo el autor puede borrar, e informa de sus entradas del diario', async () => {
  const db = fakePrisma();
  const api = await startApi(db, { '/recipes': 'routes/recipes.js' });
  try {
    assert.equal((await api.request('DELETE', '/recipes/10', undefined, 2)).status, 403);
    assert.equal(db.recipes.length, 1);

    const ok = await api.request('DELETE', '/recipes/10', undefined, 1);
    assert.equal(ok.status, 200);
    // Ana tenía 2 entradas con esta receta; la de Luis no se cuenta.
    assert.deepEqual(ok.body, { deleted: true, orphanedLogEntries: 2 });
    assert.equal(db.recipes.length, 0);
    assert.ok(db.logEntries.every((e) => e.recipeId === null));

    // Receta borrada mientras alguien la miraba: mensaje legible.
    const gone = await api.request('GET', '/recipes/10', undefined, 2);
    assert.equal(gone.status, 404);
    assert.equal(gone.body.error, 'Esta receta ya no existe');
  } finally {
    await api.close();
  }
});

// ---- Borrar la cuenta ----

test('DELETE /auth/me: con contraseña incorrecta no borra nada (403, no 401)', async () => {
  const db = fakePrisma();
  const api = await startApi(db, { '/auth': 'routes/auth.js' });
  try {
    const wrong = await api.request('DELETE', '/auth/me', { password: 'otra-cosa' }, 1);
    // 403: la app cierra la sesión ante un 401, y un error al teclear no debe echar al usuario.
    assert.equal(wrong.status, 403);
    assert.ok(db.users.some((u) => u.id === 1));

    assert.equal((await api.request('DELETE', '/auth/me', {}, 1)).status, 400);
    assert.equal((await api.request('DELETE', '/auth/me', { password: PASSWORD })).status, 401);
    assert.ok(db.users.some((u) => u.id === 1));
  } finally {
    await api.close();
  }
});

test('DELETE /auth/me: con la contraseña correcta borra la cuenta y sus recetas', async () => {
  const db = fakePrisma();
  const api = await startApi(db, { '/auth': 'routes/auth.js' });
  try {
    const ok = await api.request('DELETE', '/auth/me', { password: PASSWORD }, 1);
    assert.equal(ok.status, 204);
    assert.ok(!db.users.some((u) => u.id === 1));
    assert.equal(db.recipes.length, 0);
    // Luis no se ve afectado.
    assert.ok(db.users.some((u) => u.id === 2));
    // Un segundo intento con el mismo token: la cuenta ya no existe.
    assert.equal((await api.request('DELETE', '/auth/me', { password: PASSWORD }, 1)).status, 404);
  } finally {
    await api.close();
  }
});

// ---- Límite de intentos ----

test('login: a partir del 11.º intento fallido en 15 minutos responde 429', async () => {
  const db = fakePrisma();
  const api = await startApi(db, { '/auth': 'routes/auth.js' });
  try {
    for (let i = 0; i < 10; i++) {
      assert.equal((await api.request('POST', '/auth/login', { email: 'ana@example.com', password: 'mal' })).status, 401);
    }
    const blocked = await api.request('POST', '/auth/login', { email: 'ana@example.com', password: PASSWORD });
    // Bloqueado aunque ahora la contraseña sea la buena: el límite va por IP.
    assert.equal(blocked.status, 429);
    assert.match(blocked.body.error, /Demasiados intentos/);
  } finally {
    await api.close();
  }
});

test('login: los inicios de sesión correctos no gastan el cupo', async () => {
  const db = fakePrisma();
  const api = await startApi(db, { '/auth': 'routes/auth.js' });
  try {
    for (let i = 0; i < 12; i++) {
      assert.equal((await api.request('POST', '/auth/login', { email: 'ana@example.com', password: PASSWORD })).status, 200);
    }
  } finally {
    await api.close();
  }
});

test('registro: a partir del 11.º intento en 15 minutos responde 429', async () => {
  const db = fakePrisma();
  const api = await startApi(db, { '/auth': 'routes/auth.js' });
  try {
    for (let i = 0; i < 10; i++) {
      const res = await api.request('POST', '/auth/register', { email: `u${i}@example.com`, password: 'una-clave-larga', name: `U${i}` });
      assert.equal(res.status, 201);
    }
    const blocked = await api.request('POST', '/auth/register', { email: 'otro@example.com', password: 'una-clave-larga', name: 'Otro' });
    assert.equal(blocked.status, 429);
  } finally {
    await api.close();
  }
});

// ---- Validación del registro ----

test('registro: exige contraseña de 8 caracteres y un email con formato válido', async () => {
  const db = fakePrisma();
  const api = await startApi(db, { '/auth': 'routes/auth.js' });
  try {
    const short = await api.request('POST', '/auth/register', { email: 'nuevo@example.com', password: '1234567', name: 'Nuevo' });
    assert.equal(short.status, 400);
    assert.match(short.body.error, /al menos 8 caracteres/);

    for (const email of ['sin-arroba.com', 'a@b', 'con espacio@example.com', '@example.com']) {
      const res = await api.request('POST', '/auth/register', { email, password: 'una-clave-larga', name: 'Nuevo' });
      assert.equal(res.status, 400, email);
      assert.match(res.body.error, /formato del email/);
    }

    assert.equal((await api.request('POST', '/auth/register', { email: 'x@example.com', password: 'una-clave-larga', name: '  ' })).status, 400);
    assert.equal((await api.request('POST', '/auth/register', { email: 'x@example.com', password: 12345678, name: 'X' })).status, 400);

    const ok = await api.request('POST', '/auth/register', { email: '  nuevo@example.com ', password: '12345678', name: ' Nuevo ' });
    assert.equal(ok.status, 201);
    assert.deepEqual({ email: ok.body.email, name: ok.body.name }, { email: 'nuevo@example.com', name: 'Nuevo' });
    assert.ok(!('password' in ok.body));
  } finally {
    await api.close();
  }
});

// ---- Fugas de datos ----

test('ninguna respuesta de recetas o de login incluye el hash de la contraseña', async () => {
  const db = fakePrisma();
  const api = await startApi(db, { '/recipes': 'routes/recipes.js', '/auth': 'routes/auth.js' });
  try {
    const responses = [
      await api.request('GET', '/recipes/10', undefined, 2),
      await api.request('PUT', '/recipes/10', EDITED, 1),
      await api.request('POST', '/auth/login', { email: 'ana@example.com', password: PASSWORD }),
    ];
    for (const res of responses) {
      assert.equal(res.status, 200, res.text);
      assert.ok(!res.text.includes(PASSWORD_HASH), 'la respuesta contiene el hash');
      assert.ok(!res.text.includes('"password"'), 'la respuesta contiene un campo password');
    }
  } finally {
    await api.close();
  }
});

// ---- Arranque ----

test('el servidor no arranca sin JWT_SECRET', () => {
  // Cadena vacía: dotenv no sobrescribe una variable ya definida, así que .env no la rellena.
  const result = spawnSync(process.execPath, [path.join(SRC, 'index.js')], {
    env: { ...process.env, JWT_SECRET: '' },
    encoding: 'utf8',
    timeout: 10000,
  });
  assert.equal(result.status, 1);
  assert.match(result.stderr, /JWT_SECRET/);
});
