const { rateLimit } = require('express-rate-limit');

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

module.exports = { createAuthLimiter };
