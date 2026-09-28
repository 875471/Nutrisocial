// Evaluación del núcleo 3.3: buscador de recetas por despensa, con precision@k.
//
//   npm run eval:despensa
//   npm run eval:despensa -- --simulaciones=200 --semilla=7 --distractores=10
//
// Simulación: se elige una receta objetivo al azar y se construye una despensa con una parte
// de sus ingredientes (100 %, 80 %, 60 % o 40 %) más algunos ingredientes de otras recetas que
// no le sirven. Se ejecuta matchRecipesToPantry (la misma función que GET /recipes/by-pantry)
// contra todas las recetas y se mira en qué posición aparece la objetivo. precision@k es la
// proporción de simulaciones en las que aparece entre las k primeras.
//
// Usa las recetas de la base de datos del .env (solo lectura) con sus ingredientes y el alimento
// que se les asoció al guardarlas, igual que la ruta. El generador aleatorio lleva semilla: cada
// ejecución con la misma semilla y las mismas recetas da exactamente el mismo resultado.
const path = require('node:path');
require('dotenv').config({ path: path.join(__dirname, '..', '.env'), quiet: true });

const prisma = require('../src/prismaClient');
const { matchRecipesToPantry, MIN_COVERAGE } = require('../src/nutrition/pantryMatch');
const { normalize } = require('../src/nutrition/normalize');
const { seededRandom, shuffle, pick, mean, pct, num, table, writeReport, today } = require('./lib/metrics');

function argValue(name, fallback) {
  const arg = process.argv.find((a) => a.startsWith(`--${name}=`));
  return arg ? Number(arg.split('=')[1]) : fallback;
}

const SIMULATIONS_PER_LEVEL = argValue('simulaciones', 100);
const SEED = argValue('semilla', 2026);
const COVERAGE_LEVELS = [1, 0.8, 0.6, 0.4];
const DISTRACTORS = argValue('distractores', 3);
const KS = [1, 5, 10];
// Solo recetas con al menos 3 ingredientes: con 1 o 2, "el 60 %" no se puede construir.
const MIN_INGREDIENTS = 3;

const key = (ing) => (ing.foodId != null ? `food:${ing.foodId}` : `name:${normalize(ing.name)}`);

/** Despensa simulada para la receta objetivo con la cobertura pedida. */
function buildPantry(target, allIngredients, coverage, random) {
  const count = Math.max(1, Math.round(coverage * target.ingredients.length));
  const kept = shuffle(target.ingredients, random).slice(0, count);
  // Ingredientes de otras recetas que no son ninguno de los de la objetivo (ni por alimento
  // asociado ni por nombre): hacen que la despensa no sea solo la receta y que haya competencia.
  const targetKeys = new Set(target.ingredients.map(key));
  const candidates = allIngredients.filter((ing) => !targetKeys.has(key(ing)));
  const distractors = [];
  const used = new Set();
  while (distractors.length < DISTRACTORS && used.size < candidates.length) {
    const ing = pick(candidates, random);
    if (used.has(key(ing))) continue;
    used.add(key(ing));
    distractors.push(ing);
  }
  return {
    items: [...kept, ...distractors].map((ing) => ({ name: ing.name, foodId: ing.foodId })),
    realCoverage: count / target.ingredients.length,
  };
}

async function main() {
  const recipes = await prisma.recipe.findMany({
    select: { id: true, title: true, ingredients: { select: { name: true, foodId: true } } },
  });
  const eligible = recipes.filter((r) => r.ingredients.length >= MIN_INGREDIENTS);
  if (eligible.length < 10) throw new Error(`Solo hay ${eligible.length} recetas con ${MIN_INGREDIENTS} o más ingredientes`);
  const allIngredients = recipes.flatMap((r) => r.ingredients);
  const random = seededRandom(SEED);

  const rows = [];
  for (const coverage of COVERAGE_LEVELS) {
    for (let i = 0; i < SIMULATIONS_PER_LEVEL; i++) {
      const target = pick(eligible, random);
      const pantry = buildPantry(target, allIngredients, coverage, random);
      // Mismas recetas y en el mismo orden que carga la ruta (sin orderBy): los empates de
      // cobertura se resuelven igual que en la app.
      const ranking = matchRecipesToPantry(pantry.items, recipes);
      const index = ranking.findIndex((r) => r.id === target.id);
      const rank = index === -1 ? null : index + 1;
      // Recetas que la objetivo tiene empatadas delante (misma cobertura y mismas faltas).
      const tiedAhead = index <= 0 ? 0 : ranking.slice(0, index).filter((r) =>
        r.coverage === ranking[index].coverage && r.missingIngredients.length === ranking[index].missingIngredients.length).length;
      rows.push({ coverage, realCoverage: pantry.realCoverage, rank, tiedAhead, results: ranking.length, pantrySize: pantry.items.length });
    }
  }

  const summarize = (set) => ({
    n: set.length,
    realCoverage: mean(set.map((r) => r.realCoverage)),
    ...Object.fromEntries(KS.map((k) => [`p${k}`, set.filter((r) => r.rank != null && r.rank <= k).length / set.length])),
    found: set.filter((r) => r.rank != null).length / set.length,
    mrr: mean(set.map((r) => (r.rank == null ? 0 : 1 / r.rank))),
    meanRank: mean(set.filter((r) => r.rank != null).map((r) => r.rank)),
    results: mean(set.map((r) => r.results)),
    tied: set.filter((r) => r.tiedAhead > 0).length / set.length,
  });
  const byLevel = COVERAGE_LEVELS.map((c) => ({ coverage: c, ...summarize(rows.filter((r) => r.coverage === c)) }));
  const all = summarize(rows);

  const row = (label, s) => [
    label, s.n, pct(s.realCoverage, 0), ...KS.map((k) => pct(s[`p${k}`])), pct(s.found), num(s.mrr, 3),
    num(s.meanRank), num(s.results), pct(s.tied),
  ];

  const md = `
# Evaluación del buscador por despensa (núcleo 3.3)

Generado el ${today()} con \`npm run eval:despensa${DISTRACTORS === 3 ? '' : ` -- --distractores=${DISTRACTORS}`}\`: ${rows.length} simulaciones (${SIMULATIONS_PER_LEVEL} por nivel de cobertura, semilla ${SEED}) sobre las ${recipes.length} recetas de la base de datos, de las que ${eligible.length} tienen ${MIN_INGREDIENTS} o más ingredientes y pueden ser objetivo. Cada despensa lleva los ingredientes elegidos de la receta objetivo más ${DISTRACTORS} ingredientes al azar de otras recetas que no le sirven.

precision@k es la proporción de simulaciones en las que la receta objetivo aparece entre las k primeras del resultado de \`matchRecipesToPantry\`. "Cobertura real" es la media de la fracción de ingredientes de la objetivo que había en la despensa (el porcentaje nominal se redondea al número entero de ingredientes). MRR es la media de 1/posición (0 si no aparece).

## Resultados

${table(
    ['Cobertura simulada', 'Simulaciones', 'Cobertura real', 'precision@1', 'precision@5', 'precision@10', 'Aparece en la lista', 'MRR', 'Posición media (si aparece)', 'Recetas devueltas (media)', 'Con empates delante'],
    [...byLevel.map((s) => row(pct(s.coverage, 0), s)), row('Todas', all)],
  )}

- La función descarta las recetas con menos del ${pct(MIN_COVERAGE, 0)} de ingredientes cubiertos (\`MIN_COVERAGE\`), así que con una cobertura real por debajo de ese umbral la receta objetivo no puede aparecer: es una decisión de diseño (informe 11), no un fallo del emparejamiento.
- "Con empates delante" es el porcentaje de simulaciones en las que otra receta con la misma cobertura y el mismo número de faltas aparece antes que la objetivo: su orden entre ellas no depende de la despensa.
`;
  writeReport(DISTRACTORS === 3 ? 'pantrySearch' : `pantrySearch-${DISTRACTORS}-distractores`, md);
}

main()
  .catch((err) => {
    console.error(err);
    process.exitCode = 1;
  })
  .finally(() => prisma.$disconnect());
