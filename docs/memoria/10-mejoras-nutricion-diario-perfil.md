# 10. Open Food Facts como respaldo de BEDCA, añadir al diario desde la receta y perfil en modo vista

## Objetivo y problema

Esta iteración reúne tres mejoras independientes que salieron al usar la aplicación con recetas reales.

**1. Ingredientes que BEDCA no conoce.** El cálculo nutricional (informe 04) empareja cada ingrediente con uno de los 953 alimentos de BEDCA. Al probarlo con recetas de repostería se vio que ingredientes corrientes como "chips de chocolate", "esencia de vainilla", "bicarbonato" o "levadura química" no encuentran coincidencia. Tiene sentido: BEDCA es una base de datos de composición de alimentos genéricos, en su mayoría crudos, no de productos envasados ni de ingredientes de repostería. Un ingrediente sin alimento asociado se guarda, pero no suma nada, así que las kcal de esas recetas salían muy por debajo de la realidad. La aplicación ya avisaba ("Valores aproximados. No se han contado: …"), pero el aviso no arregla el número.

**2. Registrar una receta desde su propia pantalla.** Para apuntar en el diario una receta que se está viendo había que salir del detalle, ir a la pestaña Diario, abrir la hoja "Añadir" y volver a buscarla. Es el caso de uso más habitual ("me estoy comiendo esto ahora") y era el más largo.

**3. Un perfil que siempre parecía a medio rellenar.** La pestaña Perfil mostraba siempre el formulario editable, aunque los datos ya estuvieran guardados. Los datos se guardaban bien, pero la pantalla no distinguía entre *ver* y *editar*, y daba la impresión de que había que rellenarlos cada vez.

## Decisiones técnicas

**Open Food Facts como fuente complementaria.** Se valoraron varias bases de datos de productos y se eligió Open Food Facts (`world.openfoodfacts.org`) por tres motivos: es gratuita y de datos abiertos (licencia ODbL), no necesita clave de API ni registro, y tiene muchos productos envasados que se venden en España con el nombre en español, justo lo que le falta a BEDCA. Otras opciones, como FoodData Central (USDA) o Edamam, piden clave y están en inglés, y las de pago no encajan en un TFG. La decisión clave es el **orden**: Open Food Facts es solo un respaldo. Se consulta únicamente cuando BEDCA no tiene nada parecido al ingrediente, y un alimento de BEDCA nunca se sustituye por uno de Open Food Facts, aunque este último se parezca más al nombre escrito.

**Qué se considera un producto utilizable.** Muchas fichas de Open Food Facts están incompletas, así que se descartan las que no traen los cuatro valores por 100 g (`energy-kcal_100g`, `proteins_100g`, `carbohydrates_100g` y `fat_100g`) como números válidos. También se descartan las que son físicamente imposibles (más de 100 g de un macronutriente en 100 g de producto, o más de 900 kcal). La búsqueda se limita a productos vendidos en España, porque en las primeras pruebas sin ese filtro "chips de chocolate" devolvía unas galletas escocesas con el nombre en inglés.

**Criterio de parecido estricto.** Open Food Facts ordena por relevancia, pero su búsqueda es amplia: un producto que *contiene* chips de chocolate también sale al buscar "chips de chocolate". Por eso, además de exigir que todas las palabras significativas del término (sin "de", "con", etc.) aparezcan en el nombre del producto, se exige que el nombre *empiece* por la primera de ellas. Así, "Chips de chocolate negro" vale, pero "Galletas con chips de chocolate" no. Los plurales sencillos se toleran ("pepitas" ~ "pepita"). El criterio es deliberadamente conservador: es preferible dejar un ingrediente sin datos, y que la app lo avise, a sumarle las kcal de otro producto.

**Caché en la tabla `Food`.** Cada producto aceptado se guarda en la tabla `Food` que ya existía, con `source = 'OpenFoodFacts'` (el campo ya estaba en el esquema con valor por defecto `'BEDCA'`), así que no hizo falta migración. Se usa `upsert` por nombre con `update` vacío, de modo que, si ya existe un alimento con ese nombre, se conserva tal cual y nunca se sobrescribe uno de BEDCA. Guardarlo en `Food` tiene tres ventajas: la siguiente receta con ese ingrediente no vuelve a llamar a la API, las recetas y las entradas del diario pueden referenciarlo por `foodId` como a cualquier otro alimento, y el cálculo nutricional no necesita ningún cambio.

**Respetar los límites de la API.** Open Food Facts pide identificar a la aplicación con un `User-Agent` descriptivo (`NutriSocial-TFG/1.0 (contacto: …)`), y limita las búsquedas a unas 10 por minuto. Si se superan, o si el servicio está cargado, responde con una página de error en HTML en lugar de JSON. Durante el desarrollo esto pasó a menudo. Por eso el servidor se pone su propio límite de 8 búsquedas por minuto: al alcanzarlo, no llama a la API y trata el ingrediente como no encontrado. Además, recuerda durante 10 minutos las respuestas correctas de cada término. Los fallos no se recuerdan, para volver a intentarlo en la siguiente petición.

**Un fallo externo nunca rompe una receta.** Toda la comunicación con Open Food Facts (tiempo máximo de 5 segundos, errores de red, respuestas HTTP de error, cuerpos que no son JSON) está envuelta de forma que devuelve "sin resultado" en lugar de lanzar una excepción. Si falla el guardado del producto en la base de datos, el ingrediente también queda sin datos, pero la receta se guarda. Como varias consultas lentas en serie podrían sumar muchos segundos, los ingredientes de una receta (y los de una receta escaneada por OCR) se buscan ahora en paralelo.

**Añadir al diario reutilizando el `LogViewModel` compartido.** La pantalla de detalle de receta no tiene ViewModel propio, porque usa el `RecipeViewModel` compartido. En vez de crear otro, se le pasa el `LogViewModel` que `HomeScreen` ya comparte con la pestaña Diario. Así, al añadir la receta, el diario se refresca si está mostrando el día de hoy, con el mismo patrón que ya usaba `addRecommendation`. La fecha es siempre la de hoy, no la que el usuario tenga seleccionada en el Diario, porque la acción significa "me lo estoy comiendo ahora". El estado de esta acción (guardando y mensaje) va en un flujo propio, `quickAddState`, separado de los mensajes del diario, para que cada pantalla muestre solo sus avisos.

**Modo vista y modo edición en el perfil.** `ProfileUiState` gana un campo `isEditing`. La regla es sencilla: el formulario se abre directamente solo si el perfil no permite calcular el objetivo calórico (la primera vez, o si falta algún dato). Con un perfil completo se muestran los datos en solo lectura, y un botón "Modificar" abre el formulario. "Cancelar" descarta lo escrito y restaura los valores del último perfil guardado, reutilizando `withProfile`. Tras guardar se vuelve al modo vista. Hay una excepción: si al guardar todavía falta algún dato para el objetivo, el formulario sigue abierto, que es la misma regla que al cargar.

## Cómo funciona

**Búsqueda de un ingrediente al guardar una receta.** Cuando llega una receta, cada ingrediente sin `foodId` pasa por `matchFood(nombre)`, que ahora sigue tres escalones. Primero, el emparejamiento de siempre, pero solo contra los alimentos de BEDCA: nombre exacto, alias, nombre base, todas las palabras, errores de una letra… (informe 04). Si BEDCA no tiene nada, se miran los productos de Open Food Facts ya guardados en `Food` que cumplan el criterio de parecido; entre varios, se elige el de nombre más corto, que suele ser el más genérico. Solo si tampoco hay ninguno se llama a la API con `searchOpenFoodFacts(nombre)`. La respuesta se filtra (datos completos, nombre parecido) y el primer producto válido se guarda en `Food` y se añade al índice en memoria que `matchFood` mantiene de la tabla. Desde ese momento, ese producto es un alimento más: el cálculo de la receta lo multiplica por los gramos del ingrediente igual que haría con uno de BEDCA.

**Autocompletado.** `searchFoods(consulta)`, que alimenta la lista de sugerencias al escribir un ingrediente o al añadir un alimento al diario, devuelve primero las coincidencias de BEDCA y después las de los productos de Open Food Facts ya guardados. Si en total hay menos de 3 y la consulta tiene al menos 3 letras, consulta también la API. Aquí la última palabra puede estar a medio escribir ("chips de choc"). Los productos nuevos se guardan en `Food` y se añaden al final de la lista, así que la siguiente búsqueda ya los encuentra en local.

**De dónde viene cada dato.** Las respuestas de la API indican el origen: `GET /foods/search` devuelve `source` en cada sugerencia, el detalle de receta lo incluye en el alimento de cada ingrediente, y `POST /recipes/parse-ocr` devuelve `foodSource`. En la aplicación, las sugerencias de Open Food Facts llevan la etiqueta "· Open Food Facts" junto a sus kcal. En el detalle de receta, los ingredientes calculados con esos datos muestran "datos de Open Food Facts", y la línea de fuentes al pie de la información nutricional pasa a decir "BEDCA y, para lo que no está en ella, Open Food Facts".

**Añadir la receta al diario.** En el detalle de una receta aparece un botón flotante "Añadir a mi diario de hoy", con el mismo estilo que los de "Nueva receta" y "Añadir" del diario. Al pulsarlo se abre un diálogo que pregunta cuántas raciones se han comido (por defecto 1, entre 0,1 y 20, igual que en la hoja del diario) y muestra las kcal resultantes antes de confirmar. Al aceptar, `LogViewModel.addRecipeFromDetail` llama a `LogRepository.addRecipe` con la fecha de hoy. Mientras se guarda, el icono del botón pasa a ser un indicador de progreso, y al terminar un *snackbar* confirma "Añadido a tu diario de hoy: …" o muestra el error.

**Perfil.** Al abrir la pestaña con un perfil completo se ven el objetivo calórico destacado (la misma tarjeta de siempre) y una tarjeta "Tus datos" con la fecha de nacimiento y la edad, la altura, el peso, el sexo, el nivel de actividad con su descripción y el objetivo. Debajo está el botón "Modificar". En modo edición aparece el formulario de siempre con "Cancelar" y "Guardar y calcular". "Cancelar" solo se muestra cuando hay un perfil completo al que volver.

**Pruebas.** `test/openFoodFacts.test.js` añade 14 pruebas sin llamar a la API real. `globalThis.fetch` se sustituye por respuestas simuladas, y el cliente de Prisma por una tabla `Food` en memoria. Las pruebas cubren un producto completo que se guarda con `source = 'OpenFoodFacts'` y que la segunda vez sale de la tabla sin volver a llamar a la API, y productos incompletos que se descartan. También cubren los fallos: error de red, tiempo agotado, HTTP 503, cuerpo que no es JSON o fallo al guardar. Todos devuelven `null` sin lanzar. Además, se comprueba el criterio de parecido, que BEDCA tiene prioridad, el autocompletado con el umbral de 3 resultados y el límite de búsquedas por minuto. Aparte de las pruebas, se comprobó a mano contra la API real: "levadura química" devuelve "Levadura química en polvo" (195 kcal/100 g), y "chips de chocolate" ya no devuelve las galletas.

## Limitaciones

- **Open Food Facts es una base colaborativa.** Cualquiera puede subir o editar productos, sin revisión experta. Sus valores son menos fiables que los de BEDCA, que son datos analíticos de composición recopilados por la AESAN, y además varían según la marca: dos "esencias de vainilla" pueden tener composiciones muy distintas, y la aplicación se queda con la primera que encaja. Por eso solo se usa como respaldo cuando BEDCA no tiene el ingrediente, nunca como fuente principal, y la aplicación indica siempre qué valores vienen de ahí.
- **Disponibilidad de un servicio externo.** La búsqueda de Open Food Facts tiene un límite de peticiones y, en momentos de carga, responde con una página de error. Durante el desarrollo esto ocurrió en buena parte de las consultas. En esos casos el ingrediente se queda sin datos, como antes de esta mejora, y se vuelve a intentar en la siguiente receta. Con muchos usuarios a la vez, el límite de 8 búsquedas por minuto lo comparte todo el servidor. La alternativa robusta sería descargar el volcado de datos de Open Food Facts filtrado a España e importarlo, como se hizo con BEDCA.
- **El criterio de parecido deja huecos.** Al exigir que el nombre del producto empiece por el término, algunos productos válidos no se encuentran si en España se llaman de otra forma ("pepitas" o "gotas de chocolate" en lugar de "chips"). Tampoco se aplican las conversiones de unidades específicas del informe 04 (el peso de "1 unidad" o de "1 cucharada") a estos productos.
- **Lo guardado no se actualiza.** Un producto guardado en `Food` no se vuelve a consultar aunque su ficha cambie en Open Food Facts, y las recetas ya creadas conservan sus totales. Tampoco se guarda el código de barras ni la marca del producto elegido.
- **La tabla `Food` de producción.** El informe 08 detectó que la semilla de BEDCA no se había cargado en la base de datos de Neon. Si sigue vacía, al desplegar esta versión todos los ingredientes caerían en Open Food Facts. Antes de desplegar hay que confirmar que `npx prisma db seed` se ha ejecutado en producción.
- **Sin pruebas de interfaz.** Las dos mejoras de Android se han compilado, pero no hay pruebas automáticas de Compose que cubran el diálogo de raciones ni el cambio entre los modos de vista y edición del perfil.

## Archivos creados o modificados

**Backend (`backend/`)**

| Archivo | Cambio |
|---|---|
| `src/nutrition/openFoodFacts.js` (nuevo) | Búsqueda en Open Food Facts con `User-Agent`, tiempo máximo, límite por minuto y caché de respuestas; filtrado de fichas incompletas y criterio de parecido. |
| `src/nutrition/matchFood.js` (modificado) | BEDCA primero; respaldo con los productos guardados y con la API; guardado con `upsert` e índice en memoria; autocompletado ampliado. |
| `src/nutrition/normalize.js` (modificado) | La lista de palabras vacías pasa aquí para compartirla. |
| `src/nutrition/calculate.js` (modificado) | Búsqueda de los ingredientes en paralelo. |
| `src/routes/recipes.js` (modificado) | `source` en el alimento de cada ingrediente; `foodSource` y búsqueda en paralelo en `parse-ocr`. |
| `src/routes/foods.js` (modificado) | `source` en las sugerencias. |
| `test/openFoodFacts.test.js` (nuevo) | 14 pruebas con `fetch` y Prisma simulados. |

**Aplicación Android (`app/src/main/java/com/example/nutrisocial/`)**

| Archivo | Cambio |
|---|---|
| `data/RecipeModels.kt` (modificado) | `source` en `FoodRef` y `FoodSuggestion`, `foodSource` en `OcrIngredient`, e `isOpenFoodFacts`. |
| `ui/recipes/RecipeComponents.kt` (modificado) | `suggestionDetail`: kcal/100 g con la etiqueta de Open Food Facts. |
| `ui/recipes/RecipeFormScreen.kt`, `ui/log/AddEntrySheet.kt` (modificados) | Usan `suggestionDetail` en las sugerencias. |
| `ui/recipes/RecipeViewModel.kt` (modificado) | Conserva el origen del alimento elegido o propuesto por el OCR. |
| `ui/recipes/RecipeDetailScreen.kt` (modificado) | Botón "Añadir a mi diario de hoy", diálogo de raciones, *snackbar* y origen de los datos por ingrediente y al pie. |
| `ui/log/LogViewModel.kt` (modificado) | `QuickAddState`, `addRecipeFromDetail` y `onQuickAddMessageShown`. |
| `ui/home/HomeScreen.kt` (modificado) | Pasa el `LogViewModel` a la ruta de detalle y `onEditToggle` al perfil. |
| `ui/home/ProfileViewModel.kt` (modificado) | `isEditing`, `onEditToggle` (Modificar / Cancelar) e `isComplete`. |
| `ui/home/ProfileScreen.kt` (modificado) | Tarjeta de solo lectura con "Modificar", botón "Cancelar" en el formulario y nueva vista previa. |
