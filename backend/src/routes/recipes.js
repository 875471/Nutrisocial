const express = require('express');
const prisma = require('../prismaClient');
const requireAuth = require('../middleware/auth');

const router = express.Router();
router.use(requireAuth);

// ingredients y steps se guardan como JSON en texto; al responder se devuelven como arrays.
function toRecipeResponse(recipe) {
  return {
    id: recipe.id,
    title: recipe.title,
    ingredients: JSON.parse(recipe.ingredients),
    steps: JSON.parse(recipe.steps),
    servings: recipe.servings,
    prepMinutes: recipe.prepMinutes,
    authorId: recipe.authorId,
    createdAt: recipe.createdAt,
  };
}

// Devuelve el array sin elementos vacíos, o null si no es un array de strings.
function cleanStringList(value) {
  if (!Array.isArray(value) || !value.every((item) => typeof item === 'string')) return null;
  return value.map((item) => item.trim()).filter((item) => item.length > 0);
}

router.post('/', async (req, res) => {
  const { title, ingredients, steps, servings, prepMinutes } = req.body ?? {};

  const cleanTitle = typeof title === 'string' ? title.trim() : '';
  if (!cleanTitle) {
    return res.status(400).json({ error: 'El título no puede estar vacío' });
  }
  const cleanIngredients = cleanStringList(ingredients);
  if (!cleanIngredients || cleanIngredients.length === 0) {
    return res.status(400).json({ error: 'Añade al menos un ingrediente' });
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

  const recipe = await prisma.recipe.create({
    data: {
      title: cleanTitle,
      ingredients: JSON.stringify(cleanIngredients),
      steps: JSON.stringify(cleanSteps),
      servings,
      prepMinutes: prepMinutes ?? null,
      authorId: req.userId,
    },
  });
  res.status(201).json(toRecipeResponse(recipe));
});

router.get('/mine', async (req, res) => {
  const recipes = await prisma.recipe.findMany({
    where: { authorId: req.userId },
    orderBy: { createdAt: 'desc' },
  });
  res.json(recipes.map(toRecipeResponse));
});

router.get('/:id', async (req, res) => {
  const id = Number(req.params.id);
  if (!Number.isInteger(id)) {
    return res.status(400).json({ error: 'Id de receta no válido' });
  }
  const recipe = await prisma.recipe.findUnique({ where: { id } });
  if (!recipe) {
    return res.status(404).json({ error: 'Receta no encontrada' });
  }
  res.json(toRecipeResponse(recipe));
});

module.exports = router;
