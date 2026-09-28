// Evaluación del núcleo 3.4: ¿acerca el recomendador el día al objetivo más que elegir al azar?
//
//   npm run eval:recomendador
//   npm run eval:recomendador -- --simulaciones=500 --semilla=7
//
// Cada simulación es un día de un usuario ficticio:
//   1. Perfil aleatorio razonable (sexo, edad, altura, IMC entre 19 y 32, nivel de actividad y
//      objetivo) y su objetivo diario con calorieGoal.js y macroTargets.js.
//   2. Recetas ya registradas ese día (una ración de cada una), en dos escenarios:
//      - "Siguiente comida" (el pedido en la propuesta): 1 o 2 recetas al azar, dejando al menos
//        300 kcal de margen.
//      - "Cierre del día" (complementario): recetas al azar hasta que quedan entre 300 y 900 kcal,
//        es decir, falta una sola comida. Ahí una receta sí puede cerrar el día, y se ve si el
//        motor elige la que encaja o solo la más grande.
//   3. Se elige la siguiente receta con dos estrategias:
//      a) la primera recomendación del motor real (recommendRecipes, la función de
//         GET /log/recommendations, con las recetas de la base de datos inyectadas);
//      b) una receta al azar entre las que no se pasan de las kcal que quedan. En lugar de un solo
//         sorteo se usa la media de TODAS esas candidatas, que es el valor esperado de elegir al
//         azar y no depende de la suerte de un sorteo.
//   4. Se mide la desviación final respecto al objetivo tras añadir la receta: en kcal
//      (|consumido − objetivo|) y en macros (suma de |g consumidos − g objetivo| de proteínas,
//      hidratos y grasas).
//
// Lee las recetas de la base de datos del .env (solo lectura). Generador aleatorio con semilla.
const path = require('node:path');
require('dotenv').config({ path: path.join(__dirname, '..', '.env'), quiet: true });

const prisma = require('../src/prismaClient');
const { recommendRecipes } = require('../src/nutrition/recommend');
const { calculateCalorieGoal, ACTIVITY_LEVELS, GOALS } = require('../src/nutrition/calorieGoal');
const { macroTargetsFromGoal } = require('../src/nutrition/macroTargets');
const { seededRandom, pick, shuffle, mean, median, pct, num, table, writeReport, today } = require('./lib/metrics');

function argValue(name, fallback) {
  const arg = process.argv.find((a) => a.startsWith(`--${name}=`));
  return arg ? Number(arg.split('=')[1]) : fallback;
}

const SIMULATIONS = argValue('simulaciones', 200);
const SEED = argValue('semilla', 2026);
const MAX_REDRAWS = 50;
// Fecha fija para calcular las edades: el resultado no cambia según el día en que se ejecute.
const TODAY = new Date('2026-09-29T00:00:00Z');

const GOAL_LABELS = { perder_peso: 'Perder peso', mantener: 'Mantener', ganar_peso: 'Ganar peso' };

const between = (random, min, max) => min + random() * (max - min);

/** Perfil adulto aleatorio con valores dentro de los rangos que admite la app. */
function randomProfile(random) {
  const sex = random() < 0.5 ? 'M' : 'F';
  const age = Math.floor(between(random, 18, 66));
  const heightCm = Math.round(sex === 'M' ? between(random, 163, 192) : between(random, 152, 180));
  const bmi = between(random, 19, 32);
  const weightKg = Math.round(bmi * (heightCm / 100) ** 2 * 10) / 10;
  return {
    sex, heightCm, weightKg,
    birthDate: new Date(Date.UTC(TODAY.getUTCFullYear() - age, Math.floor(random() * 12), 1 + Math.floor(random() * 28))),
    activityLevel: pick(ACTIVITY_LEVELS, random),
    goal: pick(GOALS, random),
  };
}

const perServing = (r) => ({ kcal: r.kcal / r.servings, protein: r.protein / r.servings, carbs: r.carbs / r.servings, fat: r.fat / r.servings });
const add = (a, b) => ({ kcal: a.kcal + b.kcal, protein: a.protein + b.protein, carbs: a.carbs + b.carbs, fat: a.fat + b.fat });
const ZERO = { kcal: 0, protein: 0, carbs: 0, fat: 0 };

/** Desviación del día respecto al objetivo: kcal absolutas y gramos de macros (suma de absolutos). */
function deviation(consumed, goalKcal, targets) {
  return {
    kcal: Math.abs(consumed.kcal - goalKcal),
    macros: Math.abs(consumed.protein - targets.proteinG) + Math.abs(consumed.carbs - targets.carbsG) + Math.abs(consumed.fat - targets.fatG),
  };
}

// Cómo se eligen las recetas ya registradas en cada escenario. Devuelven la lista o null si el
// sorteo no cumple las condiciones (entonces se repite).
const SCENARIOS = {
  siguiente: {
    title: 'Escenario «siguiente comida» (1 o 2 recetas ya registradas)',
    draw(recipes, goalKcal, random) {
      const count = random() < 0.5 ? 1 : 2;
      const chosen = shuffle(recipes, random).slice(0, count);
      const kcal = chosen.map(perServing).reduce(add, ZERO).kcal;
      return goalKcal - kcal >= 300 ? chosen : null;
    },
  },
  cierre: {
    title: 'Escenario «cierre del día» (quedan entre 300 y 900 kcal)',
    draw(recipes, goalKcal, random) {
      const chosen = [];
      let kcal = 0;
      for (const recipe of shuffle(recipes, random)) {
        if (goalKcal - kcal <= 900) break;
        chosen.push(recipe);
        kcal += perServing(recipe).kcal;
      }
      const remaining = goalKcal - kcal;
      return remaining >= 300 && remaining <= 900 ? chosen : null;
    },
  },
};

async function simulate(scenario, recipes, random) {
  // recommendRecipes lee las recetas de la base de datos que se le pase: se le da una que ya
  // tiene cargadas las mismas filas, para no repetir la misma consulta en cada día simulado.
  const db = { recipe: { findMany: async () => recipes } };
  const byId = new Map(recipes.map((r) => [r.id, r]));
  const sims = [];
  let skipped = 0;
  while (sims.length < SIMULATIONS) {
    const user = randomProfile(random);
    const { dailyCalorieGoal } = calculateCalorieGoal(user, TODAY);
    const targets = macroTargetsFromGoal(dailyCalorieGoal, user.goal);

    let logged = null;
    for (let attempt = 0; attempt < MAX_REDRAWS && !logged; attempt++) {
      logged = scenario.draw(recipes, dailyCalorieGoal, random);
    }
    if (!logged) {
      skipped++;
      continue;
    }
    const consumed = logged.map(perServing).reduce(add, ZERO);
    const excludeRecipeIds = logged.map((r) => r.id);
    const remainingKcal = dailyCalorieGoal - consumed.kcal;

    // a) Motor real.
    const rec = await recommendRecipes({ user, consumedTotals: consumed, excludeRecipeIds, today: TODAY, db });
    const top = rec.recommendations[0];
    // b) Aleatorio entre las que caben en lo que queda.
    const candidates = recipes.filter((r) => !excludeRecipeIds.includes(r.id) && perServing(r).kcal <= remainingKcal);
    if (!top || candidates.length === 0) {
      skipped++;
      continue;
    }

    const devOf = (recipe) => deviation(add(consumed, perServing(recipe)), dailyCalorieGoal, targets);
    const candidateDevs = candidates.map(devOf);
    sims.push({
      goal: user.goal,
      dailyCalorieGoal,
      remainingKcal,
      logged: logged.length,
      before: deviation(consumed, dailyCalorieGoal, targets),
      rec: devOf(byId.get(top.id)),
      rand: { kcal: mean(candidateDevs.map((d) => d.kcal)), macros: mean(candidateDevs.map((d) => d.macros)) },
      // Referencia: la mejor receta posible a posteriori, mirando solo las kcal (cota inferior).
      oracleKcal: Math.min(...recipes.filter((r) => !excludeRecipeIds.includes(r.id)).map((r) => devOf(r).kcal)),
      recOvershoots: perServing(byId.get(top.id)).kcal > remainingKcal,
      candidates: candidates.length,
    });
  }
  return { sims, skipped };
}

// Reducción de la desviación: absoluta (media aleatoria − media recomendador) y relativa
// (1 − media recomendador / media aleatoria).
function summarize(set) {
  const m = (f) => mean(set.map(f));
  const out = {
    n: set.length, goal: m((s) => s.dailyCalorieGoal), remaining: m((s) => s.remainingKcal),
    candidates: m((s) => s.candidates), logged: m((s) => s.logged),
  };
  for (const dim of ['kcal', 'macros']) {
    const rec = m((s) => s.rec[dim]);
    const rand = m((s) => s.rand[dim]);
    out[dim] = {
      before: m((s) => s.before[dim]), rec, rand,
      absReduction: rand - rec,
      relReduction: 1 - rec / rand,
      medianRelReduction: median(set.map((s) => 1 - s.rec[dim] / s.rand[dim])),
      wins: set.filter((s) => s.rec[dim] < s.rand[dim]).length / set.length,
    };
  }
  out.oracleKcal = m((s) => s.oracleKcal);
  out.overshoot = set.filter((s) => s.recOvershoots).length / set.length;
  return out;
}

function scenarioSection(scenario, { sims, skipped }, recipeCount) {
  const groups = [...GOALS.map((g) => [GOAL_LABELS[g], sims.filter((s) => s.goal === g)]), ['Todos', sims]]
    .map(([label, set]) => [label, summarize(set)]);
  const total = groups[groups.length - 1][1];
  return `
## ${scenario.title}

${sims.length} días simulados (${skipped} perfiles descartados por no poder construir el escenario o porque ninguna receta cabía en el margen). Recetas ya registradas por día: ${num(total.logged)} de media.

### Desviación en kcal

${table(
    ['Objetivo', 'Días', 'Objetivo medio (kcal)', 'Margen antes (kcal)', 'Aleatoria', 'Recomendador', 'Reducción (kcal)', 'Reducción relativa', 'Gana', 'Mejor posible'],
    groups.map(([label, s]) => [
      label, s.n, num(s.goal, 0), num(s.remaining, 0), num(s.kcal.rand, 0), num(s.kcal.rec, 0),
      num(s.kcal.absReduction, 0), pct(s.kcal.relReduction), pct(s.kcal.wins), num(s.oracleKcal, 0),
    ]),
  )}

### Desviación en macronutrientes (g de proteínas + hidratos + grasas)

${table(
    ['Objetivo', 'Días', 'Desviación antes', 'Aleatoria', 'Recomendador', 'Reducción (g)', 'Reducción relativa', 'Gana'],
    groups.map(([label, s]) => [
      label, s.n, num(s.macros.before, 0), num(s.macros.rand, 0), num(s.macros.rec, 0),
      num(s.macros.absReduction, 0), pct(s.macros.relReduction), pct(s.macros.wins),
    ]),
  )}

- Mediana de la reducción relativa por día: kcal ${pct(total.kcal.medianRelReduction)}, macros ${pct(total.macros.medianRelReduction)}.
- La primera recomendación se pasa de las kcal que quedaban en el ${pct(total.overshoot)} de los días (el motor admite pasarse hasta un 15 %; la estrategia aleatoria, por definición, nunca se pasa).
- "Mejor posible": desviación en kcal de la receta que mejor habría cerrado el día mirando solo las kcal, una cota de lo alcanzable con estas ${recipeCount} recetas y una sola ración.
- Candidatas medias por día para la estrategia aleatoria: ${num(total.candidates)}.
`;
}

async function main() {
  const recipes = (await prisma.recipe.findMany({
    select: { id: true, title: true, servings: true, kcal: true, protein: true, carbs: true, fat: true },
  })).filter((r) => r.servings > 0 && r.kcal > 0);
  const random = seededRandom(SEED);

  const sections = [];
  for (const scenario of Object.values(SCENARIOS)) {
    sections.push(scenarioSection(scenario, await simulate(scenario, recipes, random), recipes.length));
  }

  const md = `
# Evaluación del recomendador nutricional (núcleo 3.4)

Generado el ${today()} con \`npm run eval:recomendador\`: ${SIMULATIONS} días simulados por escenario (semilla ${SEED}) con las ${recipes.length} recetas de la base de datos que tienen valores nutricionales.

Desviación final = distancia entre lo consumido en el día (recetas ya registradas + la elegida) y el objetivo. "Aleatoria" es el valor esperado de elegir al azar entre las recetas que no se pasan de las kcal que quedan (media de todas ellas); "Recomendador", la primera recomendación de \`recommendRecipes\`. La reducción relativa es 1 − (desviación media del recomendador / desviación media aleatoria). "Gana" es el porcentaje de días en que la desviación del recomendador es menor que la esperada al azar.
${sections.join('\n')}`;
  writeReport('recommenderSimulation', md);
}

main()
  .catch((err) => {
    console.error(err);
    process.exitCode = 1;
  })
  .finally(() => prisma.$disconnect());
