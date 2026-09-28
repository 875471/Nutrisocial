// Pruebas del calendario nutricional mensual (GET /log/calendar): estado de cada día frente
// al objetivo y la ruta completa sobre un cliente de Prisma en memoria.
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const jwt = require('jsonwebtoken');

const { dayStatus, calendarDays } = require('../src/nutrition/dailyLog');
const { parseMonth, formatDay } = require('../src/utils/day');

test('dayStatus: ±10 % del objetivo es adecuado, por encima excesivo y por debajo insuficiente', () => {
  assert.equal(dayStatus(2000, 2000), 'adecuado');
  // Los dos extremos del margen todavía son adecuados.
  assert.equal(dayStatus(2200, 2000), 'adecuado');
  assert.equal(dayStatus(1800, 2000), 'adecuado');
  assert.equal(dayStatus(2201, 2000), 'excesivo');
  assert.equal(dayStatus(1799, 2000), 'insuficiente');
  // Sin objetivo (perfil incompleto) no hay estado.
  assert.equal(dayStatus(1500, null), null);
});

test('parseMonth: acepta AAAA-MM y devuelve el rango del mes', () => {
  assert.deepEqual(parseMonth('2026-12'), {
    start: new Date('2026-12-01T00:00:00Z'),
    end: new Date('2027-01-01T00:00:00Z'),
  });
  for (const bad of ['2026-13', '2026-00', '2026-9', '2026-09-01', '', undefined]) {
    assert.equal(parseMonth(bad), null, String(bad));
  }
});

test('calendarDays: suma las entradas de cada día y omite los días sin entradas', () => {
  const entries = [
    { date: new Date('2026-09-03T00:00:00Z'), kcal: 900.4 },
    { date: new Date('2026-09-01T00:00:00Z'), kcal: 2000 },
    { date: new Date('2026-09-03T00:00:00Z'), kcal: 1500 },
  ];
  assert.deepEqual(calendarDays(entries, 2000, formatDay), [
    { date: '2026-09-01', kcal: 2000, status: 'adecuado' },
    { date: '2026-09-03', kcal: 2400, status: 'excesivo' },
  ]);
  assert.deepEqual(calendarDays([], 2000, formatDay), []);
});

// ---- Ruta ----

// Usuario 1 con el perfil completo (objetivo ≈ 2000 kcal) y usuario 2 sin perfil.
function fakePrisma() {
  const users = {
    1: { id: 1, birthDate: new Date('1996-01-01'), heightCm: 175, weightKg: 70, sex: 'M', activityLevel: 'ligero', goal: 'perder_peso' },
    2: { id: 2 },
  };
  const entries = [
    { userId: 1, date: new Date('2026-08-31T00:00:00Z'), kcal: 5000 },
    { userId: 1, date: new Date('2026-09-01T00:00:00Z'), kcal: 800 },
    { userId: 1, date: new Date('2026-09-15T00:00:00Z'), kcal: 5000 },
    { userId: 1, date: new Date('2026-09-30T00:00:00Z'), kcal: 1000 },
    { userId: 2, date: new Date('2026-09-10T00:00:00Z'), kcal: 1200 },
  ];
  return {
    user: { findUnique: async ({ where }) => users[where.id] ?? null },
    logEntry: {
      findMany: async ({ where }) => entries.filter((e) => e.userId === where.userId
        && e.date >= where.date.gte && e.date < where.date.lt),
    },
  };
}

async function startApi(db) {
  process.env.JWT_SECRET = 'secreto-de-prueba';
  const prismaPath = path.resolve(__dirname, '../src/prismaClient.js');
  const logPath = require.resolve('../src/routes/log');
  require.cache[prismaPath] = { id: prismaPath, filename: prismaPath, loaded: true, exports: db };
  delete require.cache[logPath];
  const express = require('express');
  const app = express();
  app.use('/log', require(logPath));
  const server = await new Promise((resolve) => { const s = app.listen(0, () => resolve(s)); });
  const base = `http://127.0.0.1:${server.address().port}`;
  const get = async (userId, url) => {
    const res = await fetch(base + url, { headers: { authorization: `Bearer ${jwt.sign({ userId }, process.env.JWT_SECRET)}` } });
    return { status: res.status, body: await res.json() };
  };
  return { get, close: () => new Promise((resolve) => server.close(resolve)) };
}

test('GET /log/calendar: solo los días del mes pedido y del propio usuario, con su estado', async () => {
  const api = await startApi(fakePrisma());
  try {
    const { status, body } = await api.get(1, '/log/calendar?month=2026-09');
    assert.equal(status, 200);
    assert.equal(body.month, '2026-09');
    assert.ok(body.dailyCalorieGoal > 1000);
    // El 31 de agosto queda fuera del mes; el 1 y el 30 de septiembre, dentro.
    assert.deepEqual(body.days.map((d) => [d.date, d.status]), [
      ['2026-09-01', 'insuficiente'],
      ['2026-09-15', 'excesivo'],
      ['2026-09-30', 'insuficiente'],
    ]);

    // Perfil incompleto: los días salen igual, pero sin estado.
    const incomplete = await api.get(2, '/log/calendar?month=2026-09');
    assert.equal(incomplete.body.dailyCalorieGoal, null);
    assert.deepEqual(incomplete.body.days, [{ date: '2026-09-10', kcal: 1200, status: null }]);
    assert.ok(incomplete.body.missingProfileFields.length > 0);

    assert.equal((await api.get(1, '/log/calendar?month=septiembre')).status, 400);
    assert.equal((await api.get(1, '/log/calendar')).status, 400);
  } finally {
    await api.close();
  }
});
