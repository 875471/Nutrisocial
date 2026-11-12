# 27. Etiquetas dietéticas

## Objetivo y problema

Hasta ahora, una persona vegana, celíaca o con intolerancia a la lactosa no tenía forma de encontrar en NutriSocial recetas que se ajustaran a su dieta, salvo abrir una a una y leer los ingredientes. El objetivo de esta parte es que cada receta pueda llevar **etiquetas dietéticas** y que los listados se puedan filtrar por ellas.

La lista de etiquetas es fija y corta: `vegano`, `vegetariano`, `sin_gluten`, `sin_lactosa` y `sin_frutos_secos`. Cubre las dietas y las alergias o intolerancias más frecuentes, y no necesita ser configurable.

La decisión de fondo es que las etiquetas son **autodeclaradas**: las elige quien publica la receta y NutriSocial no las comprueba. Esto tiene una consecuencia de seguridad que condiciona el diseño de la interfaz (ver más abajo): alguien con una alergia no debe fiarse solo de la etiqueta.

La función es gratuita. Premium sigue limitado al escáner OCR y a las recomendaciones del Diario (informe 26).

## Decisiones técnicas

**Etiquetas autodeclaradas, no deducidas de los ingredientes.** Deducirlas automáticamente parece más atractivo, pero no es fiable con los datos que tiene la aplicación. Un ingrediente se empareja con un alimento de BEDCA por nombre (informe 04), y ese emparejamiento puede fallar o quedarse sin alimento. Además, BEDCA no dice si un alimento contiene gluten o trazas de frutos secos: «harina» puede ser de trigo o de arroz, y «chocolate» puede llevar leche o no. Una etiqueta «sin gluten» calculada sobre esa base daría una falsa sensación de seguridad, que es peor que no tener etiqueta. Por eso la decide el autor, que sabe qué ha usado, y la interfaz dice claramente que no está verificada.

**Un aviso visible, siempre.** Debajo de los chips del formulario aparece siempre, en letra pequeña pero sin ocultar, el texto «Etiqueta declarada por quien publica la receta, no verificada por NutriSocial — revisa siempre los ingredientes.». El mismo aviso se repite en el detalle de cualquier receta que tenga etiquetas, justo debajo de la tarjeta con la lista de ingredientes, que es donde alguien con alergia toma la decisión. En las tarjetas del feed no se repite, para no saturarlas. El texto vive en una sola constante (`DIET_TAGS_DISCLAIMER`), para que los dos sitios no puedan diferir.

**Lista fija en un único módulo del backend.** `src/nutrition/dietTags.js` define `DIET_TAGS` y las tres funciones que usan las rutas: `cleanDietTags` valida el cuerpo de creación y edición, `parseDietTagsQuery` lee el filtro de los listados y `dietTagsWhere` construye la condición de Prisma. Así la lista no se repite en cada ruta. En la aplicación, el enum `DietTag` tiene los mismos identificadores, y una prueba unitaria comprueba que coinciden.

**Una columna `String[]`, no una tabla aparte.** En PostgreSQL una lista de texto es una columna nativa (`TEXT[]`), y Prisma la filtra con `hasEvery`, `hasSome` y `has`. Con solo cinco valores posibles, sin datos asociados a cada etiqueta, una tabla intermedia receta-etiqueta solo añadiría una relación más que cargar en cada consulta. La columna es `dietTags String[] @default([])`, de modo que las recetas existentes quedan sin etiquetas.

**El filtro exige todas las etiquetas a la vez.** Quien filtra por «vegano» y «sin gluten» quiere recetas que cumplan las dos, no las que cumplan alguna. Por eso la condición es `{ dietTags: { hasEvery: tags } }` y no `hasSome`. En la interfaz, el texto de la despensa lo dice («todas a la vez»), y el mensaje de «sin resultados» de Explorar enumera las etiquetas exigidas.

**Validación estricta y sin normalizar.** Una etiqueta que no está en la lista da `400` con el nombre de la que falla y las válidas, tanto al guardar como al filtrar. No se aceptan mayúsculas ni variantes («Vegano», «sin-gluten»): la aplicación envía siempre el identificador exacto, y aceptar variantes solo ocultaría errores de otros clientes. Los duplicados sí se quitan sin error, y las etiquetas se guardan siempre en el orden de la lista, para que dos recetas con las mismas etiquetas tengan el mismo valor.

**Editar sin `dietTags` conserva las etiquetas.** `PUT /recipes/:id` sustituye la receta entera, salvo la foto, que solo cambia si el cuerpo trae `imageBase64` (informe 12). Las etiquetas siguen la misma regla: si el cuerpo no trae `dietTags`, se conservan las que hubiera. Así, una versión antigua de la aplicación que no conoce el campo no borra las etiquetas al editar. La aplicación actual envía siempre la lista, también vacía, para poder quitarlas todas.

**El buscador filtra las etiquetas en la base de datos.** `GET /recipes/search` busca el texto en memoria, porque PostgreSQL no ignora las tildes sin la extensión `unaccent` (informe 15). Las etiquetas, en cambio, son una comparación exacta, así que se filtran en la consulta que carga los candidatos (`where: dietTagsWhere(tags)`). Así se cargan menos recetas en memoria cuando hay filtro.

**Migración segura, no `prisma migrate dev`.** La base de datos del `.env` es la de producción en Neon, y `migrate dev` puede proponer reiniciarla si detecta diferencias. Como en el informe 26, la migración `20261112120000_add_diet_tags_and_shopping_list` (compartida con la lista de la compra, informe 28) se generó con `prisma migrate diff` contra la base real, que solo lee el esquema. Se revisó: añade la columna con su valor por defecto, crea la tabla de la lista de la compra y no modifica nada más. Antes de aplicarla en producción se probó en un PostgreSQL local temporal: se aplicaron las 12 migraciones desde cero y `migrate diff` contra `schema.prisma` salió vacío, es decir, sin desfase.

## Cómo funciona

**API.**

- `POST /recipes` acepta `dietTags` opcional, una lista de identificadores. Sin el campo, la receta queda sin etiquetas.
- `PUT /recipes/:id` igual, salvo que sin el campo conserva las etiquetas que tuviera.
- Las respuestas de receta (detalle, «Mis recetas», tarjetas del feed, buscador, guardadas y buscador por despensa) incluyen `dietTags`.
- `GET /recipes/feed`, `/feed/friends`, `/search` y `/by-pantry` aceptan `?dietTags=vegano,sin_gluten` (separado por comas). Sin el parámetro, o vacío, no se filtra. Se combina con el resto de parámetros: en el buscador, con el texto y el orden; en la despensa, con la cobertura de ingredientes.

**Formulario de receta.** Debajo de las raciones y el tiempo hay una sección «Etiquetas dietéticas (opcional)» con una fila desplazable de cinco chips seleccionables y, debajo, el aviso. Al editar una receta, los chips aparecen con las etiquetas que ya tenía.

**Tarjetas y detalle.** Las etiquetas aparecen como chips pequeños de solo lectura entre la descripción y la barra de estadísticas de la tarjeta. El detalle usa la misma tarjeta (informe 15), así que también las muestra, y debajo añade el aviso. Las recetas sin etiquetas no muestran nada, ni siquiera un hueco.

**Filtros.** En Explorar, debajo de los chips de orden, hay una segunda fila con las cinco etiquetas. Cada pulsación cambia el filtro y relanza la búsqueda al momento, sin esperar como con el texto. En la Despensa, encima del botón «Buscar recetas», hay la misma fila con el texto «Solo recetas con estas etiquetas (todas a la vez)». Si ya había resultados, cambiar el filtro los recalcula. Las tarjetas de resultados de la despensa muestran también las etiquetas.

**Pruebas.** `test/dietTags.test.js` tiene 8 pruebas. Tres son de funciones puras: la validación con deduplicación y orden, la lectura del parámetro (comas, espacios, parámetro repetido, vacío) y la condición `hasEvery`. Las otras cinco prueban las rutas reales (Express + JWT) con Prisma en memoria, como en `subscription.test.js`:

- crear con etiquetas válidas y sin ellas;
- el `400` con etiquetas desconocidas, al crear y al editar, sin guardar nada;
- editar las etiquetas, conservarlas sin el campo y vaciarlas;
- el filtro en `/feed` y `/search` con una etiqueta y con dos a la vez, comprobando que solo sale la receta que tiene las dos;
- el filtro en `/by-pantry`.

El Prisma en memoria aplica `hasEvery` de verdad, así que si la ruta no pasara la condición, las pruebas de filtro fallarían. Como un Prisma falso no prueba la consulta real, también se ejecutó el backend contra un PostgreSQL local temporal con los alimentos de BEDCA cargados. Allí se comprobaron el filtro con una y con dos etiquetas en el feed, el buscador y la despensa, el `400`, y la edición de etiquetas. En la aplicación, `DietTagsAndShoppingListTest` comprueba que los identificadores coinciden con los del backend y que el parámetro se construye en el orden de la lista.

**Datos de demostración.** `prisma/seedDemoData.js` (informe 22) asigna ahora etiquetas a sus recetas. Se eligieron con criterio realista para que la demostración sea coherente: la avena y el chorizo no se marcan sin gluten, la miel y el huevo excluyen «vegano», y lo que lleva nueces, almendra o pesto no es «sin frutos secos». Además, el script añade siete recetas pensadas para enseñar combinaciones distintas: un hummus y un curry con las cinco etiquetas, un bizcocho de almendra sin gluten pero con frutos secos, una pasta al pesto solo vegetariana, un brownie vegano con trigo, unos tacos de lechuga sin gluten pero con carne y unas gachas veganas de avena que no son sin gluten. En las recetas que ya existían, las etiquetas solo se ponen si no tenían ninguna, así que el script no pisa una edición. En producción quedaron 40 recetas: 12 veganas, 25 vegetarianas, 26 sin gluten, 28 sin lactosa y 34 sin frutos secos, y nueve cumplen a la vez «vegano» y «sin gluten».

## Limitaciones

- **Las etiquetas no se verifican.** Una receta etiquetada «sin gluten» puede llevar harina de trigo si su autor se equivoca o miente. El aviso lo deja claro, pero no lo impide. Una versión futura podría avisar cuando una etiqueta contradice un ingrediente evidente («vegano» con «huevo» o «mantequilla»), con una lista de palabras clave por etiqueta. Solo serviría para detectar errores claros, nunca para garantizar que una receta es apta.
- **No hay trazas ni alérgenos detallados.** «Sin frutos secos» no distingue entre ingrediente y trazas, y no hay etiquetas para otros alérgenos de declaración obligatoria (huevo, soja, marisco, sésamo…). Ampliar la lista es trivial en código, pero haría más larga la fila de chips.
- **Los filtros no se recuerdan.** El filtro de Explorar y el de la Despensa se conservan mientras dura la sesión, pero no se guardan: al cerrar la aplicación vuelven a estar vacíos. Tampoco se pueden fijar en el perfil como preferencia («soy vegana»).
- **Las tarjetas ya cargadas no se refiltran al editar.** Si se quita una etiqueta a una receta propia mientras Explorar está filtrado por ella, la tarjeta sigue en la lista, ya sin la etiqueta, hasta que se vuelve a buscar o se refresca.

## Archivos creados o modificados

- `backend/src/nutrition/dietTags.js` (nuevo): lista fija y validación de etiquetas y del filtro.
- `backend/prisma/schema.prisma`: `Recipe.dietTags String[] @default([])`.
- `backend/prisma/migrations/20261112120000_add_diet_tags_and_shopping_list/migration.sql` (nuevo, compartido con el informe 28).
- `backend/src/routes/recipes.js`: validación en `POST`/`PUT`, `dietTags` en las respuestas y filtro en `/feed`, `/feed/friends`, `/search` y `/by-pantry`.
- `backend/test/dietTags.test.js` (nuevo).
- `backend/prisma/seedDemoData.js`: etiquetas de las recetas de demostración y siete recetas nuevas.
- `app/.../data/DietTags.kt` (nuevo): enum `DietTag` y texto del aviso.
- `app/.../data/RecipeModels.kt`, `PantryModels.kt`, `FeedRepository.kt`, `PantryRepository.kt`, `ApiService.kt`: campo `dietTags` y parámetro de filtro.
- `app/.../ui/recipes/DietTagComponents.kt` (nuevo): chips seleccionables, chips de solo lectura y aviso.
- `app/.../ui/recipes/RecipeFormScreen.kt`, `RecipeViewModel.kt`: sección de etiquetas del formulario.
- `app/.../ui/recipes/RecipeFeedCard.kt`, `RecipeDetailScreen.kt`, `ui/home/FeedViewModel.kt`: etiquetas en la tarjeta y el detalle.
- `app/.../ui/explore/ExploreContent.kt`, `RecipeSearchViewModel.kt`, `ui/pantry/PantryScreen.kt`, `PantryViewModel.kt`, `ui/home/HomeScreen.kt`: filtros.
- `app/src/test/.../DietTagsAndShoppingListTest.kt` (nuevo, compartido con el informe 28).
- `docs/anexo-b-documentacion-api-rest.md`: campo `dietTags` y parámetro de filtro.
