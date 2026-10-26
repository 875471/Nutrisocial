// Pruebas de la generación de recetas con un modelo de lenguaje: validación de la petición y
// de la respuesta, y la llamada al SDK con un fetch simulado (sin red ni clave real).
const test = require('node:test');
const assert = require('node:assert/strict');
const {
  generateRecipe, validateGeneratedRecipe, cleanInputIngredients, GenerationError,
} = require('../src/ai/generateRecipe');

const VALID = {
  title: 'Arroz con pollo',
  servings: 2,
  steps: ['Dora el pollo.', 'Añade el arroz y el agua.'],
  ingredients: [
    { name: 'pechuga de pollo', quantity: 300, unit: 'g' },
    { name: 'arroz', quantity: 160, unit: 'g' },
    { name: 'pimiento rojo', quantity: 1, unit: 'unidad' },
    { name: 'aceite de oliva', quantity: 2, unit: 'cucharada' },
  ],
};

test('respuesta válida: devuelve la receta limpia', () => {
  const recipe = validateGeneratedRecipe(JSON.stringify({ ...VALID, title: '  Arroz con pollo ' }));
  assert.equal(recipe.title, 'Arroz con pollo');
  assert.deepEqual(recipe.ingredients, VALID.ingredients);
});

test('respuesta que no es JSON o no es un objeto: GenerationError', () => {
  assert.throws(() => validateGeneratedRecipe('Aquí tienes tu receta: {...}'), GenerationError);
  assert.throws(() => validateGeneratedRecipe('[1, 2]'), GenerationError);
  assert.throws(() => validateGeneratedRecipe('null'), GenerationError);
});

test('campos con tipo o forma incorrectos: GenerationError', () => {
  const broken = [
    { ...VALID, title: '' },
    { ...VALID, servings: 2.5 },
    { ...VALID, servings: 0 },
    { ...VALID, steps: [] },
    { ...VALID, steps: ['Paso', 3] },
    { ...VALID, ingredients: [] },
    { ...VALID, ingredients: [{ name: 'arroz', quantity: 100, unit: 'gramos' }] }, // unidad fuera de UNITS
    { ...VALID, ingredients: [{ name: 'arroz', quantity: -1, unit: 'g' }] },
    { ...VALID, ingredients: [{ name: 'arroz', quantity: 50000, unit: 'g' }] }, // cantidad absurda
    { ...VALID, ingredients: [{ quantity: 1, unit: 'unidad' }] },
  ];
  for (const recipe of broken) {
    assert.throws(() => validateGeneratedRecipe(JSON.stringify(recipe)), GenerationError, JSON.stringify(recipe));
  }
});

test('ingredientes de la petición: lista de nombres no vacíos, sin duplicados', () => {
  assert.deepEqual(cleanInputIngredients([' pollo ', 'arroz', 'pollo', '']), { ingredients: ['pollo', 'arroz'] });
  assert.ok(cleanInputIngredients('pollo, arroz').error);
  assert.ok(cleanInputIngredients([{ name: 'pollo' }]).error);
  assert.ok(cleanInputIngredients(['  ']).error);
  assert.ok(cleanInputIngredients(Array.from({ length: 31 }, (_, i) => `ingrediente ${i}`)).error);
});

// Respuesta de la API de mensajes con el texto dado.
function apiResponse(text, stopReason = 'end_turn') {
  return new Response(JSON.stringify({
    id: 'msg_test', type: 'message', role: 'assistant', model: 'claude-haiku-5-5',
    content: [{ type: 'text', text }], stop_reason: stopReason, stop_sequence: null,
    usage: { input_tokens: 10, output_tokens: 10 },
  }), { status: 200, headers: { 'content-type': 'application/json' } });
}

test('generateRecipe: pide salida estructurada con las unidades de la app y valida la respuesta', async (t) => {
  const fetchReal = globalThis.fetch;
  process.env.ANTHROPIC_API_KEY = 'clave-de-prueba';
  const requests = [];
  const replies = [apiResponse(JSON.stringify(VALID)), apiResponse('no es json'), apiResponse('{', 'max_tokens')];
  globalThis.fetch = async (url, init) => {
    requests.push({ url: String(url), body: JSON.parse(init.body) });
    return replies.shift();
  };
  t.after(() => { globalThis.fetch = fetchReal; delete process.env.ANTHROPIC_API_KEY; });

  const recipe = await generateRecipe(['pollo', 'arroz', 'pimiento']);
  assert.equal(recipe.title, 'Arroz con pollo');
  const { body } = requests[0];
  assert.match(requests[0].url, /\/v1\/messages$/);
  assert.equal(body.model, 'claude-haiku-5-5');
  assert.equal(body.output_config.format.type, 'json_schema');
  assert.deepEqual(body.output_config.format.schema.properties.ingredients.items.properties.unit.enum,
    require('../src/nutrition/unitConversion').UNITS);
  assert.match(body.system, /cucharadita/);
  assert.match(body.messages[0].content, /"pollo","arroz","pimiento"/);

  await assert.rejects(generateRecipe(['pollo']), GenerationError);
  await assert.rejects(generateRecipe(['pollo']), GenerationError);
});
