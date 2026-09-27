// Fotos de receta y "me gusta": validación y cálculos que no dependen de la base de datos.

// Tamaño máximo de la foto ya decodificada. El móvil la redimensiona a 800 px y la comprime
// a JPEG antes de enviarla, así que una foto normal ronda los 50-150 KB.
const MAX_IMAGE_BYTES = 2 * 1024 * 1024;

const BASE64_RE = /^[A-Za-z0-9+/]+={0,2}$/;

// Firmas de los formatos admitidos, para no guardar cualquier texto que parezca Base64.
function isSupportedImage(head) {
  const jpeg = head[0] === 0xff && head[1] === 0xd8 && head[2] === 0xff;
  const png = head[0] === 0x89 && head.toString('latin1', 1, 4) === 'PNG';
  const webp = head.toString('latin1', 0, 4) === 'RIFF' && head.toString('latin1', 8, 12) === 'WEBP';
  return jpeg || png || webp;
}

/**
 * Valida la foto recibida. `null` o ausente significa "sin foto". Devuelve { value } con el
 * Base64 limpio (o null), o { error } con el mensaje para un 400.
 */
function validateImageBase64(input) {
  if (input == null) return { value: null };
  if (typeof input !== 'string') return { error: 'La foto debe enviarse como texto Base64' };

  // Se tolera el prefijo "data:image/jpeg;base64," aunque la app no lo manda.
  const clean = input.replace(/^data:image\/[a-z]+;base64,/i, '').replace(/\s+/g, '');
  if (!clean) return { value: null };
  if (clean.length % 4 !== 0 || !BASE64_RE.test(clean)) {
    return { error: 'La foto no es un texto Base64 válido' };
  }

  const padding = clean.endsWith('==') ? 2 : clean.endsWith('=') ? 1 : 0;
  const bytes = (clean.length / 4) * 3 - padding;
  if (bytes > MAX_IMAGE_BYTES) {
    const mb = (bytes / (1024 * 1024)).toFixed(1).replace('.', ',');
    return { error: `La foto ocupa ${mb} MB y el máximo es 2 MB. Redúcela o comprímela más.` };
  }
  if (!isSupportedImage(Buffer.from(clean.slice(0, 16), 'base64'))) {
    return { error: 'La foto debe ser una imagen JPEG, PNG o WebP' };
  }
  return { value: clean };
}

// Fragmento de `include`/`select` de Prisma con lo necesario para likesCount y likedByMe:
// el total de likes y, como mucho, el like del propio usuario.
function likeFields(userId) {
  return {
    _count: { select: { likes: true } },
    likes: { where: { userId }, select: { id: true }, take: 1 },
  };
}

// Traduce lo cargado con likeFields a los dos campos de la respuesta.
function likeSummary(recipe) {
  return {
    likesCount: recipe._count?.likes ?? 0,
    likedByMe: (recipe.likes?.length ?? 0) > 0,
  };
}

// Solo el autor puede cambiar la foto de su receta.
function canEditRecipe(recipe, userId) {
  return recipe.authorId === userId;
}

module.exports = { MAX_IMAGE_BYTES, validateImageBase64, likeFields, likeSummary, canEditRecipe };
