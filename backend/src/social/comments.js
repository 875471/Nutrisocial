// Comentarios de recetas y vistas previas del feed ("Le gusta a...", últimos comentarios):
// validación y formato de las respuestas, sin acceso a la base de datos.

const MAX_COMMENT_LENGTH = 500;
const COMMENTS_PAGE_SIZE = 20;
const COMMENTS_MAX_PAGE_SIZE = 50;
const LIKERS_PREVIEW = 3;
const COMMENTS_PREVIEW = 2;

// Lo más reciente primero; el id desempata comentarios o likes del mismo instante.
const NEWEST_FIRST = [{ createdAt: 'desc' }, { id: 'desc' }];

/**
 * Valida el texto de un comentario nuevo. Devuelve { value } recortado o { error } para un 400.
 */
function validateCommentText(input) {
  if (typeof input !== 'string') return { error: 'El comentario debe ser un texto' };
  const text = input.trim();
  if (!text) return { error: 'El comentario no puede estar vacío' };
  if (text.length > MAX_COMMENT_LENGTH) {
    return { error: `El comentario tiene ${text.length} caracteres y el máximo es ${MAX_COMMENT_LENGTH}` };
  }
  return { value: text };
}

// `select` de Prisma para un comentario con el nombre de su autor.
const COMMENT_SELECT = { id: true, text: true, createdAt: true, userId: true, user: { select: { name: true } } };

function toCommentResponse(comment) {
  return {
    id: comment.id,
    text: comment.text,
    createdAt: comment.createdAt,
    authorId: comment.userId,
    authorName: comment.user.name,
  };
}

// Fragmento de `select`/`include` con los últimos comentarios de la receta para la tarjeta.
function commentsPreviewField() {
  return {
    comments: {
      orderBy: NEWEST_FIRST,
      take: COMMENTS_PREVIEW,
      select: { id: true, text: true, user: { select: { name: true } } },
    },
  };
}

function toCommentsPreview(comments) {
  return (comments ?? []).map((c) => ({ id: c.id, text: c.text, authorName: c.user.name }));
}

// `select` de la consulta aparte que trae quién ha dado los últimos likes. No cabe en la
// consulta principal: la relación `likes` ya se usa allí para saber si el usuario dio el suyo.
function likersSelect() {
  return {
    id: true,
    likes: { orderBy: NEWEST_FIRST, take: LIKERS_PREVIEW, select: { user: { select: { name: true } } } },
  };
}

// Convierte las filas de likersSelect en un mapa id de receta → nombres (el más reciente primero).
// Cada usuario da como mucho un like por receta, así que los nombres ya son distintos.
function likersByRecipe(rows) {
  return new Map(rows.map((r) => [r.id, r.likes.map((l) => l.user.name)]));
}

module.exports = {
  MAX_COMMENT_LENGTH,
  COMMENTS_PAGE_SIZE,
  COMMENTS_MAX_PAGE_SIZE,
  LIKERS_PREVIEW,
  COMMENTS_PREVIEW,
  NEWEST_FIRST,
  validateCommentText,
  COMMENT_SELECT,
  toCommentResponse,
  commentsPreviewField,
  toCommentsPreview,
  likersSelect,
  likersByRecipe,
};
