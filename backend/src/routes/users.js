const express = require('express');
const prisma = require('../prismaClient');
const requireAuth = require('../middleware/auth');
const { parseUserSearchQuery, searchUsers } = require('../social/userSearch');
const { notify } = require('../social/notifications');

const router = express.Router();
router.use(requireAuth);

// Buscador de personas por nombre, sin tildes ni mayúsculas y sin incluir a quien busca. Como el
// buscador de recetas, filtra en memoria sobre los nombres (PostgreSQL no quita las tildes sin la
// extensión unaccent) y después carga el recuento de recetas y el seguimiento de la página.
router.get('/search', async (req, res) => {
  const search = parseUserSearchQuery(req.query);
  if (search.error) return res.status(400).json({ error: search.error });

  const candidates = await prisma.user.findMany({ select: { id: true, name: true } });
  const ids = searchUsers(candidates, { ...search, excludeId: req.userId });
  const rows = await prisma.user.findMany({
    where: { id: { in: ids } },
    select: {
      id: true,
      name: true,
      _count: { select: { recipes: true } },
      followers: { where: { followerId: req.userId }, select: { id: true }, take: 1 },
    },
  });
  const byId = new Map(rows.map((u) => [u.id, u]));
  // findMany no respeta el orden de `in`: se recoloca según la búsqueda.
  const users = ids.map((id) => byId.get(id)).filter(Boolean).map((u) => ({
    id: u.id,
    name: u.name,
    isFollowedByMe: u.followers.length > 0,
    recipesCount: u._count.recipes,
  }));
  res.json({ users });
});

// Id de la ruta si es válido, no es el propio usuario y existe. Si no, responde 400/404 y
// devuelve null.
async function findOtherUserId(req, res) {
  const id = Number(req.params.id);
  if (!Number.isInteger(id) || id <= 0) {
    res.status(400).json({ error: 'Id de usuario no válido' });
    return null;
  }
  if (id === req.userId) {
    res.status(400).json({ error: 'No puedes seguirte a ti mismo' });
    return null;
  }
  const exists = await prisma.user.findUnique({ where: { id }, select: { id: true } });
  if (!exists) {
    res.status(404).json({ error: 'Este usuario ya no existe' });
    return null;
  }
  return id;
}

// Seguir y dejar de seguir. Igual que los likes, las dos son idempotentes y devuelven el estado final.
router.post('/:id/follow', async (req, res) => {
  const id = await findOtherUserId(req, res);
  if (id == null) return;
  // upsert: seguir dos veces no duplica ni choca con el índice único.
  await prisma.follow.upsert({
    where: { followerId_followingId: { followerId: req.userId, followingId: id } },
    create: { followerId: req.userId, followingId: id },
    update: {},
  });
  await notify({ userId: id, type: 'follow', actorId: req.userId });
  res.json({ isFollowedByMe: true });
});

router.delete('/:id/follow', async (req, res) => {
  const id = await findOtherUserId(req, res);
  if (id == null) return;
  await prisma.follow.deleteMany({ where: { followerId: req.userId, followingId: id } });
  res.json({ isFollowedByMe: false });
});

module.exports = router;
