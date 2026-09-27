// Objetivo calórico diario a partir de los datos de perfil.
//
// Se usa la ecuación de Mifflin-St Jeor (1990) para la tasa metabólica basal (TMB). Es la
// fórmula de referencia actual para adultos sanos: en las revisiones comparativas estima la
// TMB medida por calorimetría con menos error que la de Harris-Benedict (1919, revisada en
// 1984), que tiende a sobrestimarla. La TMB se multiplica por un factor de actividad (gasto
// energético total, TDEE) y se ajusta según el objetivo del usuario.
//
// Es una estimación estándar de población, no una recomendación médica personalizada: no
// tiene en cuenta la composición corporal, el embarazo, la lactancia ni ninguna patología.

const SEXES = ['M', 'F'];

const ACTIVITY_FACTORS = {
  sedentario: 1.2,
  ligero: 1.375,
  moderado: 1.55,
  activo: 1.725,
  muy_activo: 1.9,
};

// Déficit o superávit diario en kcal. −500 kcal/día equivale aproximadamente a perder
// 0,5 kg por semana, el ritmo que suelen recomendar las guías.
const GOAL_ADJUSTMENTS = {
  perder_peso: -500,
  mantener: 0,
  ganar_peso: 400,
};

const ACTIVITY_LEVELS = Object.keys(ACTIVITY_FACTORS);
const GOALS = Object.keys(GOAL_ADJUSTMENTS);

// Rangos admitidos al editar el perfil.
const LIMITS = {
  age: { min: 14, max: 100 },
  weightKg: { min: 30, max: 300 },
  heightCm: { min: 100, max: 250 },
};

/** Edad en años cumplidos en la fecha `today`. */
function ageFromBirthDate(birthDate, today = new Date()) {
  if (!(birthDate instanceof Date) || Number.isNaN(birthDate.getTime())) return null;
  let age = today.getUTCFullYear() - birthDate.getUTCFullYear();
  const beforeBirthday = today.getUTCMonth() < birthDate.getUTCMonth()
    || (today.getUTCMonth() === birthDate.getUTCMonth() && today.getUTCDate() < birthDate.getUTCDate());
  if (beforeBirthday) age -= 1;
  return age;
}

/** TMB en kcal/día según Mifflin-St Jeor. */
function mifflinStJeor({ weightKg, heightCm, age, sex }) {
  const base = 10 * weightKg + 6.25 * heightCm - 5 * age;
  return sex === 'M' ? base + 5 : base - 161;
}

/**
 * Calcula el objetivo a partir de un usuario (con birthDate como Date). Si falta algún dato
 * devuelve dailyCalorieGoal = null y la lista de campos que faltan, para que la app pueda
 * indicar qué rellenar.
 */
function calculateCalorieGoal(profile, today = new Date()) {
  const missingFields = [];
  if (!profile.birthDate) missingFields.push('birthDate');
  if (profile.heightCm == null) missingFields.push('heightCm');
  if (profile.weightKg == null) missingFields.push('weightKg');
  if (!SEXES.includes(profile.sex)) missingFields.push('sex');
  if (!(profile.activityLevel in ACTIVITY_FACTORS)) missingFields.push('activityLevel');
  if (!(profile.goal in GOAL_ADJUSTMENTS)) missingFields.push('goal');
  if (missingFields.length > 0) {
    return { dailyCalorieGoal: null, bmr: null, tdee: null, missingFields };
  }

  const age = ageFromBirthDate(profile.birthDate, today);
  const bmr = mifflinStJeor({ weightKg: profile.weightKg, heightCm: profile.heightCm, age, sex: profile.sex });
  const tdee = bmr * ACTIVITY_FACTORS[profile.activityLevel];
  const goal = tdee + GOAL_ADJUSTMENTS[profile.goal];
  return {
    dailyCalorieGoal: Math.round(goal),
    bmr: Math.round(bmr),
    tdee: Math.round(tdee),
    missingFields: [],
  };
}

module.exports = {
  SEXES,
  ACTIVITY_LEVELS,
  GOALS,
  ACTIVITY_FACTORS,
  GOAL_ADJUSTMENTS,
  LIMITS,
  ageFromBirthDate,
  mifflinStJeor,
  calculateCalorieGoal,
};
