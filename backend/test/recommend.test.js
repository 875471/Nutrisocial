// Pruebas del reparto de macros objetivo y del recomendador de recetas.
const test = require('node:test');
const assert = require('node:assert/strict');
const { macroTargetsFromGoal, getMacroTargets } = require('../src/nutrition/macroTargets');
const { computeRemaining, rankRecipes, recommendRecipes } = require('../src/nutrition/recommend');
const { parseDay } = require('../src/utils/day');

const TODAY = new Date(Date.UTC(2026, 8, 27));
// Objetivo de 2759 kcal para mantener (ver calorieGoal.test.js).
const MAN = { birthDate: parseDay('1996-01-01'), heightCm: 180, weightKg: 80, sex: 'M', activityLevel: 'moderado', goal: 'mantener' };

// Receta de `servings` raciones con los gramos por ración indicados; kcal por factores de Atwater.
function recipe(id, title, { protein, carbs, fat }, servings = 1) {
  const kcal = protein * 4 + carbs * 4 + fat * 9;
  return { id, title, servings, kcal: kcal * servings, protein: protein * servings, carbs: carbs * servings, fat: fat * servings };
}

// Cliente de Prisma falso que registra si se consultó.
function fakeDb(recipes) {
  const db = { calls: 0, recipe: { findMany: async () => { db.calls += 1; return recipes; } } };
  return db;
}

test('reparto de macros por objetivo en gramos (4 kcal/g proteína e hidratos, 9 kcal/g grasa)', () => {
  assert.deepEqual(macroTargetsFromGoal(2000, 'mantener'), { proteinG: 125, carbsG: 225, fatG: 66.7 });
  assert.deepEqual(macroTargetsFromGoal(2000, 'perder_peso'), { proteinG: 175, carbsG: 175, fatG: 66.7 });
  assert.deepEqual(macroTargetsFromGoal(2000, 'ganar_peso'), { proteinG: 125, carbsG: 250, fatG: 55.6 });
  // Sin objetivo reconocido se usa el reparto de mantener.
  assert.deepEqual(macroTargetsFromGoal(2000, null), macroTargetsFromGoal(2000, 'mantener'));
  assert.deepEqual(macroTargetsFromGoal(2000, 'volumen'), macroTargetsFromGoal(2000, 'mantener'));
});

test('getMacroTargets parte del objetivo calórico y es null con el perfil incompleto', () => {
  assert.deepEqual(getMacroTargets(MAN, TODAY), macroTargetsFromGoal(2759, 'mantener'));
  assert.equal(getMacroTargets({ ...MAN, weightKg: null }, TODAY), null);
});

test('lo que queda no baja de 0 cuando un macro ya está cubierto', () => {
  const remaining = computeRemaining(2000, { proteinG: 125, carbsG: 225, fatG: 66.7 }, { kcal: 1500, protein: 140, carbs: 150, fat: 40 });
  assert.deepEqual(remaining, { kcal: 500, proteinG: 0, carbsG: 75, fatG: 26.7 });
});

test('perfil incompleto: sin recomendaciones, con los campos que faltan y sin consultar recetas', async () => {
  const db = fakeDb([recipe(1, 'Cualquiera', { protein: 20, carbs: 40, fat: 10 })]);
  const result = await recommendRecipes({
    user: { ...MAN, sex: null, goal: null }, consumedTotals: { kcal: 0, protein: 0, carbs: 0, fat: 0 }, today: TODAY, db,
  });
  assert.equal(result.reason, 'perfil_incompleto');
  assert.deepEqual(result.missingFields, ['sex', 'goal']);
  assert.deepEqual(result.recommendations, []);
  assert.equal(db.calls, 0);
});

test('objetivo cubierto: con 50 kcal o menos de margen no se recomienda nada', async () => {
  const db = fakeDb([recipe(1, 'Cualquiera', { protein: 5, carbs: 5, fat: 1 })]);
  for (const kcal of [2709, 2759, 3200]) {
    const result = await recommendRecipes({
      user: MAN, consumedTotals: { kcal, protein: 150, carbs: 300, fat: 90 }, today: TODAY, db,
    });
    assert.equal(result.reason, 'objetivo_cubierto');
    assert.deepEqual(result.recommendations, []);
  }
  assert.equal(db.calls, 0);
});

test('se descartan las raciones que pasan de lo que queda en más de un 15 %, las excluidas y las sin valores', async () => {
  // Quedan 600 kcal: 50 g proteína, 90 g hidratos y 26,7 g grasa.
  const consumedTotals = { kcal: 2159, protein: 122.4, carbs: 220.4, fat: 65.3 };
  const db = fakeDb([
    recipe(1, 'Se pasa (700 kcal)', { protein: 43.75, carbs: 78.75, fat: 23.33 }),
    recipe(2, 'Casi justa (680 kcal)', { protein: 42.5, carbs: 76.5, fat: 22.67 }),
    recipe(3, 'Ya comida hoy', { protein: 40, carbs: 70, fat: 20 }),
    { id: 4, title: 'Sin valores', servings: 2, kcal: 0, protein: 0, carbs: 0, fat: 0 },
  ]);
  const result = await recommendRecipes({ user: MAN, consumedTotals, excludeRecipeIds: [3], today: TODAY, db });

  assert.equal(result.reason, undefined);
  assert.deepEqual(result.remaining, { kcal: 600, proteinG: 50, carbsG: 90, fatG: 26.7 });
  assert.deepEqual(result.recommendations.map((r) => r.id), [2]);
  assert.match(result.recommendations[0].motivo, /^Te aporta 680 kcal y 42,5 g de proteína; se pasa solo 80 kcal/);
});

test('orden: primero lo que completa el día con un reparto parecido, luego lo desequilibrado y lo muy pequeño', () => {
  // Quedan 800 kcal repartidas 25 % proteína, 45 % hidratos y 30 % grasas.
  const remaining = { kcal: 800, proteinG: 50, carbsG: 90, fatG: 26.7 };
  const recipes = [
    recipe(1, 'Ligera equilibrada', { protein: 10, carbs: 18, fat: 5.3 }), // 160 kcal, < 30 %
    recipe(2, 'Casi toda grasa', { protein: 5, carbs: 10, fat: 76 }), // 744 kcal
    recipe(3, 'Media equilibrada', { protein: 25, carbs: 45, fat: 13.3 }), // 400 kcal
    recipe(4, 'Grande equilibrada', { protein: 45, carbs: 81, fat: 24 }, 2), // 720 kcal por ración
    recipe(5, 'Enorme', { protein: 60, carbs: 120, fat: 40 }), // 1080 kcal, se descarta
  ];
  const ranked = rankRecipes(recipes, remaining);

  assert.deepEqual(ranked.map((r) => r.id), [4, 3, 2, 1]);
  // Valores por ración, no de la receta completa.
  assert.deepEqual(
    { kcal: ranked[0].kcalPorRacion, p: ranked[0].proteinPorRacion, c: ranked[0].carbsPorRacion, f: ranked[0].fatPorRacion },
    { kcal: 720, p: 45, c: 81, f: 24 },
  );
  assert.equal(ranked[0].motivo, 'Te aporta 720 kcal y 45 g de proteína, casi justo lo que te queda hoy');
  assert.equal(ranked[1].motivo, 'Te aporta 400 kcal y 25 g de proteína, encaja con lo que te queda hoy');
  assert.match(ranked[3].motivo, /es ligera/);
});

test('como mucho 5 recomendaciones', () => {
  const recipes = Array.from({ length: 8 }, (_, i) => recipe(i + 1, `Receta ${i + 1}`, { protein: 20 + i, carbs: 40, fat: 10 }));
  assert.equal(rankRecipes(recipes, { kcal: 800, proteinG: 50, carbsG: 90, fatG: 26.7 }).length, 5);
});

test('con todos los macros cubiertos pero kcal pendientes se usa el reparto general', () => {
  const recipes = [recipe(1, 'Equilibrada', { protein: 25, carbs: 45, fat: 13.3 })];
  const ranked = rankRecipes(recipes, { kcal: 500, proteinG: 0, carbsG: 0, fatG: 0 }, { defaultShares: [0.25, 0.45, 0.3] });
  assert.equal(ranked.length, 1);
  assert.ok(ranked[0].score > 0.8);
});
