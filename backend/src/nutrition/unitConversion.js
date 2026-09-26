const { normalize } = require('./normalize');

// Gramos por unidad de medida. Los volúmenes se convierten con densidad 1 (como el
// agua): es exacto para agua, leche o caldo y aproximado para aceite o harina.
const GRAMS_PER_UNIT = {
  g: 1,
  kg: 1000,
  ml: 1,
  l: 1000,
  cucharada: 15,
  cucharadita: 5,
  taza: 240,
  pizca: 0.5,
};

// "unidad" depende del alimento: peso medio aproximado de una pieza de los más
// habituales. Se busca la palabra clave en el nombre del ingrediente.
const GRAMS_PER_PIECE = {
  huevo: 60,
  cebolla: 150,
  cebolleta: 60,
  ajo: 5, // un diente
  tomate: 120,
  patata: 170,
  zanahoria: 80,
  pimiento: 160,
  calabacin: 250,
  berenjena: 250,
  pepino: 250,
  puerro: 150,
  limon: 120,
  naranja: 200,
  manzana: 180,
  pera: 170,
  platano: 120,
  aguacate: 170,
  yogur: 125,
  rebanada: 30, // de pan
};

const UNITS = [...Object.keys(GRAMS_PER_UNIT), 'unidad'];

function pieceWeight(name) {
  const words = normalize(name).replace(/,/g, ' ').split(' ');
  for (const word of words) {
    // Admite el plural: "huevos", "limones".
    const singular = [word, word.replace(/s$/, ''), word.replace(/es$/, '')];
    const key = singular.find((w) => GRAMS_PER_PIECE[w] != null);
    if (key) return GRAMS_PER_PIECE[key];
  }
  return null;
}

// Convierte una cantidad a gramos. Devuelve null si no se puede (unidad
// desconocida, o "unidad" de un alimento sin peso por pieza conocido).
// `names` son los nombres en los que buscar el peso por pieza: el que escribió el
// usuario y, si lo hay, el del alimento de BEDCA asociado.
function toGrams(quantity, unit, ...names) {
  if (typeof quantity !== 'number' || !(quantity > 0)) return null;
  if (unit === 'unidad') {
    const weight = names.map(pieceWeight).find((w) => w != null);
    return weight == null ? null : quantity * weight;
  }
  const factor = GRAMS_PER_UNIT[unit];
  return factor == null ? null : quantity * factor;
}

module.exports = { UNITS, toGrams };
