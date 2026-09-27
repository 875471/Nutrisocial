// Fechas de calendario ("YYYY-MM-DD") sin hora. El cliente decide qué día es según su zona
// horaria y lo envía como texto; aquí se guarda siempre como las 00:00 UTC de ese día, de
// modo que la zona horaria del servidor no desplaza nunca una entrada al día anterior.

const DAY_RE = /^(\d{4})-(\d{2})-(\d{2})$/;

/** "2026-09-27" → Date a las 00:00 UTC, o null si el texto no es una fecha real. */
function parseDay(text) {
  if (typeof text !== 'string') return null;
  const m = text.match(DAY_RE);
  if (!m) return null;
  const [year, month, day] = [Number(m[1]), Number(m[2]), Number(m[3])];
  const date = new Date(Date.UTC(year, month - 1, day));
  // Date.UTC acepta "2026-02-31" y lo pasa a marzo: se rechaza comparando de vuelta.
  if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day) return null;
  return date;
}

/** Date → "YYYY-MM-DD" (en UTC, coherente con parseDay). */
function formatDay(date) {
  return date.toISOString().slice(0, 10);
}

module.exports = { parseDay, formatDay };
