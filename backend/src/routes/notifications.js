const express = require('express');
const prisma = require('../prismaClient');
const requireAuth = require('../middleware/auth');
const {
  NOTIFICATIONS_PAGE_SIZE, NOTIFICATIONS_MAX_PAGE_SIZE, NOTIFICATION_SELECT, toNotificationResponse,
} = require('../social/notifications');

const router = express.Router();
router.use(requireAuth);

function parsePositiveInt(value) {
  const n = Number(value);
  return Number.isInteger(n) && n > 0 ? n : null;
}

// Notificaciones del usuario, de la más reciente a la más antigua, paginadas por cursor como el
// feed (`cursor` es el id de la última recibida). La primera página trae además `unreadCount`,
// que es lo que usa la app para el punto rojo de la campana.
router.get('/', async (req, res) => {
  let cursor = null;
  if (req.query.cursor != null && req.query.cursor !== '') {
    cursor = parsePositiveInt(req.query.cursor);
    if (cursor == null) return res.status(400).json({ error: 'Cursor no válido' });
  }
  let limit = NOTIFICATIONS_PAGE_SIZE;
  if (req.query.limit != null && req.query.limit !== '') {
    limit = parsePositiveInt(req.query.limit);
    if (limit == null) return res.status(400).json({ error: 'El límite debe ser un número entero positivo' });
    limit = Math.min(limit, NOTIFICATIONS_MAX_PAGE_SIZE);
  }
  // Un cursor que no es del usuario no debe servir para leer notificaciones ajenas: el filtro
  // por userId se aplica igualmente, pero además se rechaza para no devolver una página rara.
  if (cursor != null) {
    const own = await prisma.notification.findFirst({ where: { id: cursor, userId: req.userId }, select: { id: true } });
    if (!own) return res.status(400).json({ error: 'Cursor no válido' });
  }

  const [rows, unreadCount] = await Promise.all([
    prisma.notification.findMany({
      where: { userId: req.userId },
      orderBy: [{ createdAt: 'desc' }, { id: 'desc' }],
      take: limit + 1,
      ...(cursor != null && { cursor: { id: cursor }, skip: 1 }),
      select: NOTIFICATION_SELECT,
    }),
    cursor == null ? prisma.notification.count({ where: { userId: req.userId, read: false } }) : null,
  ]);
  const page = rows.slice(0, limit);
  res.json({
    notifications: page.map(toNotificationResponse),
    nextCursor: rows.length > limit ? page[page.length - 1].id : null,
    ...(unreadCount != null && { unreadCount }),
  });
});

// Marca como leídas todas las notificaciones del usuario (al abrir la pantalla de avisos).
router.post('/read-all', async (req, res) => {
  const { count } = await prisma.notification.updateMany({ where: { userId: req.userId, read: false }, data: { read: true } });
  res.json({ updated: count, unreadCount: 0 });
});

module.exports = router;
