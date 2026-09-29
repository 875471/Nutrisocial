const jwt = require('jsonwebtoken');

// Exige "Authorization: Bearer <token>" válido de un usuario que siga existiendo y deja su id en
// req.userId. Una cuenta borrada (desde otro dispositivo, por ejemplo) deja tokens con firma
// válida: sin comprobarlo, esas sesiones seguirían "dentro" con listas vacías, 404 y errores al
// escribir. Con 401 la app cierra la sesión sola (AuthInterceptor).
async function requireAuth(req, res, next) {
  const header = req.headers.authorization || '';
  const [scheme, token] = header.split(' ');
  if (scheme !== 'Bearer' || !token) {
    return res.status(401).json({ error: 'Falta el token de autenticación' });
  }

  let payload;
  try {
    payload = jwt.verify(token, process.env.JWT_SECRET);
  } catch (err) {
    return res.status(401).json({ error: 'Token inválido o caducado' });
  }
  if (!Number.isInteger(payload.userId)) {
    return res.status(401).json({ error: 'Token inválido o caducado' });
  }
  // Se pide el cliente en cada llamada (no al cargar el módulo) para que las pruebas puedan
  // sustituirlo por uno en memoria.
  const prisma = require('../prismaClient');
  const user = await prisma.user.findUnique({ where: { id: payload.userId }, select: { id: true } });
  if (!user) {
    return res.status(401).json({ error: 'Tu cuenta ya no existe. Vuelve a iniciar sesión o regístrate.' });
  }
  req.userId = payload.userId;
  next();
}

module.exports = requireAuth;
