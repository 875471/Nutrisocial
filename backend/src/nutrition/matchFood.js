const prisma = require('../prismaClient');
const { normalize, STOPWORDS } = require('./normalize');
const { searchOpenFoodFacts, searchOpenFoodFactsCandidates, matchesTerm, SOURCE: OFF_SOURCE } = require('./openFoodFacts');

// BEDCA nombra los alimentos como "Nombre base, descripción" ("Calabaza, cruda",
// "Arroz, blanco, hervido"). Cada alimento se indexa por su nombre completo y por la
// parte anterior a la primera coma (head), que es lo que suele escribir un usuario.
function indexFood(food) {
  const full = normalize(food.name);
  const head = full.split(',')[0].trim();
  return { food, full, head, words: new Set(full.replace(/,/g, ' ').split(' ')) };
}

// Nombres genéricos para los que BEDCA tiene muchas variantes y ninguna regla elige
// la habitual en una receta ("huevo" daría "Huevo de pato, crudo"). Clave normalizada
// → nombre exacto en BEDCA. Si un nombre deja de existir en el dataset, se ignora.
const ALIASES = {
  huevo: 'Huevo de gallina fresco',
  pollo: 'Pollo, entero, con piel, crudo',
  pan: 'Pan blanco, de barra',
  harina: 'Harina de trigo',
  azucar: 'Azúcar blanca',
  leche: 'Leche de vaca, entera',
  mantequilla: 'Mantequilla salada',
  pasta: 'Pasta alimenticia, cruda',
  macarron: 'Pasta alimenticia, cruda',
  espagueti: 'Pasta alimenticia, cruda',
  ternera: 'Ternera, parte sin especificar, cruda, con grasa separable',
  pimiento: 'Pimiento rojo, crudo',
  aceite: 'Aceite de oliva',
};

// La tabla Food se carga una vez y se mantiene en memoria (unos pocos cientos de filas).
// Solo cambia con `npx prisma db seed` (tras un seed hay que reiniciar) o al guardar un
// producto de Open Food Facts, que se añade también al índice (ver cacheProduct).
let indexPromise = null;
function loadIndex() {
  indexPromise ??= prisma.food.findMany().then((foods) => foods.map(indexFood));
  return indexPromise;
}

const isOff = (entry) => entry.food.source === OFF_SOURCE;

// Guarda un producto de Open Food Facts en Food (clave: nombre) para no volver a pedirlo.
// Si ya existe un alimento con ese nombre se conserva tal cual: nunca se pisa uno de BEDCA.
// Devuelve null si no se puede guardar: el ingrediente queda sin datos, pero la receta se guarda.
async function cacheProduct(product) {
  let food;
  try {
    food = await prisma.food.upsert({ where: { name: product.name }, create: product, update: {} });
  } catch (err) {
    // Dos peticiones a la vez pueden intentar crear el mismo producto; la segunda falla
    // por el índice único, pero el alimento ya existe.
    food = await prisma.food.findUnique({ where: { name: product.name } }).catch(() => null);
    if (!food) {
      console.warn(`No se pudo guardar "${product.name}" de Open Food Facts: ${err.message}`);
      return null;
    }
  }
  const index = await loadIndex();
  if (!index.some((e) => e.food.id === food.id)) index.push(indexFood(food));
  return food;
}

// Producto de Open Food Facts ya guardado que corresponde al término, con el mismo
// criterio con el que se eligió al buscarlo; el nombre más corto es el más genérico.
function findCachedProduct(index, name) {
  return index
    .filter((e) => isOff(e) && matchesTerm(name, e.food.name))
    .sort((a, b) => a.full.length - b.full.length)[0]?.food ?? null;
}

// Variantes de la consulta para tolerar plurales sencillos ("cebollas", "limones").
function queryVariants(query) {
  const q = normalize(query).replace(/,.*$/, '').trim();
  const variants = new Set([q]);
  const words = q.split(' ');
  const last = words.at(-1);
  if (last.endsWith('es')) variants.add([...words.slice(0, -1), last.slice(0, -2)].join(' '));
  if (last.endsWith('s')) variants.add([...words.slice(0, -1), last.slice(0, -1)].join(' '));
  return [...variants].filter(Boolean);
}

// Distancia de Levenshtein, cortando en cuanto supera `max` (solo interesa si es pequeña).
function editDistance(a, b, max) {
  if (Math.abs(a.length - b.length) > max) return max + 1;
  let prev = Array.from({ length: b.length + 1 }, (_, j) => j);
  for (let i = 1; i <= a.length; i++) {
    const curr = [i];
    let rowMin = i;
    for (let j = 1; j <= b.length; j++) {
      curr[j] = Math.min(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1));
      rowMin = Math.min(rowMin, curr[j]);
    }
    if (rowMin > max) return max + 1;
    prev = curr;
  }
  return prev[b.length];
}

// Puntuación de un alimento para una consulta ya normalizada (0 = no coincide).
function score(entry, q, allowContains) {
  if (entry.full === q) return 100;
  if (ALIASES[q] && normalize(ALIASES[q]) === entry.full) return 95;
  if (entry.head === q) return 90;
  if (entry.head.startsWith(q + ' ')) return 70;     // "arroz integral" → "Arroz integral, crudo"
  const qWords = q.split(' ').filter((w) => !STOPWORDS.has(w));
  // Todas las palabras de la consulta están en el nombre: "leche entera" → "Leche de vaca, entera".
  // Va antes que la regla siguiente para no quedarse con "Leche, desnatada" por empezar igual.
  // Dentro de esta regla se prefiere el alimento cuyo nombre base empieza por la primera
  // palabra: "yogur natural" → "Yogur, …, natural" antes que "Mousse de yogur, natural".
  if (qWords.length > 0 && qWords.every((w) => entry.words.has(w))) {
    return entry.head.startsWith(qWords[0]) ? 67 : 65;
  }
  if (q.startsWith(entry.head + ' ')) return 60;     // "cebolla morada" → "Cebolla"
  if (allowContains && entry.full.includes(q)) return 30;
  // Último recurso para errores de escritura o de OCR ("cebola" → "Cebolla"): nombre base
  // a 1 letra de distancia (2 si es largo). Las palabras cortas se excluyen porque ahí una
  // letra cambia el alimento ("pera"/"pepa").
  if (q.length >= 5) {
    const maxDistance = q.length >= 8 ? 2 : 1;
    if (editDistance(q, entry.head, maxDistance) <= maxDistance) return 20;
  }
  return 0;
}

// A igualdad de puntuación se prefiere el alimento en crudo (es como se pesan los
// ingredientes al cocinar) y, después, el nombre más corto (el más genérico).
const RAW = /\b(crudo|cruda|crudos|crudas|fresco|fresca)\b/;
function compare(a, b) {
  return (
    b.score - a.score ||
    Number(RAW.test(b.entry.full)) - Number(RAW.test(a.entry.full)) ||
    // Para "cebolla morada" gana el head más largo que encaje, no el más corto.
    (a.score === 60 ? b.entry.head.length - a.entry.head.length : 0) ||
    a.entry.full.length - b.entry.full.length
  );
}

function rank(index, query, allowContains) {
  const variants = queryVariants(query);
  const results = [];
  for (const entry of index) {
    const best = Math.max(...variants.map((q) => score(entry, q, allowContains)));
    if (best > 0) results.push({ entry, score: best });
  }
  return results.sort(compare);
}

// Alimento de la tabla Food que mejor corresponde al nombre de un ingrediente, o
// null si ninguno es suficientemente parecido (no se aceptan meras subcadenas).
// BEDCA tiene prioridad; solo si no tiene nada se recurre a Open Food Facts, primero a
// los productos ya guardados y, si no hay, a su API (el resultado se guarda en Food).
async function matchFood(name) {
  if (!normalize(name)) return null;
  const index = await loadIndex();
  const [best] = rank(index.filter((e) => !isOff(e)), name, false);
  if (best) return best.entry.food;
  const cached = findCachedProduct(index, name);
  if (cached) return cached;
  const product = await searchOpenFoodFacts(name);
  return product ? cacheProduct(product) : null;
}

// Sugerencias para el autocompletado: incluye también coincidencias por subcadena.
// Primero BEDCA y después lo ya guardado de Open Food Facts. Si hay menos de 3, se
// completa con la API de Open Food Facts (a partir de 3 letras), y lo que devuelva se
// guarda en Food para que la próxima búsqueda lo encuentre en local.
const MIN_LOCAL_RESULTS = 3;
const MIN_EXTERNAL_QUERY = 3;
async function searchFoods(query, limit = 10) {
  if (normalize(query).length < 2) return [];
  const index = await loadIndex();
  const results = [
    ...rank(index.filter((e) => !isOff(e)), query, true),
    ...rank(index.filter(isOff), query, true),
  ].map((r) => r.entry.food);
  if (results.length < MIN_LOCAL_RESULTS && normalize(query).length >= MIN_EXTERNAL_QUERY) {
    const products = await searchOpenFoodFactsCandidates(query, { prefix: true });
    for (const product of products) {
      const food = await cacheProduct(product);
      if (food && !results.some((f) => f.id === food.id)) results.push(food);
    }
  }
  return results.slice(0, limit);
}

async function findFoodById(id) {
  return (await loadIndex()).find((e) => e.food.id === id)?.food ?? null;
}

module.exports = { matchFood, searchFoods, findFoodById };
