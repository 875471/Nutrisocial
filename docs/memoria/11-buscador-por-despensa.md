# 11. Buscador de recetas por despensa

## Objetivo y problema

Hasta ahora, para decidir qué cocinar, el usuario tenía que partir de una receta y comprobar después si tenía los ingredientes. En casa suele pasar al revés: se abre la nevera, se ve lo que hay y se busca algo que se pueda hacer con eso. Este núcleo técnico (3.3) añade una **despensa**, una lista de ingredientes que el usuario tiene en casa, y un buscador que la cruza con **todas las recetas de la aplicación**, no solo con las del propio usuario. El buscador responde a dos preguntas: qué recetas se pueden cocinar ya, con todo lo necesario, y a qué recetas les faltan pocos ingredientes.

La dificultad está en decidir cuándo un ingrediente de la despensa "es" el de la receta. El usuario escribe "huevos" en la despensa y el autor de la receta escribió "Huevo de gallina", o uno escribe "aceite" y el otro "aceite de oliva". Además, la búsqueda tiene que dar resultados útiles: una receta de doce ingredientes en la que solo coincide la sal no le sirve a nadie.

## Decisiones técnicas

**Una tabla propia para la despensa, con el mismo enlace a `Food` que los ingredientes de receta.** El modelo `PantryItem` guarda el nombre tal como lo escribió el usuario y un `foodId` opcional hacia la tabla `Food`, igual que `RecipeIngredient`. Al añadir un ingrediente, el servidor lo empareja con `matchFood(nombre)`, la misma función que se usa al guardar una receta (informes 04 y 10). Así, los dos lados de la comparación se resuelven con el mismo criterio: BEDCA primero, alias para los nombres genéricos ("huevo" → "Huevo de gallina fresco") y Open Food Facts como respaldo. Un índice único `(userId, name)` impide duplicados en la base de datos. Como distingue mayúsculas y tildes, la ruta también compara los nombres normalizados antes de insertar, de modo que "Sal" y "sal" cuentan como el mismo y la respuesta es un `409`.

**Criterio de coincidencia: primero el alimento, después el nombre.** Un ingrediente de la receta se considera **cubierto** si algún elemento de la despensa cumple una de estas dos condiciones:

1. **Los dos tienen `foodId` y es el mismo.** Es el caso fiable: si "Huevos" en la despensa y "Huevo de gallina" en la receta se asociaron al mismo alimento de BEDCA, son lo mismo, aunque los nombres no se parezcan. Cuando los dos lados tienen `foodId`, manda el alimento y no se mira el nombre. "Leche" asociada a leche entera no cubre una "leche" asociada a leche desnatada.
2. **A alguno de los dos le falta `foodId`.** Entonces se compara el nombre normalizado con `normalize()` (minúsculas, sin tildes, espacios colapsados), y se acepta si los nombres coinciden o uno contiene al otro. La contención se comprueba **por palabras completas**, no por subcadenas de texto: "aceite" cubre "aceite de oliva" y "pollo" cubre "pechuga de pollo", pero "sal" no cubre "salsa de tomate". Antes de comparar, cada palabra pasa por un singular aproximado ("huevos" → "huevo", "macarrones" → "macarron"; las palabras de tres letras o menos no se tocan) para que el plural no impida la coincidencia.

No se reutiliza el emparejamiento completo de `matchFood` (distancia de edición, nombre base de BEDCA, puntuaciones), porque aquí se comparan dos textos libres entre sí y no un texto contra un catálogo. La coincidencia exacta o por contención basta para el caso sin alimento asociado, que es el minoritario.

**Umbral del 50 % de cobertura.** Para cada receta se calcula `coverage = matchedCount / totalIngredients`, y se descartan las que no llegan a 0,5. Con una despensa normal (sal, aceite, ajo, cebolla…) casi todas las recetas comparten algún ingrediente básico, y mostrarlas todas convertiría el buscador en un listado de la base de datos. La mitad es un corte sencillo y fácil de explicar: si falta más de la mitad, esa receta requiere ir a la compra, no aprovechar lo que hay. Los resultados se ordenan de mayor a menor cobertura y, a igual cobertura, primero las que tienen menos ingredientes pendientes (con un 50 %, es mejor que falte uno de dos que tres de seis).

**El servidor entrega los grupos ya separados.** `GET /recipes/by-pantry` devuelve `readyToCook` (cobertura del 100 %) y `almostReady` (el resto por encima del umbral). El cliente solo pinta: no repite el filtrado ni el orden, y si el criterio cambia no hace falta publicar una nueva versión de la aplicación.

**Lógica pura, separada de la base de datos.** `matchRecipesToPantry(pantryItems, recipes)` en `nutrition/pantryMatch.js` recibe datos ya cargados y no toca Prisma, igual que el recomendador del informe 09. La ruta se limita a leer la despensa y las recetas y a llamar a la función. Así se puede probar sin base de datos.

**Autocompletado reutilizado en Android.** La lista de sugerencias del formulario de recetas se ha extraído a `FoodSuggestionList` en `RecipeComponents.kt`, y la usan ahora el formulario y la despensa. Elegir una sugerencia añade directamente el ingrediente con el nombre del alimento, que después `matchFood` reconoce de forma exacta. También se puede escribir un nombre libre y pulsar "Añadir".

## Cómo funciona

**Despensa en el servidor.** `routes/pantry.js` ofrece tres rutas protegidas con el middleware de autenticación. `GET /pantry` devuelve los ingredientes del usuario, del más reciente al más antiguo, cada uno con su alimento asociado si lo tiene. `POST /pantry` recibe `{ name }`, limpia el texto, rechaza vacíos y nombres de más de 100 caracteres, comprueba duplicados (respuesta `409`), busca el alimento con `matchFood` y guarda el ingrediente. Si dos peticiones iguales llegan a la vez, la segunda choca con el índice único y también recibe un `409`. `DELETE /pantry/:id` borra con un filtro por `id` y `userId` a la vez, así que el ingrediente de otro usuario se trata como inexistente (`404`) sin revelar que existe.

**Búsqueda.** `GET /recipes/by-pantry` está declarada antes de `GET /recipes/:id` para que Express no interprete "by-pantry" como un id. Lee la despensa del usuario (solo `name` y `foodId`). Si está vacía, responde directamente con dos grupos vacíos. Si no, carga todas las recetas de cualquier autor con sus ingredientes, también solo con `name` y `foodId`, y llama a `matchRecipesToPantry`. Para cada receta, la función recorre sus ingredientes y comprueba si alguno de la despensa lo cubre según el criterio anterior. Los que no están cubiertos se acumulan en `missingIngredients` con el nombre que escribió el autor. Después calcula la cobertura, aplica el umbral y ordena. La respuesta incluye, por receta, `id`, `title`, `totalIngredients`, `matchedCount`, `missingIngredients` y `coverage`, además del número de ingredientes de la despensa.

**Pantalla "Despensa" en Android.** Es una nueva pestaña de la barra inferior (entre Diario y Perfil), con `PantryScreen`, `PantryViewModel` y `PantryRepository`, igual que recetas y diario. El `ViewModel` tiene el mismo ámbito que los demás de `HomeScreen`, así que al abrir una receta y volver, la despensa y los resultados siguen ahí. La pantalla tiene tres bloques:

1. **Campo "Añadir ingrediente"** con autocompletado contra `/foods/search`, con la misma espera de 300 ms que el formulario de recetas para no lanzar una petición por tecla. Mientras se guarda, el botón pasa a ser un indicador de progreso, y si el servidor responde con un error (por ejemplo, el `409` de duplicado) el mensaje aparece bajo el campo.
2. **Lista "En tu despensa"**, con una marca en los ingredientes que el servidor ha reconocido. Si el alimento asociado tiene otro nombre, se muestra debajo ("Reconocido como «Huevo de gallina fresco»"), para que el usuario vea con qué se va a comparar. Cada fila tiene un botón para quitarla. El borrado es optimista: la fila desaparece al momento y, si el servidor falla, vuelve a aparecer con un aviso en un *snackbar*.
3. **Botón "Buscar recetas"** (desactivado con la despensa vacía), que muestra los resultados en dos secciones: "Puedes cocinar ya" y "Te faltan pocos ingredientes". Cada tarjeta lleva el título, una etiqueta "Tienes 3 de 4 ingredientes" y, si falta alguno, el texto "Te falta: aceite de oliva, sal". Al tocarla se abre el detalle de la receta con la ruta `detalleReceta` de siempre, que ya cargaba cualquier receta por id, sea del usuario o no. Si se añade o se quita un ingrediente con resultados en pantalla, la búsqueda se repite sola para que no queden desfasados.

**Migración.** Como en el informe 08, el `.env` local apunta a la base de datos de producción en Neon, así que no se usó `prisma migrate dev`, que puede proponer reiniciarla. El SQL se generó sin conexión con `prisma migrate diff`, comparando el esquema anterior con el nuevo. Solo crea la tabla `PantryItem`, su índice único y las dos claves ajenas (borrado en cascada con el usuario y `SET NULL` si se borra el alimento). `prisma migrate status` confirmó que era la única migración pendiente, y se aplicó con `prisma migrate deploy` (`20260927180000_pantry`).

**Pruebas.** `test/pantryMatch.test.js` añade 9 pruebas sin base de datos. Cubren una receta totalmente cubierta, una con ingredientes que faltan (se comprueba la lista exacta de faltantes y la cobertura), una por debajo del 50 % que se descarta y que aparece al añadir un ingrediente más, la coincidencia por `foodId` compartido con nombres distintos, que con `foodId` en ambos lados manda el alimento aunque el nombre sea igual, la coincidencia solo por nombre cuando falta `foodId`, que "sal" no cubre "salsa de tomate", el orden de los resultados y los casos vacíos. La primera ejecución detectó que "macarrón" no cubría "macarrones", porque el singular inicial solo quitaba la *-s*. Por eso el singular aproximado también quita *-es* tras consonante. Además, las rutas se probaron en proceso con Prisma simulado: alta, duplicado con otra capitalización (`409`), nombre vacío (`400`), borrado de un ingrediente ajeno (`404`) y búsqueda con la despensa vacía y con contenido.

## Limitaciones

- **El emparejamiento por nombre es más simple que el de las recetas.** Cuando a alguno de los dos lados le falta `foodId`, solo se acepta la coincidencia exacta o por contención de palabras completas, con un singular aproximado. No hay tolerancia a errores de escritura, sinónimos ni variantes. Un mismo alimento escrito de forma muy distinta en la despensa y en la receta ("jitomate" y "tomate", "judías verdes" y "vainas", "maicena" y "harina de maíz") no se reconoce como el mismo. El singular también falla en plurales irregulares ("nueces" y "nuez"). En la práctica, este caso afecta sobre todo a los ingredientes que ni BEDCA ni Open Food Facts reconocen.
- **Con `foodId` en ambos lados, dos variantes del mismo alimento no coinciden.** Si la despensa tiene "leche" (asociada a leche entera) y la receta pide "leche semidesnatada" (otro alimento de BEDCA), la receta dice que falta leche, aunque en la cocina se podría sustituir sin problema. No hay una jerarquía de alimentos que diga que las dos son "leche".
- **La búsqueda no tiene en cuenta cantidades.** Un ingrediente está en la despensa o no lo está: tener 10 g de harina cuenta igual que tener 1 kg, y la receta que pide 500 g aparece como "Puedes cocinar ya". La despensa no guarda cantidades ni fechas de caducidad.
- **Todos los ingredientes pesan lo mismo.** La sal, que casi todo el mundo tiene, cuenta igual que el ingrediente principal. Una receta a la que solo le falta la carne puede salir como "te faltan pocos ingredientes". El umbral del 50 % es un valor fijo, elegido a criterio y no validado con usuarios.
- **Se cargan todas las recetas en cada búsqueda.** Con el volumen de un TFG (decenas o pocos cientos de recetas) es inmediato. Con miles, habría que prefiltrar en la base de datos, por ejemplo con las recetas que comparten al menos un `foodId` con la despensa, antes de calcular la cobertura en memoria.
- **Sin pruebas de interfaz.** La pantalla de Android se ha compilado y tiene vista previa, pero no hay pruebas automáticas de Compose. Tampoco se ha probado de extremo a extremo contra Render hasta desplegar el backend.

## Archivos creados o modificados

**Backend (`backend/`)**

| Archivo | Cambio |
|---|---|
| `prisma/schema.prisma` (modificado) | Modelo `PantryItem` y relaciones inversas en `User` y `Food`. |
| `prisma/migrations/20260927180000_pantry/migration.sql` (nuevo) | Tabla `PantryItem`, índice único `(userId, name)` y claves ajenas. |
| `src/nutrition/pantryMatch.js` (nuevo) | `matchRecipesToPantry`: criterio de coincidencia, cobertura, umbral del 50 % y orden. |
| `src/routes/pantry.js` (nuevo) | `GET /pantry`, `POST /pantry` (con `matchFood` y `409` si ya existe) y `DELETE /pantry/:id`. |
| `src/routes/recipes.js` (modificado) | `GET /recipes/by-pantry`, con los grupos `readyToCook` y `almostReady`. |
| `src/index.js` (modificado) | Monta las rutas de `/pantry`. |
| `test/pantryMatch.test.js` (nuevo) | 9 pruebas del emparejamiento. |

**Aplicación Android (`app/src/main/java/com/example/nutrisocial/`)**

| Archivo | Cambio |
|---|---|
| `data/PantryModels.kt` (nuevo) | `PantryItem`, `AddPantryItemRequest`, `PantryRecipeMatch` y `PantrySearchResult`. |
| `data/PantryRepository.kt` (nuevo) | Llamadas a la despensa, a la búsqueda por despensa y al autocompletado. |
| `ApiService.kt` (modificado) | Endpoints `pantry` y `recipes/by-pantry`. |
| `ui/pantry/PantryViewModel.kt` (nuevo) | Estado de la lista, del campo de alta con sugerencias y de la búsqueda; borrado optimista. |
| `ui/pantry/PantryScreen.kt` (nuevo) | Campo con autocompletado, lista con borrado y resultados en dos secciones. |
| `ui/recipes/RecipeComponents.kt` (modificado) | `FoodSuggestionList`, extraída del formulario para compartirla. |
| `ui/recipes/RecipeFormScreen.kt` (modificado) | Usa `FoodSuggestionList`. |
| `ui/home/HomeScreen.kt` (modificado) | Pestaña "Despensa", su ruta y el `PantryViewModel` compartido. |
