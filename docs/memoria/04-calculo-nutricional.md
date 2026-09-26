# 04 · Cálculo nutricional automático con datos de BEDCA

## Objetivo y problema

Hasta esta iteración, una receta de NutriSocial era un texto estructurado: un título, una lista de ingredientes escritos libremente ("1 calabaza mediana") y unos pasos. Para una aplicación centrada en la nutrición falta lo esencial: saber cuántas calorías y cuántos macronutrientes (proteínas, hidratos de carbono y grasas) aporta cada ración. El objetivo es que ese cálculo sea automático: el usuario indica qué ingredientes usa y en qué cantidad, y el servidor obtiene los valores nutricionales sin que nadie tenga que introducirlos a mano.

Para ello hacen falta tres cosas que la aplicación no tenía. La primera, una fuente fiable de composición de alimentos. La segunda, que los ingredientes dejen de ser texto libre y pasen a tener nombre, cantidad y unidad por separado. La tercera, un mecanismo que relacione lo que escribe el usuario ("cebollas") con el alimento correspondiente de la base de datos ("Cebolla") y que convierta unidades de cocina ("2 cucharadas", "1 unidad") a gramos, que es la unidad en la que se expresan los valores de referencia.

La fuente elegida es BEDCA (Base de Datos Española de Composición de Alimentos), mantenida por la Agencia Española de Seguridad Alimentaria y Nutrición. Frente a bases de datos extranjeras como la del USDA, tiene dos ventajas claras para este proyecto: los nombres están en español y los alimentos corresponden a productos y preparaciones habituales en España. El inconveniente es que BEDCA no ofrece una API oficial, lo que condiciona buena parte de las decisiones de esta parte.

## Decisiones técnicas

**Importación masiva en lugar de una tabla escrita a mano.** El planteamiento inicial era una tabla `Food` con unos treinta alimentos introducidos manualmente. Se descartó porque cualquier receta real incluiría enseguida ingredientes fuera de esa lista, y porque copiar valores a mano es propenso a errores. En su lugar, se importaron todos los alimentos de BEDCA con valores completos: 953 de los 969 que devuelve el servicio.

**Una librería no oficial como vía de acceso.** BEDCA solo publica sus datos a través de su web, que por dentro consulta un servicio XML público. Para no reimplementar ese protocolo se usó `pybedca`, una librería de Python de terceros que lo envuelve. Antes de escribir el script definitivo, se exploró su API real con un script auxiliar (`explore_bedca.py`), que mostró tres detalles que habrían provocado errores silenciosos. BEDCA expresa la energía en kilojulios, aunque la librería ofrece la conversión a kilocalorías. Un nutriente ausente no llega como valor nulo, sino como un objeto con el componente vacío y valor 0. Y las trazas llegan como el texto `'trace'`. El script de descarga (`fetch_bedca.py`) tiene en cuenta los tres casos. Considera "traza" como 0 g. Descarta los alimentos a los que les falta energía, proteína, hidratos o grasa. Y descarta también los que tienen macronutrientes pero energía 0, porque la librería convierte en 0 los valores vacíos y ese caso solo puede ser un registro incompleto.

**Dataset congelado y versionado en el repositorio.** Como la importación depende de una librería no oficial y de un servicio que puede cambiar sin aviso, la aplicación nunca consulta BEDCA en vivo. El script se ejecutó una sola vez y su resultado, `backend/data/bedca_foods.json` (unos 140 KB), se añadió al repositorio. Así, quien clone el proyecto obtiene exactamente los mismos datos sin volver a descargar nada ni instalar Python, y el servicio público de BEDCA no recibe una ráfaga de casi mil peticiones en cada instalación. La descarga en sí se hizo con cuidado. Hay una pausa de 0,15 s entre peticiones y tres reintentos con espera creciente. El progreso se guarda cada 25 alimentos en un archivo intermedio, excluido de git, para poder reanudarla si se interrumpe. La descarga completa duró unos diez minutos.

**Seed idempotente con Prisma.** `prisma/seed.js` lee el JSON y hace un *upsert* por nombre de cada alimento: lo crea si no existe y actualiza sus valores si ya existe. Por eso puede ejecutarse tantas veces como se quiera (`npx prisma db seed`) sin duplicar filas, por ejemplo tras regenerar el JSON. Todas las operaciones van en una única transacción, porque SQLite confirmaría cada escritura por separado y el proceso sería mucho más lento. Con transacción, la carga completa tarda unos dos segundos. Para que el *upsert* por nombre sea posible, el campo `Food.name` es único y el script de descarga elimina los nombres repetidos.

**Ingredientes normalizados en una tabla propia.** En el informe 03 los ingredientes se guardaban como un array JSON de textos dentro de `Recipe`, y ya se señalaba que se normalizarían si hacía falta. Ahora hace falta: cada ingrediente necesita su cantidad, su unidad, su peso estimado y una referencia al alimento de BEDCA. Se creó el modelo `RecipeIngredient` con esos campos, más la posición para conservar el orden. La relación con `Food` es opcional y usa `onDelete: SetNull`. Un ingrediente puede no corresponder a ningún alimento conocido, y borrar un alimento no debe borrar recetas. La migración incluye un paso de datos escrito a mano: con la función `json_each` de SQLite, convierte los ingredientes de las recetas existentes en filas de `RecipeIngredient`, sin cantidad ni alimento, para que no se pierdan. Solo después se elimina la columna antigua.

**Totales calculados al crear la receta.** Los totales de calorías y macronutrientes se calculan al crear la receta y se guardan en cuatro columnas de `Recipe`. La alternativa era recalcularlos en cada consulta. Guardarlos tiene dos ventajas: las listas de recetas no tienen que repetir el cálculo, y la receta conserva los valores con los que se creó aunque el dataset cambie después. Los valores por ración no se guardan, porque se obtienen dividiendo entre el número de raciones al construir la respuesta.

**Peso aproximado de la ración.** Decir que una receta tiene "4 raciones" y 336 kcal por ración es poco útil si no se sabe cuánto es una ración. Por eso, al calcular los macronutrientes también se suman los gramos estimados de cada ingrediente, y el total se guarda en una columna `totalWeightGrams` de `Recipe`. En esa suma cuentan todos los ingredientes cuyo peso se conoce, aunque no tengan alimento de BEDCA asociado, porque el peso no depende de la composición. La columna admite nulos: vale `null` si no se pudo estimar el peso de ningún ingrediente, para no mostrar un "0 g" engañoso. El peso por ración (`gramsPerServing`) se calcula al responder, igual que los macronutrientes por ración, y los dos valores viajan en el bloque `nutrition` de la creación, del detalle y de la lista. La migración que añade la columna la rellena para las recetas existentes sumando los gramos que ya estaban guardados en sus ingredientes.

**Conversión de unidades (`unitConversion.js`).** Se admite un conjunto cerrado de unidades: g, kg, ml, l, cucharada (15 g), cucharadita (5 g), taza (240 g), pizca (0,5 g) y unidad. El servidor rechaza cualquier otra con un mensaje que enumera las válidas, y la aplicación las ofrece en un desplegable, de modo que no se escriben a mano. "Unidad" es un caso especial, porque su peso depende del alimento: se usa una tabla de pesos medios por pieza para los alimentos más comunes (huevo, 60 g; cebolla, 150 g; diente de ajo, 5 g…). La palabra clave se busca tanto en el nombre que escribió el usuario como en el del alimento de BEDCA asociado, admitiendo plurales. Si no aparece, el peso queda sin estimar y el ingrediente no suma, en lugar de inventarse un valor.

**Emparejamiento de alimentos (`matchFood.js`).** Es la parte más delicada, porque el usuario escribe "cebollas" o "pechuga de pollo" y BEDCA nombra sus alimentos como "Cebolla" o "Pollo, pechuga, con piel, crudo". La primera decisión fue normalizar ambos textos: minúsculas, sin tildes y con los espacios colapsados. La segunda fue aprovechar la convención de BEDCA de poner la descripción tras una coma ("Calabaza, cruda"). Cada alimento se indexa por su nombre completo y por la parte anterior a la primera coma, que es lo que escribiría una persona. Sobre esa base se aplica una puntuación por reglas, ordenadas de más a menos fiable:

1. El nombre completo coincide exactamente.
2. La consulta es un alias conocido (ver más abajo).
3. La parte antes de la coma coincide exactamente ("calabaza" → "Calabaza, cruda").
4. El nombre base empieza por la consulta ("arroz integral" → "Arroz integral, crudo").
5. Todas las palabras significativas de la consulta aparecen en el nombre, sin contar palabras vacías como "de" o "con" ("pechuga de pollo" → "Pollo, pechuga…").
6. La consulta empieza por el nombre base ("cebolla morada" → "Cebolla").

A igualdad de puntuación se prefiere el alimento en crudo, porque es como se pesan los ingredientes al cocinar, y después el de nombre más corto, que suele ser el más genérico. La consulta también se prueba en singular ("limones" → "limon") para tolerar plurales sencillos.

Las pruebas con los datos reales mostraron que el orden de las reglas importa. En una primera versión, la regla 6 iba antes que la 5, y "leche entera" acababa emparejada con "Leche, desnatada, pasteurizada" solo porque empieza por "leche". También mostraron que algunos nombres genéricos tienen en BEDCA tantas variantes que ninguna regla elige la habitual: "huevo" daba "Huevo de pato, crudo" y "pollo", "Pollo, ala, con piel, cruda". Para estos casos se añadió una tabla pequeña y explícita de alias (huevo, pollo, pan, harina, azúcar, leche, aceite…) que apunta al alimento de referencia. Es una solución deliberadamente manual y limitada. Si un alias deja de existir en el dataset, se ignora sin romper nada.

**El autocompletado como fuente principal y el emparejamiento como respaldo.** Ninguna heurística acierta siempre, así que el camino preferente es que el propio usuario elija el alimento. El endpoint `GET /foods/search` reutiliza el mismo sistema de puntuación, pero admite también coincidencias por subcadena para ofrecer sugerencias mientras se escribe. Si el usuario elige una sugerencia, la aplicación envía su `foodId` y el servidor lo usa directamente. Si no la elige, el servidor recurre a `matchFood`. El índice de alimentos se carga una vez en memoria, porque son menos de mil filas y solo cambian con el seed, en lugar de consultar la base de datos en cada pulsación.

**Compatibilidad hacia atrás de la API.** `POST /recipes` acepta ahora ingredientes como objetos `{ name, quantity, unit, foodId }`. Por compatibilidad, sigue aceptando textos sueltos, que se guardan sin cantidad. La cantidad es opcional ("sal al gusto"): un ingrediente sin cantidad se guarda y se muestra, pero no suma en el cálculo. La respuesta incluye un bloque `nutrition` con los totales, los valores por ración y la lista de ingredientes que no se han contado, para que la aplicación pueda avisar de que el resultado es parcial en lugar de mostrarlo como exacto.

**Actualización de Compose en Android.** Al probar el formulario en el emulador, la aplicación se cerraba con el error `LayoutNode should be attached to an owner` en cuanto aparecían las sugerencias. La causa no estaba en el código nuevo. Las versiones de `lifecycle` y `navigation` del proyecto arrastraban Compose UI a la versión 1.9, mientras que el BOM de Compose fijaba Material 3 en la 1.2.1, pensada para una UI anterior. La mezcla funcionaba en pantallas estáticas, pero fallaba al cambiar el tamaño de una columna con campos de texto. Se actualizó el BOM a `2025.08.00`, que alinea Material 3 (1.3.2) con Compose UI 1.9, y se adaptó la única API afectada (`menuAnchor` en el desplegable de unidades).

**Sugerencias en línea en lugar de un menú emergente.** Las sugerencias del autocompletado se dibujan como una lista dentro del propio formulario, bajo el campo, y no en un menú emergente (`ExposedDropdownMenu`). Un menú emergente es una ventana aparte que puede quitarle el foco al campo mientras se escribe y cerrar el teclado. La lista en línea evita ese problema y se oculta al elegir una opción o al salir del campo.

## Cómo funciona

El flujo empieza fuera de la aplicación, en tiempo de desarrollo. `fetch_bedca.py` pide a BEDCA la lista completa de alimentos y, uno a uno, el detalle de cada uno. De cada alimento extrae la energía en kilocalorías y los gramos de proteína, hidratos y grasa por cada 100 g, y escribe el resultado en `bedca_foods.json`. Ese archivo se versiona en el repositorio. Al preparar el backend, `npx prisma db seed` lo vuelca en la tabla `Food`.

En la aplicación, el formulario de nueva receta presenta cada ingrediente como un pequeño bloque con tres campos: el nombre, la cantidad y la unidad, esta última en un desplegable que empieza en gramos. Al escribir al menos dos letras del nombre, el `RecipeViewModel` espera 300 ms sin nuevas pulsaciones y pide sugerencias a `GET /foods/search`. Así no se lanza una petición por cada tecla. Las sugerencias aparecen bajo el campo con sus kilocalorías por 100 g, lo que ayuda a distinguir, por ejemplo, "Calabaza, cruda" (32 kcal) de "Pipa de calabaza" (600 kcal). Al tocar una, el nombre se sustituye por el de BEDCA y aparece una marca de verificación en el campo: el alimento está identificado y su `foodId` se enviará con la receta. Si el usuario vuelve a editar el nombre, la marca desaparece, porque el texto ya no corresponde necesariamente a ese alimento. La cantidad admite decimales con coma o punto, y puede dejarse vacía.

Al guardar, el servidor valida cada ingrediente: nombre no vacío, cantidad positiva si la hay, unidad de la lista y `foodId` existente si se envía. Después resuelve cada ingrediente. Si trae `foodId`, usa ese alimento. Si no, busca el más parecido con `matchFood`. Luego convierte la cantidad a gramos y, si tiene alimento y peso, suma su aporte: valor por 100 g × gramos / 100. La receta, sus ingredientes (con el peso estimado y el alimento asociado) y los cuatro totales se guardan en una sola operación de Prisma. La respuesta devuelve los ingredientes, los totales, los valores por ración y la lista de los que no se han podido contar.

Por ejemplo, una crema de calabaza para cuatro raciones con 800 g de "Calabaza, cruda" (elegida en el autocompletado) y "1 unidad" de "cebollas" (escrito sin elegir sugerencia) se calcula así. El servidor empareja "cebollas" con "Cebolla" gracias a la variante en singular y estima 150 g por pieza. El total es 800 × 32,1 / 100 + 150 × 26,1 / 100 ≈ 296 kcal, es decir, 74 kcal por ración.

La pantalla de detalle muestra primero una tarjeta de información nutricional: las kilocalorías por ración en grande, tres recuadros con los gramos de proteínas, hidratos y grasas por ración, el total de la receta completa y la fuente de los datos. Si algún ingrediente no se ha contado, se añade un aviso con sus nombres y la indicación de que los valores son aproximados. Si no se ha podido contar ninguno, como en las recetas creadas antes de esta iteración, la tarjeta explica que faltan datos y cómo obtenerlos. Bajo el nombre de cada ingrediente aparece una línea secundaria con la cantidad y, cuando corresponde, el peso estimado ("1 unidad (≈ 150 g)"). Si el emparejamiento se hizo por nombre, la línea indica con qué alimento se ha calculado ("calculado como «Cebolla»"), para que el usuario pueda detectar un emparejamiento incorrecto. Debajo de los macronutrientes, un recuadro indica el peso aproximado de cada ración y el de la receta completa ("1 ración ≈ 203 g · Receta ≈ 810 g en crudo"), para que los valores por ración tengan una referencia concreta. En la lista de recetas, cada tarjeta muestra además una etiqueta con las kilocalorías por ración.

El funcionamiento se comprobó de tres formas. El emparejamiento se probó con una batería de nombres habituales contra los datos reales. La API se probó con peticiones válidas e inválidas: unidad desconocida, cantidad negativa, `foodId` inexistente, ingredientes en el formato antiguo, peticiones sin token, y la receta migrada, que conserva sus ingredientes. El flujo completo se probó en el emulador: autocompletado, desplegable de unidades, guardado, lista y detalle de una receta nueva y de una antigua.

## Limitaciones

- **Dependencia de una librería no oficial.** `pybedca` no pertenece a BEDCA ni a la AESAN. Si el servicio web de BEDCA cambia, el script de descarga puede dejar de funcionar. Por eso el dataset está congelado en el repositorio: la aplicación sigue funcionando, pero actualizar los datos podría exigir adaptar el script. En la descarga, 12 identificadores que el servicio listaba no devolvieron detalle, y otros 4 alimentos se descartaron por tener los macronutrientes incompletos.
- **Cobertura del dataset.** BEDCA no incluye algunos alimentos básicos: no hay "limón" (solo zumo de limón y limonada), ni pasta por formatos ("macarrones", "espaguetis"), que se resuelven con alias hacia "Pasta alimenticia, cruda". Tampoco incluye productos de marca ni platos compuestos poco comunes.
- **Calidad de algunos registros de origen.** En casi todos los alimentos, la energía coincide con la estimada a partir de los macronutrientes (4 kcal/g de proteínas e hidratos y 9 kcal/g de grasas). Las excepciones son en su mayoría bebidas alcohólicas, cuya energía procede del etanol (7 kcal/g), que no es un macronutriente. Hay además algunos registros dudosos en el propio BEDCA: "Garbanzo, hervido" figura con 359 kcal/100 g, un valor propio del garbanzo seco. Estos valores se importan tal cual, sin corregirlos.
- **Emparejamiento heurístico.** `matchFood` puede equivocarse con nombres compuestos. "Nuez moscada" se empareja con "Nuez, cruda, con cáscara" por la regla 6. La tabla de alias solo cubre unos pocos genéricos. El autocompletado y la indicación "calculado como…" en el detalle mitigan el problema, pero no lo eliminan.
- **Conversiones aproximadas.** Los volúmenes se convierten con densidad 1, así que 1 taza de harina cuenta como 240 g cuando en realidad pesa unos 120 g, y 1 cucharada de aceite, 15 g en lugar de unos 13,5 g. Los pesos por pieza son medias ("1 cebolla" = 150 g), cuando una cebolla real puede pesar entre 100 y 300 g.
- **Estado del alimento.** Los valores corresponden al estado del alimento elegido, normalmente en crudo. El cálculo no tiene en cuenta la pérdida de agua al cocinar, ni el aceite absorbido al freír, ni la parte no comestible (piel, huesos, cáscara).
- **El peso por ración es el peso en crudo.** `totalWeightGrams` suma los ingredientes tal como se pesan antes de cocinar, sin aplicar factores de rendimiento por cocción. El peso real del plato puede ser muy distinto. El arroz, la pasta o las legumbres hervidos absorben agua y pesan entre dos y tres veces más una vez cocinados: en la receta de prueba, los 300 g de lentejas secas cuentan como 300 g. Una carne a la plancha o al horno, en cambio, pierde peso por evaporación. Tampoco se descuentan las partes no comestibles, y los ingredientes sin cantidad ("sal al gusto") no suman. Incorporar factores de rendimiento (*yield factors*, Yf) por técnica de cocción, como los que publican el USDA o el proyecto EuroFIR, queda como posible ampliación futura del núcleo de cálculo. Sería necesario que cada ingrediente o la receta indicara la técnica de cocción. Con ese dato se multiplicaría el peso crudo por el factor correspondiente y se estimaría también el peso de la ración ya cocinada.
- **Totales fijados al crear la receta.** Si el dataset se actualiza, las recetas existentes conservan sus totales anteriores hasta que se implemente la edición de recetas o un recálculo explícito. Tras un nuevo seed, además, hay que reiniciar el servidor para que el índice en memoria se recargue.
- **Recetas anteriores a esta iteración.** Sus ingredientes se migraron como texto sin cantidad, así que no tienen información nutricional. No se intentó extraer la cantidad del texto ("1 calabaza mediana"), porque el resultado no sería fiable.

## Archivos creados o modificados

**Scripts y datos (`backend/`)**

| Archivo | Responsabilidad |
|---|---|
| `scripts/explore_bedca.py` (nuevo) | Exploración de la API de `pybedca` y de la estructura de un alimento. |
| `scripts/fetch_bedca.py` (nuevo) | Descarga de todos los alimentos, extracción de macronutrientes, pausas, reintentos y progreso reanudable. |
| `data/bedca_foods.json` (nuevo) | Dataset congelado: 953 alimentos con kcal, proteínas, hidratos y grasas por 100 g. |
| `prisma/seed.js` (nuevo) | Carga idempotente (*upsert* por nombre) del JSON en la tabla `Food`. |
| `package.json` (modificado) | Registro del seed para `npx prisma db seed`. |
| `.gitignore` (modificado) | Excluye el archivo de progreso intermedio de la descarga. |

**Backend (`backend/`)**

| Archivo | Responsabilidad |
|---|---|
| `prisma/schema.prisma` (modificado) | Modelos `Food` y `RecipeIngredient`, y totales nutricionales en `Recipe`. |
| `prisma/migrations/…_nutrition/` (nuevo) | Crea las tablas, migra los ingredientes antiguos con `json_each` y elimina la columna JSON. |
| `prisma/migrations/…_recipe_weight/` (nuevo) | Añade `Recipe.totalWeightGrams` y la rellena para las recetas existentes. |
| `src/nutrition/normalize.js` (nuevo) | Normalización de textos para comparar nombres. |
| `src/nutrition/unitConversion.js` (nuevo) | Unidades admitidas, conversión a gramos y pesos medios por pieza. |
| `src/nutrition/matchFood.js` (nuevo) | Índice en memoria, puntuación por reglas, alias, `matchFood` y `searchFoods`. |
| `src/nutrition/calculate.js` (nuevo) | Resolución de ingredientes, suma de aportes y del peso en crudo, y resumen nutricional (con peso por ración) de la respuesta. |
| `src/routes/foods.js` (nuevo) | `GET /foods/search` (autocompletado) y `GET /foods/units`. |
| `src/routes/recipes.js` (modificado) | Validación de ingredientes estructurados, cálculo al crear y bloque `nutrition` en las respuestas. |
| `src/index.js` (modificado) | Registro de las rutas de alimentos. |

**Aplicación Android (`app/src/main/java/com/example/nutrisocial/`)**

| Archivo | Responsabilidad |
|---|---|
| `data/RecipeModels.kt` (modificado) | `RecipeIngredient`, `Nutrition`, `Macros`, `IngredientInput`, `FoodSuggestion` y la lista de unidades. |
| `ApiService.kt`, `data/RecipeRepository.kt` (modificados) | Búsqueda de alimentos. |
| `ui/recipes/RecipeViewModel.kt` (modificado) | Filas de ingrediente estructuradas, búsqueda con espera y validación de cantidades. |
| `ui/recipes/RecipeFormScreen.kt` (modificado) | Bloques de ingrediente, sugerencias en línea y desplegable de unidades. |
| `ui/recipes/RecipeDetailScreen.kt` (modificado) | Tarjeta de información nutricional con peso aproximado de la ración, y detalle de cantidad y alimento por ingrediente. |
| `ui/recipes/RecipeComponents.kt` (modificado) | Etiqueta de kcal por ración en las tarjetas y formato numérico en español. |
| `ui/home/HomeScreen.kt` (modificado) | Conexión de las nuevas acciones del formulario. |

**Otros:** `gradle/libs.versions.toml` (BOM de Compose 2025.08.00, que alinea Material 3 con Compose UI 1.9).
