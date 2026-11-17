// Estadísticas del diario en un periodo (GET /log/stats y, más adelante, el resumen semanal): una
// serie con un punto por día (también los días sin entradas, a cero, para que el gráfico no salte
// días) y las medias. Funciones puras, sin base de datos.

const STATS_PERIODS = [7, 30];
const DAY_MS = 24 * 60 * 60 * 1000;

const round1 = (value) => Math.round(value * 10) / 10;

/**
 * Días del periodo de `days` días que termina en `end` (incluido), como Date a las 00:00 UTC.
 * Devuelve { start, endExclusive, dates } con `dates` del más antiguo al más reciente.
 */
function periodDays(end, days) {
  const start = new Date(end.getTime() - (days - 1) * DAY_MS);
  const dates = Array.from({ length: days }, (_, i) => new Date(start.getTime() + i * DAY_MS));
  return { start, endExclusive: new Date(end.getTime() + DAY_MS), dates };
}

/**
 * Agrega las entradas (con date, kcal, protein, carbs, fat) por día. `dates` son los días del
 * periodo; `formatDay` convierte cada Date en "AAAA-MM-DD".
 * Las medias son por día CON registro: un día sin apuntar nada suele ser un olvido, no un ayuno, y
 * contarlo como 0 hundiría la media.
 */
function statsSeries(entries, dates, formatDay) {
  const byDay = new Map(dates.map((d) => [formatDay(d), { kcal: 0, protein: 0, carbs: 0, fat: 0, entries: 0 }]));
  for (const entry of entries) {
    const bucket = byDay.get(formatDay(entry.date));
    if (!bucket) continue;
    bucket.kcal += entry.kcal;
    bucket.protein += entry.protein;
    bucket.carbs += entry.carbs;
    bucket.fat += entry.fat;
    bucket.entries += 1;
  }
  const days = [...byDay.entries()].map(([date, b]) => ({
    date,
    kcal: Math.round(b.kcal),
    protein: round1(b.protein),
    carbs: round1(b.carbs),
    fat: round1(b.fat),
    entries: b.entries,
  }));
  const logged = days.filter((d) => d.entries > 0);
  const average = (key) => (logged.length === 0 ? null : round1(logged.reduce((sum, d) => sum + d[key], 0) / logged.length));
  return {
    days,
    daysLogged: logged.length,
    averages: logged.length === 0
      ? null
      : { kcal: Math.round(average('kcal')), protein: average('protein'), carbs: average('carbs'), fat: average('fat') },
  };
}

module.exports = { STATS_PERIODS, periodDays, statsSeries };
