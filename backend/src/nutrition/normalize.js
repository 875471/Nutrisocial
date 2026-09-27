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

module.exports = { normalize, STOPWORDS };
