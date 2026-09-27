const express = require('express');
const prisma = require('../prismaClient');
const requireAuth = require('../middleware/auth');
const { matchFood } = require('../nutrition/matchFood');
const { normalize } = require('../nutrition/normalize');

const MAX_NAME = 100;

const router = express.Router();
router.use(requireAuth);

const INCLUDE_FOOD = { food: { select: { id: true, name: true, source: true } } };

function toItemResponse(item) {
  return { id: item.id, name: item.name, food: item.food, createdAt: item.createdAt };
}

router.get('/', async (req, res) => {
  const items = await prisma.pantryItem.findMany({
    where: { userId: req.userId },
    orderBy: { createdAt: 'desc' },
    include: INCLUDE_FOOD,
  });
  res.json(items.map(toItemResponse));
});

// Añade un ingrediente ({ name }) y lo empareja con un alimento de Food si lo hay.
router.post('/', async (req, res) => {
  const { name } = req.body ?? {};
  const cleanName = typeof name === 'string' ? name.trim().replace(/\s+/g, ' ') : '';
  if (!cleanName) {
    return res.status(400).json({ error: 'El ingrediente necesita un nombre' });
  }
  if (cleanName.length > MAX_NAME) {
    return res.status(400).json({ error: `El nombre no puede superar ${MAX_NAME} caracteres` });
  }

  // El índice único distingue mayúsculas y tildes; aquí "Sal" y "sal" cuentan como el mismo.
  const existing = await prisma.pantryItem.findMany({ where: { userId: req.userId }, select: { name: true } });
  const key = normalize(cleanName);
  if (existing.some((item) => normalize(item.name) === key)) {
    return res.status(409).json({ error: `«${cleanName}» ya está en tu despensa` });
  }

  const food = await matchFood(cleanName);
  try {
    const item = await prisma.pantryItem.create({
      data: { userId: req.userId, name: cleanName, foodId: food?.id ?? null },
      include: INCLUDE_FOOD,
    });
    res.status(201).json(toItemResponse(item));
  } catch (err) {
    // Dos peticiones a la vez con el mismo nombre: la segunda choca con el índice único.
    if (err.code === 'P2002') {
      return res.status(409).json({ error: `«${cleanName}» ya está en tu despensa` });
    }
    throw err;
  }
});

router.delete('/:id', async (req, res) => {
  const id = Number(req.params.id);
  if (!Number.isInteger(id)) {
    return res.status(400).json({ error: 'Id de ingrediente no válido' });
  }
  // Con userId en el filtro, un ingrediente de otro usuario se trata como inexistente.
  const { count } = await prisma.pantryItem.deleteMany({ where: { id, userId: req.userId } });
  if (count === 0) {
    return res.status(404).json({ error: 'Ingrediente no encontrado' });
  }
  res.status(204).end();
});

module.exports = router;
