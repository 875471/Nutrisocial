// Da Premium (plan anual, un año desde hoy) a una cuenta existente, sin pasar por la pantalla
// de planes de la app. Script puntual para preparar una demostración; no forma parte del seed.
// Se ejecuta contra la base de datos que indique el .env en ese momento:
//
//   node scripts/setPremium.js --email=tu@email.com
//
// Si la cuenta ya tenía una suscripción (activa o cancelada) la reactiva con un periodo nuevo,
// igual que POST /subscription/subscribe: ejecutarlo dos veces no crea dos filas.
require('dotenv').config({ path: require('node:path').resolve(__dirname, '../.env') });

const prisma = require('../src/prismaClient');
const { periodEnd } = require('../src/billing/plans');

const EMAIL = (process.argv.find((a) => a.startsWith('--email=')) || '').slice('--email='.length).trim();
const PLAN = 'annual';

async function main() {
  if (!EMAIL) throw new Error('Uso: node scripts/setPremium.js --email=tu@email.com');
  const user = await prisma.user.findUnique({ where: { email: EMAIL }, select: { id: true, name: true } });
  if (!user) throw new Error(`No hay ninguna cuenta con el email ${EMAIL}`);

  const now = new Date();
  const fields = { plan: PLAN, status: 'active', startedAt: now, currentPeriodEnd: periodEnd(PLAN, now), canceledAt: null };
  const subscription = await prisma.subscription.upsert({
    where: { userId: user.id },
    create: { userId: user.id, ...fields },
    update: fields,
  });
  console.log(`${user.name} (${EMAIL}) es Premium: plan ${subscription.plan} hasta ${subscription.currentPeriodEnd.toISOString().slice(0, 10)}.`);
}

main()
  .catch((err) => {
    console.error(err.message);
    process.exitCode = 1;
  })
  .finally(() => prisma.$disconnect());
