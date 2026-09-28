// Evaluación del núcleo 3.2: exactitud del cálculo de macronutrientes. Para cada receta de
// data/macroReferenceRecipes.json se ejecuta el MISMO cálculo que POST /recipes
// (resolveIngredients: matchFood + conversión de unidades + suma) y se compara, por ración, con
// un valor de referencia calculado aparte (ver el LEEME del JSON).
//
//   npm run eval:macros                          # todas las recetas (avisa si hay sin verificar)
//   npm run eval:macros -- --solo-verificadas    # solo las marcadas "verified": true
//   npm run eval:macros -- --con-off             # permite consultar Open Food Facts
//
// Lee la tabla Food de la base de datos del .env (solo lectura). Por defecto Open Food Facts está
// desactivado, como en prisma/seedDemoData.js: así el resultado es reproducible y la evaluación
// no guarda productos nuevos en Food. Un ingrediente sin alimento en BEDCA cuenta como 0.
const path = require('node:path');
require('dotenv').config({ path: path.join(__dirname, '..', '.env') });

const ALLOW_OFF = process.argv.includes('--con-off');
const ONLY_VERIFIED = process.argv.includes('--solo-verificadas');
if (!ALLOW_OFF) {
  globalThis.fetch = async () => { throw new Error('Open Food Facts desactivado en la evaluación'); };
}

const prisma = require('../src/prismaClient');
const { resolveIngredients } = require('../src/nutrition/calculate');
const { findFoodById } = require('../src/nutrition/matchFood');
const { mean, median, pct, num, table, writeReport, today } = require('./lib/metrics');

const MACROS = ['kcal', 'protein', 'carbs', 'fat'];
const LABELS = { kcal: 'Energía (kcal)', protein: 'Proteínas (g)', carbs: 'Hidratos (g)', fat: 'Grasas (g)' };

const data = require(path.join(__dirname, 'data', 'macroReferenceRecipes.json'));

/** Totales de referencia de la receta completa: los escritos a mano o la suma del desglose. */
function referenceTotals(recipe) {
  if (recipe.referenceTotals) return recipe.referenceTotals;
  const totals = { kcal: 0, protein: 0, carbs: 0, fat: 0 };
  for (const { food, grams } of recipe.referenceBreakdown) {
    const per100 = data.referenceFoods[food];
    if (!per100) throw new Error(`"${food}" (receta ${recipe.id}) no está en referenceFoods`);
    for (const key of MACROS) totals[key] += (per100[key] * grams) / 100;
  }
  return totals;
}

// Un alimento es incoherente si sus kcal se alejan de las que dan sus macros con los factores de
// Atwater (4·P + 4·H + 9·G) más de un 30 % y más de 40 kcal/100 g. En alimentos sin alcohol eso
// indica un error en el dato de origen, no en el cálculo de la app (en las bebidas alcohólicas la
// diferencia es normal: el alcohol aporta 7 kcal/g y no está en los macros).
function isIncoherent(food) {
  const atwater = 4 * food.protein + 4 * food.carbs + 9 * food.fat;
  return Math.abs(atwater - food.kcal) > Math.max(40, 0.3 * food.kcal);
}

const perServing = (totals, servings) => Object.fromEntries(MACROS.map((k) => [k, totals[k] / servings]));

async function evaluateRecipe(recipe) {
  // Mismo formato que valida cleanIngredients en POST /recipes, sin foodId elegido a mano.
  const input = recipe.ingredients.map((i) => ({ name: i.name, quantity: i.quantity, unit: i.unit, foodId: null }));
  const { ingredients, totals } = await resolveIngredients(input);
  const app = perServing(totals, recipe.servings);
  const ref = perServing(referenceTotals(recipe), recipe.servings);
  const details = await Promise.all(ingredients.map(async (ing) => {
    const food = ing.foodId == null ? null : await findFoodById(ing.foodId);
    return { name: ing.name, food: food?.name ?? null, grams: ing.grams, incoherent: food != null && isIncoherent(food), foodRow: food };
  }));
  return {
    id: recipe.id,
    title: recipe.title,
    servings: recipe.servings,
    verified: recipe.verified === true,
    app,
    ref,
    absError: Object.fromEntries(MACROS.map((k) => [k, Math.abs(app[k] - ref[k])])),
    // Error relativo sobre la referencia; sin sentido si la referencia es casi 0 (p. ej. grasa de
    // una receta sin grasa), así que entonces no se calcula.
    pctError: Object.fromEntries(MACROS.map((k) => [k, ref[k] >= 1 ? Math.abs(app[k] - ref[k]) / ref[k] : NaN])),
    signedPct: (app.kcal - ref.kcal) / ref.kcal,
    uncounted: details.filter((d) => d.food == null || d.grams == null).map((d) => d.name),
    incoherentFoods: details.filter((d) => d.incoherent).map((d) => d.foodRow),
    details,
  };
}

async function main() {
  const selected = data.recipes.filter((r) => !ONLY_VERIFIED || r.verified === true);
  if (selected.length === 0) {
    console.error('No hay recetas verificadas: marca con "verified": true las que hayas revisado.');
    process.exitCode = 1;
    return;
  }
  const unverified = selected.filter((r) => r.verified !== true).length;
  const results = [];
  for (const recipe of selected) results.push(await evaluateRecipe(recipe));

  const finite = (values) => values.filter(Number.isFinite);
  const summaryRows = (set) => MACROS.map((k) => [
    LABELS[k],
    num(mean(set.map((r) => r.absError[k]))),
    num(median(set.map((r) => r.absError[k]))),
    pct(mean(finite(set.map((r) => r.pctError[k])))),
    pct(median(finite(set.map((r) => r.pctError[k])))),
  ]);
  const overallPct = (set) => mean(MACROS.map((k) => mean(finite(set.map((r) => r.pctError[k])))));
  const header = ['Macronutriente', 'Error medio absoluto', 'Mediana del error absoluto', 'Error medio porcentual', 'Mediana del error porcentual'];

  // Recetas con algún alimento de datos incoherentes: se da también el error sin ellas, para
  // separar los errores del dato de origen de los del método de cálculo.
  const affected = results.filter((r) => r.incoherentFoods.length > 0);
  const clean = results.filter((r) => r.incoherentFoods.length === 0);
  const incoherentFoods = [...new Map(affected.flatMap((r) => r.incoherentFoods).map((f) => [f.id, f])).values()];
  const foodLine = (f) => {
    const users = affected.filter((r) => r.incoherentFoods.some((x) => x.id === f.id)).map((r) => r.title);
    return `- «${f.name}» (${f.source}): ${num(f.kcal)} kcal/100 g, pero sus macros suman ${num(4 * f.protein + 4 * f.carbs + 9 * f.fat)} kcal (4·P + 4·H + 9·G). Afecta a: ${users.join(', ')}.`;
  };
  const cleanSection = affected.length === 0 || clean.length === 0 ? '' : `
### Sin las recetas con un alimento incoherente en la tabla Food

${incoherentFoods.map(foodLine).join('\n')}

Es un error del dato de origen, no del cálculo. Sin ${affected.length === 1 ? 'esa receta' : 'esas recetas'} (${clean.length} recetas):

${table(header, summaryRows(clean))}

- Error porcentual medio de los cuatro macronutrientes: **${pct(overallPct(clean))}**.
`;
  const within = (limit) => results.filter((r) => Math.abs(r.signedPct) <= limit).length;

  const warning = unverified > 0
    ? `> **Resultados PROVISIONALES.** ${unverified} de las ${selected.length} recetas tienen valores de referencia sin verificar (borrador escrito con valores típicos de tablas, ver el LEEME de \`eval/data/macroReferenceRecipes.json\`). No citar estos números en la memoria hasta revisar las referencias y volver a ejecutar con \`--solo-verificadas\`.\n`
    : '> Todas las recetas evaluadas tienen la referencia verificada.\n';

  const md = `
# Evaluación del cálculo de macronutrientes (núcleo 3.2)

Generado el ${today()} con \`npm run eval:macros${ONLY_VERIFIED ? ' -- --solo-verificadas' : ''}\` sobre ${selected.length} recetas de \`eval/data/macroReferenceRecipes.json\`. Open Food Facts ${ALLOW_OFF ? 'activado' : 'desactivado (solo BEDCA)'}. Valores **por ración**.

${warning}
## Error por macronutriente

${table(header, summaryRows(results))}

- Error porcentual medio de los cuatro macronutrientes: **${pct(overallPct(results))}**.
- Recetas con las kcal por ración a ±10 % de la referencia: ${within(0.1)}/${results.length}; a ±20 %: ${within(0.2)}/${results.length}.
- Sesgo medio de las kcal (positivo = la app da más que la referencia): ${pct(mean(results.map((r) => r.signedPct)))}.
- El error porcentual no se calcula cuando la referencia es menor de 1 (kcal o g), para no dividir por casi cero.
${cleanSection}
## Resultados por receta (por ración)

${table(
    ['Receta', 'Verificada', 'kcal app / ref.', 'Error kcal', 'Proteínas app / ref.', 'Hidratos app / ref.', 'Grasas app / ref.', 'Sin contar'],
    results.map((r) => [
      r.title, r.verified ? 'sí' : 'no',
      `${num(r.app.kcal, 0)} / ${num(r.ref.kcal, 0)}`,
      pct(r.signedPct),
      `${num(r.app.protein)} / ${num(r.ref.protein)}`,
      `${num(r.app.carbs)} / ${num(r.ref.carbs)}`,
      `${num(r.app.fat)} / ${num(r.ref.fat)}`,
      r.uncounted.join(', ') || '—',
    ]),
  )}

## Emparejamiento de cada ingrediente

Alimento de la tabla Food elegido por \`matchFood\` y gramos estimados por la conversión de unidades, para localizar el origen de cada error.

${results.map((r) => `**${r.title}**\n\n${table(['Ingrediente', 'Alimento asociado', 'Gramos (app)'],
    r.details.map((d) => [d.name, `${d.food ?? '(ninguno)'}${d.incoherent ? ' (dato incoherente)' : ''}`, d.grams == null ? '—' : num(d.grams, 0)]))}`).join('\n\n')}
`;
  writeReport('macroAccuracy', md);
}

main()
  .catch((err) => {
    console.error(err);
    process.exitCode = 1;
  })
  .finally(() => prisma.$disconnect());
