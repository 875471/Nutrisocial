const express = require('express');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const prisma = require('../prismaClient');
const requireAuth = require('../middleware/auth');
const { createAuthLimiter } = require('../middleware/rateLimit');

const router = express.Router();

const MIN_PASSWORD_LENGTH = 8;
// bcrypt solo usa los primeros 72 bytes: lo que pase de ahí se ignoraría sin avisar.
const MAX_PASSWORD_BYTES = 72;
const MAX_NAME_LENGTH = 50;
const MAX_EMAIL_LENGTH = 254;
// Comprobación básica: algo@algo.dominio, sin espacios. La verificación real sería un correo
// de confirmación, que queda fuera del alcance (ver informe 13).
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

// Solo cuentan los intentos fallidos: un usuario que entra bien no gasta su cupo.
const loginLimiter = createAuthLimiter({ skipSuccessfulRequests: true });
// En el registro cuentan todos, para frenar también la creación masiva de cuentas.
const registerLimiter = createAuthLimiter();
// Borrar la cuenta pide la contraseña: se limita igual que el login para que un token robado
// no sirva para adivinarla a base de intentos.
const deleteAccountLimiter = createAuthLimiter({ skipSuccessfulRequests: true });

/** Valida los datos de registro. Devuelve { data } limpios o { error } para un 400. */
function validateRegistration({ email, password, name } = {}) {
  if (typeof email !== 'string' || typeof password !== 'string' || typeof name !== 'string') {
    return { error: 'Faltan campos: email, password, name' };
  }
  const cleanEmail = email.trim();
  const cleanName = name.trim();
  if (!cleanName) return { error: 'El nombre no puede estar vacío' };
  if (cleanName.length > MAX_NAME_LENGTH) return { error: `El nombre no puede superar ${MAX_NAME_LENGTH} caracteres` };
  if (cleanEmail.length > MAX_EMAIL_LENGTH || !EMAIL_RE.test(cleanEmail)) {
    return { error: 'El formato del email no es válido' };
  }
  if (password.length < MIN_PASSWORD_LENGTH) {
    return { error: `La contraseña debe tener al menos ${MIN_PASSWORD_LENGTH} caracteres` };
  }
  if (Buffer.byteLength(password, 'utf8') > MAX_PASSWORD_BYTES) {
    return { error: 'La contraseña es demasiado larga (máximo 72 caracteres sin tildes)' };
  }
  return { data: { email: cleanEmail, password, name: cleanName } };
}

// Datos públicos del usuario: nunca se devuelve el hash de la contraseña.
function toUserResponse(user) {
  return { id: user.id, email: user.email, name: user.name };
}

router.post('/register', registerLimiter, async (req, res) => {
  const { data, error } = validateRegistration(req.body ?? {});
  if (error) return res.status(400).json({ error });

  const existing = await prisma.user.findUnique({ where: { email: data.email }, select: { id: true } });
  if (existing) {
    return res.status(409).json({ error: 'Ya existe un usuario con ese email' });
  }
  const hashedPassword = await bcrypt.hash(data.password, 10);
  const user = await prisma.user.create({
    data: { email: data.email, password: hashedPassword, name: data.name },
  });
  res.status(201).json(toUserResponse(user));
});

router.post('/login', loginLimiter, async (req, res) => {
  const { email, password } = req.body ?? {};
  if (typeof email !== 'string' || typeof password !== 'string') {
    return res.status(400).json({ error: 'Faltan campos: email, password' });
  }
  const user = await prisma.user.findUnique({ where: { email: email.trim() } });
  if (!user) return res.status(401).json({ error: 'Credenciales incorrectas' });

  const valid = await bcrypt.compare(password, user.password);
  if (!valid) return res.status(401).json({ error: 'Credenciales incorrectas' });

  const token = jwt.sign({ userId: user.id }, process.env.JWT_SECRET, { expiresIn: '7d' });
  res.json({ token, user: toUserResponse(user) });
});

// Elimina la cuenta del usuario autenticado y, en cascada, sus recetas (con sus ingredientes
// y likes recibidos), sus likes, su diario y su despensa. Exige la contraseña actual.
router.delete('/me', requireAuth, deleteAccountLimiter, async (req, res) => {
  const { password } = req.body ?? {};
  if (typeof password !== 'string' || !password) {
    return res.status(400).json({ error: 'Introduce tu contraseña para confirmar' });
  }
  const user = await prisma.user.findUnique({ where: { id: req.userId }, select: { id: true, password: true } });
  if (!user) return res.status(404).json({ error: 'La cuenta ya no existe' });

  // 403 y no 401: el token es válido, lo que falla es la confirmación. La app cierra la
  // sesión ante cualquier 401 (AuthInterceptor), y equivocarse al teclear no debe echar al usuario.
  const valid = await bcrypt.compare(password, user.password);
  if (!valid) return res.status(403).json({ error: 'La contraseña no es correcta' });

  await prisma.user.delete({ where: { id: user.id } });
  res.status(204).end();
});

module.exports = router;
module.exports.validateRegistration = validateRegistration;
