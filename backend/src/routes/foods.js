const express = require('express');
const requireAuth = require('../middleware/auth');
const { searchFoods } = require('../nutrition/matchFood');
const { UNITS } = require('../nutrition/unitConversion');

const router = express.Router();
router.use(requireAuth);

// Autocompletado de ingredientes: GET /foods/search?q=calab
router.get('/search', async (req, res) => {
  const q = typeof req.query.q === 'string' ? req.query.q : '';
  const foods = await searchFoods(q, 10);
  // source: "BEDCA" u "OpenFoodFacts", para que la app pueda indicar de dónde sale el dato.
  res.json(foods.map(({ id, name, kcal, protein, carbs, fat, source }) => ({ id, name, kcal, protein, carbs, fat, source })));
});

// Unidades de cantidad admitidas por POST /recipes.
router.get('/units', (req, res) => res.json(UNITS));

module.exports = router;
