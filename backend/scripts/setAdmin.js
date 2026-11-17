// Da (o quita, con --off) el rol de administrador a una cuenta existente. Un administrador puede
// ocultar recetas y comentarios de cualquiera (ver src/social/moderation.js). No hay pantalla ni
// ruta para hacerlo: el rol se asigna a mano, contra la base de datos que indique el .env.
//
//   node scripts/setAdmin.js --email=tu@email.com [--off]
require('dotenv').config({ path: require('node:path').resolve(__dirname, '../.env') });

const prisma = require('../src/prismaClient');

const EMAIL = (process.argv.find((a) => a.startsWith('--email=')) || '').slice('--email='.length).trim();
const ON = !process.argv.includes('--off');

async function main() {
  if (!EMAIL) throw new Error('Uso: node scripts/setAdmin.js --email=tu@email.com [--off]');
  const user = await prisma.user.update({ where: { email: EMAIL }, data: { isAdmin: ON }, select: { name: true } })
    .catch(() => null);
  if (!user) throw new Error(`No hay ninguna cuenta con el email ${EMAIL}`);
  console.log(`${user.name} (${EMAIL}) ${ON ? 'es administrador' : 'ya no es administrador'}.`);
}

main()
  .catch((err) => {
    console.error(err.message);
    process.exitCode = 1;
  })
  .finally(() => prisma.$disconnect());
