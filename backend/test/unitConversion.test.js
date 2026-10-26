// Pruebas de la conversión de cantidades a gramos con densidad por ingrediente.
const test = require('node:test');
const assert = require('node:assert/strict');
const { toGrams } = require('../src/nutrition/unitConversion');

test('una cucharada de aceite y una de harina pesan distinto', () => {
  const aceite = toGrams(1, 'cucharada', 'Aceite de oliva');
  const harina = toGrams(1, 'cucharada', 'Harina de trigo');
  assert.equal(aceite, 15 * 0.92);
  assert.equal(harina, 15 * 0.53);
  assert.notEqual(aceite, harina);
});

test('un ingrediente que no está en la tabla sigue usando densidad 1', () => {
  assert.equal(toGrams(1, 'cucharada', 'Vinagre'), 15);
  assert.equal(toGrams(2, 'taza', 'Caldo de pollo'), 480);
});

test('la densidad se busca también en el nombre del alimento de BEDCA y gana la clave más larga', () => {
  assert.equal(toGrams(100, 'ml', 'AOVE', 'Aceite de oliva virgen extra'), 92);
  assert.equal(toGrams(1, 'taza', 'Azúcar moreno'), 240 * 0.82);
});

test('las unidades de peso no se corrigen por densidad', () => {
  assert.equal(toGrams(100, 'g', 'Harina'), 100);
  assert.equal(toGrams(1, 'kg', 'Aceite'), 1000);
});
