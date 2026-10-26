# 24. Generación de recetas con IA a partir de los ingredientes disponibles

## Objetivo y problema

El buscador por despensa (informe 11) solo encuentra recetas que ya existen en la aplicación. Si nadie ha publicado una receta con lo que el usuario tiene en casa, no hay nada que proponerle. Un modelo de lenguaje puede escribir una receta nueva con esos ingredientes, pero tiene un problema conocido para una aplicación de nutrición: los valores nutricionales que da un modelo son poco fiables, porque no los calcula a partir de una tabla de composición, sino que los genera como texto plausible.

El objetivo de esta parte es aprovechar el modelo solo para lo que hace bien, que es proponer un título, unos pasos y unas cantidades razonables, y seguir calculando los macros con el pipeline propio del proyecto contra BEDCA (informe 04). Además, las recetas generadas tienen que quedar identificables como tales una vez guardadas.

## Decisiones técnicas

**El modelo no aporta macros.** El formato de respuesta que se le pide no tiene ningún campo nutricional. La receta propuesta pasa por `resolveIngredients`, exactamente igual que una receta escrita a mano: emparejamiento con BEDCA u Open Food Facts, conversión de unidades con densidades (informe 04) y suma de aportes. Así, una receta generada y una escrita por un usuario son comparables, y los errores del modelo en las cantidades se notan en el resultado en lugar de quedar ocultos tras unos macros inventados.

**Proveedor y modelo.** Se usa la API de Anthropic con su SDK oficial para Node.js (`@anthropic-ai/sdk`) y el modelo Claude Haiku 5.5 (`claude-haiku-5-5`), el más rápido y barato de la familia actual. Generar una receta corta no necesita un modelo mayor. El esfuerzo de razonamiento se fija en `low`. La clave se lee de la variable de entorno `ANTHROPIC_API_KEY`.

**Salida estructurada y validación propia.** La petición usa la salida estructurada de la API (`output_config.format` con un esquema JSON): el modelo solo puede responder con un objeto que tenga `title`, `servings`, `steps` e `ingredients`, y la unidad de cada ingrediente está restringida por un `enum` a las unidades que acepta la aplicación (`UNITS`, de `unitConversion.js`). Aun así, la respuesta se vuelve a validar a mano (`validateGeneratedRecipe`) por dos motivos: el esquema no admite límites numéricos, y una respuesta incompleta (cortada por longitud o rechazada) no debe llegar al cálculo. La validación comprueba tipos, raciones enteras entre 1 y 20, como mucho 30 pasos y 30 ingredientes, y un tope de cantidad por unidad (5 kg, 20 cucharadas, 30 unidades…) contra cantidades absurdas.

**Errores del servicio externo como 502.** Si la respuesta no es válida o la API falla (clave incorrecta, límite de uso, caída), la ruta responde `502 Bad Gateway` con un mensaje claro: el fallo es del servicio externo, no de la petición del usuario ni un error interno. Si falta la clave en el servidor, responde `503` y lo deja escrito en el log, en lugar de un `500` genérico.

**Propuesta sin guardar.** `POST /recipes/generate` no escribe en la base de datos, igual que `POST /recipes/parse-ocr` (informe 05). Si al usuario le gusta la propuesta, la guarda con el `POST /recipes` normal, que acepta un campo nuevo `aiGenerated: true`. Ese campo se guarda en `Recipe.aiGenerated` (booleano, `false` por defecto) y se devuelve en el detalle y en las tarjetas del feed, para que la interfaz pueda marcarlas. Solo se fija al crear: editar una receta no lo cambia.

**Coste acotado.** Cada generación es una llamada de pago, así que la ruta tiene un límite de 10 peticiones cada 15 minutos por conexión, con el mismo limitador que las rutas de autenticación (informe 13).

## Cómo funciona

La aplicación envía `POST /recipes/generate` con `{ "ingredients": ["pollo", "arroz", "pimiento"] }`. El servidor limpia la lista (sin vacíos ni duplicados, como mucho 30 nombres de 80 caracteres) y llama al modelo. El mensaje de sistema le pide una receta casera en español con esos ingredientes como base, le permite suponer sal, aceite, agua y especias comunes, le da literalmente la lista de unidades válidas y le pide cantidades realistas para el número de raciones. Como la lista la escribe el usuario, se envía serializada como JSON y el mensaje de sistema indica que se trate solo como nombres de ingredientes, ignorando cualquier instrucción que aparezca en ella.

Con la respuesta validada, los ingredientes se resuelven contra la tabla `Food` y se devuelve la propuesta completa: título, raciones, pasos, cada ingrediente con su peso estimado en gramos y el alimento asociado (o `null` si no se ha reconocido), el bloque `nutrition` (totales, por ración, peso y lista de ingredientes que no han contado) y `aiGenerated: true`. En una prueba con una propuesta simulada, 1 taza de arroz se convirtió en 204 g y 2 cucharadas de aceite en 27,6 g, gracias a las densidades del informe 04, y un ingrediente sin emparejar (pimentón) apareció en `uncountedIngredients`.

En Android solo se ha añadido la capa de datos: `ApiService.generateRecipe`, `RecipeRepository.generateRecipe` y los modelos `GenerateRecipeRequest` y `GeneratedRecipeProposal`, además del campo `aiGenerated` en `Recipe`, `FeedRecipe` y `CreateRecipeRequest`. La pantalla queda para más adelante.

**Pruebas.** `test/generateRecipe.test.js` añade 5 pruebas sin red ni clave real: una respuesta válida, respuestas que no son JSON o no son un objeto, diez respuestas con campos mal formados (título vacío, raciones decimales o cero, pasos vacíos o no textuales, unidad fuera de `UNITS`, cantidad negativa o absurda, ingrediente sin nombre), la limpieza de la petición, y una llamada real al SDK con `fetch` simulado. En esa última se comprueba que la petición pide salida estructurada con las unidades de la aplicación, el modelo correcto y los ingredientes del usuario, y que una respuesta no JSON o cortada por `max_tokens` acaba en `GenerationError`. La ruta HTTP se probó a mano con Prisma en memoria: `400` sin ingredientes, `503` sin clave, `502` con JSON roto y con un `401` de la API, y `200` con la propuesta resuelta.

## Limitaciones

- **La calidad de la receta depende del modelo.** La validación garantiza que la forma y los rangos sean correctos, no que la receta sea buena o que los pasos sean coherentes con las cantidades. Una cantidad dentro de los topes puede seguir siendo poco realista (300 g de sal entran en el límite de 5 kg).
- **No se ha probado contra la API real.** Todas las pruebas usan respuestas simuladas; falta generar recetas reales y revisar su calidad, y medir latencia y coste por petición.
- **Los ingredientes que el modelo nombra de forma poco habitual pueden no emparejarse** con BEDCA, y entonces no cuentan en los macros. El resultado lo indica en `uncountedIngredients`, igual que en una receta manual.
- **El límite de uso es por conexión, no por usuario.** Varios usuarios detrás de la misma IP comparten cupo, y un usuario puede rodearlo cambiando de red. No hay un presupuesto global de gasto.
- **Dependencia de un servicio externo de pago.** Sin clave o sin saldo, la función deja de estar disponible (`503`/`502`); el resto de la aplicación no se ve afectado.
- **Sin interfaz todavía.** Falta la pantalla en Android y marcar visualmente las recetas con `aiGenerated`.

## Archivos creados o modificados

**Backend (`backend/`)**

| Archivo | Responsabilidad |
|---|---|
| `src/ai/generateRecipe.js` (nuevo) | Prompt, esquema de salida, llamada al modelo y validación de la petición y de la respuesta. |
| `src/routes/recipes.js` (modificado) | `POST /recipes/generate` con límite de uso; `aiGenerated` al crear y en las respuestas. |
| `prisma/schema.prisma` (modificado) | Campo `Recipe.aiGenerated`. |
| `prisma/migrations/20261026120000_recipe_ai_generated/` (nuevo) | Añade la columna con `DEFAULT false`. |
| `package.json`, `package-lock.json` (modificados) | Dependencia `@anthropic-ai/sdk`. |
| `test/generateRecipe.test.js` (nuevo) | 5 pruebas de la validación y de la llamada al SDK. |

**Aplicación Android (`app/src/main/java/com/example/nutrisocial/`)**

| Archivo | Responsabilidad |
|---|---|
| `ApiService.kt` (modificado) | `generateRecipe`. |
| `data/RecipeRepository.kt` (modificado) | `generateRecipe`, que devuelve la propuesta sin guardarla. |
| `data/RecipeModels.kt` (modificado) | `GenerateRecipeRequest`, `GeneratedRecipeProposal` y `aiGenerated` en `Recipe`, `FeedRecipe` y `CreateRecipeRequest`. |
