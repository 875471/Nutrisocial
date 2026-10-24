// Página pública de una receta para los enlaces que se comparten desde la app
// (https://nutrisocial.onrender.com/share/recipes/<id>). Es la ÚNICA zona de la API sin
// requireAuth: quien recibe el enlace puede no tener la app ni cuenta. Solo lee, y devuelve
// HTML autocontenido (CSS en línea, sin JavaScript) porque se abre directamente en un navegador.
//
// Todo lo que escriben los usuarios (título, autor, ingredientes, pasos...) pasa por escapeHtml.
const express = require('express');
const prisma = require('../prismaClient');
const { escapeHtml } = require('../utils/html');
const { nutritionSummary } = require('../nutrition/calculate');

const router = express.Router();

// Sin scripts ni recursos externos: aunque algo se colase sin escapar, el navegador no lo ejecutaría.
const CSP = "default-src 'none'; style-src 'unsafe-inline'";

const STYLE = `
  body { margin: 0; background: #f6f7f4; color: #1f2a1f; font: 16px/1.5 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }
  main { max-width: 640px; margin: 0 auto; padding: 24px 16px 48px; }
  h1 { margin: 0 0 4px; font-size: 1.7em; line-height: 1.2; }
  h2 { margin: 28px 0 8px; font-size: 1.15em; }
  .meta { margin: 0; color: #5b665b; }
  .macros { display: flex; flex-wrap: wrap; gap: 8px; margin: 20px 0 0; padding: 0; list-style: none; }
  .macros li { flex: 1 1 110px; padding: 10px 12px; background: #fff; border: 1px solid #dfe5dc; border-radius: 10px; }
  .macros b { display: block; font-size: 1.25em; }
  .macros span { color: #5b665b; font-size: .9em; }
  ul.ingredients, ol { margin: 0; padding-left: 1.4em; }
  li + li { margin-top: 4px; }
  footer { margin-top: 40px; color: #5b665b; font-size: .9em; }
`;

function page(title, body) {
  return `<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escapeHtml(title)}</title>
<style>${STYLE}</style>
</head>
<body><main>
${body}
<footer>Receta compartida desde NutriSocial.</footer>
</main></body>
</html>`;
}

// 0.5 -> "0,5"; sin cantidad, cadena vacía.
function formatQuantity(quantity) {
  return quantity == null ? '' : String(Math.round(quantity * 100) / 100).replace('.', ',');
}

function ingredientLine(ing) {
  const amount = [formatQuantity(ing.quantity), ing.unit ?? ''].filter(Boolean).join(' ');
  return `<li>${escapeHtml(ing.name)}${amount ? ` — ${escapeHtml(amount)}` : ''}</li>`;
}

function recipePage(recipe) {
  const { perServing } = nutritionSummary(recipe, recipe.ingredients);
  const steps = JSON.parse(recipe.steps);
  const servings = `${recipe.servings} ${recipe.servings === 1 ? 'ración' : 'raciones'}`;
  const minutes = recipe.prepMinutes ? ` · ${recipe.prepMinutes} min` : '';
  const macro = (value, label) => `<li><b>${escapeHtml(String(value).replace('.', ','))}</b><span>${label}</span></li>`;
  return page(`${recipe.title} · NutriSocial`, `
<h1>${escapeHtml(recipe.title)}</h1>
<p class="meta">Por ${escapeHtml(recipe.author.name)} · ${servings}${minutes}</p>
${recipe.description ? `<p>${escapeHtml(recipe.description)}</p>` : ''}
<ul class="macros" aria-label="Valores por ración">
${macro(perServing.kcal, 'kcal / ración')}
${macro(`${perServing.protein} g`, 'proteína')}
${macro(`${perServing.carbs} g`, 'hidratos')}
${macro(`${perServing.fat} g`, 'grasa')}
</ul>
<h2>Ingredientes</h2>
<ul class="ingredients">
${recipe.ingredients.map(ingredientLine).join('\n')}
</ul>
<h2>Pasos</h2>
<ol>
${steps.map((step) => `<li>${escapeHtml(step)}</li>`).join('\n')}
</ol>`);
}

function notFoundPage() {
  return page('Receta no encontrada · NutriSocial', `
<h1>Receta no encontrada</h1>
<p class="meta">Puede que el enlace esté mal o que su autor la haya borrado.</p>`);
}

router.get('/recipes/:id', async (req, res) => {
  res.set('Content-Security-Policy', CSP);
  res.type('html');
  const id = Number(req.params.id);
  const recipe = Number.isInteger(id) && id > 0 && await prisma.recipe.findUnique({
    where: { id },
    // Solo lo que se enseña: nada de la foto en Base64 ni, sobre todo, datos de la cuenta del autor.
    select: {
      title: true, description: true, steps: true, servings: true, prepMinutes: true,
      kcal: true, protein: true, carbs: true, fat: true, totalWeightGrams: true,
      author: { select: { name: true } },
      ingredients: {
        orderBy: { position: 'asc' },
        select: { name: true, quantity: true, unit: true, grams: true, foodId: true },
      },
    },
  });
  if (!recipe) return res.status(404).send(notFoundPage());
  res.send(recipePage(recipe));
});

module.exports = router;
