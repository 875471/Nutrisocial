// Pruebas del respaldo con Open Food Facts. No se llama a la API real: se sustituye
// globalThis.fetch por respuestas simuladas, y Prisma por una tabla Food en memoria.
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const OFF_PATH = require.resolve('../src/nutrition/openFoodFacts');
const MATCH_PATH = require.resolve('../src/nutrition/matchFood');
const PRISMA_PATH = path.resolve(__dirname, '../src/prismaClient.js');

const BEDCA = [
  { id: 1, name: 'Chocolate, con leche', kcal: 530, protein: 7, carbs: 57, fat: 30, source: 'BEDCA' },
  { id: 2, name: 'Harina de trigo', kcal: 340, protein: 10, carbs: 72, fat: 1, source: 'BEDCA' },
];

const COMPLETE = {
  product_name: 'Bicarbonato sódico',
  nutriments: { 'energy-kcal_100g': 0, proteins_100g: 0, carbohydrates_100g: 0, fat_100g: 0 },
};
const INCOMPLETE = {
  product_name: 'Bicarbonato de sodio',
  nutriments: { 'energy-kcal_100g': 0, proteins_100g: 0 },
};
const CHIPS = {
  product_name_es: 'Chips de chocolate negro',
  product_name: 'Dark chocolate chips',
  nutriments: { 'energy-kcal_100g': 490, proteins_100g: '5.4999999', carbohydrates_100g: 60, fat_100g: 25 },
};

// Tabla Food en memoria con la parte de la API de Prisma que usa matchFood.
function fakePrisma(initial = BEDCA) {
  const rows = initial.map((f) => ({ ...f }));
  const db = {
    rows,
    upserts: 0,
    food: {
      findMany: async () => rows.map((f) => ({ ...f })),
      findUnique: async ({ where }) => rows.find((f) => f.name === where.name) ?? null,
      upsert: async ({ where, create }) => {
        db.upserts += 1;
        let row = rows.find((f) => f.name === where.name);
        if (!row) {
          row = { id: rows.length + 100, ...create };
          rows.push(row);
        }
        return { ...row };
      },
    },
  };
  return db;
}

// Carga openFoodFacts desde cero: guarda en memoria respuestas y peticiones recientes.
function freshOff() {
  delete require.cache[OFF_PATH];
  return require(OFF_PATH);
}

// Carga matchFood desde cero (su índice en memoria es de módulo) con la base indicada.
function loadMatchFood(db) {
  delete require.cache[MATCH_PATH];
  delete require.cache[OFF_PATH];
  require.cache[PRISMA_PATH] = { id: PRISMA_PATH, filename: PRISMA_PATH, loaded: true, exports: db };
  return require(MATCH_PATH);
}

// Sustituye fetch durante una prueba y cuenta las llamadas.
function mockFetch(t, handler) {
  const original = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (url, options) => {
    calls.push({ url: String(url), options });
    return handler(url, options);
  };
  t.after(() => {
    globalThis.fetch = original;
  });
  return calls;
}

const jsonResponse = (products) => ({ ok: true, json: async () => ({ products }) });

test('toFood descarta productos a los que les falta algún valor nutricional', () => {
  const { toFood } = freshOff();
  assert.equal(toFood(INCOMPLETE), null);
  assert.equal(toFood({ product_name: 'Sin nutrientes' }), null);
  assert.equal(toFood({ ...COMPLETE, product_name: '' }), null);
  assert.equal(toFood({ ...CHIPS, nutriments: { ...CHIPS.nutriments, fat_100g: 'n/d' } }), null);
  // Prefiere el nombre en español, acepta números enviados como texto y los redondea.
  assert.deepEqual(toFood(CHIPS), {
    name: 'Chips de chocolate negro', kcal: 490, protein: 5.5, carbs: 60, fat: 25, source: 'OpenFoodFacts',
  });
});

test('matchesTerm exige las palabras del término, con plurales y prefijo opcional', () => {
  const { matchesTerm } = freshOff();
  assert.ok(matchesTerm('chips de chocolate', 'Chips de chocolate negro'));
  assert.ok(matchesTerm('bicarbonato', 'Bicarbonato sódico'));
  assert.ok(matchesTerm('pepitas de chocolate', 'Pepita de chocolate'));
  assert.ok(!matchesTerm('chips de chocolate', 'Galletas con chocolate'));
  // El nombre tiene que empezar por el término: otro producto que lo contiene no vale.
  assert.ok(!matchesTerm('chips de chocolate', 'Galletas con chips de chocolate'));
  assert.ok(!matchesTerm('chips de chocolate', 'Orange Chocolate Chip Shortbread'));
  assert.ok(!matchesTerm('bicarb', 'Bicarbonato sódico'));
  assert.ok(matchesTerm('bicarb', 'Bicarbonato sódico', { prefix: true }));
});

test('searchOpenFoodFacts salta los incompletos, se identifica y pide la búsqueda en español', async (t) => {
  const calls = mockFetch(t, () => jsonResponse([INCOMPLETE, COMPLETE]));
  const { searchOpenFoodFacts } = freshOff();
  const food = await searchOpenFoodFacts('bicarbonato');
  assert.deepEqual(food, { name: 'Bicarbonato sódico', kcal: 0, protein: 0, carbs: 0, fat: 0, source: 'OpenFoodFacts' });
  assert.equal(calls.length, 1);
  const url = new URL(calls[0].url);
  assert.equal(url.searchParams.get('search_terms'), 'bicarbonato');
  assert.equal(url.searchParams.get('lc'), 'es');
  assert.equal(url.searchParams.get('tag_0'), 'spain');
  assert.match(calls[0].options.headers['User-Agent'], /^NutriSocial-TFG/);
  assert.ok(calls[0].options.signal, 'la petición debe tener un tiempo máximo');
});

test('searchOpenFoodFacts devuelve null si solo hay productos incompletos o que no se parecen', async (t) => {
  mockFetch(t, () => jsonResponse([INCOMPLETE, CHIPS]));
  const { searchOpenFoodFacts } = freshOff();
  assert.equal(await searchOpenFoodFacts('bicarbonato'), null);
});

test('un fallo de red, un error HTTP o una respuesta rara devuelven null sin lanzar', async (t) => {
  t.mock.method(console, 'warn', () => {});
  const responses = [
    () => { throw new TypeError('fetch failed'); },
    () => { throw new DOMException('The operation was aborted due to timeout', 'TimeoutError'); },
    () => ({ ok: false, status: 503, json: async () => ({}) }),
    () => ({ ok: true, json: async () => { throw new SyntaxError('Unexpected token <'); } }),
    () => ({ ok: true, json: async () => ({ error: 'nope' }) }),
  ];
  for (const respond of responses) {
    const original = globalThis.fetch;
    globalThis.fetch = async () => respond();
    try {
      assert.equal(await freshOff().searchOpenFoodFacts('bicarbonato'), null);
    } finally {
      globalThis.fetch = original;
    }
  }
});

test('la misma búsqueda no se repite y los fallos no se recuerdan', async (t) => {
  t.mock.method(console, 'warn', () => {});
  let fail = true;
  const calls = mockFetch(t, () => (fail ? { ok: false, status: 503 } : jsonResponse([COMPLETE])));
  const { searchOpenFoodFacts } = freshOff();
  assert.equal(await searchOpenFoodFacts('bicarbonato'), null);
  fail = false;
  assert.equal((await searchOpenFoodFacts('bicarbonato'))?.name, 'Bicarbonato sódico');
  assert.equal((await searchOpenFoodFacts('Bicarbonato'))?.name, 'Bicarbonato sódico');
  assert.equal(calls.length, 2);
});

test('no se pasa del límite de búsquedas por minuto de Open Food Facts', async (t) => {
  t.mock.method(console, 'warn', () => {});
  const calls = mockFetch(t, () => jsonResponse([]));
  const { searchOpenFoodFacts } = freshOff();
  for (let i = 0; i < 12; i++) assert.equal(await searchOpenFoodFacts(`producto ${i}`), null);
  assert.equal(calls.length, 8);
});

test('matchFood usa BEDCA si tiene el alimento y no llama a Open Food Facts', async (t) => {
  const calls = mockFetch(t, () => jsonResponse([COMPLETE]));
  const { matchFood } = loadMatchFood(fakePrisma());
  assert.equal((await matchFood('harina'))?.name, 'Harina de trigo');
  assert.equal(calls.length, 0);
});

test('matchFood guarda en Food el producto de Open Food Facts y la segunda vez no lo vuelve a pedir', async (t) => {
  const calls = mockFetch(t, () => jsonResponse([INCOMPLETE, COMPLETE]));
  const db = fakePrisma();
  const { matchFood, findFoodById } = loadMatchFood(db);

  const food = await matchFood('bicarbonato');
  assert.equal(food.name, 'Bicarbonato sódico');
  assert.equal(food.source, 'OpenFoodFacts');
  assert.equal(db.upserts, 1);
  assert.ok(db.rows.some((f) => f.name === 'Bicarbonato sódico' && f.source === 'OpenFoodFacts'));
  // También queda en el índice en memoria: se puede usar por id (registro diario, recetas).
  assert.equal((await findFoodById(food.id))?.name, 'Bicarbonato sódico');

  const again = await matchFood('Bicarbonato');
  assert.equal(again.id, food.id);
  assert.equal(calls.length, 1, 'la segunda búsqueda debe salir de la tabla Food');
});

test('los productos guardados de Open Food Facts se cargan de la base tras reiniciar', async (t) => {
  const calls = mockFetch(t, () => jsonResponse([]));
  const cached = { id: 50, name: 'Bicarbonato sódico', kcal: 0, protein: 0, carbs: 0, fat: 0, source: 'OpenFoodFacts' };
  const { matchFood } = loadMatchFood(fakePrisma([...BEDCA, cached]));
  assert.equal((await matchFood('bicarbonato'))?.id, 50);
  assert.equal(calls.length, 0);
});

test('matchFood devuelve null sin romper si Open Food Facts falla o no tiene nada', async (t) => {
  t.mock.method(console, 'warn', () => {});
  const db = fakePrisma();
  const { matchFood } = loadMatchFood(db);

  const original = globalThis.fetch;
  t.after(() => { globalThis.fetch = original; });
  globalThis.fetch = async () => { throw new TypeError('fetch failed'); };
  assert.equal(await matchFood('esencia de vainilla'), null);
  globalThis.fetch = async () => jsonResponse([INCOMPLETE]);
  assert.equal(await matchFood('bicarbonato'), null);
  assert.equal(db.upserts, 0);
});

test('matchFood devuelve null si no se puede guardar el producto', async (t) => {
  t.mock.method(console, 'warn', () => {});
  mockFetch(t, () => jsonResponse([COMPLETE]));
  const db = fakePrisma();
  db.food.upsert = async () => { throw new Error('conexión perdida'); };
  const { matchFood } = loadMatchFood(db);
  assert.equal(await matchFood('bicarbonato'), null);
});

test('searchFoods completa con Open Food Facts cuando hay menos de 3 resultados locales', async (t) => {
  const calls = mockFetch(t, () => jsonResponse([CHIPS, INCOMPLETE]));
  const db = fakePrisma();
  const { searchFoods } = loadMatchFood(db);

  const results = await searchFoods('chips de choc');
  assert.deepEqual(results.map((f) => f.name), ['Chips de chocolate negro']);
  assert.equal(results[0].source, 'OpenFoodFacts');
  assert.equal(calls.length, 1);

  // La siguiente búsqueda ya lo tiene en local, detrás de lo que haya en BEDCA.
  const next = await searchFoods('chocolate');
  assert.deepEqual(next.map((f) => f.name), ['Chocolate, con leche', 'Chips de chocolate negro']);
});

test('searchFoods no consulta fuera con 3 resultados locales o con consultas de 2 letras', async (t) => {
  const calls = mockFetch(t, () => jsonResponse([CHIPS]));
  const many = [...BEDCA, ...['Harina de maíz', 'Harina de arroz'].map((name, i) => ({
    id: 10 + i, name, kcal: 350, protein: 7, carbs: 78, fat: 1, source: 'BEDCA',
  }))];
  const { searchFoods } = loadMatchFood(fakePrisma(many));
  assert.equal((await searchFoods('harina')).length, 3);
  await searchFoods('xz');
  assert.equal(calls.length, 0);
});
