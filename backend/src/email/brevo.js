// Envío de correos transaccionales con la API REST de Brevo (antes Sendinblue):
// https://developers.brevo.com/reference/sendtransacemail
//
// Configuración (en .env y en el panel de Render):
//   BREVO_API_KEY       clave de API de la cuenta de Brevo
//   BREVO_SENDER_EMAIL  remitente verificado en Brevo (Brevo rechaza remitentes sin verificar)
//   BREVO_SENDER_NAME   nombre del remitente (opcional, "NutriSocial" por defecto)
//   APP_BASE_URL        URL pública del backend para los enlaces (opcional)
//
// Si falta la configuración, el correo no se envía pero nada falla: el registro sigue adelante
// y el servidor lo avisa en el log. Fuera de producción el log incluye además el enlace o el
// código, para poder probar el flujo completo sin cuenta de Brevo.

const BREVO_URL = 'https://api.brevo.com/v3/smtp/email';
const TIMEOUT_MS = 10000;
const DEFAULT_BASE_URL = 'https://nutrisocial.onrender.com';

function appBaseUrl() {
  return (process.env.APP_BASE_URL || DEFAULT_BASE_URL).replace(/\/+$/, '');
}

function escapeHtml(text) {
  return String(text ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
}

/**
 * Envía un correo. Nunca lanza: devuelve { sent: true } o { sent: false, reason } para que
 * un fallo del proveedor no rompa el registro ni la recuperación de contraseña.
 */
async function sendEmail({ to, toName, subject, html, text }) {
  const apiKey = process.env.BREVO_API_KEY;
  const senderEmail = process.env.BREVO_SENDER_EMAIL;
  if (!apiKey || !senderEmail) {
    console.warn(`Correo a ${to} NO enviado ("${subject}"): falta BREVO_API_KEY o BREVO_SENDER_EMAIL.`);
    return { sent: false, reason: 'not-configured' };
  }
  try {
    const res = await fetch(BREVO_URL, {
      method: 'POST',
      headers: { 'api-key': apiKey, 'content-type': 'application/json', accept: 'application/json' },
      body: JSON.stringify({
        sender: { email: senderEmail, name: process.env.BREVO_SENDER_NAME || 'NutriSocial' },
        to: [{ email: to, ...(toName && { name: toName }) }],
        subject,
        htmlContent: html,
        textContent: text,
      }),
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
    if (!res.ok) {
      const body = await res.text().catch(() => '');
      console.error(`Brevo rechazó el correo a ${to}: HTTP ${res.status} ${body.slice(0, 300)}`);
      return { sent: false, reason: `http-${res.status}` };
    }
    return { sent: true };
  } catch (err) {
    console.error(`No se pudo contactar con Brevo para enviar a ${to}: ${err.message}`);
    return { sent: false, reason: 'network' };
  }
}

// Fuera de producción, si no se ha podido enviar, el enlace o el código se escriben en el log.
function logForDevelopment(result, what) {
  if (!result.sent && process.env.NODE_ENV !== 'production') console.info(`[desarrollo] ${what}`);
  return result;
}

function layout(title, bodyHtml) {
  return `<div style="font-family:Arial,sans-serif;max-width:520px;margin:auto;color:#1E1B16">
  <h2 style="color:#2E7D32">${title}</h2>${bodyHtml}
  <p style="color:#7F7667;font-size:12px">Si no has sido tú, puedes ignorar este correo.</p></div>`;
}

/** Correo con el enlace para confirmar la dirección. El enlace caduca en 24 horas. */
async function sendVerificationEmail(to, token, name) {
  const link = `${appBaseUrl()}/auth/verify?token=${encodeURIComponent(token)}`;
  const greeting = name ? `Hola, ${escapeHtml(name)}:` : 'Hola:';
  const result = await sendEmail({
    to,
    toName: name,
    subject: 'Confirma tu correo en NutriSocial',
    html: layout('Confirma tu correo', `<p>${greeting}</p>
      <p>Para empezar a usar NutriSocial, confirma que este correo es tuyo:</p>
      <p><a href="${link}" style="background:#2E7D32;color:#fff;padding:12px 20px;border-radius:16px;text-decoration:none;display:inline-block">Confirmar mi correo</a></p>
      <p style="font-size:13px">O copia este enlace en el navegador: <br>${link}</p>
      <p style="font-size:13px">El enlace caduca en 24 horas.</p>`),
    text: `${name ? `Hola, ${name}:` : 'Hola:'}\n\nConfirma tu correo en NutriSocial abriendo este enlace (caduca en 24 horas):\n${link}\n`,
  });
  return logForDevelopment(result, `Enlace de verificación para ${to}: ${link}`);
}

/** Correo con el código de un solo uso para elegir una contraseña nueva. Caduca en 1 hora. */
async function sendPasswordResetEmail(to, code, name) {
  const greeting = name ? `Hola, ${escapeHtml(name)}:` : 'Hola:';
  const result = await sendEmail({
    to,
    toName: name,
    subject: 'Tu código para cambiar la contraseña de NutriSocial',
    html: layout('Cambia tu contraseña', `<p>${greeting}</p>
      <p>Escribe este código en la aplicación para elegir una contraseña nueva:</p>
      <p style="font-size:28px;letter-spacing:4px;font-weight:bold;color:#2E7D32">${code}</p>
      <p style="font-size:13px">Caduca en 1 hora y solo sirve una vez.</p>`),
    text: `${name ? `Hola, ${name}:` : 'Hola:'}\n\nTu código para cambiar la contraseña de NutriSocial es: ${code}\nCaduca en 1 hora y solo sirve una vez.\n`,
  });
  return logForDevelopment(result, `Código de recuperación para ${to}: ${code}`);
}

module.exports = { sendEmail, sendVerificationEmail, sendPasswordResetEmail, appBaseUrl };
