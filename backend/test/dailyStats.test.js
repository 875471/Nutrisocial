// Pruebas de las estadísticas del diario (GET /log/stats, src/nutrition/dailyStats.js): serie con
// un punto por día (también los vacíos), medias solo de los días con registro y validación.
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const jwt = require('jsonwebtoken');

const { periodDays, statsSeries } = require('../src/nutrition/dailyStats');
const { formatDay } = require('../src/utils/day');

const d = (text) => new Date(`${text}T00:00:00Z`);

test('periodDays: 7 días que terminan en el día dado, incluido, cruzando de mes', () => {
  const { start, endExclusive, dates } = periodDays(d('2026-11-03'), 7);
  assert.equal(formatDay(start), '2026-10-28');
  assert.equal(formatDay(endExclusive), '2026-11-04');
  assert.deepEqual(dates.map(formatDay), ['2026-10-28', '2026-10-29', '2026-10-30', '2026-10-31', '2026-11-01', '2026-11-02', '2026-11-03']);
});

test('statsSeries: días vacíos a cero y medias solo de los días con registro', () => {
  const { dates } = periodDays(d('2026-11-07'), 7);
  const entries = [
    { date: d('2026-11-01'), kcal: 500, protein: 20, carbs: 60, fat: 15 },
    { date: d('2026-11-01'), kcal: 700.4, protein: 30.25, carbs: 80, fat: 25 },
    { date: d('2026-11-03'), kcal: 1800, protein: 90, carbs: 200, fat: 60 },
    // Fuera del periodo: se ignora.
    { date: d('2026-10-20'), kcal: 9999, protein: 0, carbs: 0, fat: 0 },
  ];
  const stats = statsSeries(entries, dates, formatDay);
  assert.equal(stats.days.length, 7);
  assert.deepEqual(stats.days[0], { date: '2026-11-01', kcal: 1200, protein: 50.3, carbs: 140, fat: 40, entries: 2 });
  assert.deepEqual(stats.days[1], { date: '2026-11-02', kcal: 0, protein: 0, carbs: 0, fat: 0, entries: 0 });
  assert.equal(stats.daysLogged, 2);
  assert.deepEqual(stats.averages, { kcal: 1500, protein: 70.2, carbs: 170, fat: 50 });

  const empty = statsSeries([], dates, formatDay);
  assert.equal(empty.daysLogged, 0);
  assert.equal(empty.averages, null);
});

function fakePrisma() {
  const users = {
    1: { id: 1, birthDate: new Date('1996-01-01'), heightCm: 175, weightKg: 70, sex: 'M', activityLevel: 'ligero', goal: 'mantener' },
    2: { id: 2 },
  };
  const entries = [
    { userId: 1, date: d('2026-11-10'), kcal: 2000, protein: 100, carbs: 250, fat: 70 },
    { userId: 1, date: d('2026-11-16'), kcal: 1800, protein: 80, carbs: 220, fat: 60 },
    { userId: 1, date: d('2026-11-17'), kcal: 2500, protein: 120, carbs: 300, fat: 90 },
    { userId: 2, date: d('2026-11-17'), kcal: 5000, protein: 1, carbs: 1, fat: 1 },
  ];
  return {
    user: { findUnique: async ({ where }) => users[where.id] ?? null },
    logEntry: {
      findMany: async ({ where }) => entries.filter((e) => e.userId === where.userId && e.date >= where.date.gte && e.date < where.date.lt),
    },
  };
}

async function startApi(db) {
  process.env.JWT_SECRET = 'secreto-de-prueba';
  const prismaPath = path.resolve(__dirname, '../src/prismaClient.js');
  require.cache[prismaPath] = { id: prismaPath, filename: prismaPath, loaded: true, exports: db };
  for (const mod of ['../src/routes/log', '../src/nutrition/matchFood', '../src/nutrition/recommend']) {
    delete require.cache[require.resolve(mod)];
  }
  const express = require('express');
  const app = express();
  app.use('/log', require('../src/routes/log'));
  const server = await new Promise((resolve) => { const s = app.listen(0, () => resolve(s)); });
  const base = `http://127.0.0.1:${server.address().port}`;
  const get = async (userId, url) => {
    const res = await fetch(base + url, { headers: { authorization: `Bearer ${jwt.sign({ userId }, process.env.JWT_SECRET)}` } });
    return { status: res.status, body: await res.json() };
  };
  return { get, close: () => new Promise((resolve) => server.close(resolve)) };
}

test('GET /log/stats: 7 y 30 días con objetivos; solo las entradas propias', async () => {
  const api = await startApi(fakePrisma());
  try {
    const week = await api.get(1, '/log/stats?days=7&end=2026-11-17');
    assert.equal(week.status, 200);
    assert.equal(week.body.from, '2026-11-11');
    assert.equal(week.body.to, '2026-11-17');
    assert.equal(week.body.days.length, 7);
    assert.equal(week.body.daysLogged, 2, 'la entrada del día 10 queda fuera de la semana');
    assert.equal(week.body.averages.kcal, 2150, 'sin contar los 5000 kcal de otro usuario');
    assert.ok(week.body.dailyCalorieGoal > 1500);
    assert.ok(week.body.macroTargets.proteinG > 0);

    const month = await api.get(1, '/log/stats?days=30&end=2026-11-17');
    assert.equal(month.body.days.length, 30);
    assert.equal(month.body.daysLogged, 3);

    // Sin perfil completo, objetivos a null pero la serie igual.
    const noProfile = await api.get(2, '/log/stats?end=2026-11-17');
    assert.equal(noProfile.body.days.length, 7, '7 días por defecto');
    assert.equal(noProfile.body.dailyCalorieGoal, null);
    assert.equal(noProfile.body.macroTargets, null);

    for (const url of ['/log/stats?days=14&end=2026-11-17', '/log/stats?days=7', '/log/stats?days=7&end=2026-02-31']) {
      assert.equal((await api.get(1, url)).status, 400, url);
    }
  } finally {
    await api.close();
  }
});
