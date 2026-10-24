// Pruebas de la página pública de una receta compartida (routes/share.js): se abre sin sesión,
// devuelve HTML, escapa todo lo que escriben los usuarios, no pide datos de la cuenta del autor
// y responde 404 en HTML si la receta no existe. Las rutas se prueban por HTTP sobre un cliente
// de Prisma simulado.
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const { escapeHtml } = require('../src/utils/html');

const SRC = path.resolve(__dirname, '../src');

const RECIPE = {
  id: 7,
  title: '<script>alert(1)</script>Tortilla',
  description: 'Rica & fácil',
  steps: JSON.stringify(['Batir <b>huevos</b>', 'Cuajar']),
  servings: 4,
  prepMinutes: 40,
  kcal: 1000, protein: 50.4, carbs: 80, fat: 41,
  totalWeightGrams: 900,
  author: { name: '"><img src=x onerror=alert(1)>', email: 'autor@correo.es', password: 'hash-secreto' },
  ingredients: [
    { name: 'huevo <i>', quantity: 6, unit: 'unidad', grams: 300, foodId: 1 },
    { name: 'sal', quantity: 0.5, unit: 'pizca', grams: null, foodId: null },
  ],
};

// Arranca solo el router de /share sobre un findUnique simulado que guarda cada consulta.
async function startShare() {
  const queries = [];
  const prismaPath = path.join(SRC, 'prismaClient.js');
  delete require.cache[path.join(SRC, 'routes', 'share.js')];
  require.cache[prismaPath] = {
    id: prismaPath, filename: prismaPath, loaded: true,
    exports: { recipe: { findUnique: async (args) => { queries.push(args); return args.where.id === RECIPE.id ? RECIPE : null; } } },
  };
  const express = require('express');
  const app = express();
  app.use('/share', require(path.join(SRC, 'routes', 'share.js')));
  const server = await new Promise((resolve) => { const s = app.listen(0, () => resolve(s)); });
  const base = `http://127.0.0.1:${server.address().port}`;
  const get = async (url) => {
    const res = await fetch(base + url);
    return { status: res.status, type: res.headers.get('content-type'), csp: res.headers.get('content-security-policy'), text: await res.text() };
  };
  return { get, queries, close: () => new Promise((resolve) => server.close(resolve)) };
}

test('escapeHtml escapa los cinco caracteres especiales y tolera null', () => {
  assert.equal(escapeHtml(`<a href="x" title='y'>&</a>`), '&lt;a href=&quot;x&quot; title=&#39;y&#39;&gt;&amp;&lt;/a&gt;');
  assert.equal(escapeHtml(null), '');
});

test('GET /share/recipes/:id se abre sin sesión y devuelve la receta en HTML', async () => {
  const api = await startShare();
  try {
    const res = await api.get('/share/recipes/7');
    assert.equal(res.status, 200);
    assert.match(res.type, /^text\/html/);
    assert.match(res.csp, /default-src 'none'/);
    assert.match(res.text, /4 raciones · 40 min/);
    // Por ración: 1000 / 4 kcal, 50,4 / 4 g de proteína...
    for (const value of ['<b>250</b>', '<b>12,6 g</b>', '<b>20 g</b>', '<b>10,3 g</b>']) assert.ok(res.text.includes(value), value);
    assert.ok(res.text.includes('<li>sal — 0,5 pizca</li>'));
    assert.match(res.text, /<ol>\s*<li>Batir &lt;b&gt;huevos&lt;\/b&gt;<\/li>\s*<li>Cuajar<\/li>/);
  } finally {
    await api.close();
  }
});

test('todo lo que escriben los usuarios sale escapado', async () => {
  const api = await startShare();
  try {
    const { text } = await api.get('/share/recipes/7');
    assert.ok(!text.includes('<script'), 'no debe haber ningún <script>');
    assert.ok(!text.includes('<img'), 'no debe haber ningún <img>');
    assert.ok(!text.includes('<i>'));
    assert.ok(text.includes('<h1>&lt;script&gt;alert(1)&lt;/script&gt;Tortilla</h1>'));
    assert.ok(text.includes('Por &quot;&gt;&lt;img src=x onerror=alert(1)&gt;'));
    assert.ok(text.includes('Rica &amp; fácil'));
  } finally {
    await api.close();
  }
});

test('del autor solo se pide el nombre: nunca su email ni su contraseña', async () => {
  const api = await startShare();
  try {
    const { text } = await api.get('/share/recipes/7');
    assert.deepEqual(api.queries[0].select.author, { select: { name: true } });
    assert.ok(!('imageBase64' in api.queries[0].select));
    assert.ok(!text.includes('autor@correo.es') && !text.includes('hash-secreto'));
  } finally {
    await api.close();
  }
});

test('una receta que no existe o un id no válido dan 404 en HTML', async () => {
  const api = await startShare();
  try {
    for (const url of ['/share/recipes/999', '/share/recipes/abc', '/share/recipes/-1']) {
      const res = await api.get(url);
      assert.equal(res.status, 404, url);
      assert.match(res.type, /^text\/html/, url);
      assert.match(res.text, /Receta no encontrada/, url);
    }
    // Los ids no válidos ni siquiera llegan a la base de datos.
    assert.equal(api.queries.length, 1);
  } finally {
    await api.close();
  }
});
