// Tokens de un solo uso para verificar el email y recuperar la contraseña.
//
// En la base de datos solo se guarda su hash SHA-256: si se filtrara la tabla User, los
// tokens pendientes no servirían para verificar cuentas ni cambiar contraseñas. SHA-256 sin
// sal basta aquí (a diferencia de las contraseñas, que van con bcrypt) porque los tokens son
// aleatorios y de alta entropía: no hay diccionario con el que atacarlos.
const crypto = require('node:crypto');

const VERIFICATION_TTL_MS = 24 * 60 * 60 * 1000;
const RESET_TTL_MS = 60 * 60 * 1000;

// Sin 0/O ni 1/I/L, que se confunden al copiarlos de un correo.
const CODE_ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
const CODE_LENGTH = 8;

/** Token del enlace de verificación: 32 bytes aleatorios en hexadecimal (256 bits). */
function generateVerificationToken() {
  return crypto.randomBytes(32).toString('hex');
}

/**
 * Código de recuperación para teclearlo en la app, p. ej. "K7QM-3XPD": 8 caracteres de 31
 * posibles (unos 40 bits). Es más corto que un enlace, así que va acompañado de caducidad de
 * 1 hora, un solo uso, el email de la cuenta y límite de intentos por IP.
 */
function generateResetCode() {
  let code = '';
  for (let i = 0; i < CODE_LENGTH; i++) code += CODE_ALPHABET[crypto.randomInt(CODE_ALPHABET.length)];
  return `${code.slice(0, 4)}-${code.slice(4)}`;
}

/** Forma canónica de un código tecleado: sin espacios ni guiones y en mayúsculas. */
function normalizeCode(code) {
  return String(code ?? '').replace(/[\s-]/g, '').toUpperCase();
}

function hashToken(token) {
  return crypto.createHash('sha256').update(token).digest('hex');
}

module.exports = {
  VERIFICATION_TTL_MS, RESET_TTL_MS, generateVerificationToken, generateResetCode, normalizeCode, hashToken,
};
