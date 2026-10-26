const express = require('express');
const prisma = require('../prismaClient');
const requireAuth = require('../middleware/auth');
const { parseUserSearchQuery, searchUsers } = require('../social/userSearch');
const { notify } = require('../social/notifications');
const { findOtherUserId } = require('../social/otherUser');

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

// Gente a la que sigue el usuario, por orden alfabético: de esta lista se elige a quién
// compartir una receta (POST /recipes/:id/share).
router.get('/following', async (req, res) => {
  const follows = await prisma.follow.findMany({
    where: { followerId: req.userId },
    select: { following: { select: { id: true, name: true } } },
    orderBy: { following: { name: 'asc' } },
  });
  res.json(follows.map((f) => f.following));
});

const followTarget = (req, res) => findOtherUserId(res, req.params.id, req.userId, 'No puedes seguirte a ti mismo');

// Seguir y dejar de seguir. Igual que los likes, las dos son idempotentes y devuelven el estado final.
router.post('/:id/follow', async (req, res) => {
  const id = await followTarget(req, res);
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
  const id = await followTarget(req, res);
  if (id == null) return;
  await prisma.follow.deleteMany({ where: { followerId: req.userId, followingId: id } });
  res.json({ isFollowedByMe: false });
});

module.exports = router;
