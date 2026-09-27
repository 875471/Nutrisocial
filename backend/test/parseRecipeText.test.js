// Pruebas de la heurística de post-procesado del OCR. Ejecutar con: npm test
const test = require('node:test');
const assert = require('node:assert/strict');
const { parseRecipeText, parseQuantityLine } = require('../src/ocr/parseRecipeText');

test('cantidades: enteros, decimales, fracciones, rangos y números escritos', () => {
  const cases = [
    ['200 g de harina', { rawName: 'harina', quantity: 200, unit: 'g' }],
    ['200g harina', { rawName: 'harina', quantity: 200, unit: 'g' }],
    ['1/2 cebolla', { rawName: 'cebolla', quantity: 0.5, unit: 'unidad' }],
    ['1 1/2 tazas de leche', { rawName: 'leche', quantity: 1.5, unit: 'taza' }],
    ['½ kg de patatas', { rawName: 'patatas', quantity: 0.5, unit: 'kg' }],
    ['1,5 l de caldo', { rawName: 'caldo', quantity: 1.5, unit: 'l' }],
    ['2-3 dientes de ajo', { rawName: 'ajo', quantity: 2.5, unit: 'unidad' }],
    ['2 cdas. de aceite de oliva', { rawName: 'aceite de oliva', quantity: 2, unit: 'cucharada' }],
    ['1 cdta sal', { rawName: 'sal', quantity: 1, unit: 'cucharadita' }],
    ['3 huevos', { rawName: 'huevos', quantity: 3, unit: 'unidad' }],
    ['dos huevos', { rawName: 'huevos', quantity: 2, unit: 'unidad' }],
    ['media cebolla', { rawName: 'cebolla', quantity: 0.5, unit: 'unidad' }],
    ['1 pizca de pimienta', { rawName: 'pimienta', quantity: 1, unit: 'pizca' }],
    ['1 vaso de agua', { rawName: 'agua', quantity: 1, unit: 'taza' }],
  ];
  for (const [line, expected] of cases) {
    assert.deepEqual(parseQuantityLine(line), expected, line);
  }
});

test('no confunde con ingredientes: temperaturas, tiempos, pasos y "una vez"', () => {
  for (const line of ['180º durante 20 minutos', '20 minutos al horno', 'Una vez frito, escurrir', '2 batir los huevos']) {
    assert.equal(parseQuantityLine(line), null, line);
  }
});

test('la unidad "g" no se come el principio del nombre', () => {
  assert.deepEqual(parseQuantityLine('200 gambas'), { rawName: 'gambas', quantity: 200, unit: 'unidad' });
  assert.deepEqual(parseQuantityLine('2 limones'), { rawName: 'limones', quantity: 2, unit: 'unidad' });
});

test('receta con encabezados, viñetas, numeración y pasos partidos en varias líneas', () => {
  const text = [
    'Tortilla de patatas',
    'Para 4 personas',
    'Ingredientes:',
    '- 1 kg de patatas',
    '- 6 huevos',
    '- 1 cebolla',
    '- Aceite de oliva',
    '- Sal',
    'Preparación:',
    '1. Pelar y cortar las patatas',
    'en láminas finas.',
    '2. Freír las patatas y la cebolla',
    'a fuego lento.',
    '3. Batir los huevos, mezclar y cuajar.',
  ].join('\n');
  const r = parseRecipeText(text);
  assert.equal(r.title, 'Tortilla de patatas');
  assert.equal(r.servings, 4);
  assert.deepEqual(r.ingredients.map((i) => [i.rawName, i.quantity, i.unit]), [
    ['patatas', 1, 'kg'],
    ['huevos', 6, 'unidad'],
    ['cebolla', 1, 'unidad'],
    ['Aceite de oliva', null, null],
    ['Sal', null, null],
  ]);
  assert.deepEqual(r.steps, [
    'Pelar y cortar las patatas en láminas finas.',
    'Freír las patatas y la cebolla a fuego lento.',
    'Batir los huevos, mezclar y cuajar.',
  ]);
});

test('receta sin encabezados: los pasos se reconocen por el verbo', () => {
  const text = [
    'Crema de calabaza',
    '800 g calabaza',
    '1 cebolla',
    '1 cda aceite',
    'Pochar la cebolla con el aceite.',
    'Añadir la calabaza troceada y cubrir de agua.',
    'Cocer 20 minutos y triturar.',
  ].join('\n');
  const r = parseRecipeText(text);
  assert.equal(r.title, 'Crema de calabaza');
  assert.equal(r.ingredients.length, 3);
  assert.equal(r.steps.length, 3);
  assert.deepEqual(r.lines.map((l) => l.kind), ['title', 'ingredient', 'ingredient', 'ingredient', 'step', 'step', 'step']);
});

test('ingredientes como "coco rallado" o "batata" no se toman por pasos', () => {
  const r = parseRecipeText('Postre\nIngredientes\nCoco rallado\nBatata\nPasas');
  assert.deepEqual(r.ingredients.map((i) => i.rawName), ['Coco rallado', 'Batata', 'Pasas']);
  assert.equal(r.steps.length, 0);
});

test('errores típicos del OCR en la cantidad: "l" por 1 y "O" por 0', () => {
  const r = parseRecipeText('Ingredientes\nl cebolla\n2OO g de arroz');
  assert.deepEqual(r.ingredients.map((i) => [i.rawName, i.quantity, i.unit]), [
    ['cebolla', 1, 'unidad'],
    ['arroz', 200, 'g'],
  ]);
});

test('dentro de "Preparación" una línea con número sigue siendo paso', () => {
  const r = parseRecipeText('Preparación\nHornear a 180º\n20 minutos hasta que dore.');
  assert.equal(r.ingredients.length, 0);
  assert.equal(r.steps.length, 2);
});

test('texto vacío', () => {
  assert.deepEqual(parseRecipeText('  \n\n '), { title: null, servings: null, ingredients: [], steps: [], lines: [] });
});

// Salida real de ML Kit en el emulador para una receta con letra manuscrita simulada
// (fuente Ink Free sobre papel pautado, ligeramente girada).
test('salida real de ML Kit con errores de lectura', () => {
  const text = [
    'Lentejas con verduras', '', 'Para A4 persoas', '', 'Ingredientes:', '300 9 de lentejas', '',
    '-1 cebola', '', '-2 2anahorias', '', '-1 pİmiento rojo', '', '-2 dientes de ajo', '',
    '-2 cdas de aceite de oliva', '', '-Sal', '', 'PreparAción:', '', '1. Picar la cebolla, el ajo y el', '',
    'pimiento y pocharlos en el aceite.', '', '2. Añadir las zanahorias en rodajas.', '',
    '3. Echar las lentejas y cuorir con aGUa.', '', '4. Cocer 4D mintos a fueoo lento.',
  ].join('\n');
  const r = parseRecipeText(text);
  assert.equal(r.title, 'Lentejas con verduras');
  assert.equal(r.servings, 4);
  assert.deepEqual(r.ingredients.map((i) => [i.rawName, i.quantity, i.unit]), [
    ['lentejas', 300, 'g'],
    ['cebola', 1, 'unidad'],
    ['zanahorias', 2, 'unidad'],
    ['pİmiento rojo', 1, 'unidad'],
    ['ajo', 2, 'unidad'],
    ['aceite de oliva', 2, 'cucharada'],
    ['Sal', null, null],
  ]);
  assert.equal(r.steps.length, 4);
  assert.equal(r.steps[0], 'Picar la cebolla, el ajo y el pimiento y pocharlos en el aceite.');
});

test('cantidades de más de dos cifras y decimales', () => {
  const cases = [
    ['115 g de mantequilla blanda', { rawName: 'mantequilla blanda', quantity: 115, unit: 'g' }],
    ['1500 ml de agua', { rawName: 'agua', quantity: 1500, unit: 'ml' }],
    ['0,5 kg de harina', { rawName: 'harina', quantity: 0.5, unit: 'kg' }],
    ['1.5 l de leche', { rawName: 'leche', quantity: 1.5, unit: 'l' }],
    ['3 lonchas de queso', { rawName: 'lonchas de queso', quantity: 3, unit: 'unidad' }],
  ];
  for (const [line, expected] of cases) {
    assert.deepEqual(parseQuantityLine(line), expected, line);
  }
});

test('un "1" leído como "I" o "l" pegado al número o al nombre', () => {
  const r = parseRecipeText('Ingredientes\n- Ihuevo\n- l2 huevos\nI15 g de mantequilla\n1 15 g de harina\nlhuevos\nleche\nlentejas\n1 15 gambas');
  assert.deepEqual(r.ingredients.map((i) => [i.rawName, i.quantity, i.unit]), [
    ['huevo', 1, 'unidad'],
    ['huevos', 12, 'unidad'],
    ['mantequilla', 115, 'g'],
    ['harina', 115, 'g'],
    ['huevos', 1, 'unidad'],
    ['leche', null, null],
    ['lentejas', null, null],
    ['15 gambas', 1, 'unidad'],
  ]);
});

test('título en dos líneas antes del encabezado de ingredientes', () => {
  const r = parseRecipeText('Bizcocho\nde yogur y limón\nIngredientes\n3 huevos\nPreparación\nBatir los huevos.');
  assert.equal(r.title, 'Bizcocho de yogur y limón');
  assert.deepEqual(r.ingredients.map((i) => i.rawName), ['huevos']);
  // Sin encabezados, la segunda línea solo se une al título si empieza por un conector.
  assert.equal(parseRecipeText('Ensalada\nde pasta\nTomate\nMezclar todo.').title, 'Ensalada de pasta');
  assert.equal(parseRecipeText('Ensalada\nTomate\nMezclar todo.').ingredients[0].rawName, 'Tomate');
});

test('en "Preparación", solo "cantidad + unidad + de + alimento" sin verbos es ingrediente', () => {
  const r = parseRecipeText([
    'Preparación',
    '200 g de harina',
    'Añadir 200 g de harina.',
    '2 cucharadas de aceite y remover.',
    '20 minutos de horno.',
    '3 huevos',
  ].join('\n'));
  assert.deepEqual(r.ingredients.map((i) => [i.rawName, i.quantity, i.unit]), [['harina', 200, 'g']]);
  assert.equal(r.steps.length, 4);
});

// Receta de repostería impresa con el título en dos líneas y los ingredientes a dos
// columnas. ML Kit lee por bloques, así que un ingrediente de la columna derecha puede
// llegar detrás de "Preparación", con la viñeta convertida en un punto suelto.
test('receta a dos columnas con el orden de lectura de ML Kit alterado', () => {
  const text = [
    'Galletas con chips',
    '',
    'de chocolate',
    '',
    'Ingredientes',
    '- 115 g de mantequilla blanda',
    '- 100 g de azúcar rubia',
    '- 100 g de azúcar blanca',
    '- 190 g de harina',
    '',
    '- Ihuevo',
    '- 3 g de bicarbonato',
    '- 170 g de chips de chocolate',
    '',
    'Preparación',
    '. 5 ml de esencia de vainilla',
    'Batir mantequilla con los azúcares, añadir huevo y vainilla.',
    'Incorporar harina y bicarbonato, luego los chips.',
    'Formar bolitas y aplanar ligeramente.',
    'Cocinar en freidora sobre papel mantequilla a 160 °C por 6-8 min.',
  ].join('\n');
  const r = parseRecipeText(text);
  assert.equal(r.title, 'Galletas con chips de chocolate');
  assert.deepEqual(r.ingredients.map((i) => [i.rawName, i.quantity, i.unit]), [
    ['mantequilla blanda', 115, 'g'],
    ['azúcar rubia', 100, 'g'],
    ['azúcar blanca', 100, 'g'],
    ['harina', 190, 'g'],
    ['huevo', 1, 'unidad'],
    ['bicarbonato', 3, 'g'],
    ['chips de chocolate', 170, 'g'],
    ['esencia de vainilla', 5, 'ml'],
  ]);
  assert.deepEqual(r.steps, [
    'Batir mantequilla con los azúcares, añadir huevo y vainilla.',
    'Incorporar harina y bicarbonato, luego los chips.',
    'Formar bolitas y aplanar ligeramente.',
    'Cocinar en freidora sobre papel mantequilla a 160 °C por 6-8 min.',
  ]);
});
