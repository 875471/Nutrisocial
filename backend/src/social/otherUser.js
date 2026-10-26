// Validación del id de otro usuario, compartida por seguir (routes/users.js) y por compartir
// una receta (routes/recipes.js).
const prisma = require('../prismaClient');

/**
 * Devuelve el id `rawId` (de la ruta o del cuerpo) si es un entero positivo, no es `userId` y el
 * usuario existe. Si no, responde 400/404 por `res` y devuelve null. `selfError` es el mensaje
 * para cuando apunta a uno mismo ("No puedes seguirte a ti mismo").
 */
async function findOtherUserId(res, rawId, userId, selfError) {
  const id = typeof rawId === 'number' || typeof rawId === 'string' ? Number(rawId) : NaN;
  if (!Number.isInteger(id) || id <= 0) {
    res.status(400).json({ error: 'Id de usuario no válido' });
    return null;
  }
  if (id === userId) {
    res.status(400).json({ error: selfError });
    return null;
  }
  const exists = await prisma.user.findUnique({ where: { id }, select: { id: true } });
  if (!exists) {
    res.status(404).json({ error: 'Este usuario ya no existe' });
    return null;
  }
  return id;
}

module.exports = { findOtherUserId };
