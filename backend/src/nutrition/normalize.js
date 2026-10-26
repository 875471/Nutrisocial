// Normaliza un texto para compararlo: minúsculas, sin tildes ni diéresis y con los
// espacios colapsados (la ñ queda como n en ambos lados de la comparación). Conserva las comas porque matchFood las usa para separar el
// nombre base de un alimento de BEDCA ("Calabaza, cruda") de su descripción.
function normalize(text) {
  return String(text ?? '')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9,\s]/g, ' ')
    .replace(/\s+/g, ' ')
    .replace(/\s*,\s*/g, ', ')
    .trim();
}

// Palabras que no aportan al comparar nombres por palabras ("pechuga de pollo" ~ "Pollo, pechuga").
const STOPWORDS = new Set(['de', 'del', 'la', 'el', 'los', 'las', 'con', 'en', 'y', 'al', 'a']);

// Singular aproximado: "macarrones" → "macarron", "huevos" → "huevo". Las palabras
// cortas se dejan tal cual para no tocar "sal" o "gas".
function singular(word) {
  if (word.length <= 3) return word;
  if (/[lnrdj]es$/.test(word)) return word.slice(0, -2);
  return word.endsWith('s') ? word.slice(0, -1) : word;
}

// Palabras de un nombre normalizado, sin comas y en singular
// ("Huevos" → ["huevo"], "Calabaza, cruda" → ["calabaza", "cruda"]).
function words(name) {
  return normalize(name).replace(/,/g, ' ').split(' ').filter(Boolean).map(singular);
}

// true si la secuencia `inner` aparece seguida dentro de `outer`, palabra a palabra.
// Se compara por palabras completas para que "sal" no cubra "salsa de tomate".
function containsWords(outer, inner) {
  if (inner.length === 0 || inner.length > outer.length) return false;
  for (let start = 0; start + inner.length <= outer.length; start++) {
    if (inner.every((w, i) => outer[start + i] === w)) return true;
  }
  return false;
}

module.exports = { normalize, STOPWORDS, words, containsWords };
