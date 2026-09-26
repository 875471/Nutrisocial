// Carga la tabla Food con los alimentos de BEDCA congelados en data/bedca_foods.json.
// Ejecutar con: npx prisma db seed
//
// Origen de los datos: el JSON lo genera scripts/fetch_bedca.py con pybedca, una
// librería NO oficial que envuelve el servicio público de consultas de BEDCA (BEDCA
// no ofrece una API oficial). Por eso el dataset se versiona en el repositorio en vez
// de consultarse en vivo: el seed es reproducible y no depende de que ese servicio
// siga disponible ni de volver a descargarlo en cada clon.
const fs = require('fs');
const path = require('path');
const { PrismaClient } = require('@prisma/client');

const prisma = new PrismaClient();
const DATA_FILE = path.join(__dirname, '..', 'data', 'bedca_foods.json');

async function main() {
  const foods = JSON.parse(fs.readFileSync(DATA_FILE, 'utf-8'));

  // Upsert por nombre: si el alimento ya existe se actualizan sus valores, así que
  // el seed puede repetirse tras regenerar el JSON sin duplicar filas. Se agrupa en
  // una transacción para que SQLite no haga un commit por cada alimento.
  await prisma.$transaction(
    foods.map(({ name, kcal, protein, carbs, fat, source }) =>
      prisma.food.upsert({
        where: { name },
        update: { kcal, protein, carbs, fat, source },
        create: { name, kcal, protein, carbs, fat, source },
      }),
    ),
  );

  console.log(`Seed completado: ${foods.length} alimentos de BEDCA en la tabla Food`);
}

main()
  .catch((err) => {
    console.error(err);
    process.exitCode = 1;
  })
  .finally(() => prisma.$disconnect());
