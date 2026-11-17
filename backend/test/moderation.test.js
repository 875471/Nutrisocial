// Pruebas de la moderación mínima (src/social/moderation.js): ocultar y volver a mostrar recetas y
// comentarios, quién puede hacerlo y que lo oculto no salga en el feed, el detalle ni los
// comentarios. Las rutas se prueban de verdad (Express + JWT) con un cliente de Prisma en memoria.
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const jwt = require('jsonwebtoken');

const {
  parseHiddenBody, canModerateRecipe, canModerateComment, canSeeRecipe,
} = require('../src/social/moderation');

test('reglas de moderación: autor, autor de la receta y administrador', () => {
  const recipe = { authorId: 1, hidden: true };
  assert.equal(canModerateRecipe(recipe, { id: 1 }), true);
  assert.equal(canModerateRecipe(recipe, { id: 2 }), false);
  assert.equal(canModerateRecipe(recipe, { id: 3, isAdmin: true }), true);
  assert.equal(canSeeRecipe(recipe, { id: 2 }), false);
  assert.equal(canSeeRecipe({ authorId: 1, hidden: false }, { id: 2 }), true);

  const comment = { userId: 2, recipe: { authorId: 1 } };
  assert.equal(canModerateComment(comment, { id: 2 }), true, 'autor del comentario');
  assert.equal(canModerateComment(comment, { id: 1 }), true, 'autor de la receta');
  assert.equal(canModerateComment(comment, { id: 4 }), false);
  assert.equal(canModerateComment(comment, { id: 4, isAdmin: true }), true);

  assert.deepEqual(parseHiddenBody({ hidden: true }), { value: true });
  assert.ok(parseHiddenBody({ hidden: 'true' }).error);
  assert.ok(parseHiddenBody(undefined).error);
});

// Usuarios: 1 Ana (autora de la receta 10), 2 Luis, 3 Admin. Luis ha comentado la receta 10.
function fakePrisma() {
  const users = [{ id: 1, name: 'Ana', isAdmin: false }, { id: 2, name: 'Luis', isAdmin: false }, { id: 3, name: 'Admin', isAdmin: true }];
  const recipes = [10, 11].map((id) => ({
    id, title: `Receta ${id}`, description: null, steps: '["Paso"]', servings: 2, prepMinutes: 10,
    kcal: 400, protein: 20, carbs: 40, fat: 10, totalWeightGrams: null, imageBase64: null,
    aiGenerated: false, dietTags: [], hidden: false, authorId: 1, createdAt: new Date(Date.UTC(2026, 10, id)),
  }));
  const comments = [
    { id: 1, text: 'Qué buena', userId: 2, recipeId: 10, hidden: false, createdAt: new Date('2026-11-15T10:00:00Z') },
    { id: 2, text: 'Spam', userId: 2, recipeId: 10, hidden: false, createdAt: new Date('2026-11-15T11:00:00Z') },
  ];
  const userName = (id) => users.find((u) => u.id === id).name;
  const visibleComments = (recipeId) => comments.filter((c) => c.recipeId === recipeId && !c.hidden)
    .sort((a, b) => b.createdAt - a.createdAt);
  // La receta con todo lo que piden recipeInclude/feedSelect, contando solo los comentarios visibles.
  const view = (r) => ({
    ...r,
    ingredients: [],
    author: { name: userName(r.authorId), followers: [] },
    _count: { likes: 0, comments: visibleComments(r.id).length },
    likes: [],
    saves: [],
    comments: visibleComments(r.id).slice(0, 2).map((c) => ({ ...c, user: { name: userName(c.userId) } })),
  });
  const matches = (row, where = {}) => Object.entries(where).every(([key, cond]) => {
    if (cond && typeof cond === 'object' && 'in' in cond) return cond.in.includes(row[key]);
    return row[key] === cond;
  });

  return {
    recipes,
    comments,
    user: { findUnique: async ({ where }) => users.find((u) => u.id === where.id) ?? null },
    recipe: {
      findUnique: async ({ where }) => {
        const r = recipes.find((x) => x.id === where.id);
        return r ? view(r) : null;
      },
      findMany: async ({ where, take } = {}) => {
        const rows = recipes.filter((r) => matches(r, where)).sort((a, b) => b.createdAt - a.createdAt).map(view);
        return take == null ? rows : rows.slice(0, take);
      },
      update: async ({ where, data }) => Object.assign(recipes.find((r) => r.id === where.id), data),
    },
    comment: {
      findUnique: async ({ where }) => {
        const c = comments.find((x) => x.id === where.id);
        return c ? { ...c, recipe: { authorId: recipes.find((r) => r.id === c.recipeId).authorId } } : null;
      },
      findMany: async ({ where }) => comments.filter((c) => matches(c, where))
        .sort((a, b) => b.createdAt - a.createdAt)
        .map((c) => ({ ...c, user: { name: userName(c.userId) } })),
      count: async ({ where }) => comments.filter((c) => matches(c, where)).length,
      update: async ({ where, data }) => Object.assign(comments.find((c) => c.id === where.id), data),
    },
  };
}

async function startApi(db) {
  process.env.JWT_SECRET = 'secreto-de-prueba';
  const prismaPath = path.resolve(__dirname, '../src/prismaClient.js');
  require.cache[prismaPath] = { id: prismaPath, filename: prismaPath, loaded: true, exports: db };
  for (const route of ['../src/routes/recipes', '../src/routes/comments']) delete require.cache[require.resolve(route)];

  const express = require('express');
  const app = express();
  app.use(express.json());
  app.use('/recipes', require('../src/routes/recipes'));
  app.use('/comments', require('../src/routes/comments'));
  const server = await new Promise((resolve) => { const s = app.listen(0, () => resolve(s)); });
  const base = `http://127.0.0.1:${server.address().port}`;
  const as = (userId) => async (method, url, body) => {
    const res = await fetch(base + url, {
      method,
      headers: { 'content-type': 'application/json', authorization: `Bearer ${jwt.sign({ userId }, process.env.JWT_SECRET)}` },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const text = await res.text();
    return { status: res.status, body: text ? JSON.parse(text) : null };
  };
  return { ana: as(1), luis: as(2), admin: as(3), close: () => new Promise((resolve) => server.close(resolve)) };
}

const feedIds = (res) => res.body.recipes.map((r) => r.id);

test('PUT /recipes/:id/hidden: la autora la oculta, desaparece para los demás y vuelve al mostrarla', async () => {
  const db = fakePrisma();
  const api = await startApi(db);
  try {
    assert.equal((await api.luis('PUT', '/recipes/10/hidden', { hidden: true })).status, 403);
    assert.equal((await api.ana('PUT', '/recipes/10/hidden', {})).status, 400);

    const hide = await api.ana('PUT', '/recipes/10/hidden', { hidden: true });
    assert.equal(hide.status, 200);
    assert.deepEqual(hide.body, { id: 10, hidden: true });

    assert.deepEqual(feedIds(await api.luis('GET', '/recipes/feed')), [11]);
    assert.equal((await api.luis('GET', '/recipes/10')).status, 404);
    assert.equal((await api.luis('GET', '/recipes/10/comments')).status, 404);
    // Para quien no la ve, intentar mostrarla tampoco revela que existe.
    assert.equal((await api.luis('PUT', '/recipes/10/hidden', { hidden: false })).status, 404);

    // La autora y el administrador siguen viéndola, marcada como oculta.
    const own = await api.ana('GET', '/recipes/10');
    assert.equal(own.status, 200);
    assert.equal(own.body.hidden, true);
    assert.equal((await api.admin('GET', '/recipes/10')).status, 200);
    assert.deepEqual(feedIds(await api.ana('GET', '/recipes/feed')), [11], 'el feed no enseña lo oculto ni a su autora');

    assert.equal((await api.ana('PUT', '/recipes/10/hidden', { hidden: false })).status, 200);
    assert.deepEqual(feedIds(await api.luis('GET', '/recipes/feed')), [11, 10]);
  } finally {
    await api.close();
  }
});

test('PUT /recipes/:id/hidden: un administrador puede ocultar la receta de otra persona', async () => {
  const db = fakePrisma();
  const api = await startApi(db);
  try {
    assert.equal((await api.admin('PUT', '/recipes/11/hidden', { hidden: true })).status, 200);
    assert.equal(db.recipes.find((r) => r.id === 11).hidden, true);
    assert.equal((await api.admin('PUT', '/recipes/99/hidden', { hidden: true })).status, 404);
  } finally {
    await api.close();
  }
});

test('PUT /comments/:id/hidden: lo oculto no sale en la lista, el total ni la vista previa del feed', async () => {
  const db = fakePrisma();
  const api = await startApi(db);
  try {
    // Ni el autor del comentario ni la autora de la receta: un tercero no puede.
    db.comments.push({ id: 3, text: 'Mío', userId: 1, recipeId: 11, hidden: false, createdAt: new Date() });
    assert.equal((await api.luis('PUT', '/comments/3/hidden', { hidden: true })).status, 403);
    assert.equal((await api.luis('PUT', '/comments/99/hidden', { hidden: true })).status, 404);

    // La autora de la receta oculta el spam que Luis dejó en su receta.
    assert.equal((await api.ana('PUT', '/comments/2/hidden', { hidden: true })).status, 200);
    const list = await api.luis('GET', '/recipes/10/comments');
    assert.deepEqual(list.body.comments.map((c) => c.id), [1]);
    assert.equal(list.body.total, 1);
    const card = (await api.luis('GET', '/recipes/feed')).body.recipes.find((r) => r.id === 10);
    assert.equal(card.commentsCount, 1);
    assert.deepEqual(card.commentsPreview.map((c) => c.id), [1]);

    // Luis, autor del comentario, puede volver a mostrarlo.
    assert.equal((await api.luis('PUT', '/comments/2/hidden', { hidden: false })).status, 200);
    assert.equal((await api.luis('GET', '/recipes/10/comments')).body.total, 2);
  } finally {
    await api.close();
  }
});
