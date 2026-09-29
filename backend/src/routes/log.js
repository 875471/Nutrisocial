const express = require('express');
const prisma = require('../prismaClient');
const requireAuth = require('../middleware/auth');
const { findFoodById } = require('../nutrition/matchFood');
const { calculateCalorieGoal } = require('../nutrition/calorieGoal');
const {
  LOG_LIMITS, macrosFromRecipe, macrosFromFood, sumTotals, compareWithGoal, compareMacrosWithGoals, calendarDays,
} = require('../nutrition/dailyLog');
const { getMacroTargets } = require('../nutrition/macroTargets');
const { recommendRecipes } = require('../nutrition/recommend');
const { parseDay, parseMonth, formatDay } = require('../utils/day');

const router = express.Router();
router.use(requireAuth);

function toEntryResponse(entry) {
  return {
    id: entry.id,
    date: formatDay(entry.date),
    type: entry.grams != null ? 'food' : 'recipe',
    name: entry.name,
    recipeId: entry.recipeId,
    foodId: entry.foodId,
    servings: entry.servings,
    grams: entry.grams,
    kcal: Math.round(entry.kcal),
    protein: entry.protein,
    carbs: entry.carbs,
    fat: entry.fat,
    createdAt: entry.createdAt,
  };
}

function inRange(value, { min, max }) {
  return typeof value === 'number' && Number.isFinite(value) && value >= min && value <= max;
}

// Crea una entrada a partir de una receta (recipeId + servings) o de un alimento suelto
// (foodId + grams). Los valores se calculan ahora y se guardan: si la receta cambia o se
// borra después, el histórico no se altera.
router.post('/', async (req, res) => {
  const { date, recipeId, servings, foodId, grams } = req.body ?? {};

  const day = parseDay(date);
  if (!day) return res.status(400).json({ error: 'La fecha debe tener el formato AAAA-MM-DD' });

  const fromRecipe = recipeId != null;
  const fromFood = foodId != null;
  if (fromRecipe === fromFood) {
    return res.status(400).json({ error: 'Indica una receta (recipeId y servings) o un alimento (foodId y grams)' });
  }

  let data;
  if (fromRecipe) {
    if (!Number.isInteger(recipeId)) return res.status(400).json({ error: 'Id de receta no válido' });
    if (!inRange(servings, LOG_LIMITS.servings)) {
      return res.status(400).json({
        error: `Las raciones deben estar entre ${LOG_LIMITS.servings.min} y ${LOG_LIMITS.servings.max}`,
      });
    }
    // Igual que GET /recipes/:id, cualquier receta existente se puede registrar.
    const recipe = await prisma.recipe.findUnique({ where: { id: recipeId } });
    if (!recipe) return res.status(404).json({ error: 'Receta no encontrada' });
    data = { name: recipe.title, recipeId, servings, ...macrosFromRecipe(recipe, servings) };
  } else {
    if (!Number.isInteger(foodId)) return res.status(400).json({ error: 'Id de alimento no válido' });
    if (!inRange(grams, LOG_LIMITS.grams)) {
      return res.status(400).json({
        error: `Los gramos deben estar entre ${LOG_LIMITS.grams.min} y ${LOG_LIMITS.grams.max}`,
      });
    }
    const food = await findFoodById(foodId);
    if (!food) return res.status(404).json({ error: 'Alimento no encontrado' });
    data = { name: food.name, foodId, grams, ...macrosFromFood(food, grams) };
  }

  const entry = await prisma.logEntry.create({ data: { ...data, userId: req.userId, date: day } });
  res.status(201).json(toEntryResponse(entry));
});

// Usuario y entradas del día indicado en ?date=. Si falla, ya ha respondido y devuelve null.
async function loadDay(req, res) {
  const day = parseDay(req.query.date);
  if (!day) {
    res.status(400).json({ error: 'Indica la fecha con ?date=AAAA-MM-DD' });
    return null;
  }
  const [user, entries] = await Promise.all([
    prisma.user.findUnique({ where: { id: req.userId } }),
    prisma.logEntry.findMany({
      where: { userId: req.userId, date: day },
      orderBy: { createdAt: 'asc' },
    }),
  ]);
  if (!user) {
    res.status(404).json({ error: 'Usuario no encontrado' });
    return null;
  }
  return { day, user, entries };
}

// Entradas de un día, totales y comparación con los objetivos del perfil: kcal y gramos de
// proteína, hidratos y grasas (los mismos objetivos que usa el recomendador).
router.get('/', async (req, res) => {
  const loaded = await loadDay(req, res);
  if (!loaded) return;
  const { day, user, entries } = loaded;

  const totals = sumTotals(entries);
  const { dailyCalorieGoal, missingFields } = calculateCalorieGoal(user);
  res.json({
    date: formatDay(day),
    entries: entries.map(toEntryResponse),
    totals,
    ...compareWithGoal(totals.kcal, dailyCalorieGoal),
    ...compareMacrosWithGoals(totals, getMacroTargets(user)),
    missingProfileFields: missingFields,
  });
});

// Recetas recomendadas para completar el día, sin repetir las ya registradas ese día.
router.get('/recommendations', async (req, res) => {
  const loaded = await loadDay(req, res);
  if (!loaded) return;
  const { day, user, entries } = loaded;

  const excludeRecipeIds = [...new Set(entries.map((entry) => entry.recipeId).filter((id) => id != null))];
  const { remaining, recommendations, reason, missingFields } = await recommendRecipes({
    user, consumedTotals: sumTotals(entries), excludeRecipeIds,
  });
  res.json({
    date: formatDay(day),
    remaining,
    recommendations,
    ...(reason && { reason }),
    ...(missingFields && { missingProfileFields: missingFields }),
  });
});

// Calendario de un mes (?month=AAAA-MM): kcal de cada día con alguna entrada y su estado frente
// al objetivo calórico. Se compara con el objetivo ACTUAL del perfil, no con el que tuviera el
// usuario ese día: no se guarda un histórico de objetivos (ver informe 16).
router.get('/calendar', async (req, res) => {
  const month = parseMonth(req.query.month);
  if (!month) return res.status(400).json({ error: 'Indica el mes con ?month=AAAA-MM' });

  const [user, entries] = await Promise.all([
    prisma.user.findUnique({ where: { id: req.userId } }),
    prisma.logEntry.findMany({
      where: { userId: req.userId, date: { gte: month.start, lt: month.end } },
      select: { date: true, kcal: true },
    }),
  ]);
  if (!user) return res.status(404).json({ error: 'Usuario no encontrado' });

  const { dailyCalorieGoal, missingFields } = calculateCalorieGoal(user);
  res.json({
    month: req.query.month,
    dailyCalorieGoal,
    days: calendarDays(entries, dailyCalorieGoal, formatDay),
    missingProfileFields: missingFields,
  });
});

// Cambia la cantidad de una entrada (raciones si es de receta, gramos si es de alimento) y
// recalcula sus valores con la receta o el alimento ACTUALES. No se puede cambiar el tipo.
router.put('/:id', async (req, res) => {
  const id = Number(req.params.id);
  if (!Number.isInteger(id)) return res.status(400).json({ error: 'Id de entrada no válido' });
  const { servings, grams } = req.body ?? {};

  const entry = await prisma.logEntry.findFirst({ where: { id, userId: req.userId } });
  if (!entry) return res.status(404).json({ error: 'Entrada no encontrada' });

  // El tipo se decide igual que en toEntryResponse: al borrar la receta o el alimento, su id
  // pasa a null (onDelete: SetNull), pero las raciones o los gramos se conservan.
  let data;
  if (entry.grams == null) {
    if (grams != null) {
      return res.status(400).json({ error: 'Esta entrada es de una receta: indica las raciones (servings), no gramos' });
    }
    if (!inRange(servings, LOG_LIMITS.servings)) {
      return res.status(400).json({
        error: `Las raciones deben estar entre ${LOG_LIMITS.servings.min} y ${LOG_LIMITS.servings.max}`,
      });
    }
    const recipe = entry.recipeId != null
      ? await prisma.recipe.findUnique({ where: { id: entry.recipeId } })
      : null;
    if (!recipe) {
      return res.status(404).json({ error: 'La receta de esta entrada ya no existe; no se pueden recalcular sus valores' });
    }
    data = { servings, ...macrosFromRecipe(recipe, servings) };
  } else {
    if (servings != null) {
      return res.status(400).json({ error: 'Esta entrada es de un alimento: indica los gramos (grams), no raciones' });
    }
    if (!inRange(grams, LOG_LIMITS.grams)) {
      return res.status(400).json({
        error: `Los gramos deben estar entre ${LOG_LIMITS.grams.min} y ${LOG_LIMITS.grams.max}`,
      });
    }
    const food = entry.foodId != null ? await findFoodById(entry.foodId) : null;
    if (!food) {
      return res.status(404).json({ error: 'El alimento de esta entrada ya no existe; no se pueden recalcular sus valores' });
    }
    data = { grams, ...macrosFromFood(food, grams) };
  }

  // updateMany con userId, igual que DELETE: si la entrada desaparece o no es del usuario, 404.
  const { count } = await prisma.logEntry.updateMany({ where: { id, userId: req.userId }, data });
  if (count === 0) return res.status(404).json({ error: 'Entrada no encontrada' });
  res.json(toEntryResponse({ ...entry, ...data }));
});

router.delete('/:id', async (req, res) => {
  const id = Number(req.params.id);
  if (!Number.isInteger(id)) return res.status(400).json({ error: 'Id de entrada no válido' });
  // deleteMany con userId: si la entrada es de otro usuario no se borra y se responde 404,
  // sin revelar que existe.
  const { count } = await prisma.logEntry.deleteMany({ where: { id, userId: req.userId } });
  if (count === 0) return res.status(404).json({ error: 'Entrada no encontrada' });
  res.status(204).end();
});

module.exports = router;
