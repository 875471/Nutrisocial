// Reparto objetivo de macronutrientes según el objetivo del usuario.
//
// Los porcentajes (sobre las kcal diarias) están dentro de los rangos aceptables de
// distribución de macronutrientes (AMDR) para adultos: 10-35 % proteína, 45-65 % hidratos y
// 20-35 % grasas. En déficit se sube la proteína al máximo del rango para ayudar a conservar
// masa muscular; para ganar peso se sube la parte de hidratos. Es una heurística general, no
// una pauta dietética personalizada.

const { calculateCalorieGoal } = require('./calorieGoal');

const MACRO_SPLITS = {
  perder_peso: { protein: 0.35, carbs: 0.35, fat: 0.30 },
  mantener: { protein: 0.25, carbs: 0.45, fat: 0.30 },
  ganar_peso: { protein: 0.25, carbs: 0.50, fat: 0.25 },
};

const DEFAULT_GOAL = 'mantener';

// Energía por gramo (factores de Atwater).
const KCAL_PER_GRAM = { protein: 4, carbs: 4, fat: 9 };

function round1(value) {
  return Math.round(value * 10) / 10;
}

/** Reparto (fracciones) del objetivo; con un objetivo nulo o desconocido, el de mantener. */
function macroSplitFor(goal) {
  return MACRO_SPLITS[goal] ?? MACRO_SPLITS[DEFAULT_GOAL];
}

/** Gramos objetivo de cada macronutriente para unas kcal diarias y un objetivo. */
function macroTargetsFromGoal(dailyCalorieGoal, goal) {
  const split = macroSplitFor(goal);
  return {
    proteinG: round1((dailyCalorieGoal * split.protein) / KCAL_PER_GRAM.protein),
    carbsG: round1((dailyCalorieGoal * split.carbs) / KCAL_PER_GRAM.carbs),
    fatG: round1((dailyCalorieGoal * split.fat) / KCAL_PER_GRAM.fat),
  };
}

/** Gramos objetivo del usuario, o null si su perfil no permite calcular el objetivo calórico. */
function getMacroTargets(user, today = new Date()) {
  const { dailyCalorieGoal } = calculateCalorieGoal(user, today);
  if (dailyCalorieGoal == null) return null;
  return macroTargetsFromGoal(dailyCalorieGoal, user.goal);
}

module.exports = { MACRO_SPLITS, KCAL_PER_GRAM, macroSplitFor, macroTargetsFromGoal, getMacroTargets };
