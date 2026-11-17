const express = require('express');
const prisma = require('../prismaClient');
const requireAuth = require('../middleware/auth');
const { parseHiddenBody, canModerateComment } = require('../social/moderation');

// Comentarios sueltos, por su propio id. Listar y crear van bajo /recipes/:id/comments
// (ver routes/recipes.js), porque siempre se hace en el contexto de una receta.
const router = express.Router();
router.use(requireAuth);

// Borra un comentario propio. No hay edición: para corregirlo se borra y se escribe otro.
router.delete('/:id', async (req, res) => {
  const id = Number(req.params.id);
  if (!Number.isInteger(id) || id <= 0) {
    return res.status(400).json({ error: 'Id de comentario no válido' });
  }
  const comment = await prisma.comment.findUnique({ where: { id }, select: { userId: true } });
  if (!comment) {
    return res.status(404).json({ error: 'Este comentario ya no existe' });
  }
  if (comment.userId !== req.userId) {
    return res.status(403).json({ error: 'Solo el autor puede borrar el comentario' });
  }
  await prisma.comment.delete({ where: { id } });
  res.json({ deleted: true });
});

// Moderación: oculta ({ hidden: true }) o vuelve a mostrar (false) un comentario. Pueden
// hacerlo su autor, el autor de la receta comentada y un administrador (ver social/moderation.js).
router.put('/:id/hidden', async (req, res) => {
  const hidden = parseHiddenBody(req.body);
  if (hidden.error) return res.status(400).json({ error: hidden.error });
  const id = Number(req.params.id);
  if (!Number.isInteger(id) || id <= 0) {
    return res.status(400).json({ error: 'Id de comentario no válido' });
  }
  const [comment, user] = await Promise.all([
    prisma.comment.findUnique({ where: { id }, select: { userId: true, recipe: { select: { authorId: true } } } }),
    prisma.user.findUnique({ where: { id: req.userId }, select: { id: true, isAdmin: true } }),
  ]);
  if (!comment) {
    return res.status(404).json({ error: 'Este comentario ya no existe' });
  }
  if (!canModerateComment(comment, user)) {
    return res.status(403).json({ error: 'Solo el autor del comentario, el de la receta o un administrador pueden ocultarlo' });
  }
  await prisma.comment.update({ where: { id }, data: { hidden: hidden.value } });
  res.json({ id, hidden: hidden.value });
});

module.exports = router;
