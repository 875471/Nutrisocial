const express = require('express');
require('dotenv').config();

// Sin secreto no se pueden firmar ni verificar tokens: mejor no arrancar que fallar después,
// en el primer login, con un error confuso de jsonwebtoken.
if (!process.env.JWT_SECRET) {
  console.error('ERROR: falta la variable de entorno JWT_SECRET (en .env o en el panel de Render). El servidor no arranca.');
  process.exit(1);
}

const app = express();
// Render pone un proxy delante: sin esto, req.ip sería la IP del proxy y el límite de intentos
// de login (middleware/rateLimit.js) se repartiría entre todos los usuarios a la vez.
app.set('trust proxy', 1);
// Las fotos de receta viajan en Base64 dentro del JSON: 2 MB de imagen son unos 2,7 MB
// de texto. La ruta valida después el tamaño real de la foto (ver social/recipeSocial.js).
app.use(express.json({ limit: '3mb' }));

const authRoutes = require('./routes/auth');
const recipeRoutes = require('./routes/recipes');
const foodRoutes = require('./routes/foods');
const profileRoutes = require('./routes/profile');
const logRoutes = require('./routes/log');
const pantryRoutes = require('./routes/pantry');
const commentRoutes = require('./routes/comments');
const userRoutes = require('./routes/users');
const notificationRoutes = require('./routes/notifications');
app.use('/auth', authRoutes);
app.use('/recipes', recipeRoutes);
app.use('/foods', foodRoutes);
app.use('/profile', profileRoutes);
app.use('/log', logRoutes);
app.use('/pantry', pantryRoutes);
app.use('/comments', commentRoutes);
app.use('/users', userRoutes);
app.use('/notifications', notificationRoutes);

app.get('/health', (req, res) => res.json({ status: 'ok' }));

// Cualquier error no controlado (Express 5 captura también los de rutas async) responde en JSON.
app.use((err, req, res, next) => {
  if (err.type === 'entity.parse.failed') {
    return res.status(400).json({ error: 'El cuerpo de la petición no es un JSON válido' });
  }
  if (err.type === 'entity.too.large') {
    return res.status(413).json({ error: 'La petición es demasiado grande. Si lleva una foto, comprímela más.' });
  }
  const status = err.status || err.statusCode || 500;
  if (status >= 500) console.error(err);
  res.status(status).json({ error: status >= 500 ? 'Error interno del servidor' : err.message });
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => console.log(`NutriSocial API en puerto ${PORT}`));
