const { rateLimit, ipKeyGenerator } = require('express-rate-limit');
const jwt = require('jsonwebtoken');

const FIFTEEN_MINUTES = 15 * 60 * 1000;

/**
 * Límite de peticiones por IP para las rutas de autenticación, contra ataques de fuerza bruta
 * sobre contraseñas. Por defecto, 10 intentos cada 15 minutos. Al superarlo responde 429 con
 * un mensaje en JSON, como el resto de errores de la API.
 *
 * Con `skipSuccessfulRequests` solo cuentan los intentos fallidos: quien entra bien no gasta cupo.
 */
function createAuthLimiter({ limit = 10, windowMs = FIFTEEN_MINUTES, skipSuccessfulRequests = false } = {}) {
  return rateLimit({
    windowMs,
    limit,
    skipSuccessfulRequests,
    // Cabecera RateLimit estándar para que el cliente sepa cuándo reintentar; sin las X-RateLimit-* antiguas.
    standardHeaders: 'draft-8',
    legacyHeaders: false,
    handler: (req, res, next, options) => {
      // La ventana es deslizante por IP: el tiempo real de espera puede ser menor que la ventana entera.
      const minutes = Math.ceil(options.windowMs / 60000);
      res.status(429).json({
        error: `Demasiados intentos desde esta conexión. Vuelve a probar dentro de unos minutos (${minutes} como mucho).`,
      });
    },
  });
}

/**
 * Clave del límite general: el usuario si la petición trae un token válido y, si no, la IP. Contar
 * por usuario evita que varias personas detrás de la misma IP (una red móvil con NAT, una
 * residencia) se repartan un único cupo, y que alguien con muchas IPs y una sola cuenta lo esquive.
 * Solo se lee el token para elegir la clave: autenticar sigue siendo cosa de requireAuth.
 */
function apiLimiterKey(req) {
  const [scheme, token] = (req.headers.authorization || '').split(' ');
  if (scheme === 'Bearer' && token) {
    try {
      const { userId } = jwt.verify(token, process.env.JWT_SECRET);
      if (Number.isInteger(userId)) return `user:${userId}`;
    } catch {
      // Token no válido: cuenta por IP, y requireAuth responderá 401.
    }
  }
  return `ip:${ipKeyGenerator(req.ip)}`;
}

/**
 * Límite general para toda la API (OWASP API4, consumo de recursos sin restricción): por defecto
 * 300 peticiones por minuto y usuario (o IP), unas 5 por segundo sostenidas, muy por encima del uso
 * normal de la app pero suficiente para frenar un script que la martillee. Se puede cambiar con la
 * variable de entorno API_RATE_LIMIT_PER_MINUTE.
 */
function createApiLimiter({ limit = Number(process.env.API_RATE_LIMIT_PER_MINUTE) || 300 } = {}) {
  return rateLimit({
    windowMs: 60 * 1000,
    limit,
    keyGenerator: apiLimiterKey,
    standardHeaders: 'draft-8',
    legacyHeaders: false,
    handler: (req, res) => {
      res.status(429).json({ error: 'Demasiadas peticiones seguidas. Espera un minuto y vuelve a intentarlo.' });
    },
  });
}

module.exports = { createAuthLimiter, createApiLimiter, apiLimiterKey };
