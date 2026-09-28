// Buscador de recetas por texto libre (GET /recipes/search): filtra por título e ingredientes
// y ordena. Funciones puras sobre recetas ya cargadas (id, título, nombres de ingredientes,
// prepMinutes y createdAt); la ruta carga la página resultante con todos sus datos.
const { normalize, STOPWORDS } = require('../nutrition/normalize');

const SORT_FIELDS = ['createdAt', 'prepMinutes'];
const ORDERS = ['asc', 'desc'];
const MAX_QUERY_LENGTH = 100;

// Singular aproximado de una palabra de la búsqueda ("patatas" → "patata", "limones" → "limon"),
// para que el plural encuentre también el singular. Las palabras cortas se dejan tal cual.
function singular(word) {
  if (word.length <= 3) return word;
  if (/[lnrdj]es$/.test(word)) return word.slice(0, -2);
  return word.endsWith('s') ? word.slice(0, -1) : word;
}

/** Palabras de la búsqueda, normalizadas y sin palabras vacías ("de", "con"...). */
function queryTerms(q) {
  return normalize(q).replace(/,/g, ' ').split(' ')
    .filter((w) => w && !STOPWORDS.has(w))
    .map(singular);
}

// Texto en el que se busca: título e ingredientes, normalizados y separados por "|" para que
// una palabra no pueda empezar en el título y acabar en un ingrediente.
function searchableText(recipe) {
  return [recipe.title, ...recipe.ingredients.map((i) => i.name)].map(normalize).join(' | ');
}

/**
 * true si todas las palabras de la búsqueda aparecen en el título o en algún ingrediente
 * (como subcadena: "pollo" encuentra "pechuga de pollo" y "arroz" encuentra "Arroz con leche").
 */
function matchesQuery(recipe, terms) {
  if (terms.length === 0) return true;
  const text = searchableText(recipe);
  return terms.every((term) => text.includes(term));
}

/**
 * Orden de los resultados. Por tiempo, las recetas sin tiempo indicado van siempre al final
 * (no se sabe si son rápidas o lentas); a igualdad, la más reciente primero y, después, el id.
 */
function compareRecipes(sortBy, order) {
  const sign = order === 'asc' ? 1 : -1;
  const newestFirst = (a, b) => b.createdAt - a.createdAt || b.id - a.id;
  if (sortBy === 'prepMinutes') {
    return (a, b) => {
      const aMissing = a.prepMinutes == null;
      const bMissing = b.prepMinutes == null;
      if (aMissing !== bMissing) return aMissing ? 1 : -1;
      return (aMissing ? 0 : sign * (a.prepMinutes - b.prepMinutes)) || newestFirst(a, b);
    };
  }
  return (a, b) => sign * (a.createdAt - b.createdAt) || sign * (a.id - b.id);
}

/**
 * Valida los parámetros de búsqueda. Devuelve { q, sortBy, order } con los valores por defecto
 * (todas las recetas, las más recientes primero) o { error } para un 400.
 */
function parseSearchQuery(query) {
  const q = query.q == null ? '' : query.q;
  if (typeof q !== 'string') return { error: 'La búsqueda debe ser un texto' };
  if (q.length > MAX_QUERY_LENGTH) return { error: `La búsqueda no puede pasar de ${MAX_QUERY_LENGTH} caracteres` };
  const sortBy = query.sortBy || 'createdAt';
  if (!SORT_FIELDS.includes(sortBy)) return { error: `sortBy debe ser ${SORT_FIELDS.join(' o ')}` };
  const order = query.order || 'desc';
  if (!ORDERS.includes(order)) return { error: 'order debe ser asc o desc' };
  return { q: q.trim(), sortBy, order };
}

/**
 * Filtra, ordena y pagina. `cursor` es el id de la última receta recibida (null para la
 * primera página). Devuelve { ids, nextCursor, total } o { error } si el cursor no está en los
 * resultados (por ejemplo, porque la receta se ha borrado entre una página y la siguiente).
 */
function searchRecipes(recipes, { q, sortBy, order, cursor, limit }) {
  const terms = queryTerms(q);
  const results = recipes.filter((r) => matchesQuery(r, terms)).sort(compareRecipes(sortBy, order));
  let start = 0;
  if (cursor != null) {
    const index = results.findIndex((r) => r.id === cursor);
    if (index === -1) return { error: 'Cursor no válido: vuelve a empezar la búsqueda' };
    start = index + 1;
  }
  const page = results.slice(start, start + limit);
  const hasMore = start + limit < results.length;
  return {
    ids: page.map((r) => r.id),
    nextCursor: hasMore ? page[page.length - 1].id : null,
    total: results.length,
  };
}

module.exports = { SORT_FIELDS, MAX_QUERY_LENGTH, queryTerms, matchesQuery, compareRecipes, parseSearchQuery, searchRecipes };
