// Deja pasar solo a usuarios con una suscripción Premium activa. Va después de requireAuth,
// que es quien deja el id en req.userId. Una suscripción cancelada no da acceso aunque su
// currentPeriodEnd no haya llegado: cancelar revoca Premium al momento (simplificación del
// modelo simulado, ver informe 26).
async function requirePremium(req, res, next) {
  // Como en requireAuth: el cliente se pide en cada llamada para que las pruebas lo sustituyan.
  const prisma = require('../prismaClient');
  const subscription = await prisma.subscription.findUnique({
    where: { userId: req.userId },
    select: { status: true },
  });
  if (subscription?.status !== 'active') {
    return res.status(403).json({ error: 'Esta función es solo para usuarias Premium.' });
  }
  next();
}

module.exports = requirePremium;
