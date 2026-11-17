// Moderación mínima de contenido social: una receta o un comentario puede quedar OCULTO
// (`hidden = true`). No se borra: el contenido sigue en la base de datos y quien lo ocultó puede
// volver a mostrarlo. Lo oculto no sale en el feed, el buscador, las guardadas, el buscador por
// despensa ni en los comentarios, y su detalle responde 404 a quien no puede moderarlo.
//
// Quién puede ocultar:
//   - una receta: su autor o un administrador;
//   - un comentario: su autor, el autor de la receta comentada (modera su propia conversación)
//     o un administrador.
// Administrador es un usuario con `isAdmin = true`, que solo se activa con scripts/setAdmin.js.

// Fragmento de `where` para lo que se puede enseñar a cualquiera.
const VISIBLE = Object.freeze({ hidden: false });

// Valida el cuerpo de PUT .../hidden. Devuelve { value } o { error } para un 400.
function parseHiddenBody(body) {
  const hidden = body?.hidden;
  if (typeof hidden !== 'boolean') return { error: 'Indica "hidden": true para ocultar o false para volver a mostrar' };
  return { value: hidden };
}

function canModerateRecipe(recipe, user) {
  return Boolean(user?.isAdmin) || recipe.authorId === user?.id;
}

// `comment` lleva userId y recipe.authorId.
function canModerateComment(comment, user) {
  return Boolean(user?.isAdmin) || comment.userId === user?.id || comment.recipe?.authorId === user?.id;
}

// ¿Puede `user` ver la receta? Las visibles, todos; las ocultas, solo quien puede moderarlas.
function canSeeRecipe(recipe, user) {
  return !recipe.hidden || canModerateRecipe(recipe, user);
}

module.exports = { VISIBLE, parseHiddenBody, canModerateRecipe, canModerateComment, canSeeRecipe };
