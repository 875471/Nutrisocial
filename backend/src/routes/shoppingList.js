// Lista de la compra de cada usuario (gratuita, como la despensa). Los elementos son sueltos o
// salen de una receta (POST /from-recipe: lo que falta en la despensa).
//
// Simplificaciones deliberadas, fuera del alcance:
// - No se fusionan elementos: el mismo ingrediente añadido dos veces (de dos recetas, o de una
//   receta y a mano) son dos filas. Sumar "200 g" y "1 taza" exigiría convertir unidades entre
//   ingredientes distintos, y hacerlo mal sería peor que no hacerlo. La cantidad es texto libre.
// - Marcar un elemento como comprado no lo añade a la despensa: es un paso manual del usuario.
const express = require('express');
const prisma = require('../prismaClient');
const requireAuth = require('../middleware/auth');
const { missingIngredients } = require('../nutrition/pantryMatch');

const MAX_LABEL = 100;
const MAX_QUANTITY = 50;

const router = express.Router();
router.use(requireAuth);

const INCLUDE_SOURCE = { sourceRecipe: { select: { title: true } } };
// Sin marcar primero y, dentro de cada grupo, en el orden en que se añadieron.
const LIST_ORDER = [{ checked: 'asc' }, { createdAt: 'asc' }, { id: 'asc' }];

function toItemResponse(item) {
  return {
    id: item.id,
    label: item.label,
    quantity: item.quantity,
    checked: item.checked,
    sourceRecipeId: item.sourceRecipeId,
    // Para enseñar "de: Tortilla de patatas"; null si es suelto o la receta se ha borrado.
    sourceRecipeTitle: item.sourceRecipe?.title ?? null,
    createdAt: item.createdAt,
  };
}

function parseId(value) {
  const id = Number(value);
  return Number.isInteger(id) && id > 0 ? id : null;
}

function cleanText(value) {
  return typeof value === 'string' ? value.trim().replace(/\s+/g, ' ') : '';
}

// Nombre obligatorio. Devuelve { value } o { error }.
function validateLabel(value) {
  const label = cleanText(value);
  if (!label) return { error: 'El elemento necesita un nombre' };
  if (label.length > MAX_LABEL) return { error: `El nombre no puede superar ${MAX_LABEL} caracteres` };
  return { value: label };
}

// Cantidad opcional en texto libre ("2", "500 g", "un manojo"); vacía o null es "sin cantidad".
function validateQuantity(value) {
  if (value == null) return { value: null };
  if (typeof value !== 'string') return { error: 'La cantidad debe ser un texto' };
  const quantity = cleanText(value);
  if (quantity.length > MAX_QUANTITY) return { error: `La cantidad no puede superar ${MAX_QUANTITY} caracteres` };
  return { value: quantity || null };
}

const PLURAL_UNITS = { unidad: 'unidades', cucharada: 'cucharadas', cucharadita: 'cucharaditas', taza: 'tazas', pizca: 'pizcas' };

/** Cantidad de un ingrediente de receta como texto: { 200, "g" } → "200 g", { 0.5, "taza" } → "0,5 tazas". */
function formatIngredientQuantity(quantity, unit) {
  if (quantity == null) return null;
  const number = String(Math.round(quantity * 100) / 100).replace('.', ',');
  if (!unit) return number;
  return `${number} ${quantity === 1 ? unit : PLURAL_UNITS[unit] ?? unit}`;
}

router.get('/', async (req, res) => {
  const items = await prisma.shoppingListItem.findMany({
    where: { userId: req.userId },
    orderBy: LIST_ORDER,
    include: INCLUDE_SOURCE,
  });
  res.json(items.map(toItemResponse));
});

// Elemento suelto ({ label, quantity? }), para lo que no viene de una receta.
router.post('/', async (req, res) => {
  const body = req.body ?? {};
  const label = validateLabel(body.label);
  if (label.error) return res.status(400).json({ error: label.error });
  const quantity = validateQuantity(body.quantity);
  if (quantity.error) return res.status(400).json({ error: quantity.error });

  const item = await prisma.shoppingListItem.create({
    data: { userId: req.userId, label: label.value, quantity: quantity.value },
    include: INCLUDE_SOURCE,
  });
  res.status(201).json(toItemResponse(item));
});

// Añade los ingredientes de la receta que faltan en la despensa del usuario, uno por elemento y
// con sourceRecipeId. Lo que la despensa cubre (también con un sustituto) no se añade: se usa la
// misma comparación que el buscador por despensa (nutrition/pantryMatch.js).
router.post('/from-recipe/:recipeId', async (req, res) => {
  const recipeId = parseId(req.params.recipeId);
  if (recipeId == null) return res.status(400).json({ error: 'Id de receta no válido' });
  const recipe = await prisma.recipe.findUnique({
    where: { id: recipeId },
    select: {
      id: true,
      title: true,
      ingredients: { orderBy: { position: 'asc' }, select: { name: true, quantity: true, unit: true, foodId: true } },
    },
  });
  if (!recipe) return res.status(404).json({ error: 'Esta receta ya no existe' });

  const pantry = await prisma.pantryItem.findMany({ where: { userId: req.userId }, select: { name: true, foodId: true } });
  const missing = missingIngredients(pantry, recipe);
  // Una transacción: o se añaden todos los que faltan o ninguno.
  const created = missing.length === 0 ? [] : await prisma.$transaction(missing.map((ing) =>
    prisma.shoppingListItem.create({
      data: {
        userId: req.userId,
        label: ing.name.slice(0, MAX_LABEL),
        quantity: formatIngredientQuantity(ing.quantity, ing.unit),
        sourceRecipeId: recipe.id,
      },
      include: INCLUDE_SOURCE,
    })));
  res.status(created.length > 0 ? 201 : 200).json({
    added: created.map(toItemResponse),
    // Ingredientes que no se han añadido porque la despensa ya los cubre.
    alreadyInPantry: recipe.ingredients.length - missing.length,
  });
});

// Marca o desmarca ({ checked }) o corrige el nombre o la cantidad. Solo los elementos propios:
// uno de otro usuario se trata como inexistente.
router.patch('/:id', async (req, res) => {
  const id = parseId(req.params.id);
  if (id == null) return res.status(400).json({ error: 'Id de elemento no válido' });
  const body = req.body ?? {};
  const data = {};
  if ('checked' in body) {
    if (typeof body.checked !== 'boolean') return res.status(400).json({ error: 'checked debe ser true o false' });
    data.checked = body.checked;
  }
  if ('label' in body) {
    const label = validateLabel(body.label);
    if (label.error) return res.status(400).json({ error: label.error });
    data.label = label.value;
  }
  if ('quantity' in body) {
    const quantity = validateQuantity(body.quantity);
    if (quantity.error) return res.status(400).json({ error: quantity.error });
    data.quantity = quantity.value;
  }
  if (Object.keys(data).length === 0) {
    return res.status(400).json({ error: 'Indica checked, label o quantity' });
  }

  const { count } = await prisma.shoppingListItem.updateMany({ where: { id, userId: req.userId }, data });
  if (count === 0) return res.status(404).json({ error: 'Este elemento ya no está en tu lista' });
  const item = await prisma.shoppingListItem.findUnique({ where: { id }, include: INCLUDE_SOURCE });
  // Borrado justo entre las dos consultas (desde otro dispositivo, por ejemplo).
  if (!item) return res.status(404).json({ error: 'Este elemento ya no está en tu lista' });
  res.json(toItemResponse(item));
});

// Vacía de golpe lo ya comprado. Se exige ?checked=true para que un DELETE sin parámetros no
// pueda borrar la lista entera por error.
router.delete('/', async (req, res) => {
  if (req.query.checked !== 'true') {
    return res.status(400).json({ error: 'Usa ?checked=true para borrar los elementos marcados' });
  }
  const { count } = await prisma.shoppingListItem.deleteMany({ where: { userId: req.userId, checked: true } });
  res.json({ deleted: count });
});

router.delete('/:id', async (req, res) => {
  const id = parseId(req.params.id);
  if (id == null) return res.status(400).json({ error: 'Id de elemento no válido' });
  const { count } = await prisma.shoppingListItem.deleteMany({ where: { id, userId: req.userId } });
  if (count === 0) return res.status(404).json({ error: 'Este elemento ya no está en tu lista' });
  res.status(204).end();
});

module.exports = router;
module.exports.formatIngredientQuantity = formatIngredientQuantity;
