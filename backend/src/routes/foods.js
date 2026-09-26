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
  res.json(foods.map(({ id, name, kcal, protein, carbs, fat }) => ({ id, name, kcal, protein, carbs, fat })));
});

// Unidades de cantidad admitidas por POST /recipes.
router.get('/units', (req, res) => res.json(UNITS));

module.exports = router;
