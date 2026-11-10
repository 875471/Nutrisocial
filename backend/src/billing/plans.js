// Planes de la suscripción Premium simulada. Es la única fuente de los precios: GET
// /subscription/plans los devuelve tal cual y la app no tiene ninguno escrito a mano.
const PLANS = {
  monthly: { id: 'monthly', priceCents: 399, currency: 'EUR', periodMonths: 1, label: 'Mensual' },
  annual: { id: 'annual', priceCents: 2999, currency: 'EUR', periodMonths: 12, label: 'Anual' },
};

function isValidPlan(plan) {
  return typeof plan === 'string' && Object.hasOwn(PLANS, plan);
}

/**
 * `date` + `months` meses de calendario, en UTC. Si el día no existe en el mes de destino se
 * queda en el último día de ese mes (31 de enero + 1 mes → 28 o 29 de febrero), en vez de
 * pasar al mes siguiente como haría Date.setUTCMonth.
 */
function addMonths(date, months) {
  const result = new Date(date.getTime());
  const day = result.getUTCDate();
  result.setUTCDate(1);
  result.setUTCMonth(result.getUTCMonth() + months);
  const lastDay = new Date(Date.UTC(result.getUTCFullYear(), result.getUTCMonth() + 1, 0)).getUTCDate();
  result.setUTCDate(Math.min(day, lastDay));
  return result;
}

/** Fin del periodo de un plan que empieza en `start`. */
function periodEnd(planId, start) {
  return addMonths(start, PLANS[planId].periodMonths);
}

module.exports = { PLANS, isValidPlan, addMonths, periodEnd };
