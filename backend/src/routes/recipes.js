const express = require('express');
const prisma = require('../prismaClient');
const requireAuth = require('../middleware/auth');
const { UNITS } = require('../nutrition/unitConversion');
const { resolveIngredients, nutritionSummary } = require('../nutrition/calculate');
const { matchFood } = require('../nutrition/matchFood');
const { parseRecipeText } = require('../ocr/parseRecipeText');

const MAX_OCR_TEXT = 20000;

const router = express.Router();
router.use(requireAuth);

const INCLUDE_INGREDIENTS = {
  ingredients: { orderBy: { position: 'asc' }, include: { food: { select: { id: true, name: true } } } },
};

// Los pasos se guardan como JSON en texto; al responder se devuelven como array.
// Los ingredientes vienen de RecipeIngredient, con el alimento asociado si lo hay.
function toRecipeResponse(recipe) {
  return {
    id: recipe.id,
    title: recipe.title,
    ingredients: recipe.ingredients.map((ing) => ({
      name: ing.name,
      quantity: ing.quantity,
      unit: ing.unit,
      grams: ing.grams == null ? null : Math.round(ing.grams * 10) / 10,
      food: ing.food,
    })),
    steps: JSON.parse(recipe.steps),
    servings: recipe.servings,
    prepMinutes: recipe.prepMinutes,
    nutrition: nutritionSummary(recipe, recipe.ingredients),
    authorId: recipe.authorId,
    createdAt: recipe.createdAt,
  };
}

// Devuelve el array sin elementos vacíos, o null si no es un array de strings.
function cleanStringList(value) {
  if (!Array.isArray(value) || !value.every((item) => typeof item === 'string')) return null;
  return value.map((item) => item.trim()).filter((item) => item.length > 0);
}

// Valida los ingredientes: cada uno es { name, quantity?, unit?, foodId? } o, por
// compatibilidad, un texto suelto (se guarda sin cantidad). Devuelve la lista limpia,
// sin los de nombre vacío, o un mensaje de error.
function cleanIngredients(value) {
  if (!Array.isArray(value)) return { error: 'Los ingredientes deben ser una lista' };
  const result = [];
  for (const raw of value) {
    const item = typeof raw === 'string' ? { name: raw } : raw;
    if (item === null || typeof item !== 'object' || typeof item.name !== 'string') {
      return { error: 'Cada ingrediente necesita un nombre' };
    }
    const name = item.name.trim();
    if (!name) continue;
    const quantity = item.quantity ?? null;
    const unit = item.unit ?? null;
    const foodId = item.foodId ?? null;
    if (quantity !== null && (typeof quantity !== 'number' || !(quantity > 0) || quantity > 100000)) {
      return { error: `La cantidad de "${name}" debe ser un número mayor que 0` };
    }
    if (quantity !== null && !UNITS.includes(unit)) {
      return { error: `Unidad no válida para "${name}". Usa una de: ${UNITS.join(', ')}` };
    }
    if (foodId !== null && !Number.isInteger(foodId)) {
      return { error: `Alimento no válido para "${name}"` };
    }
    result.push({ name, quantity, unit: quantity === null ? null : unit, foodId });
  }
  if (result.length === 0) return { error: 'Añade al menos un ingrediente' };
  return { ingredients: result };
}

router.post('/', async (req, res) => {
  const { title, ingredients, steps, servings, prepMinutes } = req.body ?? {};

  const cleanTitle = typeof title === 'string' ? title.trim() : '';
  if (!cleanTitle) {
    return res.status(400).json({ error: 'El título no puede estar vacío' });
  }
  const parsedIngredients = cleanIngredients(ingredients);
  if (parsedIngredients.error) {
    return res.status(400).json({ error: parsedIngredients.error });
  }
  const cleanSteps = cleanStringList(steps);
  if (!cleanSteps || cleanSteps.length === 0) {
    return res.status(400).json({ error: 'Añade al menos un paso' });
  }
  if (!Number.isInteger(servings) || servings < 1) {
    return res.status(400).json({ error: 'Las raciones deben ser un número entero mayor que 0' });
  }
  if (prepMinutes != null && (!Number.isInteger(prepMinutes) || prepMinutes < 0)) {
    return res.status(400).json({ error: 'El tiempo de preparación debe ser un número entero de minutos' });
  }

  // Cálculo nutricional en el momento de crear la receta: los totales quedan guardados
  // y no cambian aunque después se actualice la tabla Food.
  const { ingredients: resolved, totals } = await resolveIngredients(parsedIngredients.ingredients);

  const recipe = await prisma.recipe.create({
    data: {
      title: cleanTitle,
      steps: JSON.stringify(cleanSteps),
      servings,
      prepMinutes: prepMinutes ?? null,
      ...totals,
      authorId: req.userId,
      ingredients: { create: resolved },
    },
    include: INCLUDE_INGREDIENTS,
  });
  res.status(201).json(toRecipeResponse(recipe));
});

// Propuesta de receta a partir del texto reconocido por OCR en el móvil. No guarda nada:
// la app la vuelca en el formulario para que el usuario la revise y la guarde con POST /.
router.post('/parse-ocr', async (req, res) => {
  const { rawText } = req.body ?? {};
  if (typeof rawText !== 'string' || !rawText.trim()) {
    return res.status(400).json({ error: 'No se ha recibido texto que analizar' });
  }
  if (rawText.length > MAX_OCR_TEXT) {
    return res.status(400).json({ error: 'El texto es demasiado largo para ser una receta' });
  }

  const parsed = parseRecipeText(rawText);
  const ingredients = [];
  for (const ing of parsed.ingredients) {
    const food = await matchFood(ing.rawName);
    ingredients.push({
      rawName: ing.rawName,
      quantity: ing.quantity,
      unit: ing.unit,
      matched: food != null,
      foodId: food?.id ?? null,
      foodName: food?.name ?? null,
    });
  }
  res.json({
    title: parsed.title,
    servings: parsed.servings,
    ingredients,
    steps: parsed.steps,
    // Clasificación de cada línea: no la usa la app, sirve para evaluar la heurística.
    lines: parsed.lines,
  });
});

router.get('/mine', async (req, res) => {
  const recipes = await prisma.recipe.findMany({
    where: { authorId: req.userId },
    orderBy: { createdAt: 'desc' },
    include: INCLUDE_INGREDIENTS,
  });
  res.json(recipes.map(toRecipeResponse));
});

router.get('/:id', async (req, res) => {
  const id = Number(req.params.id);
  if (!Number.isInteger(id)) {
    return res.status(400).json({ error: 'Id de receta no válido' });
  }
  const recipe = await prisma.recipe.findUnique({ where: { id }, include: INCLUDE_INGREDIENTS });
  if (!recipe) {
    return res.status(404).json({ error: 'Receta no encontrada' });
  }
  res.json(toRecipeResponse(recipe));
});

module.exports = router;
