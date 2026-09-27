const express = require('express');
const prisma = require('../prismaClient');
const requireAuth = require('../middleware/auth');
const {
  SEXES, ACTIVITY_LEVELS, GOALS, LIMITS, ageFromBirthDate, calculateCalorieGoal,
} = require('../nutrition/calorieGoal');
const { parseDay, formatDay } = require('../utils/day');

const router = express.Router();
router.use(requireAuth);

const PROFILE_FIELDS = ['birthDate', 'heightCm', 'weightKg', 'sex', 'activityLevel', 'goal'];

// Perfil del usuario con el objetivo calórico calculado en el momento (la edad cambia con
// el tiempo, así que el objetivo no se guarda).
function toProfileResponse(user) {
  const { dailyCalorieGoal, bmr, tdee, missingFields } = calculateCalorieGoal(user);
  return {
    id: user.id,
    email: user.email,
    name: user.name,
    birthDate: user.birthDate ? formatDay(user.birthDate) : null,
    age: user.birthDate ? ageFromBirthDate(user.birthDate) : null,
    heightCm: user.heightCm,
    weightKg: user.weightKg,
    sex: user.sex,
    activityLevel: user.activityLevel,
    goal: user.goal,
    dailyCalorieGoal,
    bmr,
    tdee,
    missingFields,
  };
}

function inRange(value, { min, max }) {
  return typeof value === 'number' && Number.isFinite(value) && value >= min && value <= max;
}

// Valida solo los campos presentes en el cuerpo (actualización parcial); null borra el dato.
// Devuelve { data } con los valores a guardar o { error }.
function validateProfile(body) {
  const data = {};
  for (const field of PROFILE_FIELDS) {
    if (!(field in body)) continue;
    const value = body[field];
    if (value === null) {
      data[field] = null;
      continue;
    }
    switch (field) {
      case 'birthDate': {
        const date = parseDay(value);
        if (!date) return { error: 'La fecha de nacimiento debe tener el formato AAAA-MM-DD' };
        const age = ageFromBirthDate(date);
        if (!inRange(age, LIMITS.age)) {
          return { error: `La edad debe estar entre ${LIMITS.age.min} y ${LIMITS.age.max} años` };
        }
        data.birthDate = date;
        break;
      }
      case 'heightCm':
        if (!inRange(value, LIMITS.heightCm)) {
          return { error: `La altura debe estar entre ${LIMITS.heightCm.min} y ${LIMITS.heightCm.max} cm` };
        }
        data.heightCm = value;
        break;
      case 'weightKg':
        if (!inRange(value, LIMITS.weightKg)) {
          return { error: `El peso debe estar entre ${LIMITS.weightKg.min} y ${LIMITS.weightKg.max} kg` };
        }
        data.weightKg = value;
        break;
      case 'sex':
        if (!SEXES.includes(value)) return { error: 'El sexo debe ser "M" o "F"' };
        data.sex = value;
        break;
      case 'activityLevel':
        if (!ACTIVITY_LEVELS.includes(value)) {
          return { error: `Nivel de actividad no válido. Usa uno de: ${ACTIVITY_LEVELS.join(', ')}` };
        }
        data.activityLevel = value;
        break;
      case 'goal':
        if (!GOALS.includes(value)) return { error: `Objetivo no válido. Usa uno de: ${GOALS.join(', ')}` };
        data.goal = value;
        break;
      default:
        break;
    }
  }
  return { data };
}

router.get('/', async (req, res) => {
  const user = await prisma.user.findUnique({ where: { id: req.userId } });
  if (!user) return res.status(404).json({ error: 'Usuario no encontrado' });
  res.json(toProfileResponse(user));
});

router.put('/', async (req, res) => {
  const body = req.body;
  if (body === null || typeof body !== 'object' || Array.isArray(body)) {
    return res.status(400).json({ error: 'El cuerpo debe ser un objeto JSON' });
  }
  const { data, error } = validateProfile(body);
  if (error) return res.status(400).json({ error });

  const exists = await prisma.user.findUnique({ where: { id: req.userId }, select: { id: true } });
  if (!exists) return res.status(404).json({ error: 'Usuario no encontrado' });
  const user = await prisma.user.update({ where: { id: req.userId }, data });
  res.json(toProfileResponse(user));
});

module.exports = router;
module.exports.validateProfile = validateProfile;
