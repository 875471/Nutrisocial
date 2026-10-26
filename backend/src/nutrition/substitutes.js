const { words, containsWords } = require('./normalize');

// Grupos de ingredientes que se consideran sustituibles entre sí para cocinar una receta.
// No implica que tengan los mismos macros: eso se sigue calculando con el ingrediente real
// que haya en la despensa, nunca con el de la receta.
const SUBSTITUTE_GROUPS = [
  ['mantequilla', 'margarina'],
  ['leche', 'leche semidesnatada', 'leche desnatada'],
  ['nata', 'nata para cocinar'],
  ['aceite de oliva', 'aceite de girasol'],
  ['azucar', 'azucar moreno'],
  ['yogur', 'yogur griego'],
];

// Cada miembro ya troceado en palabras, con el índice de su grupo.
const MEMBERS = SUBSTITUTE_GROUPS.flatMap((group, groupIndex) =>
  group.map((member) => ({ groupIndex, words: words(member) })));

// Miembro más específico (el de más palabras) contenido en el nombre, o null. Así
// "leche semidesnatada" es "leche semidesnatada" y no solo "leche".
function memberOf(nameWords) {
  let best = null;
  for (const member of MEMBERS) {
    if (containsWords(nameWords, member.words) && (!best || member.words.length > best.words.length)) {
      best = member;
    }
  }
  return best;
}

// true si los dos nombres (ya troceados con words()) son miembros distintos del mismo grupo.
// Dos nombres que dan el mismo miembro no son un sustituto sino el mismo ingrediente; si no
// se cubrieron es porque su foodId dice que son alimentos distintos, y eso manda.
function areSubstitutes(wordsA, wordsB) {
  const a = memberOf(wordsA);
  const b = memberOf(wordsB);
  return a != null && b != null && a !== b && a.groupIndex === b.groupIndex;
}

module.exports = { SUBSTITUTE_GROUPS, areSubstitutes };
