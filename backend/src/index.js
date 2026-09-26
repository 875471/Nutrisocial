const express = require('express');
require('dotenv').config();
const app = express();
app.use(express.json());

const authRoutes = require('./routes/auth');
const recipeRoutes = require('./routes/recipes');
app.use('/auth', authRoutes);
app.use('/recipes', recipeRoutes);

app.get('/health', (req, res) => res.json({ status: 'ok' }));

// Cualquier error no controlado (Express 5 captura también los de rutas async) responde en JSON.
app.use((err, req, res, next) => {
  if (err.type === 'entity.parse.failed') {
    return res.status(400).json({ error: 'El cuerpo de la petición no es un JSON válido' });
  }
  const status = err.status || err.statusCode || 500;
  if (status >= 500) console.error(err);
  res.status(status).json({ error: status >= 500 ? 'Error interno del servidor' : err.message });
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => console.log(`NutriSocial API en puerto ${PORT}`));
