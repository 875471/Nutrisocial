// Datos de DEMOSTRACIÓN: usuarios, recetas y "me gusta" ficticios para que el feed, el
// buscador por despensa y las recomendaciones tengan contenido al enseñar la aplicación.
//
// NO forma parte del seed automático (`npx prisma db seed`, que carga BEDCA): se ejecuta a
// mano, una vez, contra la base de datos que indique el .env en ese momento:
//
//   node prisma/seedDemoData.js --dry-run   # solo lee: muestra cómo se empareja cada ingrediente
//   node prisma/seedDemoData.js             # crea lo que falte
//
// Es idempotente: los usuarios se buscan por email, las recetas por autor y título, y los
// likes usan el índice único (usuario, receta). Ejecutarlo dos veces no duplica nada.
//
// Los emails usan el dominio reservado ".example" (RFC 2606): no existen ni pueden recibir
// correo, así que nunca se escribirá a una persona real. Todos comparten la contraseña
// DEMO_PASSWORD y se crean con el email ya verificado.
//
// La tabla Food debe tener BEDCA cargada (`npx prisma db seed`) para que las recetas salgan
// con kcal y macros. Los ingredientes se emparejan con matchFood, igual que en POST /recipes,
// pero sin consultar Open Food Facts: así el resultado es reproducible y no depende de un
// servicio externo con límite de peticiones.
require('dotenv').config();
const bcrypt = require('bcryptjs');

// Sin red: matchFood tratará como "sin datos" lo que no esté en BEDCA en vez de ir a Open Food Facts.
globalThis.fetch = async () => { throw new Error('Open Food Facts desactivado en el seed de demostración'); };

const prisma = require('../src/prismaClient');
const { resolveIngredients } = require('../src/nutrition/calculate');

const DRY_RUN = process.argv.includes('--dry-run');
const DEMO_PASSWORD = 'demo1234';
const DOMAIN = 'nutrisocial.example';

const USERS = [
  ['Lucía Martín', 'lucia.martin'],
  ['Hugo Fernández', 'hugo.fernandez'],
  ['Carmen Ruiz', 'carmen.ruiz'],
  ['Pablo Gómez', 'pablo.gomez'],
  ['Marta Sánchez', 'marta.sanchez'],
  ['Javier López', 'javier.lopez.demo'],
  ['Elena Navarro', 'elena.navarro'],
  ['Daniel Torres', 'daniel.torres'],
  ['Sofía Romero', 'sofia.romero'],
  ['Alejandro Díaz', 'alejandro.diaz'],
  ['Paula Moreno', 'paula.moreno'],
  ['Adrián Jiménez', 'adrian.jimenez'],
  ['Irene Castro', 'irene.castro'],
  ['Sergio Ortega', 'sergio.ortega'],
].map(([name, local]) => ({ name, email: `${local}@${DOMAIN}` }));

// [título, autor (índice en USERS), raciones, minutos, ingredientes [nombre, cantidad, unidad], pasos]
const RECIPES = [
  // ---- Desayunos ----
  ['Porridge de avena con plátano', 0, 1, 10,
    [['avena', 50, 'g'], ['leche', 200, 'ml'], ['plátano', 1, 'unidad'], ['canela', 1, 'pizca']],
    ['Calentar la leche en un cazo.', 'Añadir la avena y cocer 5 minutos removiendo.', 'Servir con el plátano en rodajas y la canela.']],
  ['Tostadas con tomate y aceite', 3, 2, 5,
    [['pan', 120, 'g'], ['tomate', 1, 'unidad'], ['aceite de oliva', 2, 'cucharada'], ['sal', 1, 'pizca']],
    ['Tostar el pan.', 'Rallar el tomate y extenderlo sobre el pan.', 'Aliñar con el aceite y la sal.']],
  ['Yogur griego con fresas y nueces', 6, 1, 5,
    [['yogur griego', 125, 'g'], ['fresa', 100, 'g'], ['nuez', 20, 'g'], ['miel', 1, 'cucharadita']],
    ['Lavar y trocear las fresas.', 'Servir el yogur con las fresas, las nueces picadas y la miel por encima.']],
  ['Tortitas de avena y huevo', 8, 2, 15,
    [['avena', 80, 'g'], ['huevo', 2, 'unidad'], ['leche', 100, 'ml'], ['plátano', 1, 'unidad']],
    ['Triturar todos los ingredientes hasta tener una masa lisa.', 'Cuajar pequeñas porciones en una sartén antiadherente, 2 minutos por cada lado.']],
  ['Tostada de aguacate y huevo', 11, 1, 10,
    [['pan', 60, 'g'], ['aguacate', 0.5, 'unidad'], ['huevo', 1, 'unidad'], ['sal', 1, 'pizca']],
    ['Tostar el pan y machacar el aguacate con la sal.', 'Cocer el huevo 7 minutos y cortarlo en láminas.', 'Montar la tostada con el aguacate y el huevo.']],
  ['Batido de plátano y avena', 4, 1, 5,
    [['leche', 250, 'ml'], ['plátano', 1, 'unidad'], ['avena', 30, 'g'], ['canela', 1, 'pizca']],
    ['Triturar todo en la batidora hasta que no queden grumos.', 'Servir frío.']],
  // ---- Comidas ----
  ['Lentejas con verduras', 2, 4, 50,
    [['lentejas', 300, 'g'], ['cebolla', 1, 'unidad'], ['zanahoria', 2, 'unidad'], ['pimiento rojo', 1, 'unidad'], ['ajo', 2, 'unidad'], ['aceite de oliva', 2, 'cucharada'], ['sal', 1, 'pizca']],
    ['Picar la cebolla, el ajo y el pimiento y pocharlos en el aceite.', 'Añadir la zanahoria en rodajas y las lentejas lavadas.', 'Cubrir de agua y cocer 40 minutos a fuego medio.', 'Rectificar de sal.']],
  ['Garbanzos con espinacas', 5, 4, 30,
    [['garbanzo hervido', 400, 'g'], ['espinacas', 250, 'g'], ['ajo', 3, 'unidad'], ['comino', 1, 'cucharadita'], ['aceite de oliva', 3, 'cucharada']],
    ['Dorar los ajos laminados en el aceite.', 'Añadir las espinacas y rehogar hasta que reduzcan.', 'Incorporar los garbanzos escurridos y el comino y cocinar 10 minutos.']],
  ['Arroz con pollo y verduras', 1, 4, 45,
    [['arroz', 320, 'g'], ['pollo', 500, 'g'], ['pimiento rojo', 1, 'unidad'], ['calabacín', 1, 'unidad'], ['tomate triturado', 200, 'g'], ['aceite de oliva', 3, 'cucharada']],
    ['Dorar el pollo troceado en el aceite y reservar.', 'Sofreír el pimiento y el calabacín, y añadir el tomate.', 'Incorporar el arroz y el pollo, cubrir con el doble de agua y cocer 18 minutos.', 'Dejar reposar 5 minutos antes de servir.']],
  ['Merluza al horno con patatas', 7, 2, 40,
    [['merluza', 400, 'g'], ['patata', 2, 'unidad'], ['cebolla', 1, 'unidad'], ['aceite de oliva', 3, 'cucharada'], ['perejil', 1, 'cucharada']],
    ['Precalentar el horno a 200 ºC.', 'Hornear las patatas y la cebolla en rodajas con el aceite 25 minutos.', 'Colocar la merluza encima y hornear 12 minutos más.', 'Espolvorear el perejil picado.']],
  ['Macarrones con tomate y atún', 9, 4, 20,
    [['pasta', 400, 'g'], ['tomate frito', 300, 'g'], ['atún en aceite', 160, 'g'], ['cebolla', 1, 'unidad'], ['queso mozzarella', 100, 'g']],
    ['Cocer la pasta según el paquete.', 'Pochar la cebolla y añadir el tomate y el atún escurrido.', 'Mezclar con la pasta y gratinar con el queso.']],
  ['Pollo al limón con arroz', 12, 2, 30,
    [['pechuga de pollo', 300, 'g'], ['zumo de limón', 3, 'cucharada'], ['ajo', 2, 'unidad'], ['arroz', 150, 'g'], ['aceite de oliva', 2, 'cucharada']],
    ['Cocer el arroz y reservar.', 'Dorar el pollo en tiras con el ajo en el aceite.', 'Añadir el zumo de limón y dejar reducir 3 minutos.', 'Servir junto al arroz.']],
  ['Ensalada de quinoa y garbanzos', 6, 2, 20,
    [['quinoa', 120, 'g'], ['garbanzo hervido', 200, 'g'], ['tomate', 2, 'unidad'], ['pepino', 1, 'unidad'], ['aceite de oliva', 2, 'cucharada']],
    ['Cocer la quinoa 12 minutos y enfriarla.', 'Trocear el tomate y el pepino.', 'Mezclar todo con los garbanzos y aliñar con el aceite.']],
  ['Espaguetis a la boloñesa', 13, 4, 40,
    [['pasta', 400, 'g'], ['carne picada de ternera', 400, 'g'], ['tomate triturado', 400, 'g'], ['cebolla', 1, 'unidad'], ['zanahoria', 1, 'unidad'], ['aceite de oliva', 2, 'cucharada']],
    ['Picar la cebolla y la zanahoria y pocharlas en el aceite.', 'Añadir la carne y dorarla.', 'Incorporar el tomate y cocer 25 minutos a fuego lento.', 'Mezclar con la pasta recién cocida.']],
  ['Salmón a la plancha con verduras', 0, 2, 20,
    [['salmón', 300, 'g'], ['calabacín', 1, 'unidad'], ['berenjena', 1, 'unidad'], ['aceite de oliva', 2, 'cucharada'], ['sal', 1, 'pizca']],
    ['Cortar las verduras en rodajas y hacerlas a la plancha.', 'Marcar el salmón 3 minutos por cada lado.', 'Servir con las verduras y un poco de sal.']],
  ['Alubias blancas con chorizo', 3, 4, 90,
    [['alubias blancas', 300, 'g'], ['chorizo', 120, 'g'], ['cebolla', 1, 'unidad'], ['pimiento rojo', 1, 'unidad'], ['ajo', 2, 'unidad']],
    ['Dejar las alubias en remojo la noche anterior.', 'Cocerlas con la cebolla, el pimiento y el ajo 75 minutos.', 'Añadir el chorizo en rodajas los últimos 15 minutos.']],
  ['Pollo al curry con arroz', 10, 4, 35,
    [['pechuga de pollo', 500, 'g'], ['cebolla', 1, 'unidad'], ['leche de coco', 200, 'ml'], ['arroz', 250, 'g'], ['aceite de oliva', 2, 'cucharada']],
    ['Pochar la cebolla en el aceite.', 'Añadir el pollo troceado y dorarlo.', 'Incorporar la leche de coco y cocer 15 minutos.', 'Servir con el arroz cocido.']],
  ['Crema de calabaza', 2, 4, 35,
    [['calabaza', 800, 'g'], ['cebolla', 1, 'unidad'], ['patata', 1, 'unidad'], ['aceite de oliva', 2, 'cucharada'], ['sal', 1, 'pizca']],
    ['Pelar y trocear la calabaza, la cebolla y la patata.', 'Pocharlo todo en el aceite 5 minutos.', 'Cubrir de agua y cocer 20 minutos.', 'Triturar y rectificar de sal.']],
  // ---- Cenas ----
  ['Tortilla de patatas', 1, 4, 40,
    [['patata', 600, 'g'], ['huevo', 6, 'unidad'], ['cebolla', 1, 'unidad'], ['aceite de oliva', 4, 'cucharada'], ['sal', 1, 'pizca']],
    ['Pelar y cortar las patatas y la cebolla en láminas finas.', 'Pocharlas en el aceite a fuego suave 20 minutos.', 'Mezclar con los huevos batidos y la sal.', 'Cuajar la tortilla en la sartén por ambos lados.']],
  ['Revuelto de champiñones y ajos tiernos', 8, 2, 15,
    [['champiñón', 250, 'g'], ['huevo', 4, 'unidad'], ['ajo', 2, 'unidad'], ['aceite de oliva', 1, 'cucharada']],
    ['Laminar los champiñones y el ajo y saltearlos en el aceite.', 'Añadir los huevos batidos y remover a fuego suave hasta que cuajen.']],
  ['Ensalada mediterránea', 5, 2, 10,
    [['lechuga', 150, 'g'], ['tomate', 2, 'unidad'], ['pepino', 1, 'unidad'], ['atún en aceite', 80, 'g'], ['aceite de oliva', 2, 'cucharada']],
    ['Lavar y trocear las verduras.', 'Añadir el atún escurrido.', 'Aliñar con el aceite justo antes de servir.']],
  ['Sopa de verduras', 12, 4, 35,
    [['zanahoria', 2, 'unidad'], ['puerro', 1, 'unidad'], ['calabacín', 1, 'unidad'], ['patata', 1, 'unidad'], ['aceite de oliva', 1, 'cucharada']],
    ['Trocear todas las verduras.', 'Rehogarlas 5 minutos en el aceite.', 'Cubrir con 1,5 l de agua y cocer 25 minutos.']],
  ['Pechuga de pavo con pisto', 9, 2, 30,
    [['pavo', 300, 'g'], ['calabacín', 1, 'unidad'], ['pimiento rojo', 1, 'unidad'], ['tomate triturado', 200, 'g'], ['cebolla', 1, 'unidad'], ['aceite de oliva', 2, 'cucharada']],
    ['Hacer el pisto: pochar la cebolla, el pimiento y el calabacín y añadir el tomate.', 'Cocinar 15 minutos a fuego lento.', 'Hacer el pavo a la plancha y servir con el pisto.']],
  ['Calabacín relleno de atún', 13, 2, 35,
    [['calabacín', 2, 'unidad'], ['atún en aceite', 160, 'g'], ['tomate frito', 100, 'g'], ['cebolla', 1, 'unidad'], ['queso mozzarella', 60, 'g']],
    ['Vaciar los calabacines partidos por la mitad.', 'Sofreír la cebolla con la pulpa del calabacín, el atún y el tomate.', 'Rellenar, cubrir con el queso y hornear 20 minutos a 190 ºC.']],
  ['Gambas al ajillo', 7, 2, 10,
    [['gamba', 300, 'g'], ['ajo', 4, 'unidad'], ['aceite de oliva', 4, 'cucharada'], ['perejil', 1, 'cucharada']],
    ['Calentar el aceite con los ajos laminados.', 'Añadir las gambas peladas y saltear 2 minutos.', 'Espolvorear el perejil y servir enseguida.']],
  ['Tofu salteado con verduras', 11, 2, 20,
    [['tofu', 250, 'g'], ['pimiento rojo', 1, 'unidad'], ['zanahoria', 1, 'unidad'], ['calabacín', 1, 'unidad'], ['aceite de oliva', 2, 'cucharada']],
    ['Cortar el tofu en dados y dorarlo en el aceite.', 'Añadir las verduras en tiras y saltear a fuego fuerte 6 minutos.']],
  // ---- Postres ----
  ['Bizcocho de yogur', 4, 8, 50,
    [['yogur', 125, 'g'], ['harina de trigo', 250, 'g'], ['azúcar', 200, 'g'], ['huevo', 3, 'unidad'], ['aceite de girasol', 100, 'ml']],
    ['Precalentar el horno a 180 ºC.', 'Batir los huevos con el azúcar y añadir el yogur y el aceite.', 'Incorporar la harina tamizada.', 'Hornear 40 minutos en un molde engrasado.']],
  ['Manzanas asadas con canela', 10, 4, 40,
    [['manzana', 4, 'unidad'], ['canela', 1, 'cucharadita'], ['miel', 2, 'cucharada'], ['nuez', 30, 'g']],
    ['Descorazonar las manzanas.', 'Rellenarlas con las nueces, la miel y la canela.', 'Hornear 35 minutos a 180 ºC.']],
  ['Arroz con leche', 0, 6, 50,
    [['leche', 1, 'l'], ['arroz', 150, 'g'], ['azúcar', 120, 'g'], ['canela', 1, 'cucharadita']],
    ['Cocer el arroz en la leche a fuego muy suave 40 minutos, removiendo a menudo.', 'Añadir el azúcar y cocer 5 minutos más.', 'Servir frío con canela por encima.']],
  ['Mousse de chocolate', 6, 4, 20,
    [['chocolate negro', 150, 'g'], ['huevo', 3, 'unidad'], ['azúcar', 40, 'g']],
    ['Fundir el chocolate al baño maría.', 'Mezclarlo con las yemas.', 'Montar las claras con el azúcar e incorporarlas con movimientos envolventes.', 'Enfriar al menos 3 horas.']],
  ['Macedonia de frutas', 2, 4, 15,
    [['naranja', 2, 'unidad'], ['manzana', 1, 'unidad'], ['plátano', 2, 'unidad'], ['fresa', 200, 'g']],
    ['Pelar y trocear toda la fruta.', 'Mezclarla con el zumo de una de las naranjas y servir fría.']],
];

// Reparte los likes de forma determinista (el mismo resultado en cada ejecución): cada
// usuario da like a las recetas cuyo (índice de usuario + índice de receta) cae en ciertas
// posiciones. Nadie da like a sus propias recetas.
function likesFor(userIndex, recipes) {
  return recipes.filter((r, i) => r.authorIndex !== userIndex && (userIndex * 7 + i * 3) % 5 < 2);
}

async function main() {
  const foodCount = await prisma.food.count();
  if (foodCount === 0) {
    throw new Error('La tabla Food está vacía: carga antes BEDCA con `npx prisma db seed`.');
  }
  console.log(`${DRY_RUN ? '[prueba, sin escribir] ' : ''}Food tiene ${foodCount} alimentos.`);

  // ---- Usuarios ----
  const hash = await bcrypt.hash(DEMO_PASSWORD, 10);
  const users = [];
  let createdUsers = 0;
  for (const u of USERS) {
    let user = await prisma.user.findUnique({ where: { email: u.email }, select: { id: true, email: true } });
    if (!user && !DRY_RUN) {
      user = await prisma.user.create({
        data: { email: u.email, name: u.name, password: hash, emailVerified: true },
        select: { id: true, email: true },
      });
      createdUsers++;
    }
    users.push(user);
  }

  // ---- Recetas ----
  // Fechas repartidas en los últimos 30 días, de la más antigua a la más reciente, para que el
  // feed tenga un orden natural.
  const now = Date.now();
  const DAY = 24 * 60 * 60 * 1000;
  const recipes = [];
  let createdRecipes = 0;
  for (const [i, [title, authorIndex, servings, prepMinutes, ingredients, steps]] of RECIPES.entries()) {
    const author = users[authorIndex];
    const existing = author && await prisma.recipe.findFirst({ where: { authorId: author.id, title }, select: { id: true } });
    const input = ingredients.map(([name, quantity, unit]) => ({ name, quantity, unit, foodId: null }));
    const { ingredients: resolved, totals } = await resolveIngredients(input);

    if (DRY_RUN) {
      const unmatched = resolved.filter((r) => r.foodId == null).map((r) => r.name);
      const kcal = Math.round(totals.kcal / servings);
      console.log(`${existing ? '(ya existe) ' : ''}${title}: ${kcal} kcal/ración` +
        (unmatched.length ? ` · sin datos: ${unmatched.join(', ')}` : ''));
      continue;
    }
    if (existing) {
      recipes.push({ id: existing.id, authorIndex });
      continue;
    }
    const recipe = await prisma.recipe.create({
      data: {
        title,
        steps: JSON.stringify(steps),
        servings,
        prepMinutes,
        ...totals,
        authorId: author.id,
        createdAt: new Date(now - (RECIPES.length - i) * DAY + (i % 7) * 3600 * 1000),
        ingredients: { create: resolved },
      },
      select: { id: true },
    });
    recipes.push({ id: recipe.id, authorIndex });
    createdRecipes++;
  }
  if (DRY_RUN) return;

  // ---- Likes ----
  let likes = 0;
  for (const [userIndex, user] of users.entries()) {
    for (const recipe of likesFor(userIndex, recipes)) {
      await prisma.recipeLike.upsert({
        where: { userId_recipeId: { userId: user.id, recipeId: recipe.id } },
        create: { userId: user.id, recipeId: recipe.id },
        update: {},
      });
      likes++;
    }
  }

  console.log(`Usuarios: ${createdUsers} creados, ${USERS.length - createdUsers} ya existían.`);
  console.log(`Recetas: ${createdRecipes} creadas, ${RECIPES.length - createdRecipes} ya existían.`);
  console.log(`Me gusta: ${likes} (los que ya existían no se duplican).`);
  console.log(`Contraseña de todas las cuentas de demostración: ${DEMO_PASSWORD} (emails @${DOMAIN}).`);
}

main()
  .catch((err) => {
    console.error(err.message);
    process.exitCode = 1;
  })
  .finally(() => prisma.$disconnect());
