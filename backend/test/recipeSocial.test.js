// Pruebas de las fotos de receta, los "me gusta" y los comentarios. Las rutas se prueban de verdad (Express +
// JWT) con un cliente de Prisma en memoria que imita las consultas que usan.
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const jwt = require('jsonwebtoken');

const { validateImageBase64, likeSummary, likeFields, MAX_IMAGE_BYTES } = require('../src/social/recipeSocial');
const { validateCommentText, MAX_COMMENT_LENGTH } = require('../src/social/comments');

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

test('validateCommentText: recorta, rechaza vacíos y textos demasiado largos', () => {
  assert.deepEqual(validateCommentText('  ¡Qué buena pinta!  '), { value: '¡Qué buena pinta!' });
  assert.match(validateCommentText('   ').error, /vacío/);
  assert.match(validateCommentText(undefined).error, /texto/);
  assert.ok(validateCommentText('a'.repeat(MAX_COMMENT_LENGTH)).value);
  assert.match(validateCommentText('a'.repeat(MAX_COMMENT_LENGTH + 1)).error, /máximo es 500/);
});

// ---- Rutas ----

// Lo más reciente primero, con el id como desempate (como NEWEST_FIRST en las rutas).
const newestFirst = (a, b) => b.createdAt - a.createdAt || b.id - a.id;

// Base de datos en memoria: usuarios 1 (Ana), 2 (Luis), 3 (Marta) y 4 (Pablo); la receta 10
// es de Ana. Los likes y comentarios llevan una fecha creciente para que el orden sea estable.
function fakePrisma() {
  const users = { 1: 'Ana', 2: 'Luis', 3: 'Marta', 4: 'Pablo' };
  const recipes = [
    { id: 10, title: 'Crema de calabaza', authorId: 1, servings: 4, prepMinutes: 30, kcal: 560, protein: 12, carbs: 58, fat: 31, totalWeightGrams: null, steps: '["Cocer"]', imageBase64: null, createdAt: new Date('2026-09-01') },
    { id: 11, title: 'Tortilla', authorId: 2, servings: 2, prepMinutes: null, kcal: 800, protein: 30, carbs: 50, fat: 50, totalWeightGrams: null, steps: '["Batir"]', imageBase64: null, createdAt: new Date('2026-09-02') },
    { id: 12, title: 'Pisto', authorId: 2, servings: 3, prepMinutes: 40, kcal: 450, protein: 9, carbs: 40, fat: 27, totalWeightGrams: null, steps: '["Pochar"]', imageBase64: null, createdAt: new Date('2026-09-03') },
  ];
  const likes = [];
  const comments = [];
  const saves = [];
  const follows = [];
  let clock = Date.parse('2026-09-10');
  const now = () => new Date((clock += 1000));

  const withAuthor = (c) => ({ ...c, user: { name: users[c.userId] } });

  // Devuelve la receta con las relaciones que pide la consulta (include o select).
  function shape(recipe, query = {}) {
    const spec = query.include ?? query.select ?? {};
    const out = { ...recipe, author: { name: users[recipe.authorId] }, ingredients: [] };
    // isFollowedByMe: solo el seguimiento del usuario que pregunta al autor.
    const followersWhere = spec.author?.select?.followers?.where;
    if (followersWhere) {
      out.author.followers = follows
        .filter((f) => f.followingId === recipe.authorId && f.followerId === followersWhere.followerId)
        .map((f) => ({ id: f.id }));
    }
    const recipeLikes = likes.filter((l) => l.recipeId === recipe.id);
    const recipeComments = comments.filter((c) => c.recipeId === recipe.id).sort(newestFirst);
    if (spec._count) {
      out._count = { likes: recipeLikes.length };
      if (spec._count.select.comments) out._count.comments = recipeComments.length;
    }
    if (spec.likes?.where) {
      // likedByMe: solo el like del usuario que pregunta.
      const userId = spec.likes.where.userId;
      out.likes = recipeLikes.filter((l) => l.userId === userId).map((l) => ({ id: l.id }));
    } else if (spec.likes) {
      // likersPreview: los últimos likes con el nombre de quien los dio.
      out.likes = recipeLikes.sort(newestFirst).slice(0, spec.likes.take).map((l) => ({ user: { name: users[l.userId] } }));
    }
    if (spec.comments) out.comments = recipeComments.slice(0, spec.comments.take).map(withAuthor);
    // savedByMe: solo el guardado del usuario que pregunta.
    if (spec.saves) out.saves = saves.filter((s) => s.recipeId === recipe.id && s.userId === spec.saves.where.userId);
    return out;
  }

  return {
    likes,
    comments,
    saves,
    follows,
    recipes,
    // Añade un comentario directamente, sin pasar por la API.
    addComment: (userId, recipeId, text) => {
      const comment = { id: comments.length + 1, userId, recipeId, text, createdAt: now() };
      comments.push(comment);
      return comment;
    },
    recipe: {
      findUnique: async ({ where, ...query }) => {
        const recipe = recipes.find((r) => r.id === where.id);
        return recipe ? shape(recipe, query) : null;
      },
      findMany: async ({ where, take = Infinity, cursor, skip = 0, ...query }) => {
        const sorted = recipes
          .filter((r) => !where?.id?.in || where.id.in.includes(r.id))
          .filter((r) => !where?.authorId?.in || where.authorId.in.includes(r.authorId))
          .sort(newestFirst);
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
        if (!like) likes.push((like = { id: likes.length + 1, ...create, createdAt: now() }));
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
    savedRecipe: {
      upsert: async ({ where: { userId_recipeId: key }, create }) => {
        let save = saves.find((s) => s.userId === key.userId && s.recipeId === key.recipeId);
        if (!save) saves.push((save = { id: saves.length + 1, ...create, createdAt: now() }));
        return save;
      },
      deleteMany: async ({ where }) => {
        const before = saves.length;
        for (let i = saves.length - 1; i >= 0; i--) {
          if (saves[i].userId === where.userId && saves[i].recipeId === where.recipeId) saves.splice(i, 1);
        }
        return { count: before - saves.length };
      },
      findUnique: async ({ where: { userId_recipeId: key } }) =>
        saves.find((s) => s.userId === key.userId && s.recipeId === key.recipeId) ?? null,
      findMany: async ({ where, take, cursor, skip = 0, select }) => {
        const sorted = saves.filter((s) => s.userId === where.userId).sort(newestFirst);
        const start = cursor
          ? sorted.findIndex((s) => s.userId === cursor.userId_recipeId.userId && s.recipeId === cursor.userId_recipeId.recipeId)
          : 0;
        return sorted.slice(start + skip, start + skip + take)
          .map((s) => ({ recipe: shape(recipes.find((r) => r.id === s.recipeId), { select: select.recipe.select }) }));
      },
    },
    follow: {
      findMany: async ({ where }) => follows.filter((f) => f.followerId === where.followerId),
      upsert: async ({ where: { followerId_followingId: key }, create }) => {
        let follow = follows.find((f) => f.followerId === key.followerId && f.followingId === key.followingId);
        if (!follow) follows.push((follow = { id: follows.length + 1, ...create, createdAt: now() }));
        return follow;
      },
      deleteMany: async ({ where }) => {
        const before = follows.length;
        for (let i = follows.length - 1; i >= 0; i--) {
          if (follows[i].followerId === where.followerId && follows[i].followingId === where.followingId) follows.splice(i, 1);
        }
        return { count: before - follows.length };
      },
    },
    user: {
      findUnique: async ({ where }) => (users[where.id] ? { id: where.id } : null),
      // Sin `where`, todos (id y nombre); con `where.id.in`, con el recuento de recetas y el
      // seguimiento de quien pregunta, como en GET /users/search.
      findMany: async ({ where, select }) => Object.entries(users)
        .map(([id, name]) => ({ id: Number(id), name }))
        .filter((u) => !where?.id?.in || where.id.in.includes(u.id))
        .map((u) => (select._count
          ? {
            ...u,
            _count: { recipes: recipes.filter((r) => r.authorId === u.id).length },
            followers: follows
              .filter((f) => f.followingId === u.id && f.followerId === select.followers.where.followerId)
              .map((f) => ({ id: f.id })),
          }
          : u)),
    },
    comment: {
      findMany: async ({ where, take, cursor, skip = 0 }) => {
        const sorted = comments.filter((c) => c.recipeId === where.recipeId).sort(newestFirst);
        const start = cursor ? sorted.findIndex((c) => c.id === cursor.id) : 0;
        return sorted.slice(start + skip, start + skip + take).map(withAuthor);
      },
      count: async ({ where }) => comments.filter((c) => c.recipeId === where.recipeId).length,
      create: async ({ data }) => {
        const comment = { id: comments.length + 1, ...data, createdAt: now() };
        comments.push(comment);
        return withAuthor(comment);
      },
      findUnique: async ({ where }) => comments.find((c) => c.id === where.id) ?? null,
      delete: async ({ where }) => comments.splice(comments.findIndex((c) => c.id === where.id), 1)[0],
    },
  };
}

// Arranca la API de recetas sobre la base simulada y devuelve un cliente HTTP por usuario.
async function startApi(db) {
  process.env.JWT_SECRET = 'secreto-de-prueba';
  const prismaPath = path.resolve(__dirname, '../src/prismaClient.js');
  const recipesPath = require.resolve('../src/routes/recipes');
  const commentsPath = require.resolve('../src/routes/comments');
  const usersPath = require.resolve('../src/routes/users');
  require.cache[prismaPath] = { id: prismaPath, filename: prismaPath, loaded: true, exports: db };
  delete require.cache[recipesPath];
  delete require.cache[commentsPath];
  delete require.cache[usersPath];

  const express = require('express');
  const app = express();
  app.use(express.json({ limit: '3mb' }));
  app.use('/recipes', require(recipesPath));
  app.use('/comments', require(commentsPath));
  app.use('/users', require(usersPath));
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
  return { ana: as(1), luis: as(2), marta: as(3), pablo: as(4), close: () => new Promise((resolve) => server.close(resolve)) };
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

test('GET /recipes/:id/comments: del más reciente al más antiguo, por páginas de 20', async () => {
  const db = fakePrisma();
  for (let i = 1; i <= 25; i++) db.addComment(i % 2 ? 1 : 2, 10, `Comentario ${i}`);
  db.addComment(1, 11, 'En otra receta');
  const api = await startApi(db);
  try {
    const first = await api.luis('GET', '/recipes/10/comments');
    assert.equal(first.status, 200);
    assert.equal(first.body.comments.length, 20);
    assert.equal(first.body.total, 25);
    assert.equal(first.body.comments[0].text, 'Comentario 25');
    assert.deepEqual(
      { authorId: first.body.comments[0].authorId, authorName: first.body.comments[0].authorName },
      { authorId: 1, authorName: 'Ana' },
    );
    assert.equal(first.body.nextCursor, first.body.comments[19].id);

    const second = await api.luis('GET', `/recipes/10/comments?cursor=${first.body.nextCursor}`);
    assert.deepEqual(second.body.comments.map((c) => c.text), [5, 4, 3, 2, 1].map((n) => `Comentario ${n}`));
    assert.equal(second.body.nextCursor, null);

    const small = await api.luis('GET', '/recipes/10/comments?limit=2');
    assert.deepEqual(small.body.comments.map((c) => c.text), ['Comentario 25', 'Comentario 24']);

    assert.equal((await api.luis('GET', '/recipes/10/comments?cursor=abc')).status, 400);
    assert.equal((await api.luis('GET', '/recipes/999/comments')).status, 404);
  } finally {
    await api.close();
  }
});

test('POST /recipes/:id/comments: valida el texto y el autor es quien comenta', async () => {
  const db = fakePrisma();
  const api = await startApi(db);
  try {
    const created = await api.luis('POST', '/recipes/10/comments', { text: '  Me ha salido genial  ' });
    assert.equal(created.status, 201);
    assert.equal(created.body.text, 'Me ha salido genial');
    assert.deepEqual({ authorId: created.body.authorId, authorName: created.body.authorName }, { authorId: 2, authorName: 'Luis' });
    assert.equal(db.comments.length, 1);

    assert.equal((await api.luis('POST', '/recipes/10/comments', { text: '   ' })).status, 400);
    assert.equal((await api.luis('POST', '/recipes/10/comments', { text: 'a'.repeat(501) })).status, 400);
    assert.equal((await api.luis('POST', '/recipes/10/comments', {})).status, 400);
    assert.equal((await api.luis('POST', '/recipes/999/comments', { text: 'Hola' })).status, 404);
    assert.equal(db.comments.length, 1);
  } finally {
    await api.close();
  }
});

test('DELETE /comments/:id: solo el autor del comentario puede borrarlo', async () => {
  const db = fakePrisma();
  // Luis comenta en la receta de Ana: ni siquiera la autora de la receta puede borrarlo.
  const comment = db.addComment(2, 10, 'Le pondría más sal');
  const api = await startApi(db);
  try {
    const forbidden = await api.ana('DELETE', `/comments/${comment.id}`);
    assert.equal(forbidden.status, 403);
    assert.equal(db.comments.length, 1);

    const ok = await api.luis('DELETE', `/comments/${comment.id}`);
    assert.equal(ok.status, 200);
    assert.deepEqual(ok.body, { deleted: true });
    assert.equal(db.comments.length, 0);

    assert.equal((await api.luis('DELETE', `/comments/${comment.id}`)).status, 404);
    assert.equal((await api.luis('DELETE', '/comments/abc')).status, 400);
  } finally {
    await api.close();
  }
});

test('feed y detalle: commentsCount, commentsPreview y likersPreview', async () => {
  const db = fakePrisma();
  db.addComment(2, 10, 'Primero');
  db.addComment(3, 10, 'Segundo');
  db.addComment(4, 10, 'Tercero');
  const api = await startApi(db);
  try {
    // Cuatro likes: la vista previa se queda con los tres últimos, del más reciente al más antiguo.
    await api.ana('POST', '/recipes/10/like');
    await api.luis('POST', '/recipes/10/like');
    await api.marta('POST', '/recipes/10/like');
    await api.pablo('POST', '/recipes/10/like');
    await api.marta('POST', '/recipes/12/like');

    const feed = (await api.ana('GET', '/recipes/feed')).body.recipes;
    const crema = feed.find((r) => r.id === 10);
    assert.equal(crema.commentsCount, 3);
    assert.deepEqual(crema.commentsPreview.map((c) => [c.authorName, c.text]), [['Pablo', 'Tercero'], ['Marta', 'Segundo']]);
    assert.equal(crema.likesCount, 4);
    assert.deepEqual(crema.likersPreview, ['Pablo', 'Marta', 'Luis']);
    assert.equal(crema.likedByMe, true);
    assert.equal(crema.authorId, 1);
    assert.deepEqual(crema.steps, ['Cocer']);
    assert.equal(crema.proteinPerServing, 3);

    const pisto = feed.find((r) => r.id === 12);
    assert.deepEqual({ commentsCount: pisto.commentsCount, commentsPreview: pisto.commentsPreview }, { commentsCount: 0, commentsPreview: [] });
    assert.deepEqual(pisto.likersPreview, ['Marta']);
    assert.deepEqual(feed.find((r) => r.id === 11).likersPreview, []);

    // El detalle trae los contadores y los likers, pero no la vista previa de comentarios.
    const detail = (await api.luis('GET', '/recipes/10')).body;
    assert.equal(detail.commentsCount, 3);
    assert.deepEqual(detail.likersPreview, ['Pablo', 'Marta', 'Luis']);
    assert.equal(detail.commentsPreview, undefined);

    // Tras comentar, el siguiente feed ya lo refleja.
    await api.luis('POST', '/recipes/10/comments', { text: 'Cuarto' });
    const after = (await api.ana('GET', '/recipes/feed')).body.recipes.find((r) => r.id === 10);
    assert.equal(after.commentsCount, 4);
    assert.deepEqual(after.commentsPreview.map((c) => c.text), ['Cuarto', 'Tercero']);
  } finally {
    await api.close();
  }
});

test('GET /recipes/search: busca en todas las recetas, ordena por tiempo y trae los datos de la tarjeta', async () => {
  const db = fakePrisma();
  const api = await startApi(db);
  try {
    await api.marta('POST', '/recipes/11/like');
    // Recetas de cualquier autor, por título y sin tildes ni mayúsculas.
    const found = await api.ana('GET', '/recipes/search?q=TORTÍLLA');
    assert.equal(found.status, 200);
    assert.deepEqual(found.body.recipes.map((r) => r.id), [11]);
    assert.equal(found.body.total, 1);
    const tortilla = found.body.recipes[0];
    assert.equal(tortilla.authorName, 'Luis');
    assert.equal(tortilla.likesCount, 1);
    assert.deepEqual(tortilla.likersPreview, ['Marta']);
    assert.equal(tortilla.kcalPerServing, 400);

    // Sin texto, todas; por tiempo ascendente, la receta sin tiempo al final.
    const byTime = await api.ana('GET', '/recipes/search?sortBy=prepMinutes&order=asc');
    assert.deepEqual(byTime.body.recipes.map((r) => r.id), [10, 12, 11]);
    const pageOne = await api.ana('GET', '/recipes/search?limit=2');
    assert.deepEqual(pageOne.body.recipes.map((r) => r.id), [12, 11]);
    const pageTwo = await api.ana('GET', `/recipes/search?limit=2&cursor=${pageOne.body.nextCursor}`);
    assert.deepEqual(pageTwo.body.recipes.map((r) => r.id), [10]);
    assert.equal(pageTwo.body.nextCursor, null);

    assert.equal((await api.ana('GET', '/recipes/search?sortBy=likes')).status, 400);
  } finally {
    await api.close();
  }
});

test('recetas guardadas: guardar, savedByMe en feed/detalle/búsqueda, lista paginada y quitar', async () => {
  const db = fakePrisma();
  const api = await startApi(db);
  try {
    // Ana guarda dos recetas de Luis (11 y 12) y una suya (10), en ese orden.
    assert.deepEqual((await api.ana('POST', '/recipes/11/save')).body, { savedByMe: true });
    // Guardar dos veces no duplica.
    await api.ana('POST', '/recipes/11/save');
    await api.ana('POST', '/recipes/12/save');
    await api.ana('POST', '/recipes/10/save');
    assert.equal(db.saves.length, 3);
    assert.equal((await api.ana('POST', '/recipes/99/save')).status, 404);

    // savedByMe es de quien pregunta: para Luis, ninguna está guardada.
    const feedAna = (await api.ana('GET', '/recipes/feed')).body.recipes;
    assert.ok(feedAna.every((r) => r.savedByMe));
    const feedLuis = (await api.luis('GET', '/recipes/feed')).body.recipes;
    assert.ok(feedLuis.every((r) => !r.savedByMe));
    assert.equal((await api.ana('GET', '/recipes/11')).body.savedByMe, true);
    assert.equal((await api.luis('GET', '/recipes/11')).body.savedByMe, false);
    assert.equal((await api.ana('GET', '/recipes/search?q=pisto')).body.recipes[0].savedByMe, true);

    // Lista de guardadas: de la guardada más recientemente a la más antigua, de 2 en 2.
    const first = (await api.ana('GET', '/recipes/saved?limit=2')).body;
    assert.deepEqual(first.recipes.map((r) => r.id), [10, 12]);
    assert.equal(first.recipes[0].title, 'Crema de calabaza');
    assert.equal(first.nextCursor, 12);
    const second = (await api.ana('GET', `/recipes/saved?limit=2&cursor=${first.nextCursor}`)).body;
    assert.deepEqual(second.recipes.map((r) => r.id), [11]);
    assert.equal(second.nextCursor, null);
    assert.deepEqual((await api.luis('GET', '/recipes/saved')).body.recipes, []);

    // Quitar el guardado (dos veces no es un error) y un cursor que ya no está guardado.
    assert.deepEqual((await api.ana('DELETE', '/recipes/12/save')).body, { savedByMe: false });
    assert.equal((await api.ana('DELETE', '/recipes/12/save')).status, 200);
    assert.deepEqual((await api.ana('GET', '/recipes/saved')).body.recipes.map((r) => r.id), [10, 11]);
    assert.equal((await api.ana('GET', '/recipes/saved?cursor=12')).status, 400);
  } finally {
    await api.close();
  }
});

// ---- Seguir usuarios ----

test('seguir: es idempotente, no se puede seguir a uno mismo y se puede dejar de seguir', async () => {
  const db = fakePrisma();
  const api = await startApi(db);
  try {
    assert.deepEqual((await api.ana('POST', '/users/2/follow')).body, { isFollowedByMe: true });
    // Seguir dos veces no duplica.
    assert.deepEqual((await api.ana('POST', '/users/2/follow')).body, { isFollowedByMe: true });
    assert.deepEqual(db.follows.map((f) => [f.followerId, f.followingId]), [[1, 2]]);

    const self = await api.ana('POST', '/users/1/follow');
    assert.equal(self.status, 400);
    assert.match(self.body.error, /ti mismo/);
    assert.equal((await api.ana('POST', '/users/999/follow')).status, 404);
    assert.equal((await api.ana('POST', '/users/abc/follow')).status, 400);
    assert.equal(db.follows.length, 1);

    assert.deepEqual((await api.ana('DELETE', '/users/2/follow')).body, { isFollowedByMe: false });
    // Dejar de seguir a quien ya no se sigue no es un error.
    assert.deepEqual((await api.ana('DELETE', '/users/2/follow')).body, { isFollowedByMe: false });
    assert.equal(db.follows.length, 0);
  } finally {
    await api.close();
  }
});

test('GET /recipes/feed/friends: solo recetas de gente seguida, y vacío si no sigue a nadie', async () => {
  const db = fakePrisma();
  // Marta publica la receta más reciente de todas: no debe salirle a Ana, que no la sigue.
  db.recipes.push({ id: 13, title: 'Gazpacho', authorId: 3, servings: 4, prepMinutes: 15, kcal: 400, protein: 8, carbs: 40, fat: 20, totalWeightGrams: null, steps: '["Triturar"]', imageBase64: null, createdAt: new Date('2026-09-04') });
  const api = await startApi(db);
  try {
    const none = await api.ana('GET', '/recipes/feed/friends');
    assert.equal(none.status, 200);
    assert.deepEqual(none.body, { recipes: [], nextCursor: null });

    await api.ana('POST', '/users/2/follow');
    const first = await api.ana('GET', '/recipes/feed/friends?limit=1');
    assert.deepEqual(first.body.recipes.map((r) => r.id), [12]);
    assert.equal(first.body.nextCursor, 12);
    assert.equal(first.body.recipes[0].isFollowedByMe, true);
    const second = await api.ana('GET', `/recipes/feed/friends?limit=1&cursor=${first.body.nextCursor}`);
    assert.deepEqual(second.body.recipes.map((r) => r.id), [11]);
    assert.equal(second.body.nextCursor, null);

    // Luis no sigue a nadie: su feed de amigos está vacío aunque Ana le siga a él.
    assert.deepEqual((await api.luis('GET', '/recipes/feed/friends')).body.recipes, []);
    assert.equal((await api.ana('GET', '/recipes/feed/friends?cursor=abc')).status, 400);
    assert.equal((await api.pablo('GET', '/recipes/feed/friends?limit=0')).status, 400);
  } finally {
    await api.close();
  }
});

test('isFollowedByMe en el feed y en el detalle depende de quién pregunta', async () => {
  const db = fakePrisma();
  const api = await startApi(db);
  try {
    await api.ana('POST', '/users/2/follow');
    const feed = (await api.ana('GET', '/recipes/feed')).body.recipes;
    assert.deepEqual(feed.map((r) => [r.id, r.isFollowedByMe]), [[12, true], [11, true], [10, false]]);
    // Marta no sigue a Luis.
    assert.equal((await api.marta('GET', '/recipes/feed')).body.recipes[0].isFollowedByMe, false);
    assert.equal((await api.ana('GET', '/recipes/11')).body.isFollowedByMe, true);
    assert.equal((await api.marta('GET', '/recipes/11')).body.isFollowedByMe, false);
  } finally {
    await api.close();
  }
});

test('GET /users/search: excluye a quien busca, sin tildes, con isFollowedByMe y recetas', async () => {
  const db = fakePrisma();
  const api = await startApi(db);
  try {
    await api.ana('POST', '/users/2/follow');
    const all = await api.ana('GET', '/users/search');
    assert.equal(all.status, 200);
    // Sin texto, todos menos Ana, por orden alfabético.
    assert.deepEqual(all.body.users, [
      { id: 2, name: 'Luis', isFollowedByMe: true, recipesCount: 2 },
      { id: 3, name: 'Marta', isFollowedByMe: false, recipesCount: 0 },
      { id: 4, name: 'Pablo', isFollowedByMe: false, recipesCount: 0 },
    ]);
    assert.deepEqual((await api.ana('GET', '/users/search?q=M%C3%81RTA')).body.users.map((u) => u.id), [3]);
    // Ana no se encuentra a sí misma, pero Luis sí la encuentra (y no la sigue).
    assert.deepEqual((await api.ana('GET', '/users/search?q=ana')).body.users, []);
    assert.deepEqual((await api.luis('GET', '/users/search?q=ana')).body.users, [
      { id: 1, name: 'Ana', isFollowedByMe: false, recipesCount: 1 },
    ]);
    assert.equal((await api.ana('GET', '/users/search?limit=2')).body.users.length, 2);
    assert.equal((await api.ana('GET', '/users/search?limit=0')).status, 400);
  } finally {
    await api.close();
  }
});
