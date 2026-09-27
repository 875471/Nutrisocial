const { normalize, STOPWORDS } = require('./normalize');

// Open Food Facts: base de datos colaborativa y gratuita de productos envasados. Se usa
// solo como respaldo cuando un ingrediente no está en BEDCA (ver matchFood.js).
const SOURCE = 'OpenFoodFacts';
const SEARCH_URL = 'https://world.openfoodfacts.org/cgi/search.pl';
// Su política de uso pide identificar la aplicación y una forma de contacto.
const USER_AGENT = 'NutriSocial-TFG/1.0 (contacto: 875471@unizar.es)';
const TIMEOUT_MS = 5000;
const PAGE_SIZE = 5;
const MAX_NAME_LENGTH = 120;
// La API limita las búsquedas (unas 10 por minuto por cliente) y responde con una página
// de error si se pasa. Se deja margen y, al llegar al límite, no se consulta: el
// ingrediente se queda sin datos igual que si Open Food Facts no lo tuviera.
const MAX_REQUESTS_PER_MINUTE = 8;
// Respuestas recientes por término, para no repetir la misma búsqueda (p. ej. un
// ingrediente que no existe en ninguna de las dos fuentes, en recetas distintas).
const RESPONSE_TTL_MS = 10 * 60 * 1000;
const MAX_CACHED_RESPONSES = 200;

const recentRequests = [];
const responses = new Map();

// Valor numérico de un nutriente, o null si falta o no es un número válido. La API
// devuelve números, pero en productos antiguos algunos llegan como texto.
function nutrient(nutriments, key) {
  const raw = nutriments?.[key];
  if (raw === null || raw === undefined || raw === '') return null;
  const value = Number(raw);
  // Vienen con ruido de coma flotante (2.6700000762939): dos decimales bastan.
  return Number.isFinite(value) && value >= 0 ? Math.round(value * 100) / 100 : null;
}

// Producto de Open Food Facts → alimento con valores por 100 g, o null si le falta
// alguno de los cuatro valores (muchos productos tienen la ficha incompleta).
function toFood(product) {
  const name = String(product?.product_name_es || product?.product_name || '').trim().slice(0, MAX_NAME_LENGTH);
  if (!normalize(name)) return null;
  const n = product.nutriments;
  const values = {
    kcal: nutrient(n, 'energy-kcal_100g'),
    protein: nutrient(n, 'proteins_100g'),
    carbs: nutrient(n, 'carbohydrates_100g'),
    fat: nutrient(n, 'fat_100g'),
  };
  if (Object.values(values).some((v) => v === null)) return null;
  // Más de 100 g de un macro en 100 g de producto es un error de la ficha.
  if (values.protein > 100 || values.carbs > 100 || values.fat > 100 || values.kcal > 900) return null;
  return { name, ...values, source: SOURCE };
}

function words(text) {
  return normalize(text).replace(/,/g, ' ').split(' ').filter(Boolean);
}

// Misma palabra salvo plurales sencillos ("pepitas" ~ "pepita", "limones" ~ "limon").
function sameWord(a, b) {
  const forms = (w) => [w, w.replace(/es$/, ''), w.replace(/s$/, '')].filter(Boolean);
  const fb = forms(b);
  return forms(a).some((f) => fb.includes(f));
}

// El término se parece al nombre del producto si cada una de sus palabras significativas
// aparece en él y, además, el nombre empieza por la primera de ellas. Lo segundo evita
// quedarse con "Galletas de mantequilla con chips de chocolate" al buscar "chips de
// chocolate": se prefiere no encontrar nada a sumar las kcal de otro producto.
// Con `prefix` (autocompletado) la última palabra puede estar a medio escribir.
function matchesTerm(term, productName, { prefix = false } = {}) {
  const termWords = words(term).filter((w) => !STOPWORDS.has(w));
  const nameWords = words(productName).filter((w) => !STOPWORDS.has(w));
  if (termWords.length === 0 || nameWords.length === 0) return false;
  const last = termWords.length - 1;
  const matches = (w, i, nw) => sameWord(w, nw) || (prefix && i === last && nw.startsWith(w));
  return matches(termWords[0], 0, nameWords[0]) && termWords.every((w, i) => nameWords.some((nw) => matches(w, i, nw)));
}

// Registra una petición si no se ha llegado al límite por minuto; si se ha llegado, false.
function takeRequestSlot(now = Date.now()) {
  while (recentRequests.length && now - recentRequests[0] > 60 * 1000) recentRequests.shift();
  if (recentRequests.length >= MAX_REQUESTS_PER_MINUTE) return false;
  recentRequests.push(now);
  return true;
}

// Productos en bruto de la búsqueda, o null si la API falla (red, tiempo agotado,
// respuesta inesperada). Nunca lanza: un fallo externo no debe romper una receta.
async function fetchProducts(term) {
  const key = normalize(term);
  const cached = responses.get(key);
  if (cached && Date.now() - cached.at < RESPONSE_TTL_MS) return cached.products;
  if (!takeRequestSlot()) {
    console.warn(`Open Food Facts: límite de búsquedas por minuto alcanzado, se omite "${term}"`);
    return null;
  }
  const params = new URLSearchParams({
    search_terms: term,
    search_simple: '1',
    action: 'process',
    json: '1',
    page_size: String(PAGE_SIZE),
    lc: 'es',
    fields: 'product_name,product_name_es,nutriments',
    // Solo productos que se venden en España: nombres en español y marcas conocidas.
    tagtype_0: 'countries',
    tag_contains_0: 'contains',
    tag_0: 'spain',
  });
  try {
    const res = await globalThis.fetch(`${SEARCH_URL}?${params}`, {
      headers: { 'User-Agent': USER_AGENT, Accept: 'application/json' },
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
    if (!res.ok) {
      console.warn(`Open Food Facts respondió ${res.status} para "${term}"`);
      return null;
    }
    const body = await res.json();
    if (!Array.isArray(body?.products)) return null;
    // Solo se recuerdan las respuestas válidas: tras un fallo se vuelve a intentar.
    if (responses.size >= MAX_CACHED_RESPONSES) responses.delete(responses.keys().next().value);
    responses.set(key, { at: Date.now(), products: body.products });
    return body.products;
  } catch (err) {
    console.warn(`Open Food Facts no disponible para "${term}": ${err.message}`);
    return null;
  }
}

// Alimentos utilizables (datos completos y nombre parecido al término), sin nombres
// repetidos y en el orden de relevancia de Open Food Facts.
async function searchOpenFoodFactsCandidates(term, { prefix = false } = {}) {
  if (!normalize(term)) return [];
  const products = await fetchProducts(term);
  if (!products) return [];
  const seen = new Set();
  const foods = [];
  for (const product of products) {
    const food = toFood(product);
    if (!food || !matchesTerm(term, food.name, { prefix })) continue;
    const key = normalize(food.name);
    if (seen.has(key)) continue;
    seen.add(key);
    foods.push(food);
  }
  return foods;
}

// Mejor alimento de Open Food Facts para un ingrediente: { name, kcal, protein, carbs,
// fat, source }, o null si no hay ninguno usable o la API no responde.
async function searchOpenFoodFacts(name) {
  const [first] = await searchOpenFoodFactsCandidates(name);
  return first ?? null;
}

module.exports = { searchOpenFoodFacts, searchOpenFoodFactsCandidates, matchesTerm, toFood, SOURCE };
