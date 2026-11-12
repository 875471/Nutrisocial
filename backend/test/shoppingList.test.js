// Pruebas de la lista de la compra (/shopping-list): alta manual, POST /from-recipe (solo lo que
// falta en la despensa, contando sustitutos), marcar/desmarcar, orden de la lista y vaciar los
// marcados. Las rutas se prueban de verdad (Express + JWT) con un cliente de Prisma en memoria,
// como en subscription.test.js.
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const jwt = require('jsonwebtoken');

const { formatIngredientQuantity } = require('../src/routes/shoppingList');

test('formatIngredientQuantity: número con coma decimal y unidad en plural si no es 1', () => {
  assert.equal(formatIngredientQuantity(null, null), null);
  assert.equal(formatIngredientQuantity(200, 'g'), '200 g');
  assert.equal(formatIngredientQuantity(0.5, 'taza'), '0,5 tazas');
  assert.equal(formatIngredientQuantity(1, 'cucharada'), '1 cucharada');
  assert.equal(formatIngredientQuantity(3, 'unidad'), '3 unidades');
  assert.equal(formatIngredientQuantity(2, null), '2');
});

// Usuarios 1 (Ana) y 2 (Luis). La receta 10 es de Luis; Ana tiene huevos, aceite y margarina
// (sustituto de la mantequilla) en la despensa.
function fakePrisma() {
  const users = [{ id: 1, name: 'Ana' }, { id: 2, name: 'Luis' }];
  const recipes = [{
    id: 10,
    title: 'Bizcocho',
    ingredients: [
      { position: 0, name: 'Huevos', quantity: 3, unit: 'unidad', foodId: 101 },
      { position: 1, name: 'Harina de trigo', quantity: 250, unit: 'g', foodId: 102 },
      { position: 2, name: 'Mantequilla', quantity: 100, unit: 'g', foodId: 103 },
      { position: 3, name: 'Azúcar', quantity: 0.5, unit: 'taza', foodId: null },
      { position: 4, name: 'Aceite de oliva', quantity: 1, unit: 'cucharada', foodId: 105 },
      { position: 5, name: 'Sal', quantity: null, unit: null, foodId: null },
    ],
  }];
  const pantryItems = [
    { userId: 1, name: 'huevos', foodId: 101 },
    { userId: 1, name: 'aceite de oliva', foodId: 105 },
    { userId: 1, name: 'margarina', foodId: 999 },
  ];
  const items = [];
  let nextId = 1;
  let clock = Date.parse('2026-11-12T10:00:00Z');

  const withSource = (item) => ({ ...item, sourceRecipe: recipes.find((r) => r.id === item.sourceRecipeId) ?? null });
  const matches = (item, where) => Object.entries(where).every(([key, value]) => item[key] === value);

  return {
    items,
    pantryItems,
    user: { findUnique: async ({ where }) => users.find((u) => u.id === where.id) ?? null },
    recipe: { findUnique: async ({ where }) => recipes.find((r) => r.id === where.id) ?? null },
    pantryItem: { findMany: async ({ where }) => pantryItems.filter((p) => p.userId === where.userId) },
    $transaction: (operations) => Promise.all(operations),
    shoppingListItem: {
      create: async ({ data }) => {
        const item = { id: nextId++, quantity: null, checked: false, sourceRecipeId: null, createdAt: new Date(clock += 1000), ...data };
        items.push(item);
        return withSource(item);
      },
      findMany: async ({ where, orderBy }) => {
        assert.deepEqual(orderBy, [{ checked: 'asc' }, { createdAt: 'asc' }, { id: 'asc' }]);
        return items.filter((i) => matches(i, where))
          .sort((a, b) => Number(a.checked) - Number(b.checked) || a.createdAt - b.createdAt || a.id - b.id)
          .map(withSource);
      },
      findUnique: async ({ where }) => {
        const item = items.find((i) => i.id === where.id);
        return item ? withSource(item) : null;
      },
      updateMany: async ({ where, data }) => {
        const found = items.filter((i) => matches(i, where));
        for (const item of found) Object.assign(item, data);
        return { count: found.length };
      },
      deleteMany: async ({ where }) => {
        const doomed = items.filter((i) => matches(i, where));
        for (const item of doomed) items.splice(items.indexOf(item), 1);
        return { count: doomed.length };
      },
    },
  };
}

async function startApi(db) {
  process.env.JWT_SECRET = 'secreto-de-prueba';
  const prismaPath = path.resolve(__dirname, '../src/prismaClient.js');
  require.cache[prismaPath] = { id: prismaPath, filename: prismaPath, loaded: true, exports: db };
  const routePath = require.resolve('../src/routes/shoppingList');
  delete require.cache[routePath];

  const express = require('express');
  const app = express();
  app.use(express.json());
  app.use('/shopping-list', require(routePath));
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
  return { ana: as(1), luis: as(2), close: () => new Promise((resolve) => server.close(resolve)) };
}

const labels = (list) => list.map((i) => i.label);

test('POST /shopping-list: alta manual con cantidad opcional y validación', async () => {
  const db = fakePrisma();
  const api = await startApi(db);
  try {
    const milk = await api.ana('POST', '/shopping-list', { label: '  Leche  ', quantity: '2 l' });
    assert.equal(milk.status, 201);
    assert.equal(milk.body.label, 'Leche');
    assert.equal(milk.body.quantity, '2 l');
    assert.equal(milk.body.checked, false);
    assert.equal(milk.body.sourceRecipeId, null);

    const bread = await api.ana('POST', '/shopping-list', { label: 'Pan' });
    assert.equal(bread.status, 201);
    assert.equal(bread.body.quantity, null);

    for (const body of [{}, { label: '   ' }, { label: 'x'.repeat(101) }, { label: 'Pan', quantity: 2 }]) {
      assert.equal((await api.ana('POST', '/shopping-list', body)).status, 400, JSON.stringify(body));
    }

    // Cada uno ve solo su lista.
    assert.deepEqual(labels((await api.ana('GET', '/shopping-list')).body), ['Leche', 'Pan']);
    assert.deepEqual((await api.luis('GET', '/shopping-list')).body, []);
  } finally {
    await api.close();
  }
});

test('POST /from-recipe: añade solo lo que falta en la despensa (los sustitutos cuentan como cubiertos)', async () => {
  const db = fakePrisma();
  const api = await startApi(db);
  try {
    const res = await api.ana('POST', '/shopping-list/from-recipe/10');
    assert.equal(res.status, 201);
    // Huevos y aceite están en la despensa; la margarina sustituye a la mantequilla.
    assert.deepEqual(labels(res.body.added), ['Harina de trigo', 'Azúcar', 'Sal']);
    assert.equal(res.body.alreadyInPantry, 3);
    assert.deepEqual(res.body.added.map((i) => i.quantity), ['250 g', '0,5 tazas', null]);
    assert.ok(res.body.added.every((i) => i.sourceRecipeId === 10 && i.sourceRecipeTitle === 'Bizcocho'));

    // Sin fusión: añadirla otra vez duplica las filas (simplificación deliberada).
    await api.ana('POST', '/shopping-list/from-recipe/10');
    assert.equal(db.items.length, 6);

    // Con la despensa vacía, Luis necesita todo.
    const all = await api.luis('POST', '/shopping-list/from-recipe/10');
    assert.equal(all.body.added.length, 6);
    assert.equal(all.body.alreadyInPantry, 0);

    // Si la despensa lo cubre todo, no se añade nada.
    db.pantryItems.push(
      { userId: 1, name: 'harina de trigo', foodId: 102 },
      { userId: 1, name: 'azúcar', foodId: null },
      { userId: 1, name: 'sal', foodId: null },
    );
    const nothing = await api.ana('POST', '/shopping-list/from-recipe/10');
    assert.equal(nothing.status, 200);
    assert.deepEqual(nothing.body, { added: [], alreadyInPantry: 6 });

    assert.equal((await api.ana('POST', '/shopping-list/from-recipe/99')).status, 404);
    assert.equal((await api.ana('POST', '/shopping-list/from-recipe/abc')).status, 400);
  } finally {
    await api.close();
  }
});

test('PATCH /shopping-list/:id: marcar, desmarcar y editar; los marcados van al final', async () => {
  const db = fakePrisma();
  const api = await startApi(db);
  try {
    const ids = [];
    for (const label of ['Leche', 'Pan', 'Tomates']) ids.push((await api.ana('POST', '/shopping-list', { label })).body.id);

    const checked = await api.ana('PATCH', `/shopping-list/${ids[0]}`, { checked: true });
    assert.equal(checked.status, 200);
    assert.equal(checked.body.checked, true);
    assert.deepEqual(labels((await api.ana('GET', '/shopping-list')).body), ['Pan', 'Tomates', 'Leche']);

    const unchecked = await api.ana('PATCH', `/shopping-list/${ids[0]}`, { checked: false });
    assert.equal(unchecked.body.checked, false);
    assert.deepEqual(labels((await api.ana('GET', '/shopping-list')).body), ['Leche', 'Pan', 'Tomates']);

    const edited = await api.ana('PATCH', `/shopping-list/${ids[1]}`, { label: 'Pan integral', quantity: '1 barra' });
    assert.equal(edited.body.label, 'Pan integral');
    assert.equal(edited.body.quantity, '1 barra');
    assert.equal((await api.ana('PATCH', `/shopping-list/${ids[1]}`, { quantity: null })).body.quantity, null);

    for (const body of [{}, { checked: 'sí' }, { label: '' }]) {
      assert.equal((await api.ana('PATCH', `/shopping-list/${ids[1]}`, body)).status, 400, JSON.stringify(body));
    }
    // Un elemento de otra persona es como si no existiera.
    assert.equal((await api.luis('PATCH', `/shopping-list/${ids[2]}`, { checked: true })).status, 404);
    assert.equal(db.items.find((i) => i.id === ids[2]).checked, false);
  } finally {
    await api.close();
  }
});

test('DELETE /shopping-list?checked=true vacía solo los marcados propios; DELETE /:id borra uno', async () => {
  const db = fakePrisma();
  const api = await startApi(db);
  try {
    const ids = [];
    for (const label of ['Leche', 'Pan', 'Tomates']) ids.push((await api.ana('POST', '/shopping-list', { label })).body.id);
    const luisItem = (await api.luis('POST', '/shopping-list', { label: 'Café' })).body.id;
    await api.ana('PATCH', `/shopping-list/${ids[0]}`, { checked: true });
    await api.ana('PATCH', `/shopping-list/${ids[2]}`, { checked: true });
    await api.luis('PATCH', `/shopping-list/${luisItem}`, { checked: true });

    // Sin ?checked=true no se borra nada.
    assert.equal((await api.ana('DELETE', '/shopping-list')).status, 400);
    assert.equal(db.items.length, 4);

    const cleared = await api.ana('DELETE', '/shopping-list?checked=true');
    assert.equal(cleared.status, 200);
    assert.deepEqual(cleared.body, { deleted: 2 });
    assert.deepEqual(labels((await api.ana('GET', '/shopping-list')).body), ['Pan']);
    // Lo marcado de Luis sigue ahí.
    assert.deepEqual(labels((await api.luis('GET', '/shopping-list')).body), ['Café']);

    assert.equal((await api.luis('DELETE', `/shopping-list/${ids[1]}`)).status, 404);
    assert.equal((await api.ana('DELETE', `/shopping-list/${ids[1]}`)).status, 204);
    assert.deepEqual((await api.ana('GET', '/shopping-list')).body, []);
    assert.equal((await api.ana('DELETE', `/shopping-list/${ids[1]}`)).status, 404);
  } finally {
    await api.close();
  }
});
