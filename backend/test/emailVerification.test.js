// Pruebas de la verificación de email y la recuperación de contraseña. Brevo no se llama de
// verdad: se sustituye globalThis.fetch y se guardan los correos "enviados" para leer de
// ellos el enlace o el código, como haría el usuario desde su buzón.
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const bcrypt = require('bcryptjs');

const SRC = path.resolve(__dirname, '../src');
const fetchReal = globalThis.fetch;

// Correos que la ruta ha intentado enviar a Brevo.
let sentEmails = [];
let brevoDown = false;
globalThis.fetch = async (url, options) => {
  if (!String(url).startsWith('https://api.brevo.com/')) throw new Error(`Petición inesperada a ${url}`);
  if (brevoDown) return new Response('{"message":"servicio no disponible"}', { status: 503 });
  sentEmails.push({ headers: options.headers, body: JSON.parse(options.body) });
  return new Response('{"messageId":"<prueba>"}', { status: 201 });
};

function fakePrisma() {
  const users = [];
  const matches = (u, where) => Object.entries(where).every(([k, v]) => u[k] === v);
  const pick = (u, select) => (select ? Object.fromEntries(Object.keys(select).map((k) => [k, u[k]])) : { ...u });
  return {
    users,
    user: {
      findUnique: async ({ where, select }) => {
        const u = users.find((x) => matches(x, where));
        return u ? pick(u, select) : null;
      },
      create: async ({ data }) => {
        const u = { id: users.length + 1, emailVerified: false, ...data };
        users.push(u);
        return { ...u };
      },
      update: async ({ where, data }) => {
        const u = users.find((x) => matches(x, where));
        Object.assign(u, data);
        return { ...u };
      },
    },
  };
}

async function startApi(db, env = {}) {
  process.env.JWT_SECRET = 'secreto';
  Object.assign(process.env, { BREVO_API_KEY: 'clave-de-prueba', BREVO_SENDER_EMAIL: 'remitente@nutrisocial.example', ...env });
  for (const key of Object.keys(require.cache)) if (key.startsWith(SRC)) delete require.cache[key];
  const prismaPath = path.join(SRC, 'prismaClient.js');
  require.cache[prismaPath] = { id: prismaPath, filename: prismaPath, loaded: true, exports: db };
  sentEmails = [];
  brevoDown = false;

  const express = require('express');
  const app = express();
  app.use(express.json());
  app.use('/auth', require(path.join(SRC, 'routes/auth.js')));
  const server = await new Promise((resolve) => { const s = app.listen(0, () => resolve(s)); });
  const base = `http://127.0.0.1:${server.address().port}`;
  const request = async (method, url, body) => {
    const res = await fetchReal(base + url, {
      method,
      headers: { 'content-type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const text = await res.text();
    const isJson = (res.headers.get('content-type') ?? '').includes('json');
    return { status: res.status, body: isJson ? JSON.parse(text) : text };
  };
  return { request, close: () => new Promise((resolve) => server.close(resolve)) };
}

const NEW_USER = { email: 'nuevo@nutrisocial.example', password: 'una-clave-larga', name: 'Nuevo' };
const linkIn = (email) => email.body.textContent.match(/https?:\/\/\S+\/auth\/verify\?token=([0-9a-f]+)/);
const codeIn = (email) => email.body.textContent.match(/es: ([A-Z0-9]{4}-[A-Z0-9]{4})/)[1];

test('registro: la cuenta nace sin verificar, se envía el enlace por Brevo y el login se bloquea', async () => {
  const db = fakePrisma();
  const api = await startApi(db);
  try {
    const res = await api.request('POST', '/auth/register', NEW_USER);
    assert.equal(res.status, 201);
    assert.equal(res.body.emailVerificationRequired, true);
    assert.equal(res.body.emailSent, true);
    assert.equal(db.users[0].emailVerified, false);

    // Petición correcta a la API de Brevo, con la clave en la cabecera api-key.
    assert.equal(sentEmails.length, 1);
    assert.equal(sentEmails[0].headers['api-key'], 'clave-de-prueba');
    assert.deepEqual(sentEmails[0].body.to, [{ email: NEW_USER.email, name: 'Nuevo' }]);
    const token = linkIn(sentEmails[0])[1];
    // En la base de datos solo está el hash, no el token del enlace.
    assert.notEqual(db.users[0].verificationTokenHash, token);
    assert.ok(!JSON.stringify(db.users[0]).includes(token));

    const blocked = await api.request('POST', '/auth/login', { email: NEW_USER.email, password: NEW_USER.password });
    assert.equal(blocked.status, 403);
    assert.equal(blocked.body.code, 'EMAIL_NOT_VERIFIED');
    // Con la contraseña mal no se revela que la cuenta está sin verificar.
    assert.equal((await api.request('POST', '/auth/login', { email: NEW_USER.email, password: 'otra-cosa' })).status, 401);
  } finally {
    await api.close();
  }
});

test('verificación: el enlace confirma la cuenta una sola vez y después se puede iniciar sesión', async () => {
  const db = fakePrisma();
  const api = await startApi(db);
  try {
    await api.request('POST', '/auth/register', NEW_USER);
    const token = linkIn(sentEmails[0])[1];

    const ok = await api.request('GET', `/auth/verify?token=${token}`);
    assert.equal(ok.status, 200);
    assert.match(ok.body, /Correo confirmado/);
    assert.equal(db.users[0].emailVerified, true);

    const login = await api.request('POST', '/auth/login', { email: NEW_USER.email, password: NEW_USER.password });
    assert.equal(login.status, 200);
    assert.ok(login.body.token);

    // El mismo enlace ya no sirve, y uno inventado tampoco.
    assert.equal((await api.request('GET', `/auth/verify?token=${token}`)).status, 400);
    assert.equal((await api.request('GET', '/auth/verify?token=abc123')).status, 400);
    assert.equal((await api.request('GET', '/auth/verify')).status, 400);
  } finally {
    await api.close();
  }
});

test('verificación: un enlace caducado se rechaza y el reenvío da uno nuevo que sí funciona', async () => {
  const db = fakePrisma();
  const api = await startApi(db);
  try {
    await api.request('POST', '/auth/register', NEW_USER);
    const oldToken = linkIn(sentEmails[0])[1];
    db.users[0].verificationTokenExpires = new Date(Date.now() - 1000);
    const expired = await api.request('GET', `/auth/verify?token=${oldToken}`);
    assert.equal(expired.status, 410);
    assert.match(expired.body, /caducado/);

    const resend = await api.request('POST', '/auth/resend-verification', { email: NEW_USER.email });
    assert.equal(resend.status, 200);
    assert.equal(sentEmails.length, 2);
    const newToken = linkIn(sentEmails[1])[1];
    assert.notEqual(newToken, oldToken);
    // El enlace antiguo deja de valer.
    assert.equal((await api.request('GET', `/auth/verify?token=${oldToken}`)).status, 400);
    assert.equal((await api.request('GET', `/auth/verify?token=${newToken}`)).status, 200);

    // Con la cuenta ya verificada, o con un email desconocido, la respuesta es la misma y no se envía nada.
    const again = await api.request('POST', '/auth/resend-verification', { email: NEW_USER.email });
    const unknown = await api.request('POST', '/auth/resend-verification', { email: 'nadie@nutrisocial.example' });
    assert.deepEqual(again.body, resend.body);
    assert.deepEqual(unknown.body, resend.body);
    assert.equal(sentEmails.length, 2);
  } finally {
    await api.close();
  }
});

test('sin Brevo configurado (o caído) el registro no falla: la cuenta se crea y se avisa', async () => {
  const db = fakePrisma();
  const api = await startApi(db, { BREVO_API_KEY: '' });
  try {
    const res = await api.request('POST', '/auth/register', NEW_USER);
    assert.equal(res.status, 201);
    assert.equal(res.body.emailSent, false);
    assert.equal(sentEmails.length, 0);
    assert.equal(db.users.length, 1);

    process.env.BREVO_API_KEY = 'clave-de-prueba';
    brevoDown = true;
    const down = await api.request('POST', '/auth/register', { ...NEW_USER, email: 'otro@nutrisocial.example' });
    assert.equal(down.status, 201);
    assert.equal(down.body.emailSent, false);
  } finally {
    await api.close();
  }
});

test('recuperar la contraseña: el código cambia la contraseña una sola vez', async () => {
  const db = fakePrisma();
  db.users.push({ id: 1, email: 'ana@nutrisocial.example', name: 'Ana', password: bcrypt.hashSync('antigua-clave', 4), emailVerified: true });
  const api = await startApi(db);
  try {
    const forgot = await api.request('POST', '/auth/forgot-password', { email: 'ana@nutrisocial.example' });
    assert.equal(forgot.status, 200);
    const code = codeIn(sentEmails[0]);
    assert.ok(!JSON.stringify(db.users[0]).includes(code.replace('-', '')));

    // Código mal, contraseña corta, o email de otra cuenta: no cambia nada.
    assert.equal((await api.request('POST', '/auth/reset-password', { email: 'ana@nutrisocial.example', code: 'AAAA-AAAA', password: 'nueva-clave-1' })).status, 400);
    assert.equal((await api.request('POST', '/auth/reset-password', { email: 'ana@nutrisocial.example', code, password: 'corta' })).status, 400);
    assert.equal((await api.request('POST', '/auth/reset-password', { email: 'otra@nutrisocial.example', code, password: 'nueva-clave-1' })).status, 400);

    // Se acepta tecleado sin guion y en minúsculas.
    const ok = await api.request('POST', '/auth/reset-password', {
      email: 'ana@nutrisocial.example', code: code.replace('-', '').toLowerCase(), password: 'nueva-clave-1',
    });
    assert.equal(ok.status, 200);
    assert.equal((await api.request('POST', '/auth/login', { email: 'ana@nutrisocial.example', password: 'nueva-clave-1' })).status, 200);
    assert.equal((await api.request('POST', '/auth/login', { email: 'ana@nutrisocial.example', password: 'antigua-clave' })).status, 401);
    // Un solo uso.
    assert.equal((await api.request('POST', '/auth/reset-password', { email: 'ana@nutrisocial.example', code, password: 'otra-clave-2' })).status, 400);
  } finally {
    await api.close();
  }
});

test('recuperar la contraseña: el código caduca y no revela si el email existe', async () => {
  const db = fakePrisma();
  db.users.push({ id: 1, email: 'ana@nutrisocial.example', name: 'Ana', password: 'x', emailVerified: false });
  const api = await startApi(db);
  try {
    const known = await api.request('POST', '/auth/forgot-password', { email: 'ana@nutrisocial.example' });
    const unknown = await api.request('POST', '/auth/forgot-password', { email: 'nadie@nutrisocial.example' });
    assert.deepEqual(known.body, unknown.body);
    assert.equal(sentEmails.length, 1);

    const code = codeIn(sentEmails[0]);
    db.users[0].passwordResetTokenExpires = new Date(Date.now() - 1000);
    const expired = await api.request('POST', '/auth/reset-password', { email: 'ana@nutrisocial.example', code, password: 'nueva-clave-1' });
    assert.equal(expired.status, 400);
    assert.match(expired.body.error, /caducado/);
  } finally {
    await api.close();
  }
});

test('cambiar la contraseña con el código también verifica el email', async () => {
  const db = fakePrisma();
  db.users.push({ id: 1, email: 'ana@nutrisocial.example', name: 'Ana', password: 'x', emailVerified: false });
  const api = await startApi(db);
  try {
    await api.request('POST', '/auth/forgot-password', { email: 'ana@nutrisocial.example' });
    await api.request('POST', '/auth/reset-password', { email: 'ana@nutrisocial.example', code: codeIn(sentEmails[0]), password: 'nueva-clave-1' });
    assert.equal(db.users[0].emailVerified, true);
    assert.equal((await api.request('POST', '/auth/login', { email: 'ana@nutrisocial.example', password: 'nueva-clave-1' })).status, 200);
  } finally {
    await api.close();
  }
});
