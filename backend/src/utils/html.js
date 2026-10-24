// Escapa texto para meterlo en HTML (contenido o atributos entre comillas). Lo usan los
// correos (email/brevo.js) y la página pública de una receta (routes/share.js), donde todo lo
// que escribe un usuario debe pasar por aquí.
function escapeHtml(text) {
  return String(text ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
}

module.exports = { escapeHtml };
