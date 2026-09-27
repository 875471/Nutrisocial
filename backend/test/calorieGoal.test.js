// Pruebas del objetivo calórico, del registro diario y de la validación del perfil.
const test = require('node:test');
const assert = require('node:assert/strict');
const { ageFromBirthDate, mifflinStJeor, calculateCalorieGoal } = require('../src/nutrition/calorieGoal');
const { macrosFromRecipe, macrosFromFood, sumTotals, compareWithGoal } = require('../src/nutrition/dailyLog');
const { parseDay, formatDay } = require('../src/utils/day');
const { validateProfile } = require('../src/routes/profile');

const TODAY = new Date(Date.UTC(2026, 8, 27));

test('edad en años cumplidos, antes y después del cumpleaños', () => {
  assert.equal(ageFromBirthDate(parseDay('1996-09-27'), TODAY), 30);
  assert.equal(ageFromBirthDate(parseDay('1996-09-28'), TODAY), 29);
  assert.equal(ageFromBirthDate(parseDay('1996-01-15'), TODAY), 30);
});

test('Mifflin-St Jeor: +5 en hombres y −161 en mujeres', () => {
  // 10·80 + 6,25·180 − 5·30 + 5 = 1780
  assert.equal(mifflinStJeor({ weightKg: 80, heightCm: 180, age: 30, sex: 'M' }), 1780);
  // 10·60 + 6,25·165 − 5·25 − 161 = 1345,25
  assert.equal(mifflinStJeor({ weightKg: 60, heightCm: 165, age: 25, sex: 'F' }), 1345.25);
});

test('objetivo = TMB × factor de actividad + ajuste por objetivo', () => {
  const man = { birthDate: parseDay('1996-01-01'), heightCm: 180, weightKg: 80, sex: 'M', activityLevel: 'moderado', goal: 'mantener' };
  assert.deepEqual(calculateCalorieGoal(man, TODAY), { dailyCalorieGoal: 2759, bmr: 1780, tdee: 2759, missingFields: [] });
  assert.equal(calculateCalorieGoal({ ...man, goal: 'ganar_peso' }, TODAY).dailyCalorieGoal, 3159);

  const woman = { birthDate: parseDay('2001-01-01'), heightCm: 165, weightKg: 60, sex: 'F', activityLevel: 'sedentario', goal: 'perder_peso' };
  // 1345,25 × 1,2 − 500 = 1114,3
  assert.equal(calculateCalorieGoal(woman, TODAY).dailyCalorieGoal, 1114);
});

test('sin datos suficientes no hay objetivo y se indica qué falta', () => {
  const r = calculateCalorieGoal({ heightCm: 170, sex: 'X', activityLevel: 'moderado' }, TODAY);
  assert.equal(r.dailyCalorieGoal, null);
  assert.deepEqual(r.missingFields, ['birthDate', 'weightKg', 'sex', 'goal']);
});

test('fechas de calendario: formato estricto y días reales', () => {
  assert.equal(formatDay(parseDay('2026-02-28')), '2026-02-28');
  for (const bad of ['2026-02-30', '2026-13-01', '27/09/2026', '2026-9-27', '', null]) {
    assert.equal(parseDay(bad), null, String(bad));
  }
});

test('validación del perfil: rangos, valores permitidos y actualización parcial', () => {
  assert.deepEqual(validateProfile({ weightKg: 70, heightCm: 175 }).data, { weightKg: 70, heightCm: 175 });
  assert.deepEqual(validateProfile({ goal: null }).data, { goal: null });
  assert.ok(validateProfile({ weightKg: 25 }).error);
  assert.ok(validateProfile({ weightKg: '70' }).error);
  assert.ok(validateProfile({ heightCm: 260 }).error);
  assert.ok(validateProfile({ sex: 'H' }).error);
  assert.ok(validateProfile({ activityLevel: 'mucho' }).error);
  assert.ok(validateProfile({ goal: 'definir' }).error);
  assert.ok(validateProfile({ birthDate: '1990-02-30' }).error);
  // Menos de 14 años o más de 100.
  const year = new Date().getUTCFullYear();
  assert.ok(validateProfile({ birthDate: `${year - 10}-01-01` }).error);
  assert.ok(validateProfile({ birthDate: `${year - 102}-01-01` }).error);
  assert.ok(validateProfile({ birthDate: `${year - 40}-06-15` }).data.birthDate instanceof Date);
});

test('valores de una entrada: raciones de receta y gramos de alimento', () => {
  // Receta completa de 4 raciones con 2000 kcal: 1,5 raciones = 750 kcal.
  const recipe = { servings: 4, kcal: 2000, protein: 80, carbs: 250, fat: 70 };
  assert.deepEqual(macrosFromRecipe(recipe, 1.5), { kcal: 750, protein: 30, carbs: 93.8, fat: 26.3 });
  const food = { kcal: 364, protein: 10.3, carbs: 76.3, fat: 1 };
  assert.deepEqual(macrosFromFood(food, 50), { kcal: 182, protein: 5.2, carbs: 38.2, fat: 0.5 });
});

test('totales del día y comparación con el objetivo', () => {
  const totals = sumTotals([
    { kcal: 750, protein: 30, carbs: 93.8, fat: 26.3 },
    { kcal: 182.4, protein: 5.2, carbs: 38.2, fat: 0.5 },
  ]);
  assert.deepEqual(totals, { kcal: 932, protein: 35.2, carbs: 132, fat: 26.8 });
  assert.deepEqual(sumTotals([]), { kcal: 0, protein: 0, carbs: 0, fat: 0 });

  assert.deepEqual(compareWithGoal(932, 2000), { dailyCalorieGoal: 2000, remainingKcal: 1068, excessKcal: 0, progress: 0.466 });
  assert.deepEqual(compareWithGoal(2300, 2000), { dailyCalorieGoal: 2000, remainingKcal: 0, excessKcal: 300, progress: 1.15 });
  assert.deepEqual(compareWithGoal(500, null), { dailyCalorieGoal: null, remainingKcal: null, excessKcal: null, progress: null });
});
