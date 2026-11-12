// Etiquetas dietéticas de una receta. Son AUTODECLARADAS por quien la publica: no se deducen de
// los ingredientes ni se verifican, así que no garantizan nada a alguien con una alergia o una
// intolerancia (la app lo avisa al elegirlas). Lista fija: la comparten las rutas de creación y
// edición y los filtros de los listados.
const DIET_TAGS = ['vegano', 'vegetariano', 'sin_gluten', 'sin_lactosa', 'sin_frutos_secos'];

const unknownTagsError = (unknown) => ({
  error: `Etiqueta dietética no válida: ${unknown.join(', ')}. Usa una de: ${DIET_TAGS.join(', ')}`,
});

/**
 * Valida `dietTags` del cuerpo de POST/PUT /recipes: una lista de etiquetas de DIET_TAGS. Quita
 * duplicados y las devuelve en el orden de DIET_TAGS. Devuelve { tags } o { error } para un 400.
 */
function cleanDietTags(value) {
  if (!Array.isArray(value) || !value.every((tag) => typeof tag === 'string')) {
    return { error: 'dietTags debe ser una lista de etiquetas' };
  }
  const unknown = [...new Set(value.filter((tag) => !DIET_TAGS.includes(tag)))];
  if (unknown.length > 0) return unknownTagsError(unknown);
  return { tags: DIET_TAGS.filter((tag) => value.includes(tag)) };
}

/**
 * Lee el filtro `?dietTags=vegano,sin_gluten` de un listado. Sin parámetro (o vacío), sin filtro:
 * { tags: [] }. También acepta el parámetro repetido (?dietTags=a&dietTags=b). Devuelve { tags }
 * o { error } si alguna etiqueta no existe.
 */
function parseDietTagsQuery(value) {
  if (value == null) return { tags: [] };
  const parts = (Array.isArray(value) ? value : [value]);
  if (!parts.every((part) => typeof part === 'string')) return { error: 'dietTags debe ser una lista separada por comas' };
  const tags = parts.flatMap((part) => part.split(',')).map((tag) => tag.trim()).filter(Boolean);
  return tags.length === 0 ? { tags: [] } : cleanDietTags(tags);
}

/**
 * Condición de Prisma para las recetas que tienen TODAS las etiquetas pedidas (no basta con
 * alguna: quien filtra "vegano" y "sin gluten" quiere las dos a la vez). Sin etiquetas, ninguna.
 */
function dietTagsWhere(tags) {
  return tags.length === 0 ? {} : { dietTags: { hasEvery: tags } };
}

module.exports = { DIET_TAGS, cleanDietTags, parseDietTagsQuery, dietTagsWhere };
