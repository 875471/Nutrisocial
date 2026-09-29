// Buscador de personas (GET /users/search): filtra por nombre sin depender de tildes ni
// mayúsculas. Funciones puras sobre usuarios ya cargados ({ id, name }); la ruta completa
// después los datos de la página resultante.
const { normalize } = require('../nutrition/normalize');

const USERS_PAGE_SIZE = 20;
const USERS_MAX_PAGE_SIZE = 50;
const MAX_QUERY_LENGTH = 100;

/** Palabras de la búsqueda, normalizadas igual que los nombres de alimentos (ver normalize.js). */
function nameTerms(q) {
  return normalize(q).replace(/,/g, ' ').split(' ').filter(Boolean);
}

/**
 * Valida `q` y `limit`. Devuelve { q, limit } (sin `q`, cadena vacía; sin `limit`, 20) o
 * { error } para un 400.
 */
function parseUserSearchQuery(query) {
  const q = query.q ?? '';
  if (typeof q !== 'string') return { error: 'La búsqueda debe ser un texto' };
  if (q.length > MAX_QUERY_LENGTH) return { error: `La búsqueda no puede pasar de ${MAX_QUERY_LENGTH} caracteres` };
  let limit = USERS_PAGE_SIZE;
  if (query.limit != null && query.limit !== '') {
    limit = Number(query.limit);
    if (!Number.isInteger(limit) || limit < 1) return { error: 'El límite debe ser un número entero positivo' };
  }
  return { q: q.trim(), limit: Math.min(limit, USERS_MAX_PAGE_SIZE) };
}

/**
 * Ids de los usuarios cuyo nombre contiene todas las palabras de `q` (como subcadena: "mar"
 * encuentra a "Marta" y a "Omar"), sin `excludeId` (quien busca), por orden alfabético y como
 * mucho `limit`. Sin palabras, todos: sirve para descubrir gente a la que seguir.
 */
function searchUsers(users, { q, limit, excludeId }) {
  const terms = nameTerms(q);
  return users
    .filter((u) => u.id !== excludeId)
    .filter((u) => {
      const name = normalize(u.name);
      return terms.every((term) => name.includes(term));
    })
    .sort((a, b) => a.name.localeCompare(b.name, 'es', { sensitivity: 'base' }) || a.id - b.id)
    .slice(0, limit)
    .map((u) => u.id);
}

module.exports = { USERS_PAGE_SIZE, USERS_MAX_PAGE_SIZE, nameTerms, parseUserSearchQuery, searchUsers };
