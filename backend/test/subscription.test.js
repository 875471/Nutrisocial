// Pruebas de la suscripción Premium simulada: el cálculo del fin de periodo, las rutas de
// /subscription y el bloqueo de las dos funciones Premium (OCR y recomendaciones). Las rutas
// se prueban de verdad (Express + JWT) con un cliente de Prisma en memoria, como en
// recipeSocial.test.js.
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const jwt = require('jsonwebtoken');

const { PLANS, addMonths, periodEnd } = require('../src/billing/plans');

// ---- Funciones puras ----

test('addMonths: suma meses de calendario y se queda en el último día si el día no existe', () => {
  const day = (text) => new Date(`${text}T10:00:00.000Z`);
  assert.deepEqual(addMonths(day('2026-11-04'), 1), day('2026-12-04'));
  assert.deepEqual(addMonths(day('2026-12-15'), 1), day('2027-01-15'));
  assert.deepEqual(addMonths(day('2027-01-31'), 1), day('2027-02-28'));
  assert.deepEqual(addMonths(day('2028-01-31'), 1), day('2028-02-29'));
  assert.deepEqual(addMonths(day('2028-02-29'), 12), day('2029-02-28'));
  assert.deepEqual(addMonths(day('2026-11-04'), 12), day('2027-11-04'));
});

test('periodEnd: un mes para el plan mensual y doce para el anual', () => {
  const start = new Date('2026-03-31T08:30:00.000Z');
  assert.deepEqual(periodEnd('monthly', start), new Date('2026-04-30T08:30:00.000Z'));
  assert.deepEqual(periodEnd('annual', start), new Date('2027-03-31T08:30:00.000Z'));
});

// ---- Rutas ----

// Base de datos en memoria con los usuarios 1 (Ana) y 2 (Luis), ninguno suscrito al empezar.
// Solo imita las consultas que hacen requireAuth, requirePremium y las rutas probadas.
function fakePrisma() {
  const users = [
    { id: 1, name: 'Ana', email: 'ana@example.com' },
    { id: 2, name: 'Luis', email: 'luis@example.com' },
  ];
  const subscriptions = [];
  let nextId = 1;
  return {
    subscriptions,
    user: {
      findUnique: async ({ where }) => users.find((u) => u.id === where.id) ?? null,
    },
    subscription: {
      findUnique: async ({ where }) => subscriptions.find((s) => s.userId === where.userId) ?? null,
      upsert: async ({ where, create, update }) => {
        const existing = subscriptions.find((s) => s.userId === where.userId);
        if (existing) return Object.assign(existing, update);
        const row = { id: nextId++, canceledAt: null, ...create };
        subscriptions.push(row);
        return row;
      },
      update: async ({ where, data }) => Object.assign(subscriptions.find((s) => s.userId === where.userId), data),
    },
    // GET /log/recommendations: el día no tiene entradas.
    logEntry: { findMany: async () => [] },
  };
}

async function startApi(db) {
  process.env.JWT_SECRET = 'secreto-de-prueba';
  const prismaPath = path.resolve(__dirname, '../src/prismaClient.js');
  const routePaths = {
    '/subscription': require.resolve('../src/routes/subscription'),
    '/recipes': require.resolve('../src/routes/recipes'),
    '/log': require.resolve('../src/routes/log'),
  };
  require.cache[prismaPath] = { id: prismaPath, filename: prismaPath, loaded: true, exports: db };
  for (const routePath of Object.values(routePaths)) delete require.cache[routePath];

  const express = require('express');
  const app = express();
  app.use(express.json());
  for (const [prefix, routePath] of Object.entries(routePaths)) app.use(prefix, require(routePath));
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

// Comprueba que el fin de periodo está a `months` meses del inicio que devolvió la API.
function assertPeriod(body, months) {
  assert.equal(body.currentPeriodEnd, addMonths(new Date(body.startedAt), months).toISOString());
}

test('GET /subscription/plans devuelve los planes con sus precios', async () => {
  const api = await startApi(fakePrisma());
  try {
    const res = await api.ana('GET', '/subscription/plans');
    assert.equal(res.status, 200);
    assert.deepEqual(res.body, PLANS);
  } finally {
    await api.close();
  }
});

test('GET /subscription sin suscripción: isPremium false y nada más', async () => {
  const api = await startApi(fakePrisma());
  try {
    const res = await api.ana('GET', '/subscription');
    assert.equal(res.status, 200);
    assert.deepEqual(res.body, { isPremium: false });
  } finally {
    await api.close();
  }
});

test('POST /subscription/subscribe: cada plan calcula su fin de periodo y el plan se valida', async () => {
  const db = fakePrisma();
  const api = await startApi(db);
  try {
    const monthly = await api.ana('POST', '/subscription/subscribe', { plan: 'monthly' });
    assert.equal(monthly.status, 200);
    assert.equal(monthly.body.isPremium, true);
    assert.equal(monthly.body.plan, 'monthly');
    assert.equal(monthly.body.status, 'active');
    assertPeriod(monthly.body, 1);

    const annual = await api.luis('POST', '/subscription/subscribe', { plan: 'annual' });
    assert.equal(annual.status, 200);
    assertPeriod(annual.body, 12);

    for (const body of [{ plan: 'weekly' }, { plan: 'toString' }, {}, undefined]) {
      const res = await api.ana('POST', '/subscription/subscribe', body);
      assert.equal(res.status, 400, JSON.stringify(body));
    }
    assert.equal(db.subscriptions.length, 2);
  } finally {
    await api.close();
  }
});

test('suscribirse dos veces actualiza la fila en vez de duplicarla', async () => {
  const db = fakePrisma();
  const api = await startApi(db);
  try {
    await api.ana('POST', '/subscription/subscribe', { plan: 'monthly' });
    const again = await api.ana('POST', '/subscription/subscribe', { plan: 'annual' });
    assert.equal(again.status, 200);
    assert.equal(db.subscriptions.length, 1);
    assert.equal(db.subscriptions[0].plan, 'annual');
    assertPeriod(again.body, 12);

    const status = await api.ana('GET', '/subscription');
    assert.equal(status.body.plan, 'annual');
  } finally {
    await api.close();
  }
});

test('POST /subscription/cancel: 404 sin suscripción activa; con ella quita Premium al momento', async () => {
  const db = fakePrisma();
  const api = await startApi(db);
  try {
    assert.equal((await api.ana('POST', '/subscription/cancel')).status, 404);

    await api.ana('POST', '/subscription/subscribe', { plan: 'annual' });
    const canceled = await api.ana('POST', '/subscription/cancel');
    assert.equal(canceled.status, 200);
    assert.equal(canceled.body.isPremium, false);
    assert.equal(canceled.body.status, 'canceled');
    assert.ok(db.subscriptions[0].canceledAt instanceof Date);
    // El periodo pagado no ha terminado, pero ya no hay acceso.
    assert.ok(new Date(canceled.body.currentPeriodEnd) > new Date());
    assert.equal((await api.ana('GET', '/subscription')).body.isPremium, false);
    assert.equal((await api.ana('GET', '/log/recommendations?date=2026-11-04')).status, 403);

    // Ya cancelada: no hay nada que cancelar. Volver a suscribirse la reactiva.
    assert.equal((await api.ana('POST', '/subscription/cancel')).status, 404);
    const renewed = await api.ana('POST', '/subscription/subscribe', { plan: 'monthly' });
    assert.equal(renewed.body.isPremium, true);
    assert.equal(db.subscriptions[0].canceledAt, null);
  } finally {
    await api.close();
  }
});

test('OCR y recomendaciones: 403 sin Premium y funcionan con él', async () => {
  const api = await startApi(fakePrisma());
  try {
    const ocr = () => api.ana('POST', '/recipes/parse-ocr', { rawText: 'Tortilla de patatas' });
    const recommendations = () => api.ana('GET', '/log/recommendations?date=2026-11-04');

    for (const res of [await ocr(), await recommendations()]) {
      assert.equal(res.status, 403);
      assert.equal(res.body.error, 'Esta función es solo para usuarias Premium.');
    }

    await api.ana('POST', '/subscription/subscribe', { plan: 'monthly' });
    const ocrRes = await ocr();
    assert.equal(ocrRes.status, 200);
    assert.equal(ocrRes.body.title, 'Tortilla de patatas');
    const recRes = await recommendations();
    assert.equal(recRes.status, 200);
    // Ana no tiene perfil: el recomendador responde, pero sin recetas.
    assert.equal(recRes.body.reason, 'perfil_incompleto');

    // Premium de Ana no desbloquea nada a Luis.
    assert.equal((await api.luis('POST', '/recipes/parse-ocr', { rawText: 'Tortilla' })).status, 403);
  } finally {
    await api.close();
  }
});
