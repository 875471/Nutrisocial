const prisma = require('../prismaClient');
const { normalize } = require('./normalize');

// BEDCA nombra los alimentos como "Nombre base, descripción" ("Calabaza, cruda",
// "Arroz, blanco, hervido"). Cada alimento se indexa por su nombre completo y por la
// parte anterior a la primera coma (head), que es lo que suele escribir un usuario.
function indexFood(food) {
  const full = normalize(food.name);
  const head = full.split(',')[0].trim();
  return { food, full, head, words: new Set(full.replace(/,/g, ' ').split(' ')) };
}

// Palabras que no aportan al comparar por palabras ("pechuga de pollo" ~ "Pollo, pechuga").
const STOPWORDS = new Set(['de', 'del', 'la', 'el', 'los', 'las', 'con', 'en', 'y', 'al', 'a']);

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

// La tabla Food solo cambia con `npx prisma db seed`, así que se carga una vez y se
// mantiene en memoria (unos pocos cientos de filas). Tras un seed hay que reiniciar.
let indexPromise = null;
function loadIndex() {
  indexPromise ??= prisma.food.findMany().then((foods) => foods.map(indexFood));
  return indexPromise;
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
async function matchFood(name) {
  if (!normalize(name)) return null;
  const [best] = rank(await loadIndex(), name, false);
  return best?.entry.food ?? null;
}

// Sugerencias para el autocompletado: incluye también coincidencias por subcadena.
async function searchFoods(query, limit = 10) {
  if (normalize(query).length < 2) return [];
  return rank(await loadIndex(), query, true)
    .slice(0, limit)
    .map((r) => r.entry.food);
}

async function findFoodById(id) {
  return (await loadIndex()).find((e) => e.food.id === id)?.food ?? null;
}

module.exports = { matchFood, searchFoods, findFoodById };
