// Utilidades comunes de las evaluaciones: distancia de edición, métricas, números aleatorios
// reproducibles y formato de los informes. No dependen de ninguna librería externa.
const fs = require('node:fs');
const path = require('node:path');

/** Distancia de Levenshtein entre dos secuencias (cadenas o arrays de palabras). */
function levenshtein(a, b) {
  let prev = Array.from({ length: b.length + 1 }, (_, j) => j);
  for (let i = 1; i <= a.length; i++) {
    const curr = [i];
    for (let j = 1; j <= b.length; j++) {
      curr[j] = Math.min(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1));
    }
    prev = curr;
  }
  return prev[b.length];
}

/** Similitud entre 0 y 1: 1 − distancia / longitud de la más larga (1 si las dos están vacías). */
function similarity(a, b) {
  const longest = Math.max(a.length, b.length);
  return longest === 0 ? 1 : 1 - levenshtein(a, b) / longest;
}

/** Precisión, exhaustividad (recall) y F1 a partir de aciertos, propuestos y esperados. */
function prf(truePositives, predicted, expected) {
  const precision = predicted === 0 ? (expected === 0 ? 1 : 0) : truePositives / predicted;
  const recall = expected === 0 ? (predicted === 0 ? 1 : 0) : truePositives / expected;
  const f1 = precision + recall === 0 ? 0 : (2 * precision * recall) / (precision + recall);
  return { precision, recall, f1 };
}

const mean = (values) => (values.length === 0 ? NaN : values.reduce((s, v) => s + v, 0) / values.length);

function median(values) {
  if (values.length === 0) return NaN;
  const sorted = [...values].sort((x, y) => x - y);
  const mid = Math.floor(sorted.length / 2);
  return sorted.length % 2 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
}

/**
 * Generador pseudoaleatorio con semilla (mulberry32): la misma semilla da siempre la misma
 * secuencia, así que cada ejecución de una evaluación reproduce exactamente los mismos números.
 */
function seededRandom(seed) {
  let a = seed >>> 0;
  return function random() {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** Copia barajada de `items` (Fisher-Yates) con el generador `random`. */
function shuffle(items, random) {
  const copy = [...items];
  for (let i = copy.length - 1; i > 0; i--) {
    const j = Math.floor(random() * (i + 1));
    [copy[i], copy[j]] = [copy[j], copy[i]];
  }
  return copy;
}

const pick = (items, random) => items[Math.floor(random() * items.length)];

// ---- Formato ----

/** 0.8532 → "85,3 %". */
function pct(value, decimals = 1) {
  if (!Number.isFinite(value)) return '—';
  return `${(value * 100).toFixed(decimals).replace('.', ',')} %`;
}

/** 12.345 → "12,3" (coma decimal, como en la memoria). */
function num(value, decimals = 1) {
  if (!Number.isFinite(value)) return '—';
  return value.toFixed(decimals).replace('.', ',');
}

/** Tabla en Markdown a partir de una cabecera y filas (arrays de celdas). */
function table(header, rows) {
  const line = (cells) => `| ${cells.join(' | ')} |`;
  return [line(header), line(header.map(() => '---')), ...rows.map(line)].join('\n');
}

/** Escribe el informe en eval/results/<name>.md y lo muestra por consola. */
function writeReport(name, markdown) {
  const dir = path.join(__dirname, '..', 'results');
  fs.mkdirSync(dir, { recursive: true });
  const file = path.join(dir, `${name}.md`);
  fs.writeFileSync(file, `${markdown.trim()}\n`, 'utf8');
  console.log(markdown);
  console.log(`\nInforme guardado en ${path.relative(process.cwd(), file)}`);
}

/** Fecha de hoy "AAAA-MM-DD" en la zona horaria local, para fechar los informes. */
function today() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

module.exports = {
  levenshtein, similarity, prf, mean, median, seededRandom, shuffle, pick, pct, num, table, writeReport, today,
};
