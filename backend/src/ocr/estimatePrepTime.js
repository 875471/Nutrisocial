// Estimación del tiempo de preparación de una receta escaneada, que casi nunca lo indica.
//
//   minutos = 10 + 5 · pasos + 2 · ingredientes, redondeado a múltiplos de 5 y acotado a 10-180.
//
// No es un dato leído de la foto: es un punto de partida editable para que el campo no quede
// vacío. La base de 10 minutos cubre sacar y preparar los utensilios; cada paso suma 5 (un paso
// típico de receta casera, "pochar la cebolla", "batir los huevos", ronda esos minutos de
// trabajo activo) y cada ingrediente 2 (pesarlo, lavarlo, pelarlo o trocearlo). No puede saber
// si un paso es "hornear 40 minutos" o "dejar reposar una noche": por eso se acota a 3 horas y
// el usuario debe revisarlo (ver informe 16).

const BASE_MINUTES = 10;
const MINUTES_PER_STEP = 5;
const MINUTES_PER_INGREDIENT = 2;
const MIN_MINUTES = 10;
const MAX_MINUTES = 180;
const ROUND_TO = 5;

/** Minutos estimados para una receta con `steps` pasos e `ingredients` ingredientes. */
function estimatePrepMinutes(steps, ingredients) {
  const raw = BASE_MINUTES + MINUTES_PER_STEP * Math.max(0, steps) + MINUTES_PER_INGREDIENT * Math.max(0, ingredients);
  const rounded = Math.round(raw / ROUND_TO) * ROUND_TO;
  return Math.min(MAX_MINUTES, Math.max(MIN_MINUTES, rounded));
}

module.exports = { estimatePrepMinutes, MIN_MINUTES, MAX_MINUTES };
