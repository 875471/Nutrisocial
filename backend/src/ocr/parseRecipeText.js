// Post-procesado heurístico del texto que devuelve el OCR de una receta: separa
// título, raciones, ingredientes (con cantidad y unidad) y pasos. Solo usa expresiones
// regulares y listas de palabras; no consulta la base de datos (el emparejamiento con
// la tabla Food se hace después, en la ruta).
const { normalize } = require('../nutrition/normalize');

// ---------- Cantidades ----------

const UNICODE_FRACTIONS = { '½': 0.5, '⅓': 1 / 3, '⅔': 2 / 3, '¼': 0.25, '¾': 0.75, '⅛': 0.125 };

// Números escritos con letra que aparecen en recetas ("media cebolla", "dos huevos").
const WORD_NUMBERS = {
  un: 1, una: 1, uno: 1, medio: 0.5, media: 0.5, dos: 2, tres: 3, cuatro: 4, cinco: 5,
  seis: 6, siete: 7, ocho: 8, nueve: 9, diez: 10, doce: 12,
};

// "1", "1,5", "1.5", "1/2", "1 1/2", "½", "1½" y rangos "2-3" (se toma la media).
const NUM = String.raw`\d+(?:[.,]\d+)?`;
const FRACTION = String.raw`\d+\s*\/\s*\d+`;
const UNI = `[${Object.keys(UNICODE_FRACTIONS).join('')}]`;
const QUANTITY = String.raw`(?:\d+\s+${FRACTION}|${FRACTION}|\d*${UNI}|${NUM})`;
const QUANTITY_RE = new RegExp(String.raw`^(${QUANTITY})(?:\s*(?:-|a)\s*(${QUANTITY}))?`, 'i');

function parseNumber(text) {
  const t = text.replace(/\s+/g, ' ').trim();
  const uni = t.match(new RegExp(`^(\\d*)(${UNI})$`));
  if (uni) return Number(uni[1] || 0) + UNICODE_FRACTIONS[uni[2]];
  const mixed = t.match(/^(\d+) (\d+)\s*\/\s*(\d+)$/);
  if (mixed) return Number(mixed[1]) + Number(mixed[2]) / Number(mixed[3]);
  const frac = t.match(/^(\d+)\s*\/\s*(\d+)$/);
  if (frac) return Number(frac[2]) === 0 ? null : Number(frac[1]) / Number(frac[2]);
  return Number(t.replace(',', '.'));
}

// ---------- Unidades ----------

// Alias escritos → unidad admitida por POST /recipes (ver nutrition/unitConversion.js).
// "diente" y "vaso" no existen allí: el diente se trata como pieza (el peso por pieza del
// ajo es el de un diente) y el vaso como una taza.
const UNIT_ALIASES = [
  [/^(kg|kgs|kilo|kilos|kilogramos?)$/, 'kg'],
  [/^(g|gr|grs|gramos?)$/, 'g'],
  [/^(ml|mililitros?)$/, 'ml'],
  [/^(l|lt|litros?)$/, 'l'],
  [/^(cucharadas?|cda|cdas|cs)$/, 'cucharada'],
  [/^(cucharaditas?|cdta|cdtas|cdita|cditas|cc)$/, 'cucharadita'],
  [/^(tazas?|vasos?)$/, 'taza'],
  [/^(pizcas?)$/, 'pizca'],
  [/^(unidad|unidades|ud|uds|u|dientes?)$/, 'unidad'],
];

function toUnit(word) {
  const w = normalize(word).replace(/\.$/, '');
  const found = UNIT_ALIASES.find(([re]) => re.test(w));
  return found ? found[1] : null;
}

// ---------- Clasificación de líneas ----------

// Verbos de cocina. De cada uno se generan las formas con que suele empezar un paso:
// infinitivo ("Mezclar"), imperativo ("Mezcla"), forma de usted ("Mezcle"), vosotros
// ("Mezclad") y gerundio ("Mezclando"). Se comparan palabras completas, no prefijos,
// para no confundir ingredientes como "coco" (cocer), "batata" (batir) o "dorada" (dorar).
const VERBS = `mezclar anadir agregar incorporar hornear batir cortar picar pelar freir cocer
  hervir calentar precalentar servir remover dejar poner echar sofreir triturar rallar saltear
  lavar escurrir reservar colocar retirar verter amasar extender tapar cocinar dorar sazonar
  salpimentar untar montar enfriar meter sacar disponer repartir rellenar enrollar aplastar
  machacar marinar banar cubrir espolvorear emplatar decorar llevar hacer pasar voltear cuajar
  remojar desmoldar engrasar forrar tostar asar filetear trocear limpiar exprimir probar
  rectificar mantener apagar bajar subir quitar introducir pochar rehogar licuar colar
  fundir derretir acompanar integrar agitar sumergir escaldar blanquear gratinar glasear`
  .split(/\s+/);

// Formas irregulares frecuentes (sin tildes, como quedan tras normalizar).
const IRREGULAR = `cuece cueza hierve hierva calienta caliente precalienta precaliente sirve sirva
  remueve remueva pon ponga pone frie fria sofrie sofria vierte vierta extiende extienda haz haga
  tuesta tueste prueba pruebe manten mantenga cubre cubra dispon disponga introduce introduzca`
  .split(/\s+/);

const ENDINGS = { ar: ['a', 'e', 'ad', 'ando'], er: ['e', 'a', 'ed', 'iendo'], ir: ['e', 'a', 'id', 'iendo'] };

const ACTION_WORDS = new Set(IRREGULAR);
for (const verb of VERBS) {
  const stem = verb.slice(0, -2);
  ACTION_WORDS.add(verb);
  for (const ending of ENDINGS[verb.slice(-2)] ?? []) ACTION_WORDS.add(stem + ending);
}

// Conectores con los que empiezan muchos pasos ("Una vez frito...", "Mientras tanto...").
const STEP_OPENERS = ['una vez', 'cuando', 'mientras', 'despues', 'luego', 'por ultimo',
  'finalmente', 'a continuacion', 'al final', 'primero', 'antes de', 'en un', 'en una', 'en el', 'en la'];

function isActionWord(word) {
  // Pronombres pegados al verbo: "mezclalo", "anadelas", "sirvela".
  const withoutClitic = word.replace(/(selo|sela|selos|selas|los|las|lo|la|les|le|se)$/, '');
  return ACTION_WORDS.has(word) || (withoutClitic.length >= 3 && ACTION_WORDS.has(withoutClitic));
}

function startsWithAction(text) {
  const t = normalize(text).replace(/,/g, ' ').replace(/\s+/g, ' ').replace(/^se /, '');
  if (STEP_OPENERS.some((o) => t === o || t.startsWith(o + ' '))) return true;
  return isActionWord(t.split(' ')[0]);
}

const HEADINGS = [
  [/^ingredientes?\b/, 'ingredients'],
  [/^(preparacion|elaboracion|pasos|modo de (hacerlo|preparacion)|instrucciones|procedimiento)\b/, 'steps'],
];

// Tolera errores del OCR: una letra suelta pegada al número ("Para A4") y palabras
// truncadas ("persoas", "racion").
const SERVINGS_RE = /^(?:para\s+)?[a-z]?(\d+)\s*(pers\w*|raci\w*|comensal\w*|porci\w*|pax)\b/;
const SERVINGS_RE_2 = /^(?:raciones|porciones|comensales|personas)\s*:?\s*(\d+)\b/;

// Viñetas y numeración de pasos al principio de la línea: "- ", "• ", "1. ", "2) ", "Paso 3:".
// Una viñeta pequeña ("·", "▪") a menudo llega como un punto o una coma sueltos (". 5 ml").
const BULLET_RE = /^\s*(?:(?:[-–—•·*>+▪■◦●]|[.,;:](?=\s)|o(?=\s))\s*)+/;
const STEP_NUMBER_RE = /^\s*(?:paso\s*)?\d{1,2}\s*(?:[.)ºª:-]|\.-)(?!\d)\s*/i;

// Alimentos que se cuentan por piezas. Sirven para reconocer un "1" leído como "I" o "l"
// pegado al nombre ("Ihuevo"): solo se corrige si lo que queda es uno de estos alimentos,
// para no tocar palabras que empiezan por l de verdad ("leche", "lentejas").
const COUNTABLE_FOODS = new Set(`huevo diente loncha rebanada rodaja cebolla cebolleta tomate
  patata zanahoria limon lima naranja manzana pera platano pimiento pepino calabacin berenjena
  puerro aguacate yema clara hoja rama ramita sobre yogur pechuga filete muslo ajo uva nuez
  guindilla chalota`.split(/\s+/));

function isCountableFood(word) {
  const w = normalize(word);
  return COUNTABLE_FOODS.has(w) || COUNTABLE_FOODS.has(w.replace(/e?s$/, '')) || COUNTABLE_FOODS.has(w.replace(/s$/, ''));
}

const UNIT_WORD = String.raw`(?:kg|kilos?|g|gr|grs|gramos?|ml|l|litros?)(?![a-záéíóúñ])`;

// Errores típicos del OCR en la cantidad: "l" o "I" por 1 y "O" por 0 ("2OO g", "l cebolla").
function fixOcrDigits(line) {
  return line
    .replace(/^[lI|](?=\s|\/)/, '1')
    // Pegado a otras cifras: "I15 g" → "115 g".
    .replace(/^[lI|](?=\d)/, '1')
    // Pegado al nombre: "Ihuevo" → "1 huevo".
    .replace(/^[lI|]([a-záéíóúñ]+)/, (m, rest) => (isCountableFood(rest) ? `1 ${rest}` : m))
    // Un espacio de más dentro de la cifra antes de una unidad de peso o volumen: "1 15 g" → "115 g".
    .replace(new RegExp(String.raw`^(\d) (\d{2,3})(?=\s*${UNIT_WORD})`, 'i'), '$1$2')
    .replace(/^([0-9oO]+)(?=\s*[a-zA-Z]|$)/, (m) => (/\d/.test(m) ? m.replace(/[oO]/g, '0') : m))
    // "g" manuscrita leída como "9": "300 9 de lentejas" → "300 g de lentejas".
    .replace(/^(\d+(?:[.,]\d+)?)\s+9(?=\s+[a-zA-ZáéíóúñÁÉÍÓÚÑ])/, '$1 g');
}

// Dígitos mezclados con letras dentro de una palabra del nombre son letras mal leídas:
// "2anahorias" → "zanahorias", "t0mate" → "tomate". Solo en palabras con al menos 3 letras.
const DIGIT_AS_LETTER = { 0: 'o', 1: 'l', 2: 'z', 5: 's', 8: 'b' };
function fixOcrLetters(name) {
  return name.replace(/\S+/g, (word) => {
    const letters = (word.match(/[a-záéíóúñü]/gi) ?? []).length;
    if (letters < 3 || !/\d/.test(word)) return word;
    return word.replace(/[01258]/g, (d) => DIGIT_AS_LETTER[d]);
  });
}

function cleanName(text) {
  return text
    .replace(/^\s*(de|del)\s+/i, '')
    .replace(/[\s.,;:]+$/, '')
    .replace(/\s+/g, ' ')
    .trim();
}

/**
 * Intenta leer una línea como ingrediente con cantidad: "200 g de harina", "1/2 cebolla",
 * "2 dientes de ajo", "dos huevos". Devuelve null si no empieza por una cantidad.
 */
function parseQuantityLine(line) {
  let quantity = null;
  let rest = null;

  const m = line.match(QUANTITY_RE);
  if (m) {
    quantity = parseNumber(m[1]);
    if (m[2] != null) {
      const upper = parseNumber(m[2]);
      if (upper != null && Number.isFinite(upper)) quantity = (quantity + upper) / 2;
    }
    rest = line.slice(m[0].length);
  } else {
    const word = normalize(line).split(' ')[0];
    // "una vez..." o "un poco de..." no son cantidades.
    if (WORD_NUMBERS[word] != null && !/^\s*\S+\s+(vez|poco|chorr)/i.test(line)) {
      quantity = WORD_NUMBERS[word];
      rest = line.replace(/^\s*\S+/, '');
    }
  }
  if (quantity == null || !Number.isFinite(quantity) || quantity <= 0 || rest == null) return null;
  // Un número pegado a más cifras o a una hora ("180º", "20 minutos") no es un ingrediente.
  if (/^\s*(º|°|grados|minutos?|min\b|horas?|h\b|segundos?)/i.test(rest)) return null;

  // Unidad opcional justo después de la cantidad, pegada o no ("200g", "200 g", "2 cdas.").
  let unit = null;
  const unitMatch = rest.match(/^\s*([a-záéíóúñ]+)\.?(?=\s|$|,)/i);
  if (unitMatch) {
    unit = toUnit(unitMatch[1]);
    if (unit) rest = rest.slice(unitMatch[0].length);
  }
  const rawName = cleanName(fixOcrLetters(rest));
  if (!rawName || startsWithAction(rawName)) return null;

  // Sin unidad escrita ("2 huevos") la cantidad son piezas.
  return { rawName, quantity: Math.round(quantity * 1000) / 1000, unit: unit ?? 'unidad' };
}

/**
 * Dentro de "Preparación", una línea solo cuenta como ingrediente si tiene forma inequívoca
 * ("5 ml de esencia de vainilla": cantidad + unidad escrita + "de" + nombre) y ninguna de sus
 * palabras es un verbo de cocina. En recetas a varias columnas ML Kit no respeta el orden de
 * lectura y un ingrediente puede aparecer detrás del encabezado de los pasos.
 */
function parseStrayIngredient(line) {
  const parsed = parseQuantityLine(line);
  if (!parsed) return null;
  const shape = new RegExp(String.raw`^\s*${QUANTITY}\s*[a-záéíóúñ]+\.?\s+(de|del)\s+\S`, 'i');
  const unitMatch = line.replace(QUANTITY_RE, '').match(/^\s*([a-záéíóúñ]+)/i);
  if (!shape.test(line) || !unitMatch || !toUnit(unitMatch[1])) return null;
  if (normalize(line).replace(/,/g, ' ').split(' ').some(isActionWord)) return null;
  return parsed;
}

/** Línea con forma de ingrediente: empieza por una cantidad, o es corta, sin verbo y sin punto final. */
function looksLikeIngredient(text, words) {
  if (parseQuantityLine(fixOcrDigits(text))) return true;
  return !startsWithAction(text) && words <= 4 && !/[.!?]$/.test(text);
}

// Conectores con los que empieza la segunda línea de un título partido ("de chocolate").
const TITLE_CONTINUATION_RE = /^(de|del|con|y|e|al|a|en|sin|para)\s/i;

// Un ingrediente partido en varias líneas: la línea anterior acaba en un conector, una coma o
// un guion ("200 g de harina de" / "trigo integral"), o esta empieza por uno ("o de maíz").
const OPEN_ENDING_RE = /(?:\s(?:de|del|con|y|e|o|u|en|para|sin|al|a|la|el|los|las)|[,(-])$/i;
const CONTINUATION_START_RE = /^(?:(?:de|del|con|y|e|o|u|en|para|sin|al|a)\s|[(,)])/i;

/**
 * true si `text` (sin viñeta ni número) continúa el nombre del ingrediente de la línea
 * anterior en lugar de ser uno nuevo. Hace falta alguna señal de que el renglón se ha cortado:
 * una línea suelta sin cantidad ("Sal", "Pimienta") es un ingrediente más, no una continuación.
 * - `previousLine`: la línea anterior tal cual (con su coma o su "de" final).
 * - `previousBulleted`: si la anterior llevaba viñeta; en una lista con viñetas, un renglón sin
 *   ella que empieza en minúscula es la segunda mitad del anterior.
 * - `previousWasContinuation`: si la anterior ya era una continuación (ingrediente en tres o
 *   más líneas); entonces basta con que esta empiece en minúscula.
 */
function continuesIngredient(text, { previousLine, previousBulleted, previousWasContinuation }) {
  const lowercase = /^[a-záéíóúñü]/.test(text);
  return OPEN_ENDING_RE.test(previousLine)
    || CONTINUATION_START_RE.test(text)
    || (previousBulleted && lowercase)
    || (previousWasContinuation && lowercase);
}

/**
 * Analiza el texto completo. Devuelve { title, servings, ingredients, steps, lines }, donde
 * `lines` indica cómo se ha clasificado cada línea (útil para evaluar la heurística).
 */
function parseRecipeText(rawText) {
  const lines = String(rawText ?? '')
    // Barras de fracción tipográficas ("1⁄2", "3∕4") como una barra normal.
    .replace(/[\u2044\u2215]/g, '/')
    .split(/\r?\n/)
    .map((l) => l.replace(/\s+/g, ' ').trim())
    .filter((l) => l.length > 0);

  let title = null;
  let servings = null;
  let section = null; // 'ingredients' | 'steps' | null (sin encabezados todavía)
  const ingredients = [];
  const steps = [];
  const classified = [];
  let lastKind = null;
  // Última línea reconocida como ingrediente, para pegarle las líneas que continúan su nombre.
  let lastIngredient = null;
  // Las líneas que hay antes del primer encabezado pueden ser un título en varias líneas.
  const firstHeading = lines.findIndex((l) => {
    const n = normalize(l).replace(/[:.]+$/, '');
    return HEADINGS.some(([re]) => re.test(n)) && n.split(' ').length <= 4;
  });

  lines.forEach((original, index) => {
    const n = normalize(original).replace(/[:.]+$/, '');
    const record = (kind) => {
      classified.push({ text: original, kind });
      lastKind = kind;
      if (kind !== 'ingredient' && kind !== 'ingredient-continuation') lastIngredient = null;
    };

    const heading = HEADINGS.find(([re]) => re.test(n) && n.split(' ').length <= 4);
    if (heading) {
      section = heading[1];
      return record('heading');
    }

    const serv = n.match(SERVINGS_RE) ?? n.match(SERVINGS_RE_2);
    if (serv && n.split(' ').length <= 5) {
      servings = Number(serv[1]);
      return record('servings');
    }

    const bulleted = BULLET_RE.test(original);
    const withoutBullet = original.replace(BULLET_RE, '');
    const numbered = STEP_NUMBER_RE.test(withoutBullet);
    const text = numbered ? withoutBullet.replace(STEP_NUMBER_RE, '') : withoutBullet;
    const words = normalize(text).split(' ').length;
    // Una lista numerada no es siempre de pasos: "1. Harina", "2. 200 g de azúcar" es una
    // lista de ingredientes. Lo es en la sección de ingredientes y, sin encabezados, antes
    // del primer paso si la línea tiene forma de ingrediente (cantidad, o corta y sin verbo).
    const numberedIngredient = numbered && (section === 'ingredients'
      || (section == null && steps.length === 0 && looksLikeIngredient(text, words)));
    // A partir de aquí, "stepNumbered" solo marca los números de paso de verdad.
    const stepNumbered = numbered && !numberedIngredient;

    // 1) Línea que empieza por una cantidad → ingrediente (salvo dentro de "Preparación",
    //    donde "2 huevos" puede ser una línea partida de un paso, o si está numerada como paso).
    if (!stepNumbered && section !== 'steps') {
      const parsed = parseQuantityLine(fixOcrDigits(text));
      if (parsed) {
        ingredients.push(parsed);
        lastIngredient = { line: original, bulleted, continued: false };
        return record('ingredient');
      }
    } else if (!stepNumbered) {
      const stray = parseStrayIngredient(fixOcrDigits(text));
      if (stray) {
        ingredients.push(stray);
        return record('ingredient');
      }
    }

    const action = startsWithAction(text);

    // 1b) Continuación del ingrediente anterior, solo en el bloque de ingredientes (con
    //     encabezado, o sin encabezados antes del primer paso) y justo detrás de otro
    //     ingrediente: sin viñeta, sin número, sin verbo y con alguna señal de corte.
    //     Una "o" inicial se lee como viñeta (un círculo manuscrito), pero si el ingrediente
    //     anterior no llevaba viñeta es la conjunción: "100 ml de aceite de girasol" / "o de oliva".
    const inIngredientBlock = section === 'ingredients' || (section == null && steps.length === 0);
    const wordO = bulleted && /^o\s/i.test(original) && lastIngredient && !lastIngredient.bulleted;
    const continuation = wordO ? original : text;
    if (inIngredientBlock && lastIngredient && (!bulleted || wordO) && !numbered && !startsWithAction(continuation)
      && continuesIngredient(continuation, {
        previousLine: lastIngredient.line,
        previousBulleted: lastIngredient.bulleted,
        previousWasContinuation: lastIngredient.continued,
      })) {
      const previous = ingredients[ingredients.length - 1];
      previous.rawName = cleanName(`${previous.rawName} ${fixOcrLetters(continuation)}`);
      lastIngredient = { line: original, bulleted: lastIngredient.bulleted, continued: true };
      return record('ingredient-continuation');
    }

    // 2) Título: primera línea, corta, sin cantidad ni verbo y antes de cualquier otra cosa.
    if (index === 0 && title == null && !action && !stepNumbered && words <= 8) {
      title = cleanName(text);
      return record('title');
    }

    // 2b) Título en varias líneas ("Galletas con chips" / "de chocolate"): la línea sigue al
    //     título, es corta, no tiene cantidad, viñeta ni verbo, y va antes del primer
    //     encabezado o empieza por un conector que no puede abrir un ingrediente.
    const beforeHeading = firstHeading !== -1 && index < firstHeading;
    if (lastKind === 'title' && !bulleted && !action && !stepNumbered && words <= 8
      && (beforeHeading || TITLE_CONTINUATION_RE.test(text))) {
      title = `${title} ${text.replace(/[\s.,;:]+$/, '')}`;
      return record('title');
    }

    // 3) Ingrediente sin cantidad ("Sal", "Aceite de oliva"): en la sección de ingredientes,
    //    o antes de que aparezca ningún paso si es una línea corta sin verbo.
    const shortNoVerb = !action && !stepNumbered && words <= 4 && !/[.!?]$/.test(text);
    if (shortNoVerb && (section === 'ingredients' || (section == null && steps.length === 0))) {
      ingredients.push({ rawName: cleanName(text), quantity: null, unit: null });
      lastIngredient = { line: original, bulleted, continued: false };
      return record('ingredient');
    }

    // 4) Paso. Si continúa el anterior (el paso previo no acabó en punto y esta línea empieza
    //    en minúscula), se une: la letra manuscrita parte las frases en varias líneas.
    const previous = steps[steps.length - 1];
    const continues = lastKind === 'step' && !stepNumbered && previous && !/[.!?:]$/.test(previous) && /^[a-záéíóúñ(,]/.test(text);
    if (continues) {
      steps[steps.length - 1] = `${previous} ${text}`;
    } else {
      steps.push(text);
    }
    return record('step');
  });

  return { title, servings, ingredients, steps, lines: classified };
}

module.exports = { parseRecipeText, parseQuantityLine };
