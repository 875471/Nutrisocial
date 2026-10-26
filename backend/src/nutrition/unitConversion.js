const { normalize } = require('./normalize');

// Gramos por unidad de medida. Los volúmenes están en mililitros de agua; toGrams los
// corrige con la densidad del ingrediente (DENSITY_G_PER_ML).
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

// Gramos por mililitro. 1.0 = como el agua (valor por defecto si el ingrediente no está
// aquí). Valores aproximados, de tablas de cocina habituales.
const DENSITY_G_PER_ML = {
  aceite: 0.92,
  harina: 0.53,
  azucar: 0.85,
  'azucar moreno': 0.82,
  sal: 1.2,
  miel: 1.42,
  mantequilla: 0.96,
  margarina: 0.96,
  nata: 1.01,
  leche: 1.03,
  arroz: 0.85, // crudo, en volumen tipo taza
  cacao: 0.5,
  'cacao en polvo': 0.5,
};

// Unidades que son volumen y por tanto dependen de la densidad.
const VOLUME_UNITS = new Set(['ml', 'l', 'cucharada', 'cucharadita', 'taza']);

const UNITS = [...Object.keys(GRAMS_PER_UNIT), 'unidad'];

// Busca en `table` una palabra clave del nombre. Las claves de varias palabras
// ("azucar moreno") se prueban antes, de la más larga a la más corta, para que ganen
// a la de una sola palabra ("azucar").
function lookupByKeyword(table, name) {
  const text = normalize(name).replace(/,/g, ' ').replace(/\s+/g, ' ');
  const phrases = Object.keys(table)
    .filter((key) => key.includes(' '))
    .sort((a, b) => b.length - a.length);
  const phrase = phrases.find((key) => ` ${text} `.includes(` ${key} `));
  if (phrase) return table[phrase];
  for (const word of text.split(' ')) {
    // Admite el plural: "huevos", "limones".
    const singular = [word, word.replace(/s$/, ''), word.replace(/es$/, '')];
    const key = singular.find((w) => table[w] != null);
    if (key) return table[key];
  }
  return null;
}

const pieceWeight = (name) => lookupByKeyword(GRAMS_PER_PIECE, name);
const density = (name) => lookupByKeyword(DENSITY_G_PER_ML, name);

// Convierte una cantidad a gramos. Devuelve null si no se puede (unidad
// desconocida, o "unidad" de un alimento sin peso por pieza conocido).
// `names` son los nombres en los que buscar el peso por pieza o la densidad: el que
// escribió el usuario y, si lo hay, el del alimento de BEDCA asociado.
function toGrams(quantity, unit, ...names) {
  if (typeof quantity !== 'number' || !(quantity > 0)) return null;
  if (unit === 'unidad') {
    const weight = names.map(pieceWeight).find((w) => w != null);
    return weight == null ? null : quantity * weight;
  }
  const factor = GRAMS_PER_UNIT[unit];
  if (factor == null) return null;
  if (!VOLUME_UNITS.has(unit)) return quantity * factor;
  const gramsPerMl = names.map(density).find((d) => d != null) ?? 1;
  return quantity * factor * gramsPerMl;
}

module.exports = { UNITS, toGrams };
