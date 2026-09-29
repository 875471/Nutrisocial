// Notificaciones dentro de la app: se crean al seguir a alguien, al dar "me gusta" a una receta
// y al comentarla, y se consultan en GET /notifications (ver routes/notifications.js).
const prisma = require('../prismaClient');

const NOTIFICATION_TYPES = ['follow', 'like', 'comment'];
const NOTIFICATIONS_PAGE_SIZE = 20;
const NOTIFICATIONS_MAX_PAGE_SIZE = 50;

/**
 * Crea una notificación para `userId` provocada por `actorId`. Como los correos de Brevo, nunca
 * lanza: un fallo aquí se registra en el log y no puede romper el follow, el like o el
 * comentario que la provoca. No se notifica a uno mismo (dar like a tu propia receta).
 * "follow" y "like" se pueden deshacer y repetir: si ya había una notificación igual, no se
 * crea otra, para que quitar y volver a dar el like no llene la lista de avisos repetidos.
 */
async function notify({ userId, type, actorId, recipeId = null }) {
  if (userId == null || userId === actorId) return;
  try {
    if (type !== 'comment') {
      const existing = await prisma.notification.findFirst({ where: { userId, type, actorId, recipeId }, select: { id: true } });
      if (existing) return;
    }
    await prisma.notification.create({ data: { userId, type, actorId, recipeId } });
  } catch (err) {
    console.error(`No se pudo crear la notificación "${type}" para el usuario ${userId}: ${err.message}`);
  }
}

/** Como notify, para el autor de la receta `recipeId` ("like" o "comment"). */
async function notifyRecipeAuthor({ recipeId, type, actorId }) {
  try {
    const recipe = await prisma.recipe.findUnique({ where: { id: recipeId }, select: { authorId: true } });
    await notify({ userId: recipe?.authorId, type, actorId, recipeId });
  } catch (err) {
    console.error(`No se pudo notificar al autor de la receta ${recipeId}: ${err.message}`);
  }
}

// Lo que se carga de cada notificación para toNotificationResponse.
const NOTIFICATION_SELECT = {
  id: true, type: true, actorId: true, recipeId: true, read: true, createdAt: true,
  actor: { select: { name: true } },
  recipe: { select: { title: true } },
};

function toNotificationResponse(n) {
  return {
    id: n.id,
    type: n.type,
    actorId: n.actorId,
    actorName: n.actor.name,
    recipeId: n.recipeId,
    recipeTitle: n.recipe?.title ?? null,
    read: n.read,
    createdAt: n.createdAt,
  };
}

module.exports = {
  NOTIFICATION_TYPES, NOTIFICATIONS_PAGE_SIZE, NOTIFICATIONS_MAX_PAGE_SIZE, NOTIFICATION_SELECT,
  notify, notifyRecipeAuthor, toNotificationResponse,
};
