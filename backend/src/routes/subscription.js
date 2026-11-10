// Suscripción Premium SIMULADA: no hay cobro, la propia llamada a /subscribe hace de pago.
const express = require('express');
const prisma = require('../prismaClient');
const requireAuth = require('../middleware/auth');
const { PLANS, isValidPlan, periodEnd } = require('../billing/plans');

const router = express.Router();
router.use(requireAuth);

function toStatusResponse(subscription) {
  if (!subscription) return { isPremium: false };
  return {
    isPremium: subscription.status === 'active',
    plan: subscription.plan,
    status: subscription.status,
    startedAt: subscription.startedAt,
    currentPeriodEnd: subscription.currentPeriodEnd,
  };
}

// Los planes y sus precios, para que la pantalla de planes no los tenga escritos en la app.
router.get('/plans', (req, res) => {
  res.json(PLANS);
});

router.get('/', async (req, res) => {
  const subscription = await prisma.subscription.findUnique({ where: { userId: req.userId } });
  res.json(toStatusResponse(subscription));
});

// Alta o renovación: siempre empieza un periodo nuevo desde ahora, también si ya había una
// suscripción (activa o cancelada). Un usuario tiene como mucho una fila (userId es único).
router.post('/subscribe', async (req, res) => {
  const { plan } = req.body ?? {};
  if (!isValidPlan(plan)) {
    return res.status(400).json({ error: `El plan debe ser uno de: ${Object.keys(PLANS).join(', ')}` });
  }
  const now = new Date();
  const fields = { plan, status: 'active', startedAt: now, currentPeriodEnd: periodEnd(plan, now), canceledAt: null };
  const subscription = await prisma.subscription.upsert({
    where: { userId: req.userId },
    create: { userId: req.userId, ...fields },
    update: fields,
  });
  res.json(toStatusResponse(subscription));
});

// Cancelar quita Premium al momento; en una suscripción real se mantendría hasta currentPeriodEnd.
router.post('/cancel', async (req, res) => {
  const subscription = await prisma.subscription.findUnique({ where: { userId: req.userId } });
  if (subscription?.status !== 'active') {
    return res.status(404).json({ error: 'No tienes ninguna suscripción activa' });
  }
  const canceled = await prisma.subscription.update({
    where: { userId: req.userId },
    data: { status: 'canceled', canceledAt: new Date() },
  });
  res.json(toStatusResponse(canceled));
});

module.exports = router;
