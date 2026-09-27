// Pruebas de las fotos de receta y de los "me gusta". Las rutas se prueban de verdad (Express +
// JWT) con un cliente de Prisma en memoria que imita las consultas que usan.
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const jwt = require('jsonwebtoken');

const { validateImageBase64, likeSummary, likeFields, MAX_IMAGE_BYTES } = require('../src/social/recipeSocial');

// Cabeceras mínimas de cada formato, rellenadas hasta el tamaño pedido.
const JPEG_HEAD = Buffer.from([0xff, 0xd8, 0xff, 0xe0, 0, 0x10, 0x4a, 0x46, 0x49, 0x46, 0, 1]);
const PNG_HEAD = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0x0d]);
function image(head, bytes) {
  return Buffer.concat([head, Buffer.alloc(bytes - head.length, 7)]).toString('base64');
}
const SMALL_JPEG = image(JPEG_HEAD, 1000);

// ---- Funciones puras ----

test('likeSummary: cuenta los likes y detecta el del propio usuario', () => {
  assert.deepEqual(likeSummary({ _count: { likes: 3 }, likes: [{ id: 9 }] }), { likesCount: 3, likedByMe: true });
  assert.deepEqual(likeSummary({ _count: { likes: 3 }, likes: [] }), { likesCount: 3, likedByMe: false });
  // Sin datos de likes cargados: cero y sin like.
  assert.deepEqual(likeSummary({}), { likesCount: 0, likedByMe: false });
});

test('likeFields filtra los likes por el usuario que pregunta', () => {
  const fields = likeFields(42);
  assert.deepEqual(fields._count, { select: { likes: true } });
  assert.deepEqual(fields.likes.where, { userId: 42 });
});

test('validateImageBase64: acepta JPEG y PNG, y null significa sin foto', () => {
  assert.deepEqual(validateImageBase64(SMALL_JPEG), { value: SMALL_JPEG });
  assert.ok(validateImageBase64(image(PNG_HEAD, 500)).value);
  assert.deepEqual(validateImageBase64(null), { value: null });
  assert.deepEqual(validateImageBase64(undefined), { value: null });
  // Se tolera el prefijo data: y los saltos de línea.
  assert.equal(validateImageBase64(`data:image/jpeg;base64,${SMALL_JPEG.slice(0, 40)}\n${SMALL_JPEG.slice(40)}`).value, SMALL_JPEG);
});

test('validateImageBase64: rechaza lo que no es una imagen o pasa de 2 MB', () => {
  assert.match(validateImageBase64(123).error, /texto Base64/);
  assert.match(validateImageBase64('esto no es base64!').error, /no es un texto Base64 válido/);
  assert.match(validateImageBase64(Buffer.from('hola, soy un texto').toString('base64')).error, /JPEG, PNG o WebP/);
  assert.ok(validateImageBase64(image(JPEG_HEAD, MAX_IMAGE_BYTES)).value);
  assert.match(validateImageBase64(image(JPEG_HEAD, MAX_IMAGE_BYTES + 1)).error, /máximo es 2 MB/);
});

// ---- Rutas ----

// Base de datos en memoria: usuarios 1 (Ana) y 2 (Luis); la receta 10 es de Ana.
function fakePrisma() {
  const users = { 1: 'Ana', 2: 'Luis' };
  const recipes = [
    { id: 10, title: 'Crema de calabaza', authorId: 1, servings: 4, prepMinutes: 30, kcal: 560, protein: 12, carbs: 58, fat: 31, totalWeightGrams: null, steps: '["Cocer"]', imageBase64: null, createdAt: new Date('2026-09-01') },
    { id: 11, title: 'Tortilla', authorId: 2, servings: 2, prepMinutes: null, kcal: 800, protein: 30, carbs: 50, fat: 50, totalWeightGrams: null, steps: '["Batir"]', imageBase64: null, createdAt: new Date('2026-09-02') },
    { id: 12, title: 'Pisto', authorId: 2, servings: 3, prepMinutes: 40, kcal: 450, protein: 9, carbs: 40, fat: 27, totalWeightGrams: null, steps: '["Pochar"]', imageBase64: null, createdAt: new Date('2026-09-03') },
  ];
  const likes = [];

  // Devuelve la receta con las relaciones que pide la consulta (include o select).
  function shape(recipe, query = {}) {
    const spec = query.include ?? query.select ?? {};
    const out = { ...recipe, author: { name: users[recipe.authorId] }, ingredients: [] };
    if (spec._count) out._count = { likes: likes.filter((l) => l.recipeId === recipe.id).length };
    if (spec.likes) {
      const userId = spec.likes.where.userId;
      out.likes = likes.filter((l) => l.recipeId === recipe.id && l.userId === userId).map((l) => ({ id: l.id }));
    }
    return out;
  }

  return {
    likes,
    recipes,
    recipe: {
      findUnique: async ({ where, ...query }) => {
        const recipe = recipes.find((r) => r.id === where.id);
        return recipe ? shape(recipe, query) : null;
      },
      findMany: async ({ take, cursor, skip = 0, ...query }) => {
        const sorted = [...recipes].sort((a, b) => b.createdAt - a.createdAt || b.id - a.id);
        const start = cursor ? sorted.findIndex((r) => r.id === cursor.id) : 0;
        return sorted.slice(start + skip, start + skip + take).map((r) => shape(r, query));
      },
      update: async ({ where, data, ...query }) => {
        const recipe = recipes.find((r) => r.id === where.id);
        Object.assign(recipe, data);
        return shape(recipe, query);
      },
    },
    recipeLike: {
      upsert: async ({ where: { userId_recipeId: key }, create }) => {
        let like = likes.find((l) => l.userId === key.userId && l.recipeId === key.recipeId);
        if (!like) likes.push((like = { id: likes.length + 1, ...create }));
        return like;
      },
      deleteMany: async ({ where }) => {
        const before = likes.length;
        for (let i = likes.length - 1; i >= 0; i--) {
          if (likes[i].userId === where.userId && likes[i].recipeId === where.recipeId) likes.splice(i, 1);
        }
        return { count: before - likes.length };
      },
    },
  };
}

// Arranca la API de recetas sobre la base simulada y devuelve un cliente HTTP por usuario.
async function startApi(db) {
  process.env.JWT_SECRET = 'secreto-de-prueba';
  const prismaPath = path.resolve(__dirname, '../src/prismaClient.js');
  const recipesPath = require.resolve('../src/routes/recipes');
  require.cache[prismaPath] = { id: prismaPath, filename: prismaPath, loaded: true, exports: db };
  delete require.cache[recipesPath];

  const express = require('express');
  const app = express();
  app.use(express.json({ limit: '3mb' }));
  app.use('/recipes', require(recipesPath));
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

test('PUT /recipes/:id/image: solo el autor puede poner o quitar la foto', async () => {
  const db = fakePrisma();
  const api = await startApi(db);
  try {
    // Luis no es el autor de la receta 10: 403 y la foto no cambia.
    const forbidden = await api.luis('PUT', '/recipes/10/image', { imageBase64: SMALL_JPEG });
    assert.equal(forbidden.status, 403);
    assert.equal(db.recipes[0].imageBase64, null);

    const ok = await api.ana('PUT', '/recipes/10/image', { imageBase64: SMALL_JPEG });
    assert.equal(ok.status, 200);
    assert.equal(ok.body.imageBase64, SMALL_JPEG);
    assert.equal(ok.body.authorName, 'Ana');

    // Luis tampoco puede quitarla.
    assert.equal((await api.luis('PUT', '/recipes/10/image', { imageBase64: null })).status, 403);
    assert.equal(db.recipes[0].imageBase64, SMALL_JPEG);

    const removed = await api.ana('PUT', '/recipes/10/image', { imageBase64: null });
    assert.equal(removed.status, 200);
    assert.equal(removed.body.imageBase64, null);

    assert.equal((await api.ana('PUT', '/recipes/999/image', { imageBase64: null })).status, 404);
    assert.equal((await api.ana('PUT', '/recipes/10/image', {})).status, 400);
    assert.equal((await api.ana('PUT', '/recipes/10/image', { imageBase64: 'no-es-una-foto' })).status, 400);
  } finally {
    await api.close();
  }
});

test('likes: dar like dos veces no duplica y likedByMe depende de quién pregunta', async () => {
  const db = fakePrisma();
  const api = await startApi(db);
  try {
    assert.deepEqual((await api.ana('POST', '/recipes/11/like')).body, { likesCount: 1, likedByMe: true });
    assert.deepEqual((await api.ana('POST', '/recipes/11/like')).body, { likesCount: 1, likedByMe: true });
    assert.deepEqual((await api.luis('POST', '/recipes/11/like')).body, { likesCount: 2, likedByMe: true });

    const seenByAna = await api.ana('GET', '/recipes/11');
    assert.equal(seenByAna.body.likesCount, 2);
    assert.equal(seenByAna.body.likedByMe, true);

    assert.deepEqual((await api.ana('DELETE', '/recipes/11/like')).body, { likesCount: 1, likedByMe: false });
    // Quitar un like que ya no existe no es un error.
    assert.deepEqual((await api.ana('DELETE', '/recipes/11/like')).body, { likesCount: 1, likedByMe: false });
    // Luis sigue viendo el suyo.
    assert.equal((await api.luis('GET', '/recipes/11')).body.likedByMe, true);

    assert.equal((await api.ana('POST', '/recipes/999/like')).status, 404);
  } finally {
    await api.close();
  }
});

test('GET /recipes/feed: recetas de todos, de la más nueva a la más antigua, por páginas', async () => {
  const db = fakePrisma();
  const api = await startApi(db);
  try {
    await api.luis('POST', '/recipes/10/like');
    const first = await api.ana('GET', '/recipes/feed?limit=2');
    assert.equal(first.status, 200);
    assert.deepEqual(first.body.recipes.map((r) => r.id), [12, 11]);
    assert.equal(first.body.nextCursor, 11);
    assert.deepEqual(
      { authorName: first.body.recipes[0].authorName, kcalPerServing: first.body.recipes[0].kcalPerServing },
      { authorName: 'Luis', kcalPerServing: 150 },
    );

    const second = await api.ana('GET', `/recipes/feed?limit=2&cursor=${first.body.nextCursor}`);
    assert.deepEqual(second.body.recipes.map((r) => r.id), [10]);
    assert.equal(second.body.nextCursor, null);
    // La receta propia también sale en el feed, con el like de Luis contado.
    assert.deepEqual(
      { likesCount: second.body.recipes[0].likesCount, likedByMe: second.body.recipes[0].likedByMe },
      { likesCount: 1, likedByMe: false },
    );

    assert.equal((await api.ana('GET', '/recipes/feed?cursor=abc')).status, 400);
    assert.equal((await api.ana('GET', '/recipes/feed?limit=0')).status, 400);
  } finally {
    await api.close();
  }
});
