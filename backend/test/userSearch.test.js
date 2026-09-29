// Pruebas del buscador de personas (funciones puras de social/userSearch.js).
const test = require('node:test');
const assert = require('node:assert/strict');

const { nameTerms, parseUserSearchQuery, searchUsers } = require('../src/social/userSearch');

const USERS = [
  { id: 1, name: 'Ana López' },
  { id: 2, name: 'Álvaro Núñez' },
  { id: 3, name: 'Marta Ruiz' },
  { id: 4, name: 'Omar' },
  { id: 5, name: 'ana belén' },
];

const search = (params) => searchUsers(USERS, { q: '', limit: 20, excludeId: null, ...params });

test('nameTerms: normaliza como los alimentos (minúsculas, sin tildes, ñ como n)', () => {
  assert.deepEqual(nameTerms('  ÁLVARO  Núñez '), ['alvaro', 'nunez']);
  assert.deepEqual(nameTerms(''), []);
});

test('searchUsers: sin tildes ni mayúsculas, por subcadena y con todas las palabras', () => {
  assert.deepEqual(search({ q: 'alvaro' }), [2]);
  assert.deepEqual(search({ q: 'NUÑEZ' }), [2]);
  // "mar" está en "Marta" y en "Omar".
  assert.deepEqual(search({ q: 'mar' }), [3, 4]);
  assert.deepEqual(search({ q: 'ana lopez' }), [1]);
  assert.deepEqual(search({ q: 'ana ruiz' }), []);
});

test('searchUsers: excluye a quien busca, ordena alfabéticamente y respeta el límite', () => {
  // La tilde y la mayúscula no cambian el orden: Álvaro va antes que Ana, y "ana belén" antes que "Ana López".
  assert.deepEqual(search({}), [2, 5, 1, 3, 4]);
  assert.deepEqual(search({ q: 'ana', excludeId: 1 }), [5]);
  assert.deepEqual(search({ limit: 2 }), [2, 5]);
});

test('parseUserSearchQuery: valores por defecto y errores', () => {
  assert.deepEqual(parseUserSearchQuery({}), { q: '', limit: 20 });
  assert.deepEqual(parseUserSearchQuery({ q: '  ana ', limit: '500' }), { q: 'ana', limit: 50 });
  assert.match(parseUserSearchQuery({ limit: 'x' }).error, /límite/);
  assert.match(parseUserSearchQuery({ q: ['a', 'b'] }).error, /texto/);
  assert.match(parseUserSearchQuery({ q: 'a'.repeat(101) }).error, /100/);
});
