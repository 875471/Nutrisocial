const express = require('express');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const prisma = require('../prismaClient');
const requireAuth = require('../middleware/auth');
const { createAuthLimiter } = require('../middleware/rateLimit');
const { sendVerificationEmail, sendPasswordResetEmail } = require('../email/brevo');
const {
  VERIFICATION_TTL_MS, RESET_TTL_MS, generateVerificationToken, generateResetCode, normalizeCode, hashToken,
} = require('../auth/tokens');

const router = express.Router();

const MIN_PASSWORD_LENGTH = 8;
// bcrypt solo usa los primeros 72 bytes: lo que pase de ahí se ignoraría sin avisar.
const MAX_PASSWORD_BYTES = 72;
const MAX_NAME_LENGTH = 50;
const MAX_EMAIL_LENGTH = 254;
// Comprobación básica de formato: algo@algo.dominio, sin espacios. Que la dirección existe y
// es del usuario se comprueba con el correo de verificación.
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

// Solo cuentan los intentos fallidos: un usuario que entra bien no gasta su cupo.
const loginLimiter = createAuthLimiter({ skipSuccessfulRequests: true });
// En el registro cuentan todos, para frenar también la creación masiva de cuentas.
const registerLimiter = createAuthLimiter();
// Borrar la cuenta pide la contraseña: se limita igual que el login para que un token robado
// no sirva para adivinarla a base de intentos.
const deleteAccountLimiter = createAuthLimiter({ skipSuccessfulRequests: true });
// Las rutas que envían correos se limitan más: cada petición es un correo real (y Brevo
// tiene un cupo diario en el plan gratuito), y no deben servir para bombardear un buzón ajeno.
const emailLimiter = createAuthLimiter({ limit: 5 });
// Probar códigos de recuperación: solo cuentan los fallos.
const resetLimiter = createAuthLimiter({ skipSuccessfulRequests: true });

// Respuesta idéntica exista o no la cuenta, para que estas rutas no sirvan para averiguar
// qué emails están registrados.
const RESEND_MESSAGE = 'Si hay una cuenta pendiente de confirmar con ese email, te hemos enviado un correo nuevo.';
const FORGOT_MESSAGE = 'Si hay una cuenta con ese email, te hemos enviado un código para cambiar la contraseña.';

function validatePassword(password) {
  if (typeof password !== 'string') return 'Falta la contraseña';
  if (password.length < MIN_PASSWORD_LENGTH) return `La contraseña debe tener al menos ${MIN_PASSWORD_LENGTH} caracteres`;
  if (Buffer.byteLength(password, 'utf8') > MAX_PASSWORD_BYTES) {
    return 'La contraseña es demasiado larga (máximo 72 caracteres sin tildes)';
  }
  return null;
}

function cleanEmail(email) {
  return typeof email === 'string' ? email.trim() : '';
}

/** Valida los datos de registro. Devuelve { data } limpios o { error } para un 400. */
function validateRegistration({ email, password, name } = {}) {
  if (typeof email !== 'string' || typeof password !== 'string' || typeof name !== 'string') {
    return { error: 'Faltan campos: email, password, name' };
  }
  const trimmedEmail = email.trim();
  const trimmedName = name.trim();
  if (!trimmedName) return { error: 'El nombre no puede estar vacío' };
  if (trimmedName.length > MAX_NAME_LENGTH) return { error: `El nombre no puede superar ${MAX_NAME_LENGTH} caracteres` };
  if (trimmedEmail.length > MAX_EMAIL_LENGTH || !EMAIL_RE.test(trimmedEmail)) {
    return { error: 'El formato del email no es válido' };
  }
  const passwordError = validatePassword(password);
  if (passwordError) return { error: passwordError };
  return { data: { email: trimmedEmail, password, name: trimmedName } };
}

// Datos públicos del usuario: nunca se devuelve el hash de la contraseña.
function toUserResponse(user) {
  return { id: user.id, email: user.email, name: user.name };
}

/** Token nuevo de verificación: el valor para el enlace y los campos a guardar (hash y caducidad). */
function newVerification() {
  const token = generateVerificationToken();
  return {
    token,
    data: { verificationTokenHash: hashToken(token), verificationTokenExpires: new Date(Date.now() + VERIFICATION_TTL_MS) },
  };
}

// La cuenta se crea sin verificar y se envía el correo con el enlace. Si el correo no sale
// (Brevo sin configurar o caído), el usuario existe igualmente y puede pedir otro desde la app.
router.post('/register', registerLimiter, async (req, res) => {
  const { data, error } = validateRegistration(req.body ?? {});
  if (error) return res.status(400).json({ error });

  const existing = await prisma.user.findUnique({ where: { email: data.email }, select: { id: true } });
  if (existing) {
    return res.status(409).json({ error: 'Ya existe un usuario con ese email' });
  }
  const hashedPassword = await bcrypt.hash(data.password, 10);
  const verification = newVerification();
  const user = await prisma.user.create({
    data: { email: data.email, password: hashedPassword, name: data.name, emailVerified: false, ...verification.data },
  });
  const { sent } = await sendVerificationEmail(user.email, verification.token, user.name);
  res.status(201).json({ ...toUserResponse(user), emailVerificationRequired: true, emailSent: sent });
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

  // Solo se dice que falta verificar después de comprobar la contraseña: así no se revela a
  // cualquiera si un email está registrado.
  if (!user.emailVerified) {
    return res.status(403).json({
      error: 'Confirma tu email antes de iniciar sesión. Revisa tu correo (y la carpeta de spam).',
      code: 'EMAIL_NOT_VERIFIED',
    });
  }

  const token = jwt.sign({ userId: user.id }, process.env.JWT_SECRET, { expiresIn: '7d' });
  res.json({ token, user: toUserResponse(user) });
});

// Página mínima para el enlace del correo, que se abre en el navegador (no hay web de la app).
function htmlPage(title, message, ok) {
  const color = ok ? '#2E7D32' : '#BA1A1A';
  return `<!doctype html><html lang="es"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>${title} · NutriSocial</title></head>
<body style="font-family:Arial,sans-serif;background:#FAF6EF;color:#1E1B16;margin:0;padding:48px 16px">
<div style="max-width:440px;margin:auto;background:#fff;border-radius:16px;padding:24px;box-shadow:0 2px 6px rgba(0,0,0,.08)">
<h1 style="color:${color};font-size:22px;margin-top:0">${title}</h1><p style="line-height:1.5">${message}</p></div></body></html>`;
}

// Enlace del correo de verificación: GET /auth/verify?token=...
router.get('/verify', async (req, res) => {
  const token = typeof req.query.token === 'string' ? req.query.token : '';
  const user = token && await prisma.user.findUnique({
    where: { verificationTokenHash: hashToken(token) },
    select: { id: true, verificationTokenExpires: true },
  });
  if (!user) {
    return res.status(400).type('html').send(htmlPage('Enlace no válido',
      'Este enlace no es válido o ya se ha usado. Si ya confirmaste tu correo, inicia sesión en la app; '
      + 'si no, pide un correo nuevo desde la pantalla de inicio de sesión.', false));
  }
  if (!user.verificationTokenExpires || user.verificationTokenExpires < new Date()) {
    return res.status(410).type('html').send(htmlPage('Enlace caducado',
      'Este enlace ha caducado. Pide un correo nuevo desde la pantalla de inicio de sesión de la app.', false));
  }
  await prisma.user.update({
    where: { id: user.id },
    data: { emailVerified: true, verificationTokenHash: null, verificationTokenExpires: null },
  });
  res.type('html').send(htmlPage('¡Correo confirmado!', 'Ya puedes volver a la app e iniciar sesión.', true));
});

// Reenvía el correo de verificación con un enlace nuevo (el anterior deja de valer).
router.post('/resend-verification', emailLimiter, async (req, res) => {
  const email = cleanEmail(req.body?.email);
  if (!EMAIL_RE.test(email)) return res.status(400).json({ error: 'El formato del email no es válido' });

  const user = await prisma.user.findUnique({ where: { email }, select: { id: true, name: true, email: true, emailVerified: true } });
  if (user && !user.emailVerified) {
    const verification = newVerification();
    await prisma.user.update({ where: { id: user.id }, data: verification.data });
    await sendVerificationEmail(user.email, verification.token, user.name);
  }
  res.json({ message: RESEND_MESSAGE });
});

// Envía un código de un solo uso para elegir una contraseña nueva. Caduca en 1 hora; pedir
// otro invalida el anterior.
router.post('/forgot-password', emailLimiter, async (req, res) => {
  const email = cleanEmail(req.body?.email);
  if (!EMAIL_RE.test(email)) return res.status(400).json({ error: 'El formato del email no es válido' });

  const user = await prisma.user.findUnique({ where: { email }, select: { id: true, name: true, email: true } });
  if (user) {
    const code = generateResetCode();
    await prisma.user.update({
      where: { id: user.id },
      data: { passwordResetTokenHash: hashToken(normalizeCode(code)), passwordResetTokenExpires: new Date(Date.now() + RESET_TTL_MS) },
    });
    await sendPasswordResetEmail(user.email, code, user.name);
  }
  res.json({ message: FORGOT_MESSAGE });
});

// Cambia la contraseña con el código del correo: { email, code, password }.
router.post('/reset-password', resetLimiter, async (req, res) => {
  const { email, code, password } = req.body ?? {};
  const passwordError = validatePassword(password);
  if (passwordError) return res.status(400).json({ error: passwordError });
  const normalized = normalizeCode(code);
  if (!normalized) return res.status(400).json({ error: 'Introduce el código que te hemos enviado' });

  const user = await prisma.user.findUnique({
    where: { email: cleanEmail(email) },
    select: { id: true, passwordResetTokenHash: true, passwordResetTokenExpires: true },
  });
  const valid = user
    && user.passwordResetTokenHash === hashToken(normalized)
    && user.passwordResetTokenExpires && user.passwordResetTokenExpires > new Date();
  if (!valid) return res.status(400).json({ error: 'El código no es válido o ha caducado. Pide uno nuevo.' });

  await prisma.user.update({
    where: { id: user.id },
    data: {
      password: await bcrypt.hash(password, 10),
      // Un solo uso: el código deja de valer en cuanto se usa.
      passwordResetTokenHash: null,
      passwordResetTokenExpires: null,
      // Quien recibe el código en su buzón ha demostrado que el email es suyo.
      emailVerified: true,
      verificationTokenHash: null,
      verificationTokenExpires: null,
    },
  });
  res.json({ message: 'Contraseña cambiada. Ya puedes iniciar sesión con la nueva.' });
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
