const express = require('express');
const prisma = require('../prismaClient');
const requireAuth = require('../middleware/auth');
const { findFoodById } = require('../nutrition/matchFood');
const { calculateCalorieGoal } = require('../nutrition/calorieGoal');
const {
  LOG_LIMITS, macrosFromRecipe, macrosFromFood, sumTotals, compareWithGoal,
} = require('../nutrition/dailyLog');
const { recommendRecipes } = require('../nutrition/recommend');
const { parseDay, formatDay } = require('../utils/day');

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

// Entradas de un día, totales y comparación con el objetivo calórico del perfil.
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
