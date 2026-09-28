// Evaluación del núcleo 3.1 (parte de servidor): segmentación del texto reconocido por OCR en
// título, ingredientes y pasos. Pasa cada caso de data/ocrSegmentationCases.json por la MISMA
// función que usa POST /recipes/parse-ocr (parseRecipeText) y compara con lo esperado.
//
//   npm run eval:ocr-segmentacion
//
// Emparejamiento: cada elemento esperado se empareja con el propuesto más parecido todavía libre
// (similitud de edición sobre el texto normalizado, sin tildes ni mayúsculas) si supera el umbral:
//   - ingrediente: nombre con similitud ≥ 0,8 (tolera un plural o una letra de diferencia);
//   - paso: texto con similitud ≥ 0,9 (tolera puntuación, no una frase partida o unida de más).
// Con los pares se calculan precisión, exhaustividad y F1. Además, de los ingredientes bien
// separados se mide si la cantidad (±0,01) y la unidad también son correctas.
const path = require('node:path');
const { parseRecipeText } = require('../src/ocr/parseRecipeText');
const { normalize } = require('../src/nutrition/normalize');
const { similarity, prf, pct, table, writeReport, today } = require('./lib/metrics');

const NAME_THRESHOLD = 0.8;
const STEP_THRESHOLD = 0.9;

const { cases } = require(path.join(__dirname, 'data', 'ocrSegmentationCases.json'));

const norm = (text) => normalize(text ?? '').replace(/,/g, ' ').replace(/\s+/g, ' ').trim();

/**
 * Empareja esperados con propuestos (voraz, por orden de los esperados y eligiendo el propuesto
 * libre más parecido). Devuelve los pares y los que se quedan sin pareja en cada lado.
 */
function align(expected, predicted, keyOf, threshold) {
  const free = new Set(predicted.map((_, i) => i));
  const pairs = [];
  const missed = [];
  for (const exp of expected) {
    let best = -1;
    let bestScore = threshold;
    for (const i of free) {
      const score = similarity(norm(keyOf(exp)), norm(keyOf(predicted[i])));
      if (score >= bestScore) {
        best = i;
        bestScore = score;
      }
    }
    if (best === -1) {
      missed.push(exp);
    } else {
      free.delete(best);
      pairs.push({ expected: exp, predicted: predicted[best], score: bestScore });
    }
  }
  return { pairs, missed, extra: [...free].map((i) => predicted[i]) };
}

const sameQuantity = (a, b) => (a == null && b == null) || (a != null && b != null && Math.abs(a - b) < 0.01);

function formatIngredient(ing) {
  const amount = ing.quantity == null ? 'sin cantidad' : `${String(ing.quantity).replace('.', ',')} ${ing.unit}`;
  return `«${ing.name}» (${amount})`;
}

function evaluateCase(testCase) {
  const parsed = parseRecipeText(testCase.rawText);
  const predictedIngredients = parsed.ingredients.map((i) => ({ name: i.rawName, quantity: i.quantity, unit: i.unit }));

  const ing = align(testCase.expectedIngredients, predictedIngredients, (i) => i.name, NAME_THRESHOLD);
  const steps = align(testCase.expectedSteps, parsed.steps, (s) => s, STEP_THRESHOLD);
  const amountOk = ing.pairs.filter((p) => sameQuantity(p.expected.quantity, p.predicted.quantity)
    && (p.expected.unit ?? null) === (p.predicted.unit ?? null));
  const titleOk = testCase.expectedTitle == null
    ? parsed.title == null
    : parsed.title != null && norm(parsed.title) === norm(testCase.expectedTitle);

  const errors = [
    ...ing.missed.map((i) => `ingrediente no detectado: ${formatIngredient(i)}`),
    ...ing.extra.map((i) => `ingrediente de más: ${formatIngredient(i)}`),
    ...ing.pairs.filter((p) => !amountOk.includes(p))
      .map((p) => `cantidad o unidad: esperado ${formatIngredient(p.expected)}, obtenido ${formatIngredient(p.predicted)}`),
    ...steps.missed.map((s) => `paso no detectado: «${s}»`),
    ...steps.extra.map((s) => `paso de más: «${s}»`),
    ...(titleOk ? [] : [`título: esperado «${testCase.expectedTitle ?? '(ninguno)'}», obtenido «${parsed.title ?? '(ninguno)'}»`]),
  ];

  return {
    id: testCase.id,
    origen: testCase.origen,
    ingredients: { tp: ing.pairs.length, expected: testCase.expectedIngredients.length, predicted: predictedIngredients.length },
    amountOk: amountOk.length,
    steps: { tp: steps.pairs.length, expected: testCase.expectedSteps.length, predicted: parsed.steps.length },
    titleOk,
    errors,
  };
}

/** Suma los recuentos de una lista de resultados y calcula las métricas agregadas (micro). */
function aggregate(results) {
  const sum = (f) => results.reduce((s, r) => s + f(r), 0);
  const ing = { tp: sum((r) => r.ingredients.tp), expected: sum((r) => r.ingredients.expected), predicted: sum((r) => r.ingredients.predicted) };
  const steps = { tp: sum((r) => r.steps.tp), expected: sum((r) => r.steps.expected), predicted: sum((r) => r.steps.predicted) };
  const all = { tp: ing.tp + steps.tp, expected: ing.expected + steps.expected, predicted: ing.predicted + steps.predicted };
  return {
    cases: results.length,
    ing, steps, all,
    ingPrf: prf(ing.tp, ing.predicted, ing.expected),
    stepsPrf: prf(steps.tp, steps.predicted, steps.expected),
    allPrf: prf(all.tp, all.predicted, all.expected),
    amountAccuracy: ing.tp === 0 ? NaN : sum((r) => r.amountOk) / ing.tp,
    strictIngRecall: ing.expected === 0 ? NaN : sum((r) => r.amountOk) / ing.expected,
    titleAccuracy: results.filter((r) => r.titleOk).length / results.length,
    perfect: results.filter((r) => r.errors.length === 0).length,
  };
}

function caseAccuracy(r) {
  return prf(r.ingredients.tp + r.steps.tp, r.ingredients.predicted + r.steps.predicted, r.ingredients.expected + r.steps.expected).f1;
}

function main() {
  const results = cases.map(evaluateCase);
  const global = aggregate(results);
  const real = aggregate(results.filter((r) => r.origen === 'real'));
  const synthetic = aggregate(results.filter((r) => r.origen !== 'real'));

  const summaryRow = (label, a) => [
    label, a.cases,
    pct(a.ingPrf.precision), pct(a.ingPrf.recall), pct(a.ingPrf.f1),
    pct(a.amountAccuracy),
    pct(a.stepsPrf.precision), pct(a.stepsPrf.recall), pct(a.stepsPrf.f1),
    pct(a.titleAccuracy), pct(a.allPrf.f1), `${a.perfect}/${a.cases}`,
  ];

  const md = `
# Evaluación de la segmentación del texto OCR (núcleo 3.1)

Generado el ${today()} con \`npm run eval:ocr-segmentacion\` sobre ${cases.length} casos de \`eval/data/ocrSegmentationCases.json\` (${real.cases} salidas reales de ML Kit y ${synthetic.cases} construidos para cubrir variantes de formato). Cada texto se pasa por \`parseRecipeText\`, la misma función que usa \`POST /recipes/parse-ocr\`.

Criterios: un ingrediente está bien separado si su nombre tiene una similitud de edición ≥ ${NAME_THRESHOLD.toString().replace('.', ',')} con el esperado (texto normalizado); un paso, si su texto la tiene ≥ ${STEP_THRESHOLD.toString().replace('.', ',')}. "Cantidad y unidad" es el porcentaje de ingredientes bien separados cuya cantidad y unidad también son correctas. "Acierto global" es el F1 de ingredientes y pasos juntos.

## Resultados agregados

${table(
    ['Conjunto', 'Casos', 'Ingr. precisión', 'Ingr. exhaustividad', 'Ingr. F1', 'Cantidad y unidad', 'Pasos precisión', 'Pasos exhaustividad', 'Pasos F1', 'Título', 'Acierto global (F1)', 'Casos perfectos'],
    [summaryRow('Todos', global), summaryRow('Reales (ML Kit)', real), summaryRow('Sintéticos', synthetic)],
  )}

- Ingredientes: ${global.ing.tp} bien separados de ${global.ing.expected} esperados (${global.ing.predicted} propuestos).
- Ingredientes completamente correctos (nombre, cantidad y unidad) sobre los esperados: ${pct(global.strictIngRecall)}.
- Pasos: ${global.steps.tp} bien separados de ${global.steps.expected} esperados (${global.steps.predicted} propuestos).

## Resultados por caso

${table(
    ['Caso', 'Origen', 'Ingredientes (ok/esp./prop.)', 'Cantidad y unidad ok', 'Pasos (ok/esp./prop.)', 'Título', 'Acierto (F1)'],
    results.map((r) => [
      r.id, r.origen,
      `${r.ingredients.tp}/${r.ingredients.expected}/${r.ingredients.predicted}`,
      `${r.amountOk}/${r.ingredients.tp}`,
      `${r.steps.tp}/${r.steps.expected}/${r.steps.predicted}`,
      r.titleOk ? 'sí' : 'no',
      pct(caseAccuracy(r)),
    ]),
  )}

## Errores encontrados

${results.filter((r) => r.errors.length > 0).map((r) => `**${r.id}**\n\n${r.errors.map((e) => `- ${e}`).join('\n')}`).join('\n\n') || 'Ninguno.'}
`;
  writeReport('ocrSegmentation', md);
}

main();
