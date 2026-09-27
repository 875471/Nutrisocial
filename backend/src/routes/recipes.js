const express = require('express');
const prisma = require('../prismaClient');
const requireAuth = require('../middleware/auth');
const { UNITS } = require('../nutrition/unitConversion');
const { resolveIngredients, nutritionSummary } = require('../nutrition/calculate');
const { matchFood } = require('../nutrition/matchFood');
const { parseRecipeText } = require('../ocr/parseRecipeText');
const { matchRecipesToPantry } = require('../nutrition/pantryMatch');
const { validateImageBase64, likeFields, likeSummary, canEditRecipe } = require('../social/recipeSocial');

const MAX_OCR_TEXT = 20000;
const FEED_PAGE_SIZE = 20;
const FEED_MAX_PAGE_SIZE = 50;

const router = express.Router();
router.use(requireAuth);

const INCLUDE_INGREDIENTS = {
  ingredients: { orderBy: { position: 'asc' }, include: { food: { select: { id: true, name: true, source: true } } } },
};
const AUTHOR_NAME = { author: { select: { name: true } } };

// Todo lo que necesita toRecipeResponse, con los likes vistos por el usuario `userId`.
function recipeInclude(userId) {
  return { ...INCLUDE_INGREDIENTS, ...AUTHOR_NAME, ...likeFields(userId) };
}

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
    imageBase64: recipe.imageBase64,
    authorId: recipe.authorId,
    authorName: recipe.author.name,
    ...likeSummary(recipe),
    createdAt: recipe.createdAt,
  };
}

// Tarjeta del feed: lo justo para la lista, sin ingredientes ni pasos.
function toFeedItem(recipe) {
  return {
    id: recipe.id,
    title: recipe.title,
    imageBase64: recipe.imageBase64,
    authorId: recipe.authorId,
    authorName: recipe.author.name,
    servings: recipe.servings,
    prepMinutes: recipe.prepMinutes,
    kcalPerServing: Math.round(recipe.kcal / recipe.servings),
    ...likeSummary(recipe),
    createdAt: recipe.createdAt,
  };
}

function parseId(value) {
  const id = Number(value);
  return Number.isInteger(id) && id > 0 ? id : null;
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

/**
 * Valida el cuerpo de POST /recipes y PUT /recipes/:id y calcula los valores nutricionales.
 * Devuelve { fields } (columnas de Recipe, sin autor ni foto) e { ingredients } resueltos
 * contra la tabla Food, o { error } para un 400. La foto se valida aparte (validateImageBase64).
 */
async function buildRecipeData(body) {
  const { title, ingredients, steps, servings, prepMinutes } = body;

  const cleanTitle = typeof title === 'string' ? title.trim() : '';
  if (!cleanTitle) return { error: 'El título no puede estar vacío' };
  const parsedIngredients = cleanIngredients(ingredients);
  if (parsedIngredients.error) return { error: parsedIngredients.error };
  const cleanSteps = cleanStringList(steps);
  if (!cleanSteps || cleanSteps.length === 0) return { error: 'Añade al menos un paso' };
  if (!Number.isInteger(servings) || servings < 1) {
    return { error: 'Las raciones deben ser un número entero mayor que 0' };
  }
  if (prepMinutes != null && (!Number.isInteger(prepMinutes) || prepMinutes < 0)) {
    return { error: 'El tiempo de preparación debe ser un número entero de minutos' };
  }

  // Cálculo nutricional al guardar: los totales quedan guardados y no cambian aunque
  // después se actualice la tabla Food (al editar la receta se recalculan).
  const { ingredients: resolved, totals } = await resolveIngredients(parsedIngredients.ingredients);
  return {
    fields: {
      title: cleanTitle,
      steps: JSON.stringify(cleanSteps),
      servings,
      prepMinutes: prepMinutes ?? null,
      ...totals,
    },
    ingredients: resolved,
  };
}

// Comprueba que la receta existe y que req.userId es su autor. Si no, responde 400/404/403
// y devuelve null; si sí, devuelve el id.
async function findOwnRecipeId(req, res, action) {
  const id = parseId(req.params.id);
  if (id == null) {
    res.status(400).json({ error: 'Id de receta no válido' });
    return null;
  }
  const recipe = await prisma.recipe.findUnique({ where: { id }, select: { id: true, authorId: true } });
  if (!recipe) {
    res.status(404).json({ error: 'Esta receta ya no existe' });
    return null;
  }
  if (!canEditRecipe(recipe, req.userId)) {
    res.status(403).json({ error: `Solo el autor puede ${action} la receta` });
    return null;
  }
  return id;
}

router.post('/', async (req, res) => {
  const body = req.body ?? {};
  const image = validateImageBase64(body.imageBase64);
  if (image.error) return res.status(400).json({ error: image.error });
  const built = await buildRecipeData(body);
  if (built.error) return res.status(400).json({ error: built.error });

  const recipe = await prisma.recipe.create({
    data: {
      ...built.fields,
      imageBase64: image.value,
      authorId: req.userId,
      ingredients: { create: built.ingredients },
    },
    include: recipeInclude(req.userId),
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
  // En paralelo: algunos ingredientes pueden necesitar una consulta a Open Food Facts.
  const foods = await Promise.all(parsed.ingredients.map((ing) => matchFood(ing.rawName)));
  const ingredients = parsed.ingredients.map((ing, i) => ({
    rawName: ing.rawName,
    quantity: ing.quantity,
    unit: ing.unit,
    matched: foods[i] != null,
    foodId: foods[i]?.id ?? null,
    foodName: foods[i]?.name ?? null,
    foodSource: foods[i]?.source ?? null,
  }));
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
    include: recipeInclude(req.userId),
  });
  res.json(recipes.map(toRecipeResponse));
});

// Feed social: recetas de todos los usuarios, de la más reciente a la más antigua.
// Paginación por cursor: `cursor` es el id de la última receta recibida y la respuesta
// trae `nextCursor` (null si no hay más).
router.get('/feed', async (req, res) => {
  let cursor = null;
  if (req.query.cursor != null && req.query.cursor !== '') {
    cursor = parseId(req.query.cursor);
    if (cursor == null) return res.status(400).json({ error: 'Cursor no válido' });
  }
  const requested = req.query.limit == null || req.query.limit === '' ? FEED_PAGE_SIZE : parseId(req.query.limit);
  if (requested == null) return res.status(400).json({ error: 'El límite debe ser un número entero positivo' });
  const limit = Math.min(requested, FEED_MAX_PAGE_SIZE);

  // Se pide una de más para saber si hay página siguiente sin otra consulta.
  const recipes = await prisma.recipe.findMany({
    orderBy: [{ createdAt: 'desc' }, { id: 'desc' }],
    take: limit + 1,
    ...(cursor != null && { cursor: { id: cursor }, skip: 1 }),
    select: {
      id: true, title: true, imageBase64: true, authorId: true, servings: true, prepMinutes: true,
      kcal: true, createdAt: true, ...AUTHOR_NAME, ...likeFields(req.userId),
    },
  });
  const page = recipes.slice(0, limit);
  res.json({
    recipes: page.map(toFeedItem),
    nextCursor: recipes.length > limit ? page[page.length - 1].id : null,
  });
});

// Recetas de cualquier autor que se pueden cocinar con la despensa del usuario, ya separadas
// en las que tienen todos los ingredientes y en las que les falta alguno (ver pantryMatch.js).
router.get('/by-pantry', async (req, res) => {
  const pantry = await prisma.pantryItem.findMany({
    where: { userId: req.userId },
    select: { name: true, foodId: true },
  });
  if (pantry.length === 0) {
    return res.json({ pantrySize: 0, readyToCook: [], almostReady: [] });
  }
  const recipes = await prisma.recipe.findMany({
    select: {
      id: true, title: true, imageBase64: true, authorId: true,
      ingredients: { select: { name: true, foodId: true } }, ...AUTHOR_NAME,
    },
  });
  const byId = new Map(recipes.map((r) => [r.id, r]));
  const matches = matchRecipesToPantry(pantry, recipes).map((match) => {
    const recipe = byId.get(match.id);
    return { ...match, imageBase64: recipe.imageBase64, authorId: recipe.authorId, authorName: recipe.author.name };
  });
  res.json({
    pantrySize: pantry.length,
    readyToCook: matches.filter((r) => r.coverage === 1),
    almostReady: matches.filter((r) => r.coverage < 1),
  });
});

router.get('/:id', async (req, res) => {
  const id = parseId(req.params.id);
  if (id == null) {
    return res.status(400).json({ error: 'Id de receta no válido' });
  }
  const recipe = await prisma.recipe.findUnique({ where: { id }, include: recipeInclude(req.userId) });
  if (!recipe) {
    return res.status(404).json({ error: 'Esta receta ya no existe' });
  }
  res.json(toRecipeResponse(recipe));
});

// Edita una receta propia con los mismos campos que POST /recipes. Los ingredientes se
// sustituyen por completo y los totales se recalculan. La foto solo cambia si el cuerpo trae
// imageBase64 (null la quita); si no, se conserva la que hubiera.
router.put('/:id', async (req, res) => {
  const id = await findOwnRecipeId(req, res, 'editar');
  if (id == null) return;
  const body = req.body ?? {};
  let image = null;
  if ('imageBase64' in body) {
    image = validateImageBase64(body.imageBase64);
    if (image.error) return res.status(400).json({ error: image.error });
  }
  const built = await buildRecipeData(body);
  if (built.error) return res.status(400).json({ error: built.error });

  // Un único update anidado: borrar los ingredientes antiguos y crear los nuevos es atómico.
  const updated = await prisma.recipe.update({
    where: { id },
    data: {
      ...built.fields,
      ...(image && { imageBase64: image.value }),
      ingredients: { deleteMany: {}, create: built.ingredients },
    },
    include: recipeInclude(req.userId),
  });
  res.json(toRecipeResponse(updated));
});

// Borra una receta propia (sus ingredientes y likes se borran en cascada). Las entradas del
// diario que la usaban se conservan con sus valores copiados, pero pierden el enlace
// (onDelete: SetNull). Se informa de cuántas eran del propio autor para que la app avise;
// las de otros usuarios no se cuentan, para no revelar quién ha registrado la receta.
router.delete('/:id', async (req, res) => {
  const id = await findOwnRecipeId(req, res, 'borrar');
  if (id == null) return;
  const [orphanedLogEntries] = await prisma.$transaction([
    prisma.logEntry.count({ where: { recipeId: id, userId: req.userId } }),
    prisma.recipe.delete({ where: { id } }),
  ]);
  res.json({ deleted: true, orphanedLogEntries });
});

// Pone, cambia o quita (imageBase64: null) la foto de una receta. Solo su autor.
router.put('/:id/image', async (req, res) => {
  const body = req.body ?? {};
  if (!('imageBase64' in body)) {
    return res.status(400).json({ error: 'Falta imageBase64 (usa null para quitar la foto)' });
  }
  const image = validateImageBase64(body.imageBase64);
  if (image.error) {
    return res.status(400).json({ error: image.error });
  }
  const id = await findOwnRecipeId(req, res, 'cambiar la foto de');
  if (id == null) return;

  const updated = await prisma.recipe.update({
    where: { id },
    data: { imageBase64: image.value },
    include: recipeInclude(req.userId),
  });
  res.json(toRecipeResponse(updated));
});

// Número de likes y si el usuario ha dado el suyo, tras darlo o quitarlo.
async function likeState(recipeId, userId) {
  const recipe = await prisma.recipe.findUnique({ where: { id: recipeId }, select: likeFields(userId) });
  return likeSummary(recipe);
}

router.post('/:id/like', async (req, res) => {
  const id = parseId(req.params.id);
  if (id == null) {
    return res.status(400).json({ error: 'Id de receta no válido' });
  }
  const exists = await prisma.recipe.findUnique({ where: { id }, select: { id: true } });
  if (!exists) {
    return res.status(404).json({ error: 'Esta receta ya no existe' });
  }
  // upsert: dar like dos veces no duplica ni choca con el índice único.
  await prisma.recipeLike.upsert({
    where: { userId_recipeId: { userId: req.userId, recipeId: id } },
    create: { userId: req.userId, recipeId: id },
    update: {},
  });
  res.json(await likeState(id, req.userId));
});

router.delete('/:id/like', async (req, res) => {
  const id = parseId(req.params.id);
  if (id == null) {
    return res.status(400).json({ error: 'Id de receta no válido' });
  }
  const exists = await prisma.recipe.findUnique({ where: { id }, select: { id: true } });
  if (!exists) {
    return res.status(404).json({ error: 'Esta receta ya no existe' });
  }
  // deleteMany: quitar un like que no existe no es un error.
  await prisma.recipeLike.deleteMany({ where: { userId: req.userId, recipeId: id } });
  res.json(await likeState(id, req.userId));
});

module.exports = router;
